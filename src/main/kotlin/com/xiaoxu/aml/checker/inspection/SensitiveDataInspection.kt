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

/**
 * AML 敏感数据检查：在 **注释与字符串字面量** 中识别疑似敏感数据明文。
 *
 * 检查范围（可在 Settings → Inspections 中逐项开关）：
 *  - 身份证号：格式 + 18 位校验位双重验证
 *  - 银行卡号：16~19 位 + Luhn 校验
 *  - 手机号：11 位
 *  - 邮箱：默认关闭（误报较多）
 *
 * 每个命中都提供 **一键替换为等长脱敏值** 的 QuickFix；
 * 报警文案只显示短预览，不回显完整敏感数据。
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
                scan(holder, comment, comment.text)
            }

            override fun visitElement(element: PsiElement) {
                if (element is PsiLiteralValue && element.value is String) {
                    // 用字面量原文（含引号）扫描，保证命中区间与 PSI 文本偏移一致
                    scan(holder, element, element.text)
                }
                super.visitElement(element)
            }
        }

    private fun scan(holder: ProblemsHolder, element: PsiElement, text: String) {
        val enabled = enabledKinds()
        if (enabled.isEmpty()) {
            return
        }
        for (hit in SensitivePatterns.detect(text, enabled)) {
            holder.registerProblem(
                element,
                TextRange(hit.start, hit.end),
                message(hit),
                DesensitizeFix(hit),
            )
        }
    }

    private fun message(hit: SensitiveHit): String =
        "AML 合规：注释/字面量含疑似${hit.kind.displayName}（${SensitivePatterns.preview(hit.kind, hit.raw)}），" +
                "请替换为脱敏 mock 数据"

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
class DesensitizeFix(private val hit: SensitiveHit) : LocalQuickFix {

    override fun getFamilyName(): String = "替换为脱敏值"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val rangeInElement = descriptor.textRangeInElement ?: return
        val file = element.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val start = element.textRange.startOffset + rangeInElement.startOffset
        val end = element.textRange.startOffset + rangeInElement.endOffset
        val masked = SensitivePatterns.mask(hit.kind, hit.raw)

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(start, end, masked)
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
