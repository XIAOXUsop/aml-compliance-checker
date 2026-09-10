package com.xiaoxu.aml.checker.inspection

/** 可识别的敏感数据类型 */
enum class SensitiveKind(val displayName: String) {
    ID_CARD("身份证号"),
    BANK_CARD("银行卡号"),
    PHONE("手机号"),
    EMAIL("邮箱"),
}

/**
 * 判定置信度。
 *
 * <p>单一信号（只看值长什么样）必然在"漏报"和"误报"之间二选一：
 * 规则收紧就漏掉测试数据里的假证件号，规则放松就被订单号淹没。
 * 引入第二个信号——**标识符语义**（变量名/字段名/键名）——后可以把两者分开处理。
 */
enum class Confidence {
    /** 值形态自证：格式与校验位（身份证 MOD 11-2 / 银行卡 Luhn）都通过 */
    HIGH,

    /** 标识符名暗示敏感语义，值形似但未通过校验（常见于 mock/测试数据） */
    MEDIUM,
}

/**
 * 一次命中：类型 + 区间 + 原文 + 置信度 + **判定理由**。
 *
 * <p>理由不是装饰：合规告警必须可解释——用户需要知道"为什么说我这行有问题"，
 * 才能判断是真泄漏还是误报。
 */
data class Finding(
    val kind: SensitiveKind,
    val start: Int,
    val end: Int,
    val raw: String,
    val confidence: Confidence,
    val reasons: List<String>,
)

/**
 * 标识符语义提示：把变量名/字段名/键名映射到它"声称"的敏感类型。
 *
 * <p>匹配时忽略大小写、下划线、连字符与常见前缀（get/set 等），
 * 中文命名同样支持。
 */
object IdentifierHints {

    private val ID_CARD = listOf("idcard", "idno", "idnumber", "identity", "identitycard", "certno", "certificate", "身份证", "证件号")
    private val BANK_CARD = listOf("bankcard", "cardno", "cardnumber", "accountno", "bankaccount", "银行卡", "卡号", "账号")
    private val PHONE = listOf("phone", "mobile", "telephone", "cellphone", "phonenumber", "手机", "电话")
    private val EMAIL = listOf("email", "mail", "邮箱")

    /** 返回标识符暗示的类型；无法判定时返回 null */
    fun kindOf(identifier: String?): SensitiveKind? {
        if (identifier.isNullOrBlank()) {
            return null
        }
        val normalized = identifier.lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) {
            return null
        }
        return when {
            ID_CARD.any { normalized.contains(it) } -> SensitiveKind.ID_CARD
            BANK_CARD.any { normalized.contains(it) } -> SensitiveKind.BANK_CARD
            PHONE.any { normalized.contains(it) } -> SensitiveKind.PHONE
            EMAIL.any { normalized.contains(it) } -> SensitiveKind.EMAIL
            else -> null
        }
    }
}

/**
 * 敏感数据识别与脱敏规则（纯逻辑，不依赖 IDE，可直接单元测试）。
 *
 * <p>两轮判定：
 * <ol>
 *   <li><b>值形态</b>（{@link Confidence#HIGH}）：正则粗筛 + 校验位精筛，值本身即可自证；</li>
 *   <li><b>标识符语义</b>（{@link Confidence#MEDIUM}）：上下文变量名暗示敏感语义、
 *       值形似但未通过校验——这类几乎都是开发塞进去的 mock 数据，正是最该提醒的对象。</li>
 * </ol>
 */
object SensitivePatterns {

    /** 18 位身份证：格式粗筛（含合法出生日期段），校验位另做精筛 */
    private val ID_CARD = Regex(
        """(?<![0-9])[1-9][0-9]{5}(?:18|19|20)[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9])"""
    )

    private val BANK_CARD = Regex("""(?<![0-9])[0-9]{16,19}(?![0-9])""")
    private val PHONE = Regex("""(?<![0-9])1[3-9][0-9]{9}(?![0-9])""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    /** 宽松形态：仅在标识符语义已暗示类型时使用，避免单独使用时噪声过大 */
    private val LOOSE_ID_CARD = Regex("""(?<![0-9])[0-9]{15,18}[0-9Xx]?(?![0-9])""")
    private val LOOSE_BANK_CARD = Regex("""(?<![0-9])[0-9]{12,19}(?![0-9])""")
    private val LOOSE_PHONE = Regex("""(?<![0-9])1[0-9]{10}(?![0-9])""")
    private val LOOSE_EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")

    private val ID_WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private const val ID_CHECK_CHARS = "10X98765432"

    /**
     * 在 [text] 中检出所有启用的敏感项，按出现位置排序。
     *
     * @param hint 该文本所处的标识符名（变量名/字段名/键名），用于语义信号；无上下文时传 null
     */
    fun detect(text: String, enabled: Set<SensitiveKind>, hint: String? = null): List<Finding> {
        if (text.isEmpty() || enabled.isEmpty()) {
            return emptyList()
        }

        val findings = mutableListOf<Finding>()
        val consumed = BooleanArray(text.length)

        // 第一轮：值形态自证
        collect(text, enabled, SensitiveKind.ID_CARD, ID_CARD, ::isValidIdCard,
            "符合 18 位身份证格式且校验位有效", Confidence.HIGH, findings, consumed)
        collect(text, enabled, SensitiveKind.BANK_CARD, BANK_CARD, ::isValidLuhn,
            "符合 16~19 位卡号格式且通过 Luhn 校验", Confidence.HIGH, findings, consumed)
        collect(text, enabled, SensitiveKind.PHONE, PHONE, { true },
            "符合 11 位手机号格式", Confidence.HIGH, findings, consumed)
        collect(text, enabled, SensitiveKind.EMAIL, EMAIL, { true },
            "符合邮箱格式", Confidence.HIGH, findings, consumed)

        // 第二轮：标识符语义
        val hintedKind = IdentifierHints.kindOf(hint)
        if (hintedKind != null && hintedKind in enabled) {
            collectHinted(text, hintedKind, hint!!, findings, consumed)
        }

        return findings.sortedBy { it.start }
    }

    private fun collectHinted(text: String, kind: SensitiveKind, hint: String,
                              findings: MutableList<Finding>, consumed: BooleanArray) {
        val (pattern, shape) = when (kind) {
            SensitiveKind.ID_CARD -> LOOSE_ID_CARD to "15~18 位数字"
            SensitiveKind.BANK_CARD -> LOOSE_BANK_CARD to "12~19 位数字"
            SensitiveKind.PHONE -> LOOSE_PHONE to "11 位数字"
            SensitiveKind.EMAIL -> LOOSE_EMAIL to "含 @ 的邮箱形态"
        }
        collect(
            text, setOf(kind), kind, pattern, { true },
            "标识符「$hint」暗示为${kind.displayName}，值形似$shape 但未通过校验（常见于 mock/测试数据）",
            Confidence.MEDIUM, findings, consumed,
        )
    }

    private fun collect(text: String, enabled: Set<SensitiveKind>, kind: SensitiveKind, regex: Regex,
                        validate: (String) -> Boolean, reason: String, confidence: Confidence,
                        findings: MutableList<Finding>, consumed: BooleanArray) {
        if (kind !in enabled) {
            return
        }
        for (match in regex.findAll(text)) {
            val raw = match.value
            if (!validate(raw)) {
                continue
            }
            val start = match.range.first
            val end = match.range.last + 1
            if ((start until end).any { consumed[it] }) {
                continue
            }
            for (i in start until end) {
                consumed[i] = true
            }
            findings += Finding(kind, start, end, raw, confidence, listOf(reason))
        }
    }

    // ---------- 校验 ----------

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

    // ---------- 脱敏 ----------

    /** 生成等长的脱敏文本，可直接替换原文（保留少量首尾便于人工核对） */
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
