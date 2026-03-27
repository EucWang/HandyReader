# Mobi Module - Agent Development Guide

## Module Overview

The **mobi** module provides native MOBI, EPUB, and FB2 format parsing support using libmobi (C++ library) via JNI. It acts as a bridge between Java/Kotlin code and the native libmobi library for efficient ebook format parsing.

## Build Commands

### Module-specific Build
- `./gradlew :mobi:assembleDebug` - Build debug AAR with native libraries
- `./gradlew :mobi:assembleRelease` - Build release AAR with native libraries

### Native Build
- **CMake**: Automatically invoked during Gradle build
- **Build Script**: `src/main/cpp/CMakeLists.txt`
- **ABI Support**: armeabi-v7a, arm64-v8a, x86, x86_64

### Testing
- `./gradlew :mobi:test` - Run unit tests
- `./gradlew :mobi:lint` - Run lint checks

## Architecture

### Module Organization

The mobi module uses a **JNI-based native library architecture**:

```
mobi/
├── src/main/
│   ├── java/           # Java/Kotlin source
│   │   ├── com/wxn/mobi/
│   │   │   ├── MobiParser.kt          # Main MOBI parser
│   │   │   ├── EpubParser.kt          # EPUB parser
│   │   │   ├── Fb2Parser.kt           # FB2 parser
│   │   │   ├── FileSearcher.kt        # File searching utilities
│   │   │   ├── inative/
│   │   │   │   └── NativeLib.kt       # JNI interface
│   │   │   └── data/model/
│   │   │       ├── ParagraphData.kt    # Paragraph data model
│   │   │       ├── MetaInfo.kt         # Metadata model
│   │   │       ├── FileCrc.kt          # CRC checksum model
│   │   │       └── CountPair.kt        # Count pair model
│   └── cpp/            # C++ source
│       ├── CMakeLists.txt  # CMake build configuration
│       ├── libmobi/        # libmobi source code
│       └── jni/            # JNI bindings
```

### Key Components

#### Native Library Interface
- **NativeLib**: Kotlin interface to native functions
- **JNI Bindings**: C++ bridge between Kotlin and libmobi
- **libmobi**: Native C++ library for MOBI/EPUB parsing

#### Parsers
- **MobiParser**: MOBI format parser (Kotlin wrapper)
- **EpubParser**: EPUB format parser (Kotlin wrapper)
- **Fb2Parser**: FB2 format parser (Kotlin wrapper)

#### Data Models
- **ParagraphData**: Text paragraph data
- **MetaInfo**: Book metadata (title, author, etc.)
- **FileCrc**: File checksum for validation
- **CountPair**: Count data pair

#### Utilities
- **FileSearcher**: File searching utilities

## Technology Stack

### Core Dependencies
- **Kotlin**: Standard library
- **Android NDK**: 29.0.13599879 rc2
- **CMake**: 3.22.1
- **libmobi**: Native C++ library

### Native Build
- **CMake**: Native build system
- **NDK**: Android Native Development Kit
- **ABI Support**:
  - armeabi-v7a (32-bit ARM)
  - arm64-v8a (64-bit ARM)
  - x86 (32-bit Intel)
  - x86_64 (64-bit Intel)

### Module Dependencies
- **base**: Shared utilities

## Code Style Guidelines

### Kotlin/JNI Conventions
- **Style**: Official Kotlin style
- **Indentation**: 4 spaces
- **JNI Functions**: Follow JNI naming conventions
- **Native Types**: Map C++ types to Kotlin appropriately

### Naming Conventions
- **Parsers**: `<Format>Parser` (e.g., `MobiParser`, `EpubParser`)
- **Native Functions**: `native<FunctionName>`
- **Data Models**: Descriptive names (e.g., `ParagraphData`, `MetaInfo`)
- **JNI Methods**: Java_ + package + class + method

### JNI Interface Pattern
```kotlin
object NativeLib {
    init {
        System.loadLibrary("mobi")
    }

    external fun nativeOpenMobi(path: String): Long
    external fun nativeGetText(handle: Long): String
    external fun nativeCloseMobi(handle: Long)
}
```

## Common Patterns

### Native Function Declaration
```kotlin
// Kotlin
object NativeLib {
    init {
        System.loadLibrary("mobi")
    }

    external fun nativeParseMobi(filePath: String): ByteArray
}

// C++ JNI Implementation
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_nativeParseMobi(
    JNIEnv *env,
    jobject thiz,
    jstring filePath
) {
    // Implementation
}
```

### Parser Wrapper Pattern
```kotlin
class MobiParser {
    fun parse(filePath: String): Result<BookContent> {
        return try {
            val nativeHandle = NativeLib.nativeOpenMobi(filePath)
            val text = NativeLib.nativeGetText(nativeHandle)
            NativeLib.nativeCloseMobi(nativeHandle)
            Result.success(BookContent(text))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Data Model Mapping
```kotlin
data class ParagraphData(
    val text: String,
    val index: Int,
    val chapterIndex: Int
)

data class MetaInfo(
    val title: String?,
    val author: String?,
    val coverImage: ByteArray?
)
```

## Testing Guidelines

### Unit Tests
- **Location**: `mobi/src/test/java/com/wxn/mobi/`
- **Framework**: JUnit 4
- **Test Kotlin wrappers**: Parser logic
- **Mock native calls**: Mock NativeLib for testing
- **Test data models**: Serialization/deserialization

### Native Testing
- **C++ Unit Tests**: Test libmobi integration
- **Integration Tests**: Test Kotlin -> JNI -> C++ flow
- **ABI Testing**: Test all supported ABIs

## Key Features

### MOBI Format Support
- **Standard MOBI**: Full MOBI format support
- **KF8 (AZW3)**: Kindle Format 8 support
- **AZW**: Amazon Kindle format
- **KFX**: Limited support (experimental)

### EPUB Format Support
- **EPUB2**: Full specification support
- **EPUB3**: Most features supported
- **Metadata**: Complete metadata extraction
- **Resources**: Images, CSS, fonts

### FB2 Format Support
- **FictionBook 2.0**: Full specification
- **Metadata**: Author, title, genre
- **Images**: Base64 encoded images
- **Structure**: Sections and paragraphs

### Native Performance
- **C++ Implementation**: Fast native parsing
- **Memory Efficient**: Minimal Java heap usage
- **Multi-ABI**: Optimized for each architecture
- **Thread Safety**: Native library thread-safe

### Data Extraction
- **Text Content**: Clean text extraction
- **Metadata**: Title, author, cover, etc.
- **Structure**: Chapters and sections
- **Images**: Embedded image extraction

## Module Dependencies

### Internal Dependencies
- **base**: Utility functions and extensions

### External Dependencies
- **Android NDK**: Native development
- **libmobi**: Native parsing library

### Native Dependencies
- **libmobi**: Bundled with module
- **CMake**: Build system
- **NDK**: Toolchain

## Design Principles

### Performance
- **Native Implementation**: C++ for speed
- **Memory Efficiency**: Minimal Java object allocation
- **Lazy Loading**: Load content on demand
- **Resource Management**: Proper cleanup of native resources

### Reliability
- **Error Handling**: Robust error checking
- **Resource Cleanup**: Proper native resource release
- **ABI Support**: Multiple architecture support
- **Validation**: File format validation

### Maintainability
- **Clean JNI Interface**: Clear Kotlin/C++ boundary
- **Documentation**: Well-documented native functions
- **Testing**: Comprehensive test coverage
- **Build Automation**: Automated native builds

## Common File Locations

- **Parsers**: `mobi/src/main/java/com/wxn/mobi/`
- **JNI Interface**: `mobi/src/main/java/com/wxn/mobi/inative/NativeLib.kt`
- **Data Models**: `mobi/src/main/java/com/wxn/mobi/data/model/`
- **Native Code**: `mobi/src/main/cpp/`
- **CMake**: `mobi/src/main/cpp/CMakeLists.txt`

## Usage Examples

### Basic MOBI Parsing
```kotlin
class BookUseCase @Inject constructor(
    private val mobiParser: MobiParser
) {
    suspend fun parseMobiFile(filePath: String): Result<BookContent> {
        return withContext(Dispatchers.IO) {
            mobiParser.parse(filePath)
        }
    }
}
```

### Native Library Usage
```kotlin
object NativeLib {
    init {
        System.loadLibrary("mobi")
    }

    external fun nativeOpenMobi(path: String): Long
    external fun nativeGetMetaInfo(handle: Long): MetaInfo
    external fun nativeGetParagraph(handle: Long, index: Int): ParagraphData
    external fun nativeGetParagraphCount(handle: Long): Int
    external fun nativeCloseMobi(handle: Long)
}

// Usage
val handle = NativeLib.nativeOpenMobi(filePath)
val metaInfo = NativeLib.nativeGetMetaInfo(handle)
val count = NativeLib.nativeGetParagraphCount(handle)
for (i in 0 until count) {
    val paragraph = NativeLib.nativeGetParagraph(handle, i)
    // Process paragraph
}
NativeLib.nativeCloseMobi(handle)
```

### Parser Wrapper
```kotlin
class MobiParser {
    fun parse(filePath: String): Result<MobiBook> {
        return try {
            val handle = NativeLib.nativeOpenMobi(filePath)
            val metaInfo = NativeLib.nativeGetMetaInfo(handle)
            val paragraphs = mutableListOf<ParagraphData>()
            val count = NativeLib.nativeGetParagraphCount(handle)
            
            for (i in 0 until count) {
                paragraphs.add(NativeLib.nativeGetParagraph(handle, i))
            }
            
            NativeLib.nativeCloseMobi(handle)
            
            Result.success(MobiBook(metaInfo, paragraphs))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Best Practices

### When Using Native Library
1. **Always close handles**: Release native resources
2. **Use try-finally**: Ensure cleanup
3. **Check return values**: Validate native call results
4. **Handle exceptions**: Catch and handle native exceptions
5. **Thread safety**: Be aware of thread safety

### When Adding Native Functions
1. **Update JNI bindings**: Add Kotlin and C++ bindings
2. **Document thoroughly**: Clear documentation of behavior
3. **Add error handling**: Proper error checking
4. **Test all ABIs**: Test on all supported architectures
5. **Memory management**: Proper allocation/deallocation

### When Working with Data Models
1. **Use data classes**: Immutable data models
2. **Handle nulls**: Proper nullable types
3. **ByteArray handling**: Be careful with large byte arrays
4. **Serialization**: Implement if needed

## Important Notes

- **Native Library**: Uses JNI to call C++ code
- **Resource Management**: Always close native handles
- **ABI Specific**: Separate native library for each ABI
- **Performance**: Very fast parsing (native implementation)
- **Memory**: Efficient memory usage (mostly in native heap)
- **Thread Safety**: Native library is thread-safe
- **Error Handling**: Native errors thrown as Java exceptions

## Native Build Configuration

### CMake Options
```cmake
cmake_minimum_required(VERSION 3.22.1)
project("mobi")

# C++ Standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")

# ABI Support
ANDROID_ABI = "armeabi-v7a;arm64-v8a;x86;x86_64"

# Flexible Page Sizes
ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES = ON
```

### ABI Filters
```gradle
ndk {
    abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
}
```

## Performance Considerations

### Native Advantages
- **Speed**: C++ is faster than Java for parsing
- **Memory**: More efficient memory usage
- **Parsing**: Optimized parsing algorithms
- **Compression**: Native decompression support

### JNI Overhead
- **Method Calls**: JNI calls have overhead
- **Data Transfer**: Minimize data transfer across JNI
- **Batching**: Batch operations when possible
- **Caching**: Cache results to avoid repeated calls

## Future Considerations

- **More Formats**: Add support for more ebook formats
- **DRM Support**: Enhanced DRM handling
- **Performance**: Further optimize native code
- **New Features**: Add libmobi features as they become available
- **Testing**: Increase native code test coverage
