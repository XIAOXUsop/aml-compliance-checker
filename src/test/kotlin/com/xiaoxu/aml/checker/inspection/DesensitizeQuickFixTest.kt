package com.xiaoxu.aml.checker.inspection

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.nio.file.Files
import java.nio.file.Path

/**
 * QuickFix 测试：走真实意图（Alt+Enter）路径改写文档。
 *
 * <p>被验证的核心性质是**只动该动的那一段**：
 * <ul>
 *   <li>等长替换——长度变了会让后续所有偏移量失效，同一行的第二个命中就会替换错位置；</li>
 *   <li>一行多个命中时，每个 QuickFix 只改写自己那一处；</li>
 *   <li>引号、转义符与其他文本一字不动。</li>
 * </ul>
 * 这些性质用纯函数测不出来：`mask()` 只负责算出替换串，落在哪个区间是 QuickFix 的事。
 */
class DesensitizeQuickFixTest : LightJavaCodeInsightFixtureTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun open(file: String) {
        myFixture.enableInspections(SensitiveDataInspection())
        myFixture.configureByFile("quickfix/$file")
    }

    /** 期望结果直接读 after 文件——它是测试数据的一部分，不是手抄进断言的字符串 */
    private fun expected(file: String): String =
        Files.readString(Path.of(testDataPath, "quickfix", file))

    private fun maskFixes() = myFixture.getAllQuickFixes().filter { it.text.contains("替换为脱敏值") }

    private fun applyFirstFix() {
        val fixes = maskFixes()
        assertTrue("光标处应有可用的脱敏 QuickFix", fixes.isNotEmpty())
        myFixture.launchAction(fixes.first())
    }

    // ---------- 等长替换 ----------

    fun testIdCardIsReplacedWithAnEqualLengthMask() {
        open("BeforeIdCard.java")
        val before = myFixture.file.text

        applyFirstFix()

        assertEquals(expected("AfterIdCard.java"), myFixture.file.text)
        // 长度不变是硬要求：同一行后面的偏移量全部依赖它
        assertEquals("替换必须等长", before.length, myFixture.file.text.length)
    }

    fun testBankCardIsReplacedWithAnEqualLengthMask() {
        open("BeforeBankCard.java")
        val before = myFixture.file.text

        applyFirstFix()

        assertEquals(expected("AfterBankCard.java"), myFixture.file.text)
        assertEquals(before.length, myFixture.file.text.length)
    }

    // ---------- 一行多个命中：每个修复只管自己那一处 ----------

    fun testEachQuickFixTouchesOnlyItsOwnValue() {
        open("BeforeTwoValues.java")
        val idCard = "110101199003078531"
        val phone = "13812345678"

        val fixes = maskFixes()
        assertEquals("两个敏感值应各有一个脱敏修复", 2, fixes.size)

        // 第一次修复后：恰好一个值被脱敏，另一个原样保留。
        // 不假设两个修复的先后顺序——顺序不是这个性质的一部分，
        // "只动自己那一处"才是。
        myFixture.launchAction(fixes[0])
        val afterFirst = myFixture.file.text
        assertEquals("一次修复只该动一个值：$afterFirst",
            1, listOf(idCard, phone).count { !afterFirst.contains(it) })
        assertTrue("另一个值必须原样保留：$afterFirst",
            afterFirst.contains(idCard) || afterFirst.contains(phone))

        // 第二次修复把剩下的那个也脱敏
        val remaining = maskFixes()
        assertEquals("剩下一个值应仍可修复：$afterFirst", 1, remaining.size)
        myFixture.launchAction(remaining.single())

        assertEquals(expected("AfterBothMasked.java"), myFixture.file.text)
    }

    // ---------- 不碰其他文本 ----------

    fun testSurroundingTextAndEscapesAreLeftAlone() {
        open("BeforeEscaped.java")

        applyFirstFix()

        assertEquals(expected("AfterEscaped.java"), myFixture.file.text)
        // 转义序列原样保留——一旦被"解释"过再写回，字符串语义就变了
        assertTrue("转义符不该被改动：${myFixture.file.text}",
            myFixture.file.text.contains("备注\\\"张三\\\""))
    }

    // ---------- 修复后 PSI 与文档一致 ----------

    fun testPsiAndDocumentAreCommittedAfterTheFix() {
        open("BeforeIdCard.java")

        applyFirstFix()

        val document = myFixture.editor.document
        val manager = PsiDocumentManager.getInstance(project)
        assertTrue("修复后必须提交文档，否则 PSI 与文档不一致，后续检查会基于旧文本", manager.isCommitted(document))
        assertEquals("PSI 文本应与文档文本一致", document.text, myFixture.file.text)
    }

    // ---------- 替换后的文本不应再被报警 ----------

    fun testFixedFileNoLongerReportsTheSameFinding() {
        open("BeforeIdCard.java")
        applyFirstFix()

        val remaining = myFixture.doHighlighting().filter { it.description?.contains("AML 合规") == true }

        assertEquals("脱敏后不该再报同一处：${remaining.map { it.description }}", 0, remaining.size)
    }
}
