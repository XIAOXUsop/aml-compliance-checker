# AML Data Compliance Checker（IDEA 插件）

> 在 Java 注释与字符串字面量中检测疑似敏感数据明文（身份证号、银行卡号、手机号、邮箱），
> 就地报警并支持**一键替换为脱敏值** —— 防止真实客户数据随代码提交进入仓库。

## 背景

源自本人的开源项目 [amlagent](https://github.com/XIAOXUsop/amlagent)（商业银行智能反洗钱尽调 Agent 平台）的工程实践：
金融代码中，开发常把真实客户样例数据写进注释或字面量，这类数据一旦提交仓库就难以追溯清除。
本插件把"数据入库前脱敏"理念前移到"写代码时提醒"，并给出可一键执行的修复。

## 功能（v0.2）

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

IDEA 中：`Settings → Plugins → ⚙ → Install Plugin from Disk`，选择构建产物 zip；
警报在编辑器中就地出现，`Alt+Enter` 选择「替换为脱敏值」即可。

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
