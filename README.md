# AML Data Compliance Checker（IDEA 插件）

> 敏感数据合规检查器 — 在代码注释中检测疑似身份证号、手机号，防止真实客户数据流入仓库。

## 背景

源自本人的开源项目 [amlagent](https://github.com/XIAOXUsop/amlagent)（商业银行智能反洗钱尽调 Agent 平台）的工程实践：
在金融代码中，开发常把真实客户样例数据写进注释，这类数据一旦提交仓库就难以追溯清除。
本插件在编辑器内就地报警，把"数据入库前脱敏"理念前移到"写代码时提醒"。

## 功能（v0.1）

- ✅ 18 位身份证检测（含生日段合法性校验，订单流水号不会误报）
- ✅ 11 位手机号检测
- ✅ 报警文案仅显示掩码预览（如 `138****5678`），不回显完整数据
- 📋 计划：邮箱 / 银行卡 / 注释外 PSI 字面量扫描 / 自动替换 QuickFix

## 使用

从本仓库 Actions 下载 `buildPlugin` 产物，IDEA 中：`Settings → Plugins → ⚙ → Install Plugin from Disk`。

## 开发

基于 [JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) 官方模板：

```bash
./gradlew test              # 运行 Inspection 测试
./gradlew runIde            # 沙箱运行 IDE 实测
./gradlew buildPlugin       # 打包 build/distributions/*.zip
```

## 技术

- IntelliJ Platform SDK：自定义 LocalInspectionTool + PSI 注释访问
- Kotlin, Gradle Kotlin DSL
- 报警文案掩码输出（连报警本身也不泄露数据）

## License

MIT
