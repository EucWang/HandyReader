plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.wxn.aso.MainKt")
}

dependencies {
    // Kotlin标准库
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // 序列化
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // XML解析
    implementation(libs.jsoup)

    // HTTP客户端 - 用于Task 3的GooglePlayScraper竞品分析
    implementation(libs.okhttp)

    // 测试
    testImplementation(libs.junit)
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.14.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// ASO检查任务（当前4个检查器，即将添加CompetitorCheck）
tasks.register("checkAso") {
    group = "aso"
    description = "运行完整的ASO检查（5个检查器）并生成报告"

    doLast {
        val projectRoot = project.rootProject.projectDir
        val result = project.exec {
            workingDir = project.rootProject.projectDir
            commandLine = listOf(
                "java", "-cp",
                sourceSets.main.get().runtimeClasspath.asPath,
                "com.wxn.aso.MainKt",
                projectRoot.absolutePath
            )
        }
        if (result.exitValue != 0) {
            throw GradleException("ASO检查失败，退出码: ${result.exitValue}")
        }
    }
}

tasks.register("checkAsoQuick") {
    group = "aso"
    description = "运行快速ASO检查（仅元数据和关键词检查）"

    doLast {
        val projectRoot = project.rootProject.projectDir
        val result = project.exec {
            workingDir = project.rootProject.projectDir
            commandLine = listOf(
                "java", "-cp",
                sourceSets.main.get().runtimeClasspath.asPath,
                "com.wxn.aso.MainKt",
                projectRoot.absolutePath,
                "--quick"
            )
        }
        if (result.exitValue != 0) {
            throw GradleException("快速ASO检查失败，退出码: ${result.exitValue}")
        }
    }
}

// 生成HTML报告任务
tasks.register("generateAsoReport") {
    group = "aso"
    description = "运行ASO检查并生成HTML报告"

    doLast {
        val projectRoot = project.rootProject.projectDir
        val result = project.exec {
            workingDir = project.rootProject.projectDir
            commandLine = listOf(
                "java", "-cp",
                sourceSets.main.get().runtimeClasspath.asPath,
                "com.wxn.aso.MainKt",
                projectRoot.absolutePath,
                "--report",
                "html"
            )
        }
        if (result.exitValue != 0) {
            throw GradleException("HTML报告生成失败，退出码: ${result.exitValue}")
        }
    }
}
