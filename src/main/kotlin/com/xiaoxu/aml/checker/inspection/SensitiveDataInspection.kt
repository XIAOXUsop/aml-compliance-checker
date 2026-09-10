package com.xiaoxu.aml.checker.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralValue
import com.intellij.psi.PsiNamedElement

/**
 * AML 敏感数据检查：在 **注释与字符串字面量** 中识别疑似敏感数据明文。
 *
 * <p>采用**双信号**判定，每条告警都会说明判定依据：
 * <ul>
 *   <li><b>值形态</b>——身份证过 ISO 7064 校验位、银行卡过 Luhn，值本身即可自证；</li>
 *   <li><b>标识符语义</b>——变量名/字段名/键名暗示敏感语义且值形似时，即使校验位不通过也提示
 *       （这类多半是开发塞进去的 mock 数据，恰恰是最该提醒的场景）。</li>
 * </ul>
 *
 * <p>检查范围可在 Settings → Inspections 中逐项开关；
 * 每个命中都提供**一键替换为等长脱敏值**的 QuickFix。
 */
class SensitiveDataInspection : LocalInspectionTool() {

    @JvmField
    var checkIdCard: Boolean = true

    @JvmField
    var checkBankCard: Boolean = true

    @JvmField
    var checkPhone: Boolean = true

    @JvmField
    var checkEmail: Boolean = false

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.checkbox("checkIdCard", "检查身份证号（含校验位验证）"),
        OptPane.checkbox("checkBankCard", "检查银行卡号（含 Luhn 校验）"),
        OptPane.checkbox("checkPhone", "检查手机号"),
        OptPane.checkbox("checkEmail", "检查邮箱（误报较多）"),
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitComment(comment: PsiComment) {
                // 注释里没有标识符上下文，只用值形态信号
                scan(holder, comment, comment.text, hint = null)
            }

            override fun visitElement(element: PsiElement) {
                if (element is PsiLiteralValue && element.value is String) {
                    // 用字面量原文（含引号）扫描，保证命中区间与 PSI 文本偏移一致；
                    // 同时取外层标识符名作为语义信号
                    scan(holder, element, element.text, identifierHint(element))
                }
                super.visitElement(element)
            }
        }

    private fun scan(holder: ProblemsHolder, element: PsiElement, text: String, hint: String?) {
        val enabled = enabledKinds()
        if (enabled.isEmpty()) {
            return
        }
        for (finding in SensitivePatterns.detect(text, enabled, hint)) {
            holder.registerProblem(
                element,
                TextRange(finding.start, finding.end),
                message(finding),
                DesensitizeFix(finding),
            )
        }
    }

    /** 告警文案：短预览 + 判定依据，让用户能自行判断是真泄漏还是误报 */
    private fun message(finding: Finding): String {
        val preview = SensitivePatterns.preview(finding.kind, finding.raw)
        val confidence = when (finding.confidence) {
            Confidence.HIGH -> ""
            Confidence.MEDIUM -> "（置信度中）"
        }
        return "AML 合规：疑似${finding.kind.displayName}$confidence（$preview）。" +
                "判定依据：${finding.reasons.joinToString("；")}"
    }

    /**
     * 向上最多两层取最近的**具名祖先**作为语义提示。
     *
     * <p>只用核心 PSP 的 [PsiNamedElement]（不引入 Java 插件依赖），因此无法精确区分
     * "变量/字段/键名"与"方法名"。这是可接受的：方法名同样携带语义
     * （`setIdCard(...)` 的参数本就该被当作身份证看待），而深度上限保证不会一路爬到类名。
     */
    private fun identifierHint(element: PsiElement): String? {
        var current: PsiElement? = element.parent
        var depth = 0
        while (current != null && depth < 2) {
            if (current is PsiNamedElement) {
                current.name?.let { if (it.isNotBlank()) return it }
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun enabledKinds(): Set<SensitiveKind> = buildSet {
        if (checkIdCard) add(SensitiveKind.ID_CARD)
        if (checkBankCard) add(SensitiveKind.BANK_CARD)
        if (checkPhone) add(SensitiveKind.PHONE)
        if (checkEmail) add(SensitiveKind.EMAIL)
    }
}

/**
 * 一键把命中的敏感数据替换为等长的脱敏值（如 `110101********1234`）。
 */
class DesensitizeFix(private val finding: Finding) : LocalQuickFix {

    override fun getFamilyName(): String = "替换为脱敏值"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val rangeInElement = descriptor.textRangeInElement ?: return
        val file = element.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val start = element.textRange.startOffset + rangeInElement.startOffset
        val end = element.textRange.startOffset + rangeInElement.endOffset
        val masked = SensitivePatterns.mask(finding.kind, finding.raw)

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(start, end, masked)
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
