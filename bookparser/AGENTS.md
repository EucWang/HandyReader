# BookParser Module - Agent Development Guide

## Module Overview

The **bookparser** module handles all file format parsing and text extraction for the HandyReader application. It supports multiple ebook formats including EPUB, MOBI, AZW, PDF, TXT, FB2, and Markdown.

## Build Commands

### Module-specific Build
- `./gradlew :bookparser:assembleDebug` - Build debug AAR
- `./gradlew :bookparser:assembleRelease` - Build release AAR

### Testing
- `./gradlew :bookparser:test` - Run unit tests
- `./gradlew :bookparser:lint` - Run lint checks

## Architecture

### Module Organization

The bookparser module follows a **plugin-based parser architecture**:

```
bookparser/
├── parser/           # Format-specific parsers
│   ├── mobi/        # MOBI/AZW/AZW3 parser
│   ├── epub/        # EPUB parser
│   ├── pdf/         # PDF parser
│   ├── txt/         # TXT parser
│   ├── html/        # HTML parser
│   └── fb2/         # FB2 parser
├── domain/          # Domain models
│   ├── file/        # File handling (CachedFile)
│   └── ui/          # UI text models
├── util/            # Utilities
│   ├── FileUtil.kt  # File operations
│   ├── Common.kt    # Common utilities
│   └── BookExt.kt   # Book extensions
├── exts/            # Extension functions
├── di/              # Dependency injection (ParserModule)
└── TextParser.kt    # Main parser interface
```

### Key Components

#### Parser Interface
- **TextParser**: Main interface for text extraction
- **IFileParser**: File metadata and structure parsing
- **ITextParser**: Content text extraction

#### Format-Specific Parsers
- **MobiFileParser**: MOBI format structure parsing
- **MobiTextParser**: MOBI content extraction
- **EpubParser**: EPUB format parsing (via mobi module)
- **PdfFileParser**: PDF structure parsing
- **PdfTextParser**: PDF content extraction
- **TxtFileParser**: TXT file handling
- **HtmlFileParser**: HTML to ebook conversion
- **Fb2Parser**: FictionBook 2.0 format

#### Caching System
- **CachedFile**: Virtual file system for book content
- **CachedFileCompat**: Compatibility layer
- **CachedFileBuilder**: Builder pattern for cache creation

#### Dependency Injection
- **ParserModule**: Hilt module for parser configuration
- **Binds**: Parser implementations to interfaces

## Technology Stack

### Core Dependencies
- **Kotlin**: Standard library with serialization
- **Hilt**: Dependency injection
- **AndroidX**: Core KTX, AppCompat, Material

### Parser Libraries
- **libmobi**: MOBI/AZW parsing (via mobi module)
- **PDFBox-Android**: PDF parsing
- **Jsoup**: HTML/XML parsing
- **CommonMark**: Markdown parsing
- **KotlinX Serialization**: JSON serialization

### Storage
- **Storage Access Framework**: Document file handling
- **CachedFile system**: In-memory caching

### Module Dependencies
- **mobi**: MOBI parsing support
- **base**: Shared utilities
- **jp2forandroid**: JPEG2000 image decoding (for PDF)

## Code Style Guidelines

### Kotlin Conventions
- **Style**: Official Kotlin style
- **Indentation**: 4 spaces
- **Import ordering**: AndroidX, third-party, project

### Naming Conventions
- **Parsers**: `<Format>FileParser` (structure), `<Format>TextParser` (content)
  ```kotlin
  class MobiFileParser : IFileParser
  class MobiTextParser : ITextParser
  ```
- **Models**: Descriptive names (e.g., `BookInfo`, `ChapterData`)
- **Extension Files**: `<Type>Ext.kt` (e.g., `PrimitiveExt.kt`)

### Parser Implementation Pattern
```kotlin
class FormatTextParser @Inject constructor(
    private val dependency: SomeDependency
) : ITextParser {

    override suspend fun parse(
        file: CachedFile,
        charset: Charset
    ): Result<BookContent> {
        return try {
            // Parse logic
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Common Patterns

### Parser Selection
```kotlin
fun getParser(fileExtension: String): IFileParser {
    return when (fileExtension.lowercase()) {
        "mobi", "azw", "azw3" -> mobiFileParser
        "epub" -> epubFileParser
        "pdf" -> pdfFileParser
        "txt" -> txtFileParser
        else -> throw IllegalArgumentException("Unsupported format")
    }
}
```

### CachedFile Usage
```kotlin
val cachedFile = CachedFileBuilder()
    .setUri(uri)
    .setFileName(fileName)
    .setFileSize(fileSize)
    .build()

val content = textParser.parse(cachedFile, charset)
```

### Error Handling
```kotlin
suspend fun parseBook(file: CachedFile): Result<BookContent> {
    return withContext(Dispatchers.IO) {
        try {
            val content = parser.parse(file)
            Result.success(content)
        } catch (e: IOException) {
            Result.failure(ParseException("File read error", e))
        } catch (e: Exception) {
            Result.failure(ParseException("Parse error", e))
        }
    }
}
```

## Testing Guidelines

### Unit Tests
- **Location**: `bookparser/src/test/java/com/wxn/bookparser/`
- **Framework**: JUnit 4
- **Test each parser** with sample files
- **Test edge cases** (corrupted files, empty files)
- **Mock dependencies** (file system, network)

### Test Example
```kotlin
class MobiTextParserTest {
    @Test
    fun `parse returns success for valid MOBI file`() {
        val mockFile = mockk<CachedFile>()
        // Setup mock behavior

        val result = parser.parse(mockFile, Charset.defaultCharset())

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }
}
```

## Key Features

### Format Support

#### MOBI/AZW/AZW3
- **Structure parsing**: Metadata, chapters, images
- **Text extraction**: Clean text content
- **DRM handling**: Basic DRM support
- **Image extraction**: Embedded images
- **Complex layouts**: Tables, lists, formatting

#### EPUB
- **Standard EPUB2/EPUB3**: Full specification support
- **Chapter navigation**: Spine and TOC parsing
- **Metadata**: Title, author, cover extraction
- **Resources**: Images, CSS, fonts

#### PDF
- **Text extraction**: PDFBox integration
- **Page detection**: Automatic page boundaries
- **Image handling**: JP2 support via jp2forandroid
- **Metadata**: Document information

#### TXT
- **Encoding detection**: Automatic charset detection
- **Chapter detection**: Regex-based chapter splitting
- **Line endings**: Windows/Unix/Mac support

#### FB2 (FictionBook 2.0)
- **XML parsing**: Full FB2 specification
- **Metadata**: Author, title, genre, annotation
- **Images**: Base64 encoded images
- **Sections**: Hierarchical structure

#### Markdown
- **CommonMark**: Standard Markdown parsing
- **Metadata**: YAML frontmatter support
- **Formatting**: Bold, italic, lists, code blocks

### Caching System
- **Virtual File System**: Memory-efficient caching
- **Lazy Loading**: Load content on demand
- **Memory Management**: Automatic cleanup
- **Thread Safety**: Concurrent access support

## Module Dependencies

### Internal Dependencies
- **mobi**: MOBI/EpubParser/Fb2Parser classes
- **base**: Utility functions and extensions
- **jp2forandroid**: JPEG2000 decoding for PDF images

### External Dependencies
- **Hilt**: Dependency injection
- **Jsoup**: HTML/XML parsing
- **PDFBox-Android**: PDF parsing
- **CommonMark**: Markdown parsing
- **KotlinX Serialization**: JSON handling
- **AndroidX Storage**: Document file handling

## Design Principles

### Parser Isolation
- Each parser is independent
- No dependencies between parsers
- Common interface for all parsers

### Error Resilience
- Graceful degradation on parse errors
- Partial content recovery
- Detailed error messages

### Performance
- Lazy loading where possible
- Memory-efficient caching
- Coroutine-based async operations

### Extensibility
- Easy to add new formats
- Plugin-based architecture
- Interface-driven design

## Common File Locations

- **Parsers**: `bookparser/src/main/java/com/wxn/bookparser/parser/`
- **Domain**: `bookparser/src/main/java/com/wxn/bookparser/domain/`
- **Utilities**: `bookparser/src/main/java/com/wxn/bookparser/util/`
- **Extensions**: `bookparser/src/main/java/com/wxn/bookparser/exts/`
- **DI**: `bookparser/src/main/java/com/wxn/bookparser/di/`

## Usage Examples

### Basic Parsing
```kotlin
class BookUseCase @Inject constructor(
    private val parserProvider: ParserProvider
) {
    suspend fun parseBook(uri: Uri): Result<BookContent> {
        val cachedFile = CachedFileCompat.fromUri(uri)
        val parser = parserProvider.getParser(cachedFile.extension)
        return parser.parse(cachedFile)
    }
}
```

### Parser Selection
```kotlin
enum class BookFormat(val extension: String) {
    MOBI("mobi"),
    EPUB("epub"),
    PDF("pdf"),
    TXT("txt"),
    FB2("fb2"),
    MD("md");

    companion object {
        fun fromExtension(ext: String): BookFormat? {
            return values().find { it.extension == ext.lowercase() }
        }
    }
}
```

## Best Practices

### When Adding New Parser
1. **Implement interfaces**: IFileParser, ITextParser
2. **Handle errors**: Return Result types
3. **Test thoroughly**: Various file samples
4. **Document format**: Supported features and limitations
5. **Add DI binding**: Register in ParserModule

### When Using Parsers
1. **Use coroutines**: All parse operations are suspend functions
2. **Handle errors**: Check Result before using content
3. **Clean up resources**: Close CachedFile when done
4. **Cache results**: Avoid re-parsing

## Important Notes

- **Thread Safety**: Parsers are thread-safe but use coroutines
- **Memory**: Large files may require memory management
- **Encoding**: TXT parsers auto-detect encoding
- **DRM**: Limited DRM support (basic MOBI DRM)
- **Images**: Images extracted separately from text
- **Performance**: EPUB and MOBI are fastest, PDF is slowest

## Future Considerations

- **More Formats**: DOCX, RTF, CBZ/CBR
- **Better PDF**: Improved layout preservation
- **DRM Support**: Enhanced DRM handling
- **OCR**: Image-based PDF text extraction
- **Performance**: Further optimization for large files
