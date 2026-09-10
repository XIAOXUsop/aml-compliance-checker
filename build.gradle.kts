import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
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
