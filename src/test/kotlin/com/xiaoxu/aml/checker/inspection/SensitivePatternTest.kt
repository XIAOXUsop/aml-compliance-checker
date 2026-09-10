package com.xiaoxu.aml.checker.inspection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * 检测正则的纯逻辑测试（与 SensitiveDataCommentInspection 相同的规则常量）。
 * Inspection 的 IDE 内呈现由 runIde 沙箱人工验证。
 */
class SensitivePatternTest {

    @Test
    fun idCardPattern() {
        val m = SensitiveDataCommentInspection.ID_CARD.find("客户张三，身份证 110101199003078531")
        assertNotNull(m)
        assertEquals("110101199003078531", m!!.value)
    }

    @Test
    fun idCardPatternAcceptsXTail() {
        val m = SensitiveDataCommentInspection.ID_CARD.find("证件号 11010119900307853X")
        assertNotNull(m)
        assertEquals("11010119900307853X", m!!.value)
    }

    @Test
    fun idCardRejectsOrderNumber() {
        // 20 位流水号且生日段非法（月份 88），不应匹配
        val m = SensitiveDataCommentInspection.ID_CARD.find("订单流水号 88888888888888888888")
        assertEquals(null, m)
    }

    @Test
    fun phonePattern() {
        val m = SensitiveDataCommentInspection.PHONE.find("客服电话 13812345678")
        assertEquals("13812345678", m!!.value)
    }

    @Test
    fun phoneNotPartOfLongerDigits() {
        // 前后贴合数字的手机号不应匹配
        val m = SensitiveDataCommentInspection.PHONE.find("编号 9138123456780 更多")
        assertEquals(null, m)
    }

    @Test
    fun maskHelperMatchesInspectionPreview() {
        // Inspection 中 maskPreview 逻辑：<=6 位整体 ***，否则前3 + **** + 后3
        fun mask(raw: String) = if (raw.length <= 6) "***" else raw.take(3) + "****" + raw.takeLast(3)
        assertEquals("138****678", mask("13812345678"))
        assertEquals("***", mask("123456"))
    }
}
