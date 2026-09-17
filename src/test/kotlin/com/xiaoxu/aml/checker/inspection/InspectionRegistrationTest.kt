package com.xiaoxu.aml.checker.inspection

import com.intellij.codeInspection.InspectionEP
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * 插件注册本身。
 *
 * <p>这一层单独拿出来测，是因为它坏掉时**什么都不会发生**：inspection 的 fixture 测试
 * 直接 new 出工具实例，绕过了 plugin.xml；于是即使 `<localInspection>` 被误删，
 * 那几十项测试依然全绿，而插件在真实 IDE 里已经无声无息了。
 *
 * <p>这里读的是测试沙箱里真正加载起来的插件描述符 —— 也就是 IDE 启动时看到的同一份。
 */
class InspectionRegistrationTest : LightJavaCodeInsightFixtureTestCase() {

    private val registered: List<InspectionEP>
        get() = ExtensionPointName.create<InspectionEP>("com.intellij.localInspection")
            .extensionList
            .filter { it.shortName == SHORT_NAME }

    fun testPluginDescriptorIsLoaded() {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))

        assertNotNull("插件 $PLUGIN_ID 没有被加载——plugin.xml 的 id 或构建配置出问题了", descriptor)
        assertEquals("AML Data Compliance Checker", descriptor!!.name)
    }

    fun testInspectionIsRegisteredExactlyOnce() {
        // 注册两次会让同一处报两条一模一样的告警
        assertEquals("localInspection 扩展点里应恰好有一条本检查的注册", 1, registered.size)
    }

    fun testRegisteredExtensionInstantiatesTheRealInspection() {
        val tool = registered.single().instantiateTool()

        assertEquals(SensitiveDataInspection::class.java.name, tool?.javaClass?.name)
    }

    fun testRegistrationKeepsTheDocumentedMetadata() {
        val ep = registered.single()

        // 默认开启是"装完就有用"的前提；级别用 WARNING 而非 ERROR——
        // 误报不该在别人的工程里直接标红
        assertTrue("默认应开启", ep.enabledByDefault)
        assertEquals("WARNING", ep.level)
        assertEquals("AML 敏感数据检查", ep.displayName)
    }

    /** shortName 是用户设置落盘时的键，改了它等于让所有人的既有设置失效 */
    fun testShortNameMatchesTheOneUsedInSettings() {
        assertEquals(SHORT_NAME, registered.single().shortName)
        assertTrue("shortName 不应含空格或点", SHORT_NAME.all { it.isLetterOrDigit() || it == '_' })
    }


    private companion object {
        const val PLUGIN_ID = "com.xiaoxu.aml.checker"
        const val SHORT_NAME = "AmlSensitiveData"
    }
}
