# AML Data Compliance Checker（IDEA 插件）

> 在 Java 注释与字符串字面量中检测疑似敏感数据明文（身份证号、银行卡号、手机号、邮箱），
> 就地报警并支持**一键替换为脱敏值** —— 防止真实客户数据随代码提交进入仓库。

## 背景

源自本人的开源项目 [amlagent](https://github.com/XIAOXUsop/amlagent)（商业银行智能反洗钱尽调 Agent 平台）的工程实践：
金融代码中，开发常把真实客户样例数据写进注释或字面量，这类数据一旦提交仓库就难以追溯清除。
本插件把"数据入库前脱敏"理念前移到"写代码时提醒"，并给出可一键执行的修复。

## 功能

### 双信号判定：不只看值长什么样，还看它"叫什么名字"

单一的值形态信号必然在漏报与误报之间二选一：规则收紧就漏掉测试数据里的假证件号，
规则放松就被订单号淹没。本插件引入**第二个信号——标识符语义**（变量名 / 字段名 / 键名 / 方法名），
两者分开处理并给出**置信度**与**判定依据**：

| 信号 | 置信度 | 触发条件 | 例子 |
|---|---|---|---|
| 值形态 | **高** | 格式 + 校验位都通过，值本身即可自证 | `110101199003078531` 校验位有效 |
| 标识符语义 | 中 | 名字暗示敏感语义、值形似但未通过校验 | 名为 `idCard` 的字段值是 `110101199003078532`（校验位错） |

第二条专门兜住 **mock 数据**：开发塞进代码里的假证件号往往"格式像但校验位错"，
纯正则一律放过——而这恰恰是真实数据泄漏最常见的形态。

```
// 高置信：值形态自证
110101199003078531     → 符合 18 位身份证格式且校验位有效

// 中置信：语义信号兜住（校验位不合法，纯正则会漏）
String idCard = "110101199003078532";

// 无上下文时不猜测：注释里的同一串数字不会被上报
```

每条告警都写清**判定依据**，用户可以自己判断是真泄漏还是误报——合规告警必须可解释。

### 检查项

| 检测项 | 规则 | 默认 |
|---|---|---|
| 身份证号 | 18 位格式 **+ ISO 7064 MOD 11-2 校验位** | 开 |
| 银行卡号 | 16~19 位连续数字 **+ Luhn 校验** | 开 |
| 手机号 | 11 位，`1[3-9]` 开头 | 开 |
| 邮箱 | 常见域名后缀形态 | 关（误报较多） |

- ✅ **扫描 Java PSI 中的注释与字符串字面量**（`PsiComment` 与 `PsiLiteralValue`）
- ✅ **QuickFix 一键脱敏**：`110101199003078531` → `110101********8531`
- ⚠️ **校验位/Luhn 是必要条件之一，不是"不会误报"的保证。** 超出 16~19 位长度窗口的
  长数字、以及校验位算错的身份证不会被报；但**长度落在窗口内、又恰好通过 Luhn 的
  普通数字仍会命中**——实测 16 位订单号里约 **1/10** 满足 Luhn（50 个连续取值中 5 个），
  它们会被标成高置信银行卡号。这是本来就有的误报，别按"随机长数字不会误报"去理解它。
- ✅ **逐项开关**：Settings → Inspections → AML 合规
- ✅ 报警文案只显示短预览（如 `110************531`），连报警本身也不泄露完整数据

### 扫描边界（不覆盖什么）

上面那条"扫描 Java PSI 中的注释与字符串字面量"说的是**当前实现真正扫描的 PSI 节点**。
下列输入不在扫描范围内，请不要把它当作全量数据泄漏防护：

| 不在范围内 | 为什么 |
|---|---|
| 动态拼接 | `"客户" + id + "的卡号是" + card` 每段都是独立的字面量，拼出来的完整值从来不存在于任何一个节点里 |
| 内容不是 PSI 注释/字面量的文件 | `.properties` / `.json` / `.csv` / SQL 脚本实测**不报**——它们的内容既不是 `PsiComment` 也不是 `PsiLiteralValue` |
| 运行时数据 | 插件是静态检查，看不到数据库读出、接口传入、日志打印的内容 |
| 标识符本身 | 只扫注释与字面量；`String idCard;` 这样的声明本身不会被报警 |

> **「非 Java 语言都不在范围内」这句原先写错了**（原文把 Kotlin / Groovy / XML 一并列了进去）。
> `plugin.xml` 里那条 `<localInspection>` **没有 `language` 属性**，inspection 因此对所有语言生效；
> 而 XML 的 `XmlComment` 实现了 `PsiComment`、`XmlAttributeValue` 实现了 `PsiLiteralValue`，
> 两者都会进访问器。**实测**（一份 MyBatis mapper）：
>
> ```
> AML 合规：疑似身份证号（110************531）   覆盖='110101199003078531'   ← XML 注释
> AML 合规：疑似银行卡号（453**********486）    覆盖='4539578763621486'     ← XML 属性值
> ```
>
> SQL 文本节点里的手机号**不**命中（它不是注释也不是字面量）。Kotlin / Groovy / YAML 同理——
> 只要对应语言的 PSI 里有注释或字面量节点就会被扫，**而这取决于装了哪些语言插件**。
>
> **这不算缺陷**：扫 MyBatis mapper 与 `pom.xml` 里的真实数据，正是这个插件想干的事。
> 错的是文档说它不扫。行为现在由 `testXmlCommentsAndAttributeValuesAreScanned` 钉住。

标识符语义信号是**启发式**而非语义分析：它从字面量所在节点向上最多走两层找最近的
具名祖先，拿那个名字做关键词匹配。名字里带 `phone` 的变量会被当成手机号语境看待，
名字起得不像的就不会触发——这是刻意的取舍，因为把判断放宽到"任何形似数字"会让插件被淹没在误报里。

### 效果示意

```java
// 报警前
// 客户张三，身份证 110101199003078531
String card = "4539578763621486";

// Alt+Enter → 替换为脱敏值
// 客户张三，身份证 110101********8531
String card = "4539********1486";
```

## 为什么这些数字不会误报

```
订单流水号 88888888888888888888   → 20 位，超出银行卡号的长度窗口 16~19
110101199003078532                → 格式像身份证，但校验位错误
```

> **上一行原写的是「既不满足 Luhn」，那是错的。** 实测 `88888888888888888888`
> 的 Luhn 和为 0——它**满足**标准 Luhn，拦住它的只有长度。
> 这条理由写错是有代价的：照它去"修"校验位，或者以为"放宽长度也没关系，
> 反正校验位会挡"，都会让流水号开始误报。
>
> 顺带把校验函数的名字也改对了：它原先叫 `isValidLuhn`，实际却把长度窗口
> 一起折在里面（`isValidBankCardNumber`）。**名字只说一半，是这类错误理由的温床。**

## 使用

**方式一：直接下载安装包**（推荐，无需构建）

1. 在 [Releases 页面](https://github.com/XIAOXUsop/aml-compliance-checker/releases/latest)下载最新的
   `aml-compliance-checker-<版本>.zip`
   （0.4.5 的压缩包名、包内 jar 名与插件版本号已经核对为一致；后续版本由发布门禁逐项检查。
   这里不写死版本号，免得每次发版都要回来改，改晚了就是一个 404 的下载链接）
2. IDEA 中 `Settings → Plugins → ⚙ → Install Plugin from Disk`，选择该 zip
3. 重启 IDE

> **0.4.6 发布页已创建。** 该版本的实际 ZIP 尚待独立下载验收；下面是 2026-09-21
> 对上一版 0.4.5 公开产物的逐层核对记录，不能当作 0.4.6 的验收结果：
>
> ```
> aml-compliance-checker-0.4.5.zip                        （32448 字节）
> └── aml-compliance-checker/lib/aml-compliance-checker-0.4.5.jar
>     └── META-INF/plugin.xml →  <version>0.4.5</version>
> ```
>
> 发布流程现在会在上传前把这三处**逐个**与 tag 比一遍，不一致就拒绝上传
> （它们由三条不同的代码路径写出，没有理由假定以后也一起对）。
> 0.4.5 的插件列表版本号与 tag 一致；后续版本仍以实际 ZIP 验收为准。

> **0.4.5 不包含后续修复。** `ed85689` 修复了方法实参、Map 键语义信号与单字符邮箱识别；0.4.6 发布目标提交包含该修复。发布工作流已改为监听 `published`，也可对现有 tag 手动重跑；门禁会检查目标提交，并重新下载上传的 ZIP 逐字节核对。实际 Release ZIP 和干净 IDE 安装冒烟仍待验收；需要这批修复时可先从源码构建。
>
> 顺带把"下载到的包是不是从这份源码构建的"也回答了：把公开的 jar 与**从当前源码
> 重新构建**的 jar 逐条目比对——25 个条目的名字、内容与时间戳**全部相同**，
> 只有 `META-INF/MANIFEST.MF` 里两行环境信息不同（CI 是 `Azul 21.0.12 / Linux`，
> 本机是 `Corretto 21.0.10 / Windows`）。tag `0.4.5` 到 master 之间只有一个
> 只改 CI 配置的提交，没有任何源码改动。
>
> **v0.4.4 及更早的版本不建议使用**——原因见下面的[历史发布问题](#历史发布问题)。
> 那几个 Release 一律**不删除、不覆盖**，只是它们的产物认不出自己是哪一版。

**方式二：从源码构建**

```bash
./gradlew buildPlugin     # 产物在 build/distributions/
```

装好后：警报在编辑器中就地出现，`Alt+Enter` 选择「替换为脱敏值」即可。

## 开发

基于 [JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) 官方模板。

需要 JDK 21。

```bash
./gradlew test              # 58 项测试（纯逻辑 + 真实 IntelliJ fixture），离线
./gradlew runIde            # 沙箱运行 IDE，人工看告警与 QuickFix
./gradlew buildPlugin       # 打包 build/distributions/*.zip
```

测试分两层，覆盖的是两类完全不同的东西：

| 层 | 覆盖什么 | 为什么不能只留一层 |
|---|---|---|
| 纯逻辑（`SensitivePatternsTest` / `SemanticSignalTest`） | 正则、校验位、Luhn、脱敏串 | 快、无沙箱；但测不到"命中之后编辑器里发生了什么" |
| IntelliJ fixture（`SensitiveDataInspectionFixtureTest` / `DesensitizeQuickFixTest` / `InspectionSettingsTest` / `InspectionRegistrationTest`） | 加载真实 Java 文件走完整 PSI 与 inspection 流程、区间、光标处的 QuickFix、设置的存取往返、`plugin.xml` 注册 | 后者才是用户在 IDE 里看到的行为 |

> **注意（Windows 中文路径）**：若项目位于含中文的路径下，Gradle 的测试 classpath 会因
> `@argfile` 编码不一致而 `ClassNotFoundException`。解决办法是让守护进程编码与系统一致：
> 在 `~/.gradle/gradle.properties` 中加入
> `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=GBK -Dsun.jnu.encoding=GBK`，或把项目放在纯 ASCII 路径。

## 技术

- IntelliJ Platform SDK：`LocalInspectionTool` + PSI + `OptPane` 设置项
- Kotlin，Gradle Kotlin DSL
- QuickFix 通过 `WriteCommandAction` 改写文档，保持 PSI 一致
- 识别与脱敏逻辑抽成纯 Kotlin 对象（`SensitivePatterns`），无需 IDE 沙箱即可单测
- fixture 测试用 `LightJavaCodeInsightFixtureTestCase`，需要 `TestFrameworkType.Plugin.Java`
  与 `bundledPlugin("com.intellij.java")`；JUnit 3 形态的基类在 `useJUnitPlatform()` 下
  还需要 `junit-vintage-engine`，三者缺一测试都不会被发现（不是失败，是**不执行**）

## 历史发布问题

> 这一节记录的是**已经修复**的问题，留着它是因为"发布流程怎么会错成这样"比"错了"更值得记。
> **当前版本（v0.4.5 及以后）不受影响**；下面那几个 Release 一律**不删除、不覆盖**。

### v0.4.1 – v0.4.4：产物认不出自己是哪一版

2026-09-19 从 Release 下载核对，v0.4.4 的 asset 展开是这样的：

```
aml-compliance-checker-0.4.1.zip
└── aml-compliance-checker/lib/aml-compliance-checker-0.4.1.jar
    └── META-INF/plugin.xml →  <version>0.4.1</version>
```

**代码是 v0.4.4 的**（发布工作流按 tag 检出源码），只是版本号没跟 tag 走：
`buildPlugin` 当时没传 `-Pversion`，`project.version` 取的是 `gradle.properties`
里那个从 0.4.1 起就没再动过的值。于是三版的产物**全叫 `...-0.4.1.zip`，
而字节数各不相同**：

| 版本 | Release 里的 asset 名 | 字节数 |
|---|---|---|
| v0.4.1 | `aml-compliance-checker-0.4.1.zip` | 31574 |
| v0.4.2 | `aml-compliance-checker-0.4.1.zip` | 31562 |
| v0.4.3 | `aml-compliance-checker-0.4.1.zip` | 31680 |
| v0.4.4 | `aml-compliance-checker-0.4.1.zip` | 31680 |

它们是**四次不同的构建，却顶着同一个名字**。下载到一个 `0.4.1.zip`，你无法从名字
判断它是哪一版；而装在 IDEA 里看到的 0.4.1，也不是它的真实版本。

### 怎么修的（v0.4.5）

- 发布流程传 `-Pversion="${TAG#v}"`，让它**同时**驱动产物名与 `plugin.xml` 的 `<version>`
  （实测：`-Pversion=9.9.9` → `aml-compliance-checker-9.9.9.zip`，包内 jar 同名）；
- `gradle.properties` 里的 `version` 也从 0.4.1 更到 0.4.5——发布走 `-Pversion`，
  但**本地构建**用的仍是这个值，停在 0.4.1 会让本地产物继续叫错名字；
- `patchChangelog` 有**同一个根因**：它按 `project.version` 决定往 CHANGELOG 写哪个版本段，
  不传就取那个从没动过的值——任务以 0 退出、日志 `BUILD SUCCESSFUL`，而文件一个字节没变。
  所以**本仓库 CHANGELOG 里 0.4.2 起的段落都是事后按各版本 Release 说明补录的**，
  不是发布流程写的；
- 上传前把 zip 名 / 包内 jar 名 / `plugin.xml <version>` **逐个**与 tag 比一遍，
  不一致就拒绝上传。三处都查不是啰嗦：它们是三条不同的代码路径写的，
  那次同时错只能说明共用一个来源，**没道理假定以后也一起对**。

### 顺带修掉的另一个问题：断言验错了对象

发版断言原先写的是 `ls build/distributions/*.zip | head -1`。只有一个 zip 时（CI 里就是）
这是对的，但本地目录攒着历史产物时，它会按字典序挑中**最快的那一个**：
2026-09-20 在本机试，目录里同时有 0.4.1、0.4.5、9.9.9 三个 zip，`head -1` 拿到的是
**0.4.1**，断言于是在验一个**根本不是这次构建**的文件。

现在多于一个就直接失败并列出全部，让人先清目录——**验错对象比验出错更危险：
后者会红，前者会绿。**

## License

[Apache-2.0](LICENSE)
