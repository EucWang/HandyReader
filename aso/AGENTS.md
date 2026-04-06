# ASO模块 - Agent开发指南

## 模块概述

**ASO模块**（App Store Optimization）为HandyReader Android应用提供应用商店优化检查和分析功能。该模块独立运行，支持手动触发，可生成详细的多格式报告。

## 核心功能

### 基础检查（必需）
- **元数据检查**：应用名称、描述、分类、内容合规性
- **视觉资源检查**：图标、横幅图、截图（7张精选）
- **本地化检查**：10种语言完整性和翻译质量
- **关键词检查**：关键词密度、相关性、长度优化
- **竞品分析**：Google Play网页爬虫、文件系统缓存、竞品评分分析

### 进阶功能（可选）
- **智能建议**：规则引擎驱动的优化建议
- **基准对比**：行业Top应用对比分析

## 快速开始

### ⚠️ 重要编译约束
**禁止在WSL环境中编译此Android项目！**
- 必须在Windows命令行（cmd）或PowerShell中执行编译
- WSL环境会导致Java路径和Gradle daemon问题
- 推荐在Windows原生终端运行所有Gradle命令

**示例**:
```cmd
# Windows CMD
gradlew :aso:assembleDebug

# Windows PowerShell
.\gradlew :aso:assembleDebug
```

### 运行ASO检查
```bash
# 基础检查
./gradlew checkAso

# 生成HTML报告
./gradlew generateAsoReport

# 查看报告
open app/build/aso/reports/report_$(date +%Y%m%d_%H%M%S).html
```

### 配置文件
```bash
# 本地配置（不提交）
aso.properties

# 环境变量（CI/CD）
GOOGLE_PLAY_API_KEY=...
```

## 构建命令

### 模块编译
```bash
./gradlew :aso:assembleDebug
./gradlew :aso:assembleRelease
```

### 测试
```bash
# 单元测试
./gradlew :aso:test

# 集成测试
./gradlew :aso:connectedAndroidTest

# 特定测试
./gradlew :aso:test --tests "*MetadataCheckTest"
```

### 代码检查
```bash
./gradlew :aso:lint
./gradlew :aso:detekt
```

## 架构设计

### 分层架构
```
Presentation Layer (CLI/Gradle Tasks)
    ↓
Domain Layer (Use Cases/Checker)
    ↓
Data Layer (API/Config/Assets)
```

### 核心组件

#### 1. 检查器（Checkers）
- `AsoChecker` - 主检查器编排
- `MetadataCheck` - 元数据检查
- `VisualAssetsCheck` - 视觉资源检查
- `LocalizationCheck` - 本地化检查
- `KeywordsCheck` - 关键词检查
- `CompetitorCheck` - 竞品分析（可选）

#### 2. API客户端
- `GooglePlayAnalyticsClient` - Google Play API集成
- `BenchmarksLoader` - 基准数据加载

#### 3. 智能引擎
- `IntelligentSuggestionEngine` - 规则引擎
- `BenchmarkComparator` - 基准对比

#### 4. 报告生成
- `ReportGenerator` - 多格式报告
- `ConsoleReporter` - 控制台输出
- `HtmlReporter` - HTML可视化
- `JsonReporter` - JSON数据
- `MarkdownReporter` - Markdown文档

## 技术栈

### 核心依赖
- **Kotlin**: 2.1.10
- **Coroutines**: 异步检查
- **Hilt**: 依赖注入

### API集成
- **Google Play Android Publisher API**: v3-rev142-1.25.0
- **Google Auth Library**: OAuth2-HTTP 1.19.0

### 图像处理
- **Android Bitmap**: 原生图像分析
- **WebPAndroid**: 图像格式转换（可选）

### 数据存储
- **Room**: 竞品数据缓存
- **DataStore**: 配置管理（复用app模块）

### 网络请求
- **Ktor**: HTTP客户端（复用app模块）
- **OkHttp**: 底层网络引擎

## 数据模型

### 核心数据类
```kotlin
data class AsoReport(
    val id: String,
    val timestamp: Instant,
    val overallScore: Int,
    val categoryResults: List<CheckResult>
)

data class CheckResult(
    val category: CheckCategory,
    val score: Int,
    val maxScore: Int,
    val issues: List<Issue>
)

data class Issue(
    val severity: Severity,
    val code: String,
    val message: String,
    val suggestion: String,
    val expectedImpact: String
)

enum class CheckCategory {
    METADATA, VISUAL, LOCALIZATION, KEYWORDS, COMPETITOR
}

enum class Severity {
    CRITICAL, HIGH, MEDIUM, LOW
}
```

### 增强报告模型
```kotlin
data class EnhancedAsoReport(
    val basicReport: AsoReport,
    val competitorAnalysis: CompetitorAnalysis?,
    val suggestions: List<AISuggestion>,
    val benchmarkComparison: BenchmarkComparison?
)
```

## 配置管理

### 配置文件结构
```
aso/
├── src/main/resources/
│   ├── aso-config.json           # 默认配置模板
│   ├── category-keywords.json    # Category关键词映射
│   └── templates/
│       └── report.html           # HTML报告模板
└── assets/
    ├── benchmarks/               # 静态基准数据
    └── models/                   # AI模型（预留）
```

### 配置优先级
1. 环境变量（CI/CD）
2. 本地配置文件（aso.properties）
3. 默认配置（aso-config.json）

### 配置项说明
| 配置项 | 说明 | 默认值 | 必需 |
|--------|------|--------|------|
| `aso.googlePlay.apiKeyFile` | Google Play API密钥文件路径 | - | 是 |
| `aso.googlePlay.packageName` | 应用包名 | com.wxn.reader | 是 |
| `aso.googlePlay.category` | 应用分类 | BOOKS | 否 |
| `aso.projectRoot` | 项目根目录路径（绝对路径） | . | 否 |
| `aso.threshold.score` | 通过阈值分数 | 80 | 否 |
| `aso.screenshot.count` | 截图数量 | 7 | 否 |
| `aso.report.format` | 报告格式 | html | 否 |

## 资源管理

### 截图选择标准
```kotlin
// 优先级：PNG > JPG > 分辨率(≥1080x1920) > 文件大小(≤5MB)
val selectionCriteria = listOf(
    Metric("format", listOf("png", "jpg"), weight = 0.3),
    Metric("resolution", minWidth = 1080, minHeight = 1920, weight = 0.4),
    Metric("content", checks = listOf("ui_visible", "text_legible"), weight = 0.3)
)
```

### 资源文件组织
```
aso/assets/
├── feature-graphic.png           # 1024x500商店横幅图
├── screenshots/
│   └── phone/
│       ├── en-1.png              # 7张精选截图
│       ├── en-2.png
│       ...
│       └── en-7.png
├── benchmarks/
│   ├── reading_apps_benchmark.json
│   └── benchmark_config.json
└── models/                       # AI模型（预留）
```

## 评分算法

### 权重分配
| 类别 | 权重 | 子项数 | 说明 |
|------|------|--------|------|
| **元数据质量** | 30% | 5 | 名称、描述、分类、合规 |
| **视觉资源质量** | 35% | 5 | 图标、横幅、截图、视频 |
| **本地化质量** | 25% | 4 | 语言覆盖、翻译、质量 |
| **关键词优化** | 10% | 3 | 密度、相关性、长度 |

### 评分等级
| 分数区间 | 等级 | 说明 | 建议 |
|----------|------|------|------|
| **90-100** | 🏆 优秀 | 顶级标准 | 保持监控，微调优化 |
| **80-89** | ✅ 良好 | 有竞争力 | 重点优化薄弱项 |
| **70-79** | ⚠️ 需改进 | 有明显短板 | 制定改进计划 |
| **<70** | ❌ 不合格 | 关键问题 | 立即修复高优先级问题 |

### 评分计算示例
```kotlin
// 元数据得分 = 5个子项加权平均
val metadataScore = (appNameScore * 0.20 +
                    shortDescScore * 0.15 +
                    fullDescScore * 0.30 +
                    categoryScore * 0.20 +
                    complianceScore * 0.15)

// 总分 = 各类别加权平均
val overallScore = (metadataScore * 0.30 +
                   visualScore * 0.35 +
                   localizationScore * 0.25 +
                   keywordsScore * 0.10)
```

## 代码风格指南

### Kotlin规范
- **遵循**：官方Kotlin代码风格
- **缩进**：4空格
- **行长度**：120字符
- **命名**：camelCase（函数/变量），PascalCase（类）

### 检查器模式
```kotlin
class XxxCheck @Inject constructor(
    private val dependency1: Dependency1,
    private val dependency2: Dependency2
) {
    suspend fun check(): CheckResult {
        // 1. 数据收集
        val data = collectData()

        // 2. 检查逻辑
        val checks = performChecks(data)

        // 3. 问题识别
        val issues = identifyIssues(checks)

        // 4. 评分计算
        val score = calculateScore(checks)

        // 5. 返回结果
        return CheckResult(
            category = CheckCategory.XXX,
            score = score,
            issues = issues
        )
    }
}
```

### API客户端模式
```kotlin
class XxxApiClient @Inject constructor(
    private val credentials: Credentials,
    private val httpClient: HttpClient,
    private val cache: XxxCache
) {
    private val api = buildApi()

    suspend fun fetchData(): Result<Data> {
        // 1. 检查缓存
        cache.get()?.let { return Result.success(it) }

        // 2. 调用API
        return try {
            val data = api.getData()
            // 3. 更新缓存
            cache.put(data)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## 测试策略

### 单元测试
```kotlin
class MetadataCheckTest {
    @Test
    fun `check returns valid score for complete metadata`() {
        // Arrange
        val config = TestConfig()
        val check = MetadataCheck(config, mockParser)

        // Act
        val result = runBlocking { check.check() }

        // Assert
        assertTrue(result.score >= 80)
        assertTrue(result.issues.isEmpty())
    }
}
```

### 集成测试
```kotlin
class AsoCheckerIntegrationTest {
    @Test
    fun `runAllChecks generates complete report`() {
        val checker = hiltTestRule.inject<AsoChecker>()
        val report = runBlocking { checker.runAllChecks() }

        assertNotNull(report.id)
        assertTrue(report.overallScore in 0..100)
        assertEquals(4, report.categoryResults.size)
    }
}
```

### 测试覆盖率
- **目标**：≥90%代码覆盖率
- **工具**：JaCoCo
- **命令**：
  ```bash
  # 运行测试
  ./gradlew :aso:test
  ./gradlew :aso:testDebugUnitTest
  
  # 生成覆盖率报告
  ./gradlew :aso:testDebugUnitTestCoverage
  ```

### Hilt测试配置
ASO模块使用Hilt进行依赖注入，测试时需要Hilt测试支持：

```kotlin
// 添加到 build.gradle.kts
testImplementation("com.google.dagger:hilt-android-testing:2.51.1")
kspTest("com.google.dagger:hilt-android-compiler:2.51.1")
```

**测试模式**:
- **单元测试**: 使用Mockito mock依赖，隔离测试单个组件
- **集成测试**: 使用Hilt注入真实依赖，测试组件协作

**测试文件结构**:
```
aso/src/test/kotlin/com/wxn/aso/
├── analyzer/       # KeywordsExtractorTest, ImageAnalyzerTest
├── checker/        # MetadataCheckTest, VisualAssetsCheckTest, etc.
├── config/         # AsoConfigManagerTest
├── parser/         # StringsXmlParserTest, AndroidManifestParserTest
└── util/           # ScreenshotSelectorTest
```

### category-keywords.json
**路径**: `aso/src/main/resources/category-keywords.json`

**用途**: 为KeywordsCheck提供各category的推荐关键词列表，用于计算关键词相关性

**格式**: JSON对象，key为category名称，value为关键词数组

**示例**:
```json
{
  "BOOKS": ["ereader", "ebook", "epub", "mobi", "reader", "reading"],
  "NEWS": ["news", "newspaper", "article", "feed", "RSS"]
}
```

**加载方式**: 在`KeywordsCheck`中通过`context.assets.open("category-keywords.json")`读取

**Category说明**:
- **BOOKS**: 电子书阅读器应用，包含epub, mobi, reader, reading等关键词
- **NEWS**: 新闻阅读应用，包含news, article, feed等关键词
- **EDUCATION**: 教育学习应用，包含learning, tutorial, course等关键词
- **ENTERTAINMENT**: 娱乐应用，包含game, video, music等关键词
- **PRODUCTIVITY**: 生产力工具，包含notes, task, calendar等关键词

## 构建时工具模式

ASO模块采用**构建时工具模式**，在Gradle构建过程中运行ASO检查，而不是在Android运行时执行。

### 核心特性
- **源码访问**: 可访问项目源码目录（`app/src/main/res`等）
- **配置驱动**: 通过`aso-config.json`配置项目根目录
- **文件系统操作**: 直接读取和分析源文件
- **Gradle集成**: 通过Gradle任务触发检查

### 项目根目录配置
```json
{
  "projectRoot": "/path/to/project",
  "googlePlay": {
    "category": "BOOKS"
  }
}
```

**重要**: 
- `projectRoot`应该是**绝对路径**，不使用相对路径
- 默认值为`.`（当前工作目录）
- 在Gradle构建时，当前目录通常就是项目根目录

### 运行时 vs 构建时模式对比
| 特性 | 运行时模式 | 构建时模式（当前） |
|------|-----------|------------------|
| strings.xml访问 | Resources API | 直接解析源文件 |
| Context使用 | 必需 | 仅用于Assets访问 |
| 运行环境 | Android设备 | 构建机器 |
| 文件系统 | 应用沙盒 | 项目源码目录 |

## 常见问题

### Q1: Google Play API调用失败
**症状**：`403 Forbidden` 或 `401 Unauthorized`

**解决方案**：
1. 验证服务账户权限
2. 检查API密钥文件路径
3. 确认包名正确
4. 查看API调用限制

### Q2: 图像分析性能慢
**症状**：检查耗时>30秒

**解决方案**：
1. 限制图片大小（最大5MB）
2. 使用采样率降低分辨率
3. 并行处理多张图片
4. 缓存分析结果

### Q3: 配置文件找不到
**症状**：`FileNotFoundException: aso.properties`

**解决方案**：
1. 检查文件路径（相对项目根目录）
2. 确认`.gitignore`没有误删
3. 使用环境变量替代
4. 回退到默认配置

### Q4: 报告生成失败
**症状**：HTML报告为空或格式错误

**解决方案**：
1. 检查模板文件完整性
2. 验证数据模型序列化
3. 降级到JSON/控制台报告
4. 查看详细错误日志

## 依赖关系

### 项目内依赖
- **app**：复用网络配置（Ktor）
- **base**：复用工具类和扩展
- **无UI依赖**：纯后台模块

### 外部依赖
```toml
[versions]
googlePlayPublisher = "v3-rev142-1.25.0"
googleAuth = "1.19.0"

[libraries]
google-play-publisher = { module = "com.google.apis:google-api-services-androidpublisher", version.ref = "googlePlayPublisher" }
google-auth-oauth2-http = { module = "com.google.auth:google-auth-library-oauth2-http", version.ref = "googleAuth" }
```

## 性能优化

### 并行检查
```kotlin
suspend fun runAllChecks(): AsoReport {
    return coroutineScope {
        val results = listOf(
            async { metadataCheck.check() },
            async { visualAssetsCheck.check() },
            async { localizationCheck.check() },
            async { keywordsCheck.check() }
        ).awaitAll()

        AsoReport(results)
    }
}
```

### 智能缓存
- **竞品数据**：7天TTL
- **图像分析**：文件内容哈希作为Key
- **API响应**：使用ETag进行条件请求

### 内存管理
- **限制**：内存使用≤200MB
- **策略**：及时释放Bitmap，使用流式处理

## 安全考虑

### 敏感信息保护
```gitignore
# aso/.gitignore
aso.properties
*.json
!src/main/resources/aso-config.json
local-config.json
```

### API密钥管理
- **本地开发**：相对路径引用项目根目录密钥文件
- **CI/CD**：通过GitHub Secrets注入
- **加密**：不提交密钥到版本控制

### 数据隐私
- **竞品数据**：仅使用公开信息
- **应用数据**：不上传到外部服务器
- **报告内容**：可包含敏感配置，注意访问控制

## 扩展开发

### 添加新的检查器
1. 创建`XxxCheck.kt`继承检查接口
2. 在`AsoModule`中注册依赖
3. 在`AsoChecker`中添加编排逻辑
4. 编写单元测试
5. 更新评分权重

### 集成新的数据源
1. 创建`XxxApiClient.kt`
2. 实现缓存策略
3. 添加错误处理
4. 编写集成测试
5. 更新文档

### 自定义报告格式
1. 实现`Reporter`接口
2. 在`ReportGenerator`中注册
3. 创建模板（如需要）
4. 添加测试用例

## 相关文档

- **实施计划**：[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)
- **配置指南**：[CONFIGURATION.md](docs/CONFIGURATION.md)（待创建）
- **API参考**：[API_REFERENCE.md](docs/API_REFERENCE.md)（待创建）
- **故障排除**：[TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)（待创建）

## 维护指南

### 定期维护任务
- **每周**：更新竞品数据缓存
- **每月**：审查基准数据，更新行业趋势
- **每季度**：优化评分算法权重
- **每年**：升级Google Play API版本

### 监控指标
- 检查执行时间
- API调用成功率
- 缓存命中率
- 报告生成成功率

## 贡献指南

### 代码提交规范
```
type(scope): subject

body

footer
```

**类型**：
- `feat`：新功能
- `fix`：修复Bug
- `docs`：文档更新
- `test`：测试相关
- `refactor`：重构
- `chore`：构建/工具链

### Pull Request流程
1. Fork项目
2. 创建特性分支
3. 编写代码和测试
4. 提交PR，描述变更
5. 代码审查
6. 合并到主分支

## 版本历史

### v1.2.0（竞品分析模块）
- ✅ **CompetitorCheck完整实现** (20分)
  - Google Play网页爬虫（无需API密钥）
  - 文件系统缓存（7天TTL）
  - 竞品评分分析：评分、安装量、关键词、更新新鲜度
- ✅ **总分更新为120分**
  - 更新所有硬编码分值显示
  - 调整评分阈值（108/96/84对应90%/80%/70%）
  - 更新测试期望值
- ✅ **OkHttp 5.3.2集成**
- ✅ **构建时工具模式增强**
  - 支持竞品包名配置（aso-config.json）
  - 缓存目录管理（系统临时目录）
- ✅ **测试扩展**
  - 新增CompetitorCheckTest
  - 更新AsoCheckerTest期望值

### v1.1.0
- ✅ **LocalizationCheck完整实现** (25分)
  - 语言覆盖率检查：≥10种语言得满分
  - 翻译完整性检查：≥90%翻译完整度
  - 术语一致性检查：关键术语（app_name）覆盖率
- ✅ **KeywordsCheck完整实现** (10分)
  - 关键词密度检查：2-3%为最佳范围
  - 相关性检查：基于category关键词匹配率
  - 关键词数量检查：5-10个为最佳数量
- ✅ **构建时工具模式**
  - StringsXmlParser支持构建时文件访问
  - AsoConfig添加projectRoot和category字段
  - category-keywords.json配置文件
- ✅ **测试覆盖率提升至90%**
  - 新增AsoCheckerTest, AsoConfigManagerTest, ImageAnalyzerTest, ScreenshotSelectorTest
  - 扩展LocalizationCheckTest, KeywordsCheckTest
  - 添加Hilt测试依赖
- ✅ **KeywordsExtractor修正**
  - 添加@Inject构造函数支持Hilt
  - 修正calculateRelevance()逻辑，直接计算category关键词匹配率
- ✅ **错误信息格式标准化**
  - 统一错误代码前缀（LOCALIZATION_, KEYWORDS_等）
  - 具体数值和预期值
  - 可行的修复建议和时间估算

### v1.0.0
- ✅ MetadataCheck完整实现 (30分)
- ✅ VisualAssetsCheck完整实现 (35分)
- ✅ 基础架构和依赖注入
- ✅ 配置管理系统

### 计划中
- 🔲 TFLite模型集成
- 🔲 多应用对比
- 🔲 自动化优化建议
- 🔲 iOS App Store支持

## 联系方式

- **项目负责人**：开发团队
- **问题反馈**：GitHub Issues
- **功能建议**：GitHub Discussions

---

**最后更新**：2026-04-06
**文档版本**：1.2.0
