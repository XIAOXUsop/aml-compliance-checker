package com.xiaoxu.aml.checker.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor

/**
 * AML 合规 Inspection v0.1：识别 Java 注释中的敏感数据明文。
 *
 * 检测规则：
 *  - 18 位身份证（生日段合法性校验，过滤流水号误报）
 *  - 11 位手机号（1[3-9] 开头）
 *
 * 报警文案只展示掩码预览，不回显完整数据。
 */
class SensitiveDataCommentInspection : LocalInspectionTool() {

    companion object {
        /** 正则包内可见，供单元测试断言等价行为 */
        internal val ID_CARD = Regex("""\d{6}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]""")
        internal val PHONE = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitComment(comment: PsiComment) {
                val text = comment.text

                ID_CARD.find(text)?.let { m ->
                    holder.registerProblem(
                        comment,
                        "AML 合规：注释含疑似身份证号（${maskPreview(m.value)}），请使用脱敏 mock 数据",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                    return
                }

                PHONE.find(text)?.let { m ->
                    holder.registerProblem(
                        comment,
                        "AML 合规：注释含疑似手机号（${maskPreview(m.value)}），请替换为虚拟号码",
                        ProblemHighlightType.WEAK_WARNING
                    )
                }
            }
        }
    }

    private fun maskPreview(raw: String): String {
        if (raw.length <= 6) return "***"
        return raw.take(3) + "****" + raw.takeLast(3)
    }
}

/** 占位 QuickFix：后续版本实现自动替换 */
internal object DesensitizeFix : LocalQuickFix {
    override fun getFamilyName(): String = "替换为脱敏占位值"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // v0.1 手动处理；v0.2 提供 PSI 改写
    }
}
