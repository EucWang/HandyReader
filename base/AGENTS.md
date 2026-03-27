# Base Module - Agent Development Guide

## Module Overview

The **base** module provides shared utilities, base classes, and common extensions used across all other modules in the HandyReader project. It contains no Android-specific UI components and serves as a foundation for the entire application.

## Build Commands

### Module-specific Build
- `./gradlew :base:assembleDebug` - Build debug AAR
- `./gradlew :base:assembleRelease` - Build release AAR

### Testing
- `./gradlew :base:test` - Run unit tests
- `./gradlew :base:lint` - Run lint checks

## Architecture

### Module Organization

The base module is a **pure utility library** with the following structure:

```
base/
├── ext/           # Extension functions
├── util/          # Utility classes and functions
├── ui/            # Base UI components (Compose utilities)
├── data/          # Base data models
└── constants/     # Constants and configuration
```

### Key Components

#### Extension Functions
- **Primitive Extensions**: Kotlin standard library extensions
- **Collection Extensions**: List, Set, Map utilities
- **Context Extensions**: Android Context helpers
- **View Extensions**: View-related utilities

#### Utility Classes
- **Log Utils**: Unified logging interface (Timber integration)
- **Toaster**: Toast message utilities
- **Date/Time Utils**: Date formatting and parsing
- **File Utils**: File operations and path handling

#### Base UI Components
- **Compose Extensions**: Modifier utilities
- **Color Extensions**: Color manipulation functions
- **Dimension Extensions**: DP/SP conversion helpers

## Technology Stack

### Core Dependencies
- **Kotlin**: Standard library
- **AndroidX Core**: Core KTX extensions
- **AppCompat**: Android compatibility
- **Jetpack Compose**: Foundation utilities (Compose UI basics)
- **Material**: Material Design components
- **Timber**: Logging library
- **Toaster**: Toast message display

### No External Dependencies
The base module has minimal dependencies to maintain reusability across all modules.

## Code Style Guidelines

### Kotlin Conventions
- **Style**: Official Kotlin style (`kotlin.code.style=official`)
- **Indentation**: 4 spaces (no tabs)
- **Line length**: Prefer readability, no strict limit
- **Import ordering**:
  1. Android/AndroidX imports
  2. Third-party libraries
  3. Project imports (com.wxn.base)
  4. Blank line between groups

### Naming Conventions
- **Extension Functions**: camelCase with descriptive names
  ```kotlin
  fun Context.showToast(message: String) { ... }
  ```
- **Utility Functions**: camelCase, often in utility objects
  ```kotlin
  object FileUtils {
      fun getFileExtension(path: String): String { ... }
  }
  ```
- **Constants**: UPPER_SNAKE_CASE
  ```kotlin
  const val DEFAULT_BUFFER_SIZE = 8192
  ```

### Extension Function Guidelines
- **Keep extensions focused**: Single responsibility
- **Avoid extension conflicts**: Don't extend types you don't own unless necessary
- **Use receiver context**: Leverage the receiver object naturally
- **Document behavior**: Especially for non-obvious extensions

## Common Patterns

### Extension Functions
```kotlin
// Context extension
fun Context.dpToPx(dp: Int): Float {
    return dp * resources.displayMetrics.density
}

// Collection extension
fun <T> List<T>.safeGet(index: Int): T? {
    return if (index in indices) this[index] else null
}

// String extension
fun String.isNotNullOrBlank(): Boolean {
    return this.isNotBlank()
}
```

### Utility Objects
```kotlin
object DateUtils {
    const val DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
```

### Compose Utilities
```kotlin
// Modifier extensions
fun Modifier.verticalPadding(padding: Dp) = this.padding(
    vertical = padding
)

// Color utilities
fun Color.toArgbInt(): Int {
    return this.toArgb().toLong().toInt()
}
```

## Testing Guidelines

### Unit Tests
- **Location**: `base/src/test/java/com/wxn/base/`
- **Framework**: JUnit 4
- **Test utility functions** with various inputs
- **Test extension functions** with edge cases
- **Mock dependencies** when necessary

### Test Example
```kotlin
class StringUtilsTest {
    @Test
    fun `isNotNullOrBlank returns true for non-empty string`() {
        val result = "test".isNotNullOrBlank()
        assertTrue(result)
    }

    @Test
    fun `isNotNullOrBlank returns false for blank string`() {
        val result = "   ".isNotNullOrBlank()
        assertFalse(result)
    }
}
```

## Key Features

### Logging
- **Unified Interface**: Uses Timber for consistent logging
- **Tag Management**: Automatic tag generation
- **Debug/Release Control**: Logging behavior based on build type

### Toast Messages
- **Thread Safety**: Shows toasts from any thread
- **Duration Control**: Short and long toast support
- **Context Handling**: Accepts context parameter

### File Operations
- **Path Manipulation**: File path utilities
- **Extension Detection**: File type identification
- **Safe Operations**: Null-safe file operations

### Date/Time
- **Formatting**: Standardized date formats
- **Parsing**: Safe date parsing with error handling
- **Timezone Support**: Locale-aware operations

## Module Dependencies

The base module **has no dependencies** on other HandyReader modules.

### External Dependencies
- AndroidX Core KTX
- AndroidX AppCompat
- Jetpack Compose (BOM)
- Material
- Timber (logging)
- Toaster

## Design Principles

### Reusability
- All code should be reusable across modules
- No Android-specific UI components
- No business logic from app module

### Simplicity
- Keep functions focused and simple
- Avoid over-engineering
- Prefer standard library solutions

### Performance
- Lazy initialization where appropriate
- Avoid unnecessary object allocation
- Use inline functions for critical paths

### Null Safety
- Prefer nullable types with safe calls
- Use `requireNotNull()` for validation
- Document nullability expectations

## Common File Locations

- **Extensions**: `base/src/main/java/com/wxn/base/ext/`
- **Utilities**: `base/src/main/java/com/wxn/base/util/`
- **UI Helpers**: `base/src/main/java/com/wxn/base/ui/`
- **Constants**: `base/src/main/java/com/wxn/base/constants/`

## Important Notes

- **No ViewModels**: This module contains no ViewModels or business logic
- **No Database**: No Room entities or DAOs
- **No Network**: No networking code or API clients
- **Pure Utilities**: Only utility functions and extensions

## Usage Examples

### In Other Modules
```kotlin
// Import base module utilities
import com.wxn.base.ext.showToast
import com.wxn.base.util.DateUtils

// Use in ViewModels
class MyViewModel : ViewModel() {
    fun showMessage(context: Context) {
        context.showToast("Hello from base module")
    }

    fun formatDate(timestamp: Long): String {
        return DateUtils.formatTimestamp(timestamp)
    }
}
```

### In Compose UI
```kotlin
import com.wxn.base.ui.verticalPadding

@Composable
fun MyComponent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalPadding(16.dp)
    ) {
        // Content
    }
}
```

## Best Practices

### When Adding to Base Module
1. **Check for duplicates**: Search existing code before adding
2. **Keep it simple**: Don't add complex logic
3. **Document well**: Other developers will use this code
4. **Test thoroughly**: Base code is used everywhere
5. **Consider alternatives**: Use standard library when possible

### When Using Base Module
1. **Import explicitly**: Use full import paths
2. **Check nullability**: Handle nullable types properly
3. **Read documentation**: Understand function behavior
4. **Report issues**: Fix bugs in base module promptly

## Future Considerations

- **Multiplatform Support**: Structure for potential KMP migration
- **Performance Optimization**: Profile critical utility functions
- **Documentation**: Add more usage examples
- **Test Coverage**: Increase unit test coverage
