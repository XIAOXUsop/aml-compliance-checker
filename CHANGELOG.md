<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# AML Data Compliance Checker Changelog

## [Unreleased]

## [0.4.6] - 2026-09-25

### Fixed

- 修复方法实参与 Map 键的语义信号未生效，以及单字符邮箱识别问题；对应源码修复提交 `ed85689`。
- 发布检查现要求 tag 包含该修复，核验下载后的 ZIP 与构建产物一致，并检查包内插件版本和 IDE 兼容下界。

## [0.4.5] - 2026-09-20

### Fixed

- **产物的版本号没跟 tag 走——v0.4.4 的压缩包在 IDEA 里显示成 0.4.1。**
  `release.yml` 里 `./gradlew buildPlugin` 没传版本，于是 `project.version` 取的是
  `gradle.properties` 里那个从 0.4.1 起就没再动过的值。发布流程现在传
  `-Pversion="${TAG#v}"`（实测：`-Pversion=9.9.9` → `aml-compliance-checker-9.9.9.zip`，
  包内 jar 同名），所以**从这个版本起版本号才真正等于 tag**。
- **`gradle.properties` 里的 `version` 也从 0.4.1 更到 0.4.5**——发布走
  `-Pversion`，但本地构建用的仍是这个值，停在 0.4.1 会让本地产物继续叫错名字。
- **`patchChangelog` 同样没传 `-Pversion`**（与上面同一个根因）：它按
  `project.version` 决定往本文件写哪个版本段，不传就取 `gradle.properties` 里那个
  从 0.4.1 起没动过的值——任务以 0 退出、日志 `BUILD SUCCESSFUL`，而文件一个字节没变。
  **本文件里 0.4.2 起的段落都是事后按各版本 Release 说明补录的**，不是发布流程写的。
- **发版断言不再用 `ls ... | head -1` 挑产物。** 那样在只有一个 zip 时是对的
  （CI 里就是），但本地目录里攒着历史产物时，它会按字典序挑中**最快的那一个**：
  2026-09-20 在本机试，`build/distributions/` 下同时有 0.4.1、0.4.5、9.9.9 三个 zip，
  `head -1` 拿到的是 **0.4.1**，断言于是在验一个**根本不是这次构建**的文件。
  现在多于一个就直接失败并列出全部，让人先清目录——**验错对象比验出错更危险，
  后者会红，前者会绿**。

## [0.4.4] - 2026-09-18

### Fixed

- **v0.4.3 那次对 `release.yml` 的修改没有进 tag**（提交时只 `git add` 了单文件，
  改动一直留在工作区），于是 v0.4.3 跑的还是没有加固的版本。本次补上。
- 失败原因也确认了：`nothing to commit, working tree clean`——CHANGELOG 经 v0.4.2
  那次之后没有被改动，所以「有变化才提 PR」的分支把它判成无需提交。

## [0.4.3] - 2026-09-18

### Fixed

- 插件元数据的 `<vendor>` 补上主页链接。IntelliJ 的 schema 要求它带 url 或 email，
  只写名字会在 Marketplace 校验时被提出来——本机 `verifyPlugin` 不因此失败，
  属于**要到上架那天才暴露的缺失**。
- 发布工作流此前**从来没有成功过**：先是 `gh release upload` 默认不覆盖同名 asset，
  而工作流在每次 release 事件上重跑，撞上已存在的 asset 就以 1 退出（加 `--clobber`）。

## [0.4.2] - 2026-09-18

### Added

- 插件包内附带 LICENSE（Apache-2.0）。

## [0.4.1] - 2026-09-18

### Fixed

- 插件包内**没有 LICENSE**。Gradle 默认不会把仓库根的 LICENSE 放进 jar，
  于是分发的产物不带它自己的许可条件。现在通过 `jar { from("LICENSE") { into("META-INF") } }`
  打进 `META-INF/LICENSE`（IntelliJ 的 composedJar 从 jar 派生，会一并带上）。

## [0.4.0] - 2026-09-18

### Fixed

- **Javadoc 注释完全没被扫描。** `PsiDocCommentImpl.accept()` 的分发路径与普通注释不同：
  它调用 `visitElement()` 而不是 `visitComment()`。原先只重写 `visitComment`，
  于是所有 `/** … */` 形式的注释整类漏掉——而示例数据恰恰最常写在 Javadoc 里，
  这个漏报会一直静默存在。现在统一在 `visitElement` 里处理注释与字面量，
  三种注释形态（行 / 块 / Javadoc）都覆盖，且不会重复报警。
- README 的 License 写成了 MIT，而仓库里是 Apache-2.0。

### Added

- **真实 IntelliJ fixture 测试（26 项，测试总数 28 → 54）**：
  <!-- 这里原先写的是「21 项」——那个数**自己就对不上**：28 + 21 = 49，而不是同一行里的 54。
       2026-09-22 在 37856b3 上逐类数了一遍：Fixture 10 + QuickFix 6 + Settings 5 + Registration 5 = 26。 -->
  - Inspection 高亮：加载真实 Java 文件走完整 PSI 流程；断言告警**区间**只覆盖敏感值
    （不含引号、不含整行）、Javadoc / 行注释 / 块注释三种形态、转义字符串下偏移不漂移；
  - QuickFix：等长替换、一行多个命中时每个修复只动自己那一处、转义符与其他文本一字不改、
    修复后 PSI 与 Document 已提交、替换后同一处不再报警；
  - 设置存取往返：选项键名、默认值、`writeSettings`/`readSettings` 往返稳定——
    防止升级后把用户明确关掉的规则又打开；
  - 注册检查：`plugin.xml` 的 `localInspection` 注册、默认开关、级别与 shortName。
- README 增加「扫描边界」一节，明确写出不覆盖的输入（动态拼接、外部资源文件、
  运行时数据、非 Java 语言），并把标识符语义信号说明为启发式而非语义分析。

### Changed

- 删除模板残留：`template-cleanup.yml`、`template-verify.yml`、`.github/template-cleanup/`、
  与插件无关的 rename 模板 testData；`dependabot.yml` 原先指向不存在的 `next` 分支。
- `release.yml` 不再调用 `publishPlugin`——它需要四个本仓库没有的 secrets，
  保留只会让每次发布以必然失败的 job 结束。插件 ZIP 仍会挂到 GitHub Release 上。

## [0.3.0] - 2026-09-10

### Added

- 双信号判定：值形态（校验位/Luhn）+ **标识符语义**（变量名/字段名/键名/方法名）
- 置信度分级（高 / 中），告警文案给出**判定依据**，用户可自行分辨真泄漏与误报

### Changed

- 语义信号专门兜住校验位不合法的 mock 数据——开发塞进代码里的假证件号往往格式像但校验位错，
  纯正则一律放过，而这恰恰是真实数据泄漏最常见的形态
- 只在有标识符上下文时启用语义信号；注释中没有上下文，不做猜测（宁可少报也不误报）

## [0.2.0] - 2026-09-10

### Added

- QuickFix：就地替换为等长脱敏值，如 `110101199003078531` → `110101********8531`
- 扫描范围扩展到字符串字面量（此前仅检查注释）
- 银行卡号检测（16~19 位 + Luhn 校验）
- 邮箱检测（默认关闭，可在设置中逐项开启）
- 身份证 18 位校验位（ISO 7064 MOD 11-2）验证
- 规则的逐项开关（设置面板）
- Inspection 显示名与说明文档，修复设置页显示为未解析 key 的问题
- 17 项离线单元测试，覆盖识别、脱敏与误报控制

### Changed

- 一条文本中的全部命中都会被报告（此前每条仅报告第一个命中）
- 插件描述改为以拉丁字符开场（Plugin Verifier 要求描述前 40 字符为拉丁字符）
- 移除无效的测试 classpath 配置，补上 `testFramework(TestFrameworkType.Platform)` 声明
- CI 触发分支补上 `master`（仓库默认分支），此前 GitHub Actions 从未触发

### Fixed

- `gradlew` 缺少可执行位，导致 CI 以 exit code 126 失败
- 订单流水号等长数字不再被误报为身份证

## [0.1.0] - 2026-09-10

### Added

- 首个版本：在 Java 注释中检测 18 位身份证号与 11 位手机号
- 报警文案仅显示掩码预览，不回显完整敏感数据

[Unreleased]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.6...HEAD
[0.4.6]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.5...0.4.6
[0.4.5]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.4...0.4.5
[0.4.4]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.3...0.4.4
[0.4.3]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.2...0.4.3
[0.4.2]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.1...0.4.2
[0.4.1]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.4.0...0.4.1
[0.4.0]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/XIAOXUsop/aml-compliance-checker/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/XIAOXUsop/aml-compliance-checker/commits/0.1.0
