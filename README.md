# AML Data Compliance Checker（IDEA 插件）

> 在 Java 注释与字符串字面量中检测疑似敏感数据明文（身份证号、银行卡号、手机号、邮箱），
> 就地报警并支持**一键替换为脱敏值** —— 防止真实客户数据随代码提交进入仓库。

## 背景

源自本人的开源项目 [amlagent](https://github.com/XIAOXUsop/amlagent)（商业银行智能反洗钱尽调 Agent 平台）的工程实践：
金融代码中，开发常把真实客户样例数据写进注释或字面量，这类数据一旦提交仓库就难以追溯清除。
本插件把"数据入库前脱敏"理念前移到"写代码时提醒"，并给出可一键执行的修复。

## 功能（v0.3）

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

- ✅ **覆盖注释与字符串字面量**，一处不漏
- ✅ **QuickFix 一键脱敏**：`110101199003078531` → `110101********8531`
- ✅ **校验位/Luhn 双重验证**，订单流水号、随机长数字不会误报
- ✅ **逐项开关**：Settings → Inspections → AML 合规
- ✅ 报警文案只显示短预览（如 `110****531`），连报警本身也不泄露完整数据

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
订单流水号 88888888888888888888   → 20 位，既非合法身份证长度，也不满足 Luhn
110101199003078532                → 格式像身份证，但校验位错误
```

## 使用

**方式一：直接下载安装包**（推荐，无需构建）

1. 下载 [aml-compliance-checker-0.3.0.zip](https://github.com/XIAOXUsop/aml-compliance-checker/releases/latest/download/aml-compliance-checker-0.3.0.zip)
2. IDEA 中 `Settings → Plugins → ⚙ → Install Plugin from Disk`，选择该 zip
3. 重启 IDE

**方式二：从源码构建**

```bash
./gradlew buildPlugin     # 产物在 build/distributions/
```

装好后：警报在编辑器中就地出现，`Alt+Enter` 选择「替换为脱敏值」即可。

## 开发

基于 [JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) 官方模板。

需要 JDK 21。

```bash
./gradlew test              # 运行识别/脱敏逻辑测试（离线）
./gradlew runIde            # 沙箱运行 IDE，实测告警与 QuickFix
./gradlew buildPlugin       # 打包 build/distributions/*.zip
```

> **注意（Windows 中文路径）**：若项目位于含中文的路径下，Gradle 的测试 classpath 会因
> `@argfile` 编码不一致而 `ClassNotFoundException`。解决办法是让守护进程编码与系统一致：
> 在 `~/.gradle/gradle.properties` 中加入
> `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=GBK -Dsun.jnu.encoding=GBK`，或把项目放在纯 ASCII 路径。

## 技术

- IntelliJ Platform SDK：`LocalInspectionTool` + PSI（`PsiComment` / `PsiLiteralValue`）+ `OptPane` 设置项
- Kotlin，Gradle Kotlin DSL
- QuickFix 通过 `WriteCommandAction` 改写文档，保持 PSI 一致
- 识别与脱敏逻辑抽成纯 Kotlin 对象（`SensitivePatterns`），无需 IDE 沙箱即可单测

## License

MIT
