package com.xiaoxu.aml.checker.inspection

/** 可识别的敏感数据类型 */
enum class SensitiveKind(val displayName: String) {
    ID_CARD("身份证号"),
    BANK_CARD("银行卡号"),
    PHONE("手机号"),
    EMAIL("邮箱"),
}

/**
 * 一次命中：类型 + 在所属元素文本中的 [start, end) 区间 + 原文。
 */
data class SensitiveHit(val kind: SensitiveKind, val start: Int, val end: Int, val raw: String)

/**
 * 敏感数据识别与脱敏规则（纯逻辑，不依赖 IDE，可直接单元测试）。
 *
 * 设计取舍：宁可少报也不误报——身份证号额外做校验位校验、银行卡号额外做 Luhn 校验，
 * 这两步能挡掉绝大多数"长数字流水号"的误报。
 */
object SensitivePatterns {

    /** 18 位身份证：格式粗筛（含合法出生日期段），校验位另做精筛 */
    private val ID_CARD = Regex(
        """(?<![0-9])[1-9][0-9]{5}(?:18|19|20)[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9])"""
    )

    /** 16~19 位连续数字，Luhn 校验另做精筛 */
    private val BANK_CARD = Regex("""(?<![0-9])[0-9]{16,19}(?![0-9])""")

    /** 11 位手机号 */
    private val PHONE = Regex("""(?<![0-9])1[3-9][0-9]{9}(?![0-9])""")

    /** 邮箱（保守匹配，要求常见域名后缀形态） */
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    private val ID_WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private const val ID_CHECK_CHARS = "10X98765432"

    /**
     * 在 [text] 中检出所有启用的敏感项，按出现位置排序。
     *
     * 重叠处理：按 身份证 → 银行卡 → 手机号 → 邮箱 的优先级依次占用区间，
     * 已占用的区间不会被后续规则重复报告（例如 18 位身份证不会再被当成银行卡）。
     */
    fun detect(text: String, enabled: Set<SensitiveKind>): List<SensitiveHit> {
        if (text.isEmpty() || enabled.isEmpty()) return emptyList()

        val hits = mutableListOf<SensitiveHit>()
        val consumed = BooleanArray(text.length)

        fun collect(kind: SensitiveKind, regex: Regex, validate: (String) -> Boolean) {
            if (kind !in enabled) return
            for (match in regex.findAll(text)) {
                val raw = match.value
                if (!validate(raw)) continue
                val start = match.range.first
                val end = match.range.last + 1
                if ((start until end).any { consumed[it] }) continue
                for (i in start until end) consumed[i] = true
                hits += SensitiveHit(kind, start, end, raw)
            }
        }

        collect(SensitiveKind.ID_CARD, ID_CARD, ::isValidIdCard)
        collect(SensitiveKind.BANK_CARD, BANK_CARD, ::isValidLuhn)
        collect(SensitiveKind.PHONE, PHONE) { true }
        collect(SensitiveKind.EMAIL, EMAIL) { true }

        return hits.sortedBy { it.start }
    }

    /** 身份证 18 位校验位（ISO 7064:1983, MOD 11-2） */
    fun isValidIdCard(raw: String): Boolean {
        if (raw.length != 18) return false
        var sum = 0
        for (i in 0..16) {
            val digit = raw[i] - '0'
            if (digit !in 0..9) return false
            sum += digit * ID_WEIGHTS[i]
        }
        return ID_CHECK_CHARS[sum % 11].equals(raw[17], ignoreCase = true)
    }

    /** 银行卡号 Luhn 校验 */
    fun isValidLuhn(raw: String): Boolean {
        if (raw.length !in 16..19) return false
        var sum = 0
        var doubled = false
        for (i in raw.indices.reversed()) {
            var digit = raw[i] - '0'
            if (digit !in 0..9) return false
            if (doubled) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubled = !doubled
        }
        return sum % 10 == 0
    }

    /**
     * 生成等长的脱敏文本，可直接替换原文（保留少量首尾便于人工核对）。
     */
    fun mask(kind: SensitiveKind, raw: String): String = when (kind) {
        SensitiveKind.ID_CARD -> maskKeep(raw, keepFirst = 6, keepLast = 4)
        SensitiveKind.BANK_CARD -> maskKeep(raw, keepFirst = 4, keepLast = 4)
        SensitiveKind.PHONE -> maskKeep(raw, keepFirst = 3, keepLast = 4)
        SensitiveKind.EMAIL -> maskEmail(raw)
    }

    /** 报警文案里的短预览（比正式脱敏更短，避免刷屏） */
    fun preview(kind: SensitiveKind, raw: String): String =
        if (raw.length <= 6) "*".repeat(raw.length)
        else raw.take(3) + "*".repeat(raw.length - 6) + raw.takeLast(3)

    private fun maskKeep(raw: String, keepFirst: Int, keepLast: Int): String {
        if (raw.length <= keepFirst + keepLast) return "*".repeat(raw.length)
        return raw.take(keepFirst) +
                "*".repeat(raw.length - keepFirst - keepLast) +
                raw.takeLast(keepLast)
    }

    private fun maskEmail(raw: String): String {
        val at = raw.indexOf('@')
        if (at <= 0) return "*".repeat(raw.length)
        return raw.first() + "*".repeat(at - 1) + raw.substring(at)
    }
}
