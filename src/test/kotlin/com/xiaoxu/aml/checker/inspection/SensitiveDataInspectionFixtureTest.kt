package com.xiaoxu.aml.checker.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Inspection 高亮测试：加载**真实 Java 文件**，走完整的 PSI 解析与 inspection 流程。
 *
 * <p>本类补上的是一个真实缺口：`SensitivePatterns` 与 `IdentifierHints` 一直是纯函数，
 * 单测覆盖得很好，但从"正则命中"到"编辑器里出现一条告警"之间还隔着好几步——
 * 访问器有没有被调用、区间算得对不对、标识符上下文取到没有、设置开关有没有生效。
 * 这些一步都不能少，而一步都没被自动验证过。
 *
 * <p>断言刻意用**字节区间**而不是行号：区间才是用户看到的波浪线范围。
 * "报警区间只覆盖敏感值、不覆盖引号和整行"是这组测试的主要目的之一。
 */
class SensitiveDataInspectionFixtureTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun inspection(
        idCard: Boolean = true,
        bankCard: Boolean = true,
        phone: Boolean = true,
        email: Boolean = false,
    ) = SensitiveDataInspection().apply {
        checkIdCard = idCard
        checkBankCard = bankCard
        checkPhone = phone
        checkEmail = email
    }

    /** 加载文件并把本项目 inspection 产生的告警挑出来（其余是语法/平台自带项） */
    private fun findings(file: String, tool: SensitiveDataInspection = inspection()): List<HighlightInfo> {
        myFixture.enableInspections(tool)
        myFixture.configureByFile("inspection/$file")
        return myFixture.doHighlighting().filter { it.description?.contains("AML 合规") == true }
    }

    /**
     * **XML 文件也会被扫**——这条把既有行为钉住，因为文档曾把它写反。
     *
     * <p>README 的「扫描边界」表原先写着「非 Java 语言：Kotlin / Groovy / **XML** /
     * 前端代码都不在扫描范围内」。实测不是这样：`plugin.xml` 里那条
     * `<localInspection>` **没有 `language` 属性**，于是 inspection 对所有语言生效，
     * 而 `XmlComment` 实现了 `PsiComment`、`XmlAttributeValue` 实现了 `PsiLiteralValue`，
     * 两者都会进 `visitElement`。
     *
     * <p>实测（本用例的数据）：一份 MyBatis mapper 里，
     * **注释里的身份证**与**属性值里的银行卡号**各命中 1 条；
     * SQL 文本节点里的手机号**不**命中（它不是注释也不是字面量）。
     *
     * <p>这算不算 bug？**不算**——扫描 MyBatis mapper 与 `pom.xml` 里的真实数据正是
     * 这个插件想干的事。错的是文档说它不扫。所以这里修的是文档，并把行为钉住，
     * 免得两边再次漂开。
     */
    fun testXmlCommentsAndAttributeValuesAreScanned() {
        val hits = findings("probe.xml")
        assertEquals("XML 里应当命中 2 处（注释里的身份证、属性值里的卡号）", 2, hits.size)
        val covered = hits.map { coveredText(it) }.toSet()
        assertTrue("注释里的身份证号应当被覆盖，实际：$covered", "110101199003078531" in covered)
        assertTrue("属性值里的银行卡号应当被覆盖，实际：$covered", "4539578763621486" in covered)
    }

    /**
     * **方法调用的实参与 Map 字面量的值，也要能拿到语义提示。**
     *
     * <p>`identifierHint` 原先只向上找两层内的 `PsiNamedElement`，并带一句注释说
     * "方法名同样携带语义（`setIdCard(...)` 的参数本就该被当作身份证看待）"——
     * **那句话描述的正是唯一不成立的场景**：`PsiMethodCallExpression` 不是
     * `PsiNamedElement`，所以方法实参永远取不到提示。
     *
     * <p>而这正是最该提醒的形态：测试与 mock 数据几乎都写成
     * `assertEquals("1101…", …)`、`buildDto("4539…")`、`Map.of("cardNo", "4539…")`。
     * 实测（2026-09-22）：修之前本用例的两条命中都是**空**。
     *
     * <p>两个值都故意不通过校验位，所以"报不报"完全取决于方法名 / 键名取没取到，
     * 值形态那一轮对它们是沉默的。最后两行是**对照**：方法名无语义时不报，
     * 免得把"什么实参都报"当成修好了。
     */
    fun testMethodArgumentAndMapKeyHintsAreUsed() {
        val hits = findings("MethodArgHints.java")

        assertEquals("方法实参与 Map 值应各命中 1 条，实际：${hits.map { coveredText(it) }}",
            2, hits.size)
        val covered = hits.map { coveredText(it) }.toSet()
        assertTrue("setIdCard(...) 的实参应被命中，实际：$covered", "110101199003078532" in covered)
        assertTrue("Map.of(\"cardNo\", ...) 的值应被命中，实际：$covered", "4539578763621480" in covered)
    }

    /** 一条告警实际覆盖的原文——用它来核对区间，比行号精确 */
    private fun coveredText(info: HighlightInfo): String =
        myFixture.file.text.substring(info.startOffset, info.endOffset)

    // ---------- 注释：三种注释形态都要覆盖 ----------

    fun testIdCardInCommentIsReportedWithExactRange() {
        val hits = findings("IdCardInComment.java")

        // 两条都是 Javadoc（/** … */）。这类注释走的是 visitElement 而不是 visitComment，
        // 只重写 visitComment 会让它们整类漏掉——而示例数据最常写在 Javadoc 里。
        assertEquals("Javadoc 中的两处身份证应各报一次", 2, hits.size)
        hits.forEach {
            assertEquals("110101199003078531", coveredText(it))
            assertTrue("应说明判定依据：${it.description}", it.description!!.contains("校验位有效"))
        }
    }

    fun testAlertTextDoesNotLeakTheFullValue() {
        val hits = findings("IdCardInComment.java")

        // 连报警本身也不该把完整证件号摊在编辑器和日志里
        hits.forEach {
            assertTrue("告警文案泄露了完整原文：${it.description}",
                !it.description!!.contains("110101199003078531"))
        }
    }

    // ---------- 字符串字面量 ----------

    fun testBankCardInStringIsReportedWithExactRange() {
        val hits = findings("BankCardInString.java")

        assertEquals(1, hits.size)
        // 区间必须只盖住数字本身，不含两侧引号
        assertEquals("4539578763621486", coveredText(hits.single()))
        assertTrue("应说明判定依据：${hits.single().description}",
            hits.single().description!!.contains("Luhn"))
    }

    fun testEmailIsOffByDefaultAndOnWhenEnabled() {
        assertEquals("邮箱默认关闭，不该有告警", 0, findings("EmailInString.java").size)

        val enabled = findings("EmailInString.java", inspection(email = true))
        assertEquals(1, enabled.size)
        assertEquals("zhangsan@example.com", coveredText(enabled.single()))
    }

    fun testDisabledKindIsSilent() {
        assertEquals("关掉银行卡检查后不该再有告警",
            0, findings("BankCardInString.java", inspection(bankCard = false)).size)
    }

    // ---------- 双信号 ----------

    fun testMockIdCardWithSensitiveFieldNameIsReportedAsMediumConfidence() {
        val hits = findings("MockIdCardByFieldName.java")

        assertEquals(1, hits.size)
        assertEquals("110101199003078532", coveredText(hits.single()))
        // 校验位不合法，只有语义信号能兜住它
        assertTrue("应标为中等置信：${hits.single().description}",
            hits.single().description!!.contains("置信度中"))
        assertTrue("应指出是哪个标识符带来的判断：${hits.single().description}",
            hits.single().description!!.contains("idCard"))
    }

    fun testInvalidChecksumWithoutSensitiveContextIsNotReported() {
        // 声明自己语义的才是线索；一个叫 orderNo 的普通变量里的数字不该被当成证件号
        assertEquals(0, findings("InvalidChecksumWithoutContext.java").size)
    }

    // ---------- 转义与多命中 ----------

    fun testEscapedStringKeepsOffsetsAligned() {
        val hits = findings("EscapedString.java")

        assertEquals(1, hits.size)
        val hit = hits.single()
        // 字符串里的 \" 是 PSI 原文的一部分，区间必须按 PSI 文本算，
        // 否则会整体左移、把引号或转义符一起圈进去
        val raw = myFixture.file.text
        assertEquals(raw.indexOf("110101199003078531"), hit.startOffset)
        assertEquals("110101199003078531", coveredText(hit))
    }

    fun testMultipleValuesInOneLineAreEachReported() {
        val hits = findings("TwoValuesInOneLine.java")

        // 两个值紧邻，各自的区间必须互不含混
        assertEquals(2, hits.size)
        assertEquals(setOf("110101199003078531", "13812345678"), hits.map { coveredText(it) }.toSet())
    }

    fun testCleanFileProducesNoFindings() {
        assertEquals(0, findings("NoSensitiveData.java").size)
    }
}
