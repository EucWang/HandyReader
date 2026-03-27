# JP2ForAndroid Module - Agent Development Guide

## Module Overview

The **jp2forandroid** module provides JPEG2000 (JP2/J2K) image decoding support for the HandyReader application. It is based on the open-source openjpeg library and is primarily used to decode JPEG2000 images embedded in PDF files.

## Build Commands

### Module-specific Build
- `./gradlew :jp2forandroid:assembleDebug` - Build debug AAR with native libraries
- `./gradlew :jp2forandroid:assembleRelease` - Build release AAR with native libraries

### Native Build
- **CMake**: Automatically invoked during Gradle build
- **Build Script**: `src/main/cpp/CMakeLists.txt`
- **ABI Support**: armeabi-v7a, arm64-v8a, x86, x86_64

### Testing
- `./gradlew :jp2forandroid:test` - Run unit tests
- `./gradlew :jp2forandroid:connectedAndroidTest` - Run instrumented tests

## Architecture

### Module Organization

The jp2forandroid module uses a **JNI-based native library architecture**:

```
jp2forandroid/
├── src/main/
│   ├── java/                  # Java source
│   │   └── com/gemalto/jp2/
│   │       ├── JP2Decoder.kt  # Main JP2 decoder
│   │       ├── JP2Encoder.kt  # JP2 encoder
│   │       └── Header.kt      # Image header data class
│   ├── cpp/                   # C++ source
│   │   ├── CMakeLists.txt     # Main CMake configuration
│   │   ├── openjpg.cpp        # JNI implementation
│   │   ├── opj_config.h       # OpenJPEG configuration
│   │   ├── opj_apps_config.h  # Apps configuration
│   │   ├── opj_config_private.h # Private configuration
│   │   └── openjpeg/          # OpenJPEG library source
│   │       ├── src/           # OpenJPEG source code
│   │       ├── wrapping/      # Java wrappers
│   │       └── CMakeLists.txt # OpenJPEG CMake
│   ├── androidTest/           # Instrumented tests
│   │   └── com/gemalto/jp2/
│   │       ├── TestJp2Decoder.java
│   │       └── TestJp2Encoder.java
```

### Key Components

#### Decoder
- **JP2Decoder**: Main decoder class for JP2/J2K images
- **Header**: Image metadata (width, height, alpha, resolutions, layers)
- **Input Sources**: Byte array, file path, or InputStream

#### Encoder
- **JP2Encoder**: Encoder for creating JP2/J2K images
- **Encoding Options**: Quality, compression, resolution settings

#### Native Library
- **openjpeg**: Native C++ JPEG2000 codec library
- **JNI Bindings**: C++ bridge between Java and openjpeg
- **Configuration**: Build-time configuration for Android

## Technology Stack

### Core Dependencies
- **Java**: Pure Java implementation (no Kotlin-specific features)
- **Android NDK**: Native development
- **CMake**: Native build system
- **openjpeg**: Open-source JPEG2000 codec

### Native Build
- **CMake**: Native build system
- **NDK**: Android Native Development Kit
- **ABI Support**:
  - armeabi-v7a (32-bit ARM)
  - arm64-v8a (64-bit ARM)
  - x86 (32-bit Intel)
  - x86_64 (64-bit Intel)

### Image Formats
- **JP2**: JPEG2000 file format (ISO/IEC 15444-1)
- **J2K**: JPEG2000 codestream (raw)
- **Color Spaces**: RGB, RGBA, Grayscale, Grayscale+Alpha

## Code Style Guidelines

### Java Conventions
- **Style**: Standard Java style
- **Indentation**: 4 spaces
- **Naming**: Standard Java naming conventions
- **Package**: `com.gemalto.jp2` (preserved from original library)

### Naming Conventions
- **Classes**: PascalCase (e.g., `JP2Decoder`, `JP2Encoder`)
- **Methods**: camelCase (e.g., `decode()`, `setSkipResolutions()`)
- **Constants**: UPPER_SNAKE_CASE
- **Native Methods**: `native<FunctionName>`

### Builder Pattern
```java
public class JP2Decoder {
    private int skipResolutions = 0;
    private int layersToDecode = 0;
    private boolean premultiplication = true;
    
    public JP2Decoder setSkipResolutions(int skipResolutions) {
        this.skipResolutions = skipResolutions;
        return this;
    }
    
    public JP2Decoder setLayersToDecode(int layersToDecode) {
        this.layersToDecode = layersToDecode;
        return this;
    }
}
```

## Common Patterns

### Native Function Declaration
```java
// Java
public class JP2Decoder {
    static {
        System.loadLibrary("openjpeg");
    }
    
    private native int[] decodeJP2ByteArray(byte[] data, int reduce, int layers);
}

// C++ JNI Implementation
extern "C" JNIEXPORT jintArray JNICALL
Java_com_gemalto_jp2_JP2Decoder_decodeJP2ByteArray(
    JNIEnv *env,
    jclass clazz,
    jbyteArray data,
    jint reduce,
    jint layers
) {
    // Implementation
}
```

### Decoder Usage Pattern
```java
// From byte array
byte[] jp2Data = ...;
JP2Decoder decoder = new JP2Decoder(jpData);
decoder.setSkipResolutions(1);  // Reduce resolution by 2x
Bitmap bitmap = decoder.decode();

// From file
JP2Decoder decoder = new JP2Decoder("/path/to/image.jp2");
JP2Decoder.Header header = decoder.readHeader();
Bitmap bitmap = decoder.decode();
```

### Header Reading Pattern
```java
JP2Decoder decoder = new JP2Decoder(jpData);
JP2Decoder.Header header = decoder.readHeader();
Log.d("JP2", "Size: " + header.width + "x" + header.height);
Log.d("JP2", "Resolutions: " + header.numResolutions);
Log.d("JP2", "Quality layers: " + header.numQualityLayers);
```

## Testing Guidelines

### Unit Tests
- **Location**: `jp2forandroid/src/test/java/com/gemalto/jp2/`
- **Framework**: JUnit 4
- **Test format detection**: Test `isJPEG2000()` method
- **Test header parsing**: Validate header extraction
- **Mock native calls**: Mock JNI functions

### Instrumented Tests
- **Location**: `jp2forandroid/src/androidTest/java/com/gemalto/jp2/`
- **Framework**: AndroidJUnitRunner
- **TestJp2Decoder**: Test decoding of JP2/J2K files
- **TestJp2Encoder**: Test encoding to JP2/J2K format
- **Real images**: Test with actual JP2/J2K files

## Key Features

### Supported Formats
- **JP2**: Standard JPEG2000 file format (ISO/IEC 15444-1)
- **J2K**: Raw JPEG2000 codestream
- **Magic Number Detection**: Automatic format detection

### Color Spaces
- **RGB**: 24-bit RGB images
- **RGBA**: 32-bit RGB with alpha channel
- **Grayscale**: 8-bit grayscale
- **Grayscale+Alpha**: 16-bit grayscale with alpha

### Decoding Options
- **Resolution Reduction**: Skip resolution levels for faster decoding
- **Quality Layers**: Decode fewer quality layers for faster decoding
- **Alpha Premultiplication**: Control alpha channel premultiplication
- **Multiple Input Sources**: Byte array, file, or InputStream

### Encoding Options
- **Quality Settings**: Control compression quality
- **Compression Ratio**: Balance quality vs file size
- **Lossless/Lossy**: Support for both modes

### Performance Features
- **Native Implementation**: Fast C++ decoding
- **Memory Efficient**: Direct bitmap creation
- **Resolution Control**: Decode at lower resolutions when needed
- **Layer Control**: Decode fewer quality layers for speed

## Module Dependencies

### No Internal Dependencies
This module has **no dependencies** on other HandyReader modules.

### External Dependencies
- **Android Graphics**: Bitmap, BitmapFactory
- **Android Annotations**: @NonNull, @RequiresApi
- **Java IO**: InputStream, ByteArrayOutputStream

### Native Dependencies
- **openjpeg**: Bundled with module
- **CMake**: Build system
- **NDK**: Toolchain

## Design Principles

### Performance
- **Native Implementation**: C++ for speed
- **Direct Bitmap Creation**: Avoid intermediate conversions
- **Resolution Control**: Decode at appropriate resolution
- **Memory Efficiency**: Minimal Java object allocation

### Flexibility
- **Multiple Inputs**: Support various input sources
- **Configurable**: Extensive decoding options
- **Format Detection**: Automatic format detection
- **Color Space Support**: Multiple color spaces

### Reliability
- **Error Handling**: Robust error checking
- **Validation**: Input validation
- **Resource Management**: Proper resource cleanup
- **ABI Support**: Multiple architecture support

## Common File Locations

- **Main Classes**: `jp2forandroid/src/main/java/com/gemalto/jp2/`
- **Native Code**: `jp2forandroid/src/main/cpp/`
- **JNI Implementation**: `jp2forandroid/src/main/cpp/openjpg.cpp`
- **OpenJPEG Library**: `jp2forandroid/src/main/cpp/openjpeg/`
- **CMake**: `jp2forandroid/src/main/cpp/CMakeLists.txt`
- **Tests**: `jp2forandroid/src/androidTest/java/com/gemalto/jp2/`

## Usage Examples

### Basic Decoding
```java
// From byte array
byte[] jp2Data = ...;
JP2Decoder decoder = new JP2Decoder(jpData);
Bitmap bitmap = decoder.decode();
if (bitmap != null) {
    imageView.setImageBitmap(bitmap);
}
```

### Advanced Decoding with Options
```java
byte[] jp2Data = ...;
JP2Decoder decoder = new JP2Decoder(jpData)
    .setSkipResolutions(1)      // Reduce resolution by 2x
    .setLayersToDecode(1);      // Decode only 1 quality layer

JP2Decoder.Header header = decoder.readHeader();
Log.d("JP2", "Original size: " + header.width + "x" + header.height);

Bitmap bitmap = decoder.decode();
```

### Format Detection
```java
byte[] imageData = ...;
if (JP2Decoder.isJPEG2000(imageData)) {
    JP2Decoder decoder = new JP2Decoder(imageData);
    Bitmap bitmap = decoder.decode();
} else {
    // Handle other formats
}
```

### File Decoding
```java
String filePath = "/sdcard/image.jp2";
JP2Decoder decoder = new JP2Decoder(filePath);

// Read header first
JP2Decoder.Header header = decoder.readHeader();
Log.d("JP2", "Image info:");
Log.d("JP2", "  Size: " + header.width + "x" + header.height);
Log.d("JP2", "  Alpha: " + header.hasAlpha);
Log.d("JP2", "  Resolutions: " + header.numResolutions);
Log.d("JP2", "  Quality layers: " + header.numQualityLayers);

// Decode
Bitmap bitmap = decoder.decode();
```

### Disable Alpha Premultiplication (API 19+)
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    JP2Decoder decoder = new JP2Decoder(jpData)
        .disableBitmapPremultiplication();
    Bitmap bitmap = decoder.decode();
    // Bitmap RGB values are not premultiplied by alpha
}
```

## Best Practices

### When Using JP2Decoder
1. **Check format**: Use `isJPEG2000()` before decoding
2. **Read header**: Get image info before full decoding
3. **Set options**: Configure resolution and quality for performance
4. **Handle errors**: Check for null bitmap
5. **Recycle bitmaps**: Recycle when done to free memory

### When Performance Matters
1. **Use resolution reduction**: Lower resolution for thumbnails
2. **Limit quality layers**: Decode fewer layers for faster decoding
3. **Cache results**: Avoid re-decoding same image
4. **Use appropriate resolution**: Don't decode full resolution for small views

### When Handling Large Images
1. **Read header first**: Check image size
2. **Use resolution reduction**: Decode at lower resolution
3. **Monitor memory**: Be aware of memory usage
4. **Recycle promptly**: Free bitmap resources quickly

## Important Notes

- **Original Library**: Based on open-source openjpeg library
- **Package Name**: Preserved as `com.gemalto.jp2`
- **Java Implementation**: Written in Java (not Kotlin)
- **Native Library**: Uses JNI to call C++ openjpeg
- **Primary Use Case**: Decoding JPEG2000 images in PDF files
- **Memory**: Large images can consume significant memory
- **Thread Safety**: Native library is thread-safe
- **Performance**: Fast native decoding
- **ABI Specific**: Separate native library for each ABI

## Integration with BookParser Module

The jp2forandroid module is used by the bookparser module:

- **PDF Parsing**: Decode JPEG2000 images embedded in PDF files
- **PdfTextParser**: Uses JP2Decoder for image extraction
- **PdfFileParser**: Handles JP2 images in PDF structure

### Integration Example
```kotlin
// In PdfTextParser
class PdfTextParser {
    fun extractImage(imageData: ByteArray): Bitmap? {
        return if (JP2Decoder.isJPEG2000(imageData)) {
            val decoder = JP2Decoder(imageData)
            decoder.decode()
        } else {
            // Handle other image formats
        }
    }
}
```

## Native Build Configuration

### CMake Overview
```cmake
cmake_minimum_required(VERSION 3.22.1)
project("openjpeg")

# OpenJPEG library
add_subdirectory(openjpeg)

# JNI bindings
add_library(openjpeg SHARED
    openjpg.cpp
)

target_link_libraries(openjpeg
    openjp2  # OpenJPEG library
)
```

### Configuration Headers
- **opj_config.h**: OpenJPEG feature configuration
- **opj_apps_config.h**: Application-specific configuration
- **opj_config_private.h**: Private configuration

## Performance Considerations

### Native Advantages
- **Speed**: C++ is much faster than Java for image decoding
- **Memory**: More efficient memory usage
- **Optimization**: Optimized JPEG2000 codec algorithms

### Optimization Tips
- **Resolution Reduction**: Use for thumbnails or previews
- **Quality Layers**: Decode fewer layers for faster display
- **Memory Management**: Recycle bitmaps promptly
- **Caching**: Cache decoded images when possible

## Limitations

- **JPEG2000 Only**: Only supports JP2/J2K formats
- **Color Spaces**: Limited to RGB, RGBA, Grayscale
- **Memory**: Large images require significant memory
- **Android Version**: Some features require API 19+
- **Alpha Premultiplication**: Cannot be disabled on API < 19

## Future Considerations

- **More Features**: Add more JPEG2000 features
- **Performance**: Further optimize native code
- **Kotlin Migration**: Migrate from Java to Kotlin
- **More Tests**: Increase test coverage
- **Documentation**: Add more usage examples
