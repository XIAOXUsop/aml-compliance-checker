import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // fixture 测试的基类（BasePlatformTestCase / LightJavaCodeInsightFixtureTestCase）
    // 继承自 junit.framework.TestCase，必须编译期可见，不能只放在 runtimeOnly
    testImplementation("junit:junit:4.13.2")
    // fixture 测试是 JUnit 3 形态（BasePlatformTestCase 继承 TestCase），
    // 在 useJUnitPlatform() 下必须由 vintage 引擎来发现与执行
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")

    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
        // Java 插件的测试框架：inspection / quickfix 的 fixture 测试需要真实的 Java PSI，
        // 只有 Platform 测试框架拿不到 JavaLanguage 与 LightJavaCodeInsightFixtureTestCase
        testFramework(TestFrameworkType.Plugin.Java)
        // java-test-framework 引用了 Java 插件的生产类（如 PsiElementFactory），
        // 不显式声明 bundled plugin 时测试 JVM 里会在类解析阶段就抛 NoClassDefFoundError
        bundledPlugin("com.intellij.java")
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-Xmx2g")
        // 排除 IDEA platform 自带的 platform-test 内部测试，避免 ThreadLeakTracker/vintage bootstrap 污染
        exclude("**/*BootstrapTests*", "**/*_LastInSuiteTest*", "**/*NewIdentifierWatcherTest*")
    }
}

kotlin {
    jvmToolchain(21)
}
