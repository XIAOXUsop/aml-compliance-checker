package com.xiaoxu.aml.checker.inspection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 敏感数据识别与脱敏的纯逻辑测试（无需 IDE 沙箱）。
 * Inspection 在编辑器内的呈现与 QuickFix 由 runIde 沙箱人工验证。
 */
class SensitivePatternsTest {

    private val allKinds = SensitiveKind.entries.toSet()

    // ---------- 身份证 ----------

    @Test
    fun idCardValidChecksumAccepted() {
        assertTrue(SensitivePatterns.isValidIdCard("110101199003078531"))
    }

    @Test
    fun idCardWrongChecksumRejected() {
        // 格式合法但校验位错误
        assertFalse(SensitivePatterns.isValidIdCard("110101199003078532"))
    }

    @Test
    fun idCardDetectedInCommentLikeText() {
        val hits = SensitivePatterns.detect("// 客户张三，身份证 110101199003078531，北京户口", allKinds)

        assertEquals(1, hits.size)
        assertEquals(SensitiveKind.ID_CARD, hits[0].kind)
        assertEquals("110101199003078531", hits[0].raw)
    }

    // ---------- 银行卡 ----------

    @Test
    fun bankCardLuhnValidAccepted() {
        assertTrue(SensitivePatterns.isValidLuhn("4539578763621486"))
    }

    @Test
    fun bankCardLuhnInvalidRejected() {
        assertFalse(SensitivePatterns.isValidLuhn("4539578763621487"))
    }

    @Test
    fun bankCardDetectedInStringLiteralLikeText() {
        val hits = SensitivePatterns.detect("\"4539578763621486\"", allKinds)

        assertEquals(1, hits.size)
        assertEquals(SensitiveKind.BANK_CARD, hits[0].kind)
        assertEquals("4539578763621486", hits[0].raw)
    }

    // ---------- 手机号 ----------

    @Test
    fun phoneDetected() {
        val hits = SensitivePatterns.detect("// 客服回访电话 13812345678", allKinds)

        assertEquals(1, hits.size)
        assertEquals(SensitiveKind.PHONE, hits[0].kind)
    }

    @Test
    fun phoneNotPartOfLongerDigits() {
        val hits = SensitivePatterns.detect("编号 9138123456780 更多", allKinds)
        assertTrue(hits.none { it.kind == SensitiveKind.PHONE })
    }

    // ---------- 误报控制 ----------

    @Test
    fun orderNumberNotReported() {
        // 20 位纯数字流水号：既非合法身份证（校验位/长度不符），也非 Luhn 合法卡号
        val hits = SensitivePatterns.detect("// 订单流水号 88888888888888888888", allKinds)
        assertTrue(hits.isEmpty(), "流水号不应误报，实际命中：$hits")
    }

    @Test
    fun idCardNotDoubleReportedAsBankCard() {
        val hits = SensitivePatterns.detect("110101199003078531", allKinds)
        assertEquals(1, hits.size)
        assertEquals(SensitiveKind.ID_CARD, hits[0].kind)
    }

    // ---------- 多命中与开关 ----------

    @Test
    fun reportsAllMatchesInOrder() {
        val text = "身份证 110101199003078531 与手机号 13812345678"
        val hits = SensitivePatterns.detect(text, allKinds)

        assertEquals(2, hits.size)
        assertEquals(SensitiveKind.ID_CARD, hits[0].kind)
        assertEquals(SensitiveKind.PHONE, hits[1].kind)
        assertEquals("110101199003078531", text.substring(hits[0].start, hits[0].end))
        assertEquals("13812345678", text.substring(hits[1].start, hits[1].end))
    }

    @Test
    fun disabledKindIsNotReported() {
        val text = "联系 zhang.san@example.com 或 13812345678"

        assertTrue(SensitivePatterns.detect(text, setOf(SensitiveKind.PHONE)).none { it.kind == SensitiveKind.EMAIL })
        assertEquals(
            SensitiveKind.EMAIL,
            SensitivePatterns.detect(text, setOf(SensitiveKind.EMAIL)).single().kind,
        )
    }

    @Test
    fun emptyEnabledSetYieldsNothing() {
        assertTrue(SensitivePatterns.detect("110101199003078531", emptySet()).isEmpty())
    }

    // ---------- 脱敏 ----------

    @Test
    fun maskIsLengthPreservingAndKeepsEdges() {
        val masked = SensitivePatterns.mask(SensitiveKind.ID_CARD, "110101199003078531")

        assertEquals(18, masked.length)
        assertEquals("110101********8531", masked)
    }

    @Test
    fun maskPhoneKeepsPrefixAndSuffix() {
        assertEquals("138****5678", SensitivePatterns.mask(SensitiveKind.PHONE, "13812345678"))
    }

    @Test
    fun maskEmailKeepsFirstCharAndDomain() {
        assertEquals("z*******@example.com", SensitivePatterns.mask(SensitiveKind.EMAIL, "zhangsan@example.com"))
    }

    @Test
    fun previewHidesMostOfValue() {
        val preview = SensitivePatterns.preview(SensitiveKind.PHONE, "13812345678")

        assertEquals("138*****678", preview)
        assertFalse(preview.contains("12345678"))
    }
}
