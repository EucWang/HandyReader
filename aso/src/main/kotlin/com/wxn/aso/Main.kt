package com.wxn.aso

import com.wxn.aso.model.displayName
import com.wxn.aso.report.ReportGenerator
import com.wxn.aso.report.ReportGenerationException
import com.wxn.aso.report.UnsupportedFormatException
import com.wxn.aso.util.AnsiColor
import com.wxn.aso.util.boldBlue
import com.wxn.aso.util.boldCyan
import com.wxn.aso.util.boldGreen
import com.wxn.aso.util.boldMagenta
import com.wxn.aso.util.boldRed
import com.wxn.aso.util.boldYellow
import com.wxn.aso.util.cyan
import com.wxn.aso.util.green
import com.wxn.aso.util.printSystemInfo
import com.wxn.aso.util.red
import com.wxn.aso.util.yellow
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * ASO模块主程序
 * 纯JVM实现，无需Android环境
 * 支持命令行参数和多种报告格式
 */
fun main(args: Array<String>) = runBlocking {
    val startTime = System.currentTimeMillis()

    // 解析命令行参数
    val config = parseArguments(args)
    val projectRoot = config.projectRoot
    val isQuickMode = config.quickMode
    val reportFormat = config.reportFormat

    // 打印系统信息（调试模式）
    if (config.debugMode) {
        printSystemInfo()

        println()
    }

    // 打印标题
    println(boldCyan("=== HandyReader ASO Checker ==="))
    println(cyan("纯JVM构建时工具模式"))
    println()

    // 显示项目根目录
    println("项目根目录: ${green(projectRoot.absolutePath)}")
    println()

    // 初始化ASO组件（手动依赖注入）
    val asoComponent = AsoComponent(projectRoot)

    // 获取配置
    try {
        val asoConfig = asoComponent.getConfig()
        println("ASO配置:")
        println("  版本: ${asoConfig.version}")
        println("  包名: ${asoConfig.googlePlay.packageName}")
        println("  分类: ${asoConfig.googlePlay.category}")
        println("  项目根: ${asoConfig.projectRoot}")
        println()
    } catch (e: Exception) {
        println(yellow("警告: 无法加载配置 - ${e.message}"))
        println(yellow("将使用默认配置"))
        println()
    }

    // 运行ASO检查
    println(boldCyan("开始ASO检查..."))
    if (isQuickMode) {
        println(yellow("快速模式：仅运行元数据和关键词检查"))
    }
    println()

    val report = try {
        if (isQuickMode) {
            asoComponent.checker.runQuickChecks()
        } else {
            asoComponent.checker.runAllChecks()
        }
    } catch (e: Exception) {
        println(red("错误: ASO检查失败 - ${e.message}"))
        e.printStackTrace()
        return@runBlocking
    }
    val duration = System.currentTimeMillis() - startTime

    // 输出报告
    println()
    println(boldCyan("=== ASO检查报告 ==="))
    println("报告ID: ${cyan(report.id)}")
    println("时间戳: ${cyan(report.timestamp.toString())}")
    println("总得分: ${scoreColor(report.overallScore)}${report.overallScore}/120${AnsiColor.RESET.code}")
    println("执行时长: ${cyan("${duration}ms")}")
    println("检查项目: ${cyan(report.metadata.checksPerformed.toString())}")
    println()

    println(boldCyan("分类结果:"))
    report.categoryResults.forEach { result ->
        val percentage = (result.score * 100 / result.maxScore)
        val scoreDisplay = "${result.score}/${result.maxScore} ($percentage%)"

        println("  ${result.category.displayName}: ${scoreColor(result.score * 100 / result.maxScore)}$scoreDisplay${AnsiColor.RESET.code}")

        if (result.issues.isNotEmpty()) {
            println("    问题:")
            result.issues.forEach { issue ->
                val severityColor = when (issue.severity) {
                    com.wxn.aso.model.Severity.CRITICAL -> ::boldRed
                    com.wxn.aso.model.Severity.HIGH -> ::red
                    com.wxn.aso.model.Severity.MEDIUM -> ::yellow
                    com.wxn.aso.model.Severity.LOW -> ::cyan
                    com.wxn.aso.model.Severity.INFO -> ::cyan
                }
                println("      ${severityColor("[${issue.severity}]")} ${issue.code}")
                println("        ${issue.message}")
                if (issue.suggestion.isNotEmpty()) {
                    println("        ${cyan("建议:")} ${issue.suggestion}")
                }
            }
        }
    }

    println()

    // 评估结果
    when {
        report.overallScore >= 108 -> {
            println(boldGreen("✅ 优秀 - ASO质量达到顶级标准"))
        }
        report.overallScore >= 96 -> {
            println(green("✅ 良好 - ASO质量有竞争力"))
        }
        report.overallScore >= 84 -> {
            println(yellow("⚠️  需改进 - 存在明显短板"))
        }
        else -> {
            println(boldRed("❌ 不合格 - 存在关键问题"))
        }
    }

    // 生成报告文件
    if (reportFormat != null) {
        println()
        println(boldCyan("生成${reportFormat.uppercase()}报告..."))
        generateReport(report, reportFormat, projectRoot)
    }

    println()
    println(boldCyan("=== 检查完成 ==="))
}

/**
 * 命令行配置
 */
data class CommandLineConfig(
    val projectRoot: File,
    val quickMode: Boolean = false,
    val reportFormat: String? = null,
    val debugMode: Boolean = false
)

/**
 * 解析命令行参数
 */
fun parseArguments(args: Array<String>): CommandLineConfig {
    var projectRoot: File? = null
    var quickMode = false
    var reportFormat: String? = null
    var debugMode = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--quick", "-q" -> {
                quickMode = true
                i++
            }
            "--report", "-r" -> {
                if (i + 1 < args.size) {
                    reportFormat = args[i + 1].lowercase()
                    i += 2
                } else {
                    println(yellow("警告: --report参数缺少格式参数，将忽略"))
                    i++
                }
            }
            "--debug", "-d" -> {
                debugMode = true
                i++
            }
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }
            else -> {
                // 假设是项目根目录
                projectRoot = File(args[i])
                i++
            }
        }
    }

    // 默认项目根目录
    if (projectRoot == null) {
        projectRoot = System.getenv("ASO_PROJECT_ROOT")?.let { File(it) }
            ?: File(".").absoluteFile
    }

    return CommandLineConfig(
        projectRoot = projectRoot,
        quickMode = quickMode,
        reportFormat = reportFormat,
        debugMode = debugMode
    )
}

/**
 * 打印帮助信息
 */
fun printHelp() {
    println(boldCyan("=== HandyReader ASO Checker ==="))
    println()
    println(cyan("用法:"))
    println("  java -cp classpath com.wxn.aso.MainKt [项目根目录] [选项]")
    println()
    println(cyan("选项:"))
    println("  --quick, -q       快速模式（仅运行元数据和关键词检查）")
    println("  --report, -r      生成报告（格式: json, html, markdown）")
    println("  --debug, -d       调试模式（显示系统信息）")
    println("  --help, -h        显示此帮助信息")
    println()
    println(cyan("示例:"))
    println("  ./gradlew :aso:checkAso")
    println("  ./gradlew :aso:checkAsoQuick")
    println("  ./gradlew :aso:generateAsoReport")
    println()
    println(cyan("或直接运行:"))
    println("  java -jar aso.jar /path/to/project")
    println("  java -jar aso.jar /path/to/project --quick")
    println("  java -jar aso.jar /path/to/project --report html")
    println()
}

/**
 * 生成报告
 * @param report ASO检查报告
 * @param format 报告格式（json/html）
 * @param projectRoot 项目根目录
 * @throws ReportGenerationException 报告生成失败时抛出
 */
fun generateReport(
    report: com.wxn.aso.model.AsoReport,
    format: String,
    projectRoot: File
) {
    try {
        val generator = ReportGenerator(projectRoot)
        val outputPath = generator.generate(report, format)
        println(green("✅ 报告生成成功: $outputPath"))
    } catch (e: ReportGenerationException) {
        println(red("❌ 报告生成失败: ${e.message}"))
        if (e.cause != null) {
            println(red("   原因: ${e.cause!!.message}"))
        }
        exitProcess(1)
    } catch (e: Exception) {
        println(red("❌ 意外错误: ${e.message}"))
        e.printStackTrace()
        exitProcess(1)
    }
}

/**
 * 根据分数返回颜色函数
 */
fun scoreColor(score: Int): (String) -> String {
    return when {
        score >= 108 -> ::boldGreen
        score >= 96 -> ::green
        score >= 84 -> ::yellow
        else -> ::boldRed
    }
}

/**
 * 退出程序
 */
fun exitProcess(status: Int): Nothing {
    System.exit(status)
    throw RuntimeException("System.exit returned")
}
