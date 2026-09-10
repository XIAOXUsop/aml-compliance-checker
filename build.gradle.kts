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
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-Xmx2g")
        // 平台把 classpath 换成了 sandbox jar，这里在原有基础上追加 test 输出目录
        classpath = classpath + files(layout.buildDirectory.dir("classes/kotlin/test"))
        testClassesDirs = sourceSets["test"].output.classesDirs
        // 排除 IDEA platform 自带的 platform-test 内部测试，避免 ThreadLeakTracker/vintage bootstrap 污染
        exclude("**/*BootstrapTests*", "**/*_LastInSuiteTest*", "**/*NewIdentifierWatcherTest*")
    }
}

kotlin {
    jvmToolchain(21)
}
