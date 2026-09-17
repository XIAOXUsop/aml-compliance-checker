package com.xiaoxu.aml.checker.inspection

import com.intellij.codeInspection.options.OptCheckbox
import org.jdom.Element
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * 设置项的默认值与存取往返。
 *
 * <p>要防的具体故障：用户关掉了「检查邮箱」，插件升级后设置被重置回默认的开启状态——
 * 于是编辑器里突然多出一堆他明确关掉的告警。这类问题的根因通常是**选项键名漂移**
 * （字段改名、OptPane 的 bindId 与字段对不上），而它不会以任何测试失败的形式暴露：
 * 读不到就静默用默认值。
 *
 * <p>所以这里断言三件事：选项键名、默认值、以及「写出去再读回来」的结果与写之前一致。
 */
class InspectionSettingsTest : LightJavaCodeInsightFixtureTestCase() {

    // ---------- 选项键名 ----------

    fun testOptionsAreExactlyTheFourDocumentedCheckboxes() {
        val bindIds = SensitiveDataInspection().optionsPane.components.map { (it as OptCheckbox).bindId }

        // 这些字符串就是用户设置落盘时的键。改了名字 = 所有人的既有设置失效，
        // 而且不会报错，只会静默回到默认值。
        assertEquals(listOf("checkIdCard", "checkBankCard", "checkPhone", "checkEmail"), bindIds)
    }

    fun testEveryOptionHasAReadableLabel() {
        SensitiveDataInspection().optionsPane.components.forEach { component ->
            val label = (component as OptCheckbox).label.toString()
            assertTrue("设置项缺少说明文字：$component", label.isNotBlank())
        }
    }

    // ---------- 默认值 ----------

    fun testDefaultsMatchTheDocumentedTable() {
        val tool = SensitiveDataInspection()

        assertTrue("身份证默认开启", tool.checkIdCard)
        assertTrue("银行卡默认开启", tool.checkBankCard)
        assertTrue("手机号默认开启", tool.checkPhone)
        // 邮箱误报较多，默认关闭——这条如果被改掉，用户会突然收到大量告警
        assertFalse("邮箱默认关闭", tool.checkEmail)
    }

    // ---------- 存取往返 ----------

    /**
     * 走 IDEA 自己保存/恢复 inspection 设置的那两个方法。
     *
     * <p>不模拟"用户点了设置界面"——那层由平台负责；真正属于本插件的是
     * [com.intellij.codeInspection.InspectionProfileEntry.writeSettings] /
     * [com.intellij.codeInspection.InspectionProfileEntry.readSettings] 里
     * 字段名与 OptPane bindId 的对应关系，那正是会悄悄坏掉的部分。
     */
    fun testToggledSettingsSurviveAWriteReadRoundTrip() {
        val saved = SensitiveDataInspection().apply {
            checkEmail = true   // 用户打开
            checkPhone = false  // 用户关闭
        }

        val node = Element("inspection_tool")
        saved.writeSettings(node)
        val xml = com.intellij.openapi.util.JDOMUtil.write(node)
        assertTrue("设置应当被序列化到 XML 里：$xml", xml.contains("checkEmail"))

        val restored = SensitiveDataInspection()
        restored.readSettings(node)

        assertTrue("用户打开的规则在恢复后应保持打开", restored.checkEmail)
        assertFalse("用户关掉的规则在恢复后必须保持关闭，否则升级会把用户的选择重置回去",
            restored.checkPhone)
        // 没被改动的项保持默认
        assertTrue(restored.checkIdCard)
        assertTrue(restored.checkBankCard)
    }

    /** 往返两次的结果必须稳定——第二次读回来不能又变回默认 */
    fun testRoundTripIsStableAcrossRepeatedSaveAndLoad() {
        var tool = SensitiveDataInspection().apply { checkIdCard = false }
        repeat(3) {
            val node = Element("inspection_tool")
            tool.writeSettings(node)
            tool = SensitiveDataInspection().apply { readSettings(node) }
        }

        assertFalse("三轮存取之后设置仍然必须保持", tool.checkIdCard)
    }
}
