package com.xiaoxu.aml.checker.inspection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 双信号判定的测试：值形态（HIGH）与标识符语义（MEDIUM）。
 *
 * 设计意图：单一的值形态信号必然在漏报与误报之间二选一。
 * 开发塞进代码里的 mock 证件号往往<b>校验位不合法</b>，纯正则一律放过——
 * 而这恰恰是"真实数据泄漏"最常见的形态。标识符语义信号专门用来兜住这一类。
 */
class SemanticSignalTest {

    private val allKinds = SensitiveKind.entries.toSet()

    // ---------- 标识符语义映射 ----------

    @Test
    fun mapsCommonEnglishNames() {
        assertEquals(SensitiveKind.ID_CARD, IdentifierHints.kindOf("idCard"))
        assertEquals(SensitiveKind.ID_CARD, IdentifierHints.kindOf("id_no"))
        assertEquals(SensitiveKind.BANK_CARD, IdentifierHints.kindOf("bankCardNo"))
        assertEquals(SensitiveKind.PHONE, IdentifierHints.kindOf("mobile"))
        assertEquals(SensitiveKind.EMAIL, IdentifierHints.kindOf("userEmail"))
    }

    @Test
    fun mapsChineseNames() {
        assertEquals(SensitiveKind.ID_CARD, IdentifierHints.kindOf("身份证"))
        assertEquals(SensitiveKind.BANK_CARD, IdentifierHints.kindOf("卡号"))
        assertEquals(SensitiveKind.PHONE, IdentifierHints.kindOf("手机"))
    }

    @Test
    fun unrelatedIdentifierYieldsNoHint() {
        assertNull(IdentifierHints.kindOf("orderNo"))
        assertNull(IdentifierHints.kindOf("totalAmount"))
        assertNull(IdentifierHints.kindOf(null))
        assertNull(IdentifierHints.kindOf("  "))
    }

    // ---------- 语义信号兜住校验位不合法的 mock 数据 ----------

    @Test
    fun mockIdCardWithInvalidChecksumIsCaughtWhenIdentifierHintsIt() {
        // 校验位错误：纯值形态判定会放过，但它出现在名为 idCard 的字段里
        val findings = SensitivePatterns.detect("110101199003078532", allKinds, hint = "idCard")

        assertEquals(1, findings.size)
        assertEquals(Confidence.MEDIUM, findings[0].confidence)
        assertEquals(SensitiveKind.ID_CARD, findings[0].kind)
    }

    @Test
    fun sameValueWithoutHintIsNotReported() {
        // 没有上下文时不做猜测：这正是"宁可少报也不误报"的边界
        assertTrue(SensitivePatterns.detect("110101199003078532", allKinds, hint = null).isEmpty())
    }

    @Test
    fun validValueStaysHighAndIsNotDoubleReported() {
        val findings = SensitivePatterns.detect("110101199003078531", allKinds, hint = "idCard")

        assertEquals(1, findings.size, "值形态已自证，不应再产生一条语义命中：$findings")
        assertEquals(Confidence.HIGH, findings[0].confidence)
    }

    @Test
    fun loosePhoneIsCaughtWhenIdentifierHintsIt() {
        // 第二位是 0，不满足严格手机号格式（1[3-9] 开头），只能靠语义信号兜住
        var findings = SensitivePatterns.detect("10000000000", allKinds, hint = "mobile")
        assertEquals(1, findings.size, "语义命中应生效：$findings")
        assertEquals(Confidence.MEDIUM, findings[0].confidence)
        assertEquals(SensitiveKind.PHONE, findings[0].kind)

        // 不在手机号语义下时，同样的数字不应当被当作手机号
        findings = SensitivePatterns.detect("10000000000", allKinds, hint = "orderNo")
        assertTrue(findings.none { it.kind == SensitiveKind.PHONE }, "无提示时不应猜测：$findings")
    }

    @Test
    fun hintDoesNotCrossTypes() {
        // 名字说是手机号，但值是身份证的形态 —— 不应按身份证上报，
        // 也不应因为"手机号"提示就把 18 位数字当手机号
        val findings = SensitivePatterns.detect("110101199003078531", allKinds, hint = "mobile")
        assertTrue(findings.none { it.confidence == Confidence.MEDIUM })
    }

    // ---------- 可解释性 ----------

    @Test
    fun reasonExplainsWhyItWasFlagged() {
        val finding = SensitivePatterns.detect("110101199003078532", allKinds, hint = "idCard").single()

        val reason = finding.reasons.single()
        assertTrue(reason.contains("idCard"), "理由应指出触发的标识符：$reason")
        assertTrue(reason.contains("未通过校验"), "理由应说明为何只是中等置信度：$reason")
    }

    @Test
    fun highConfidenceReasonCitesTheValidation() {
        val finding = SensitivePatterns.detect("110101199003078531", allKinds).single()

        assertTrue(finding.reasons.single().contains("校验位"), finding.reasons.single())
    }

    @Test
    fun disabledKindSuppressesSemanticSignalToo() {
        val findings = SensitivePatterns.detect(
            "110101199003078532", setOf(SensitiveKind.PHONE), hint = "idCard")

        assertTrue(findings.isEmpty(), "身份证检查关闭时，语义信号也不应触发：$findings")
    }
}
