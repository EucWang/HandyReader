# HandyReader - Agent Development Guide

This guide helps agentic coding agents work effectively in the HandyReader Android project.

## ⚠️ Critical Operating Constraints

### 🚫 **MANDATORY: Windows 11 Native Environment Only**
- **REQUIRED**: This project MUST run on Windows 11 system
- **ALLOWED**: Use **cmd** or **PowerShell** ONLY for running scripts and commands
- **FORBIDDEN**: **NEVER use WSL bash** to execute any commands
- **Reason**: Java path issues, Gradle daemon conflicts, and native module compilation failures in WSL

**Examples**:
```cmd
# ✅ CORRECT - Windows CMD
gradlew :app:assembleDebug

# ✅ CORRECT - PowerShell
.\gradlew :app:build

# ❌ FORBIDDEN - WSL Bash
./gradlew build  # DO NOT USE
```

### 🚫 **MANDATORY: No Git Commands**
- **FORBIDDEN**: **DO NOT use any git commands** for version control or code commits
- **Reason**: Git operations are handled externally by development team
- **What to avoid**: `git add`, `git commit`, `git push`, `git status`, `git diff`, etc.
- **Allowed**: File operations (read/write/edit) using appropriate tools, bash commands for non-git operations

## Build Commands

### Core Build Operations
- `./gradlew build` - Full build of all modules
- `./gradlew assembleDebug` - Debug APK build
- `./gradlew assembleRelease` - Release APK build
- `./gradlew bundleRelease` - Release AAB bundle for Play Store

### Testing
- `./gradlew test` - Run unit tests across all modules
- `./gradlew connectedAndroidTest` - Run instrumented tests on connected device/emulator
- `./gradlew :app:test` - Run unit tests for app module only
- `./gradlew :app:testDebugUnitTest` - Run debug unit tests for app module
- `./gradlew :base:test --tests "*ExampleUnitTest"` - Run specific test class

### Single Test Execution
```bash
# Run specific test class
./gradlew :app:test --tests "*ExampleUnitTest"

# Run specific test method
./gradlew :app:test --tests "*ExampleUnitTest.testMethod"

# Run tests for specific module
./gradlew :bookread:test --tests "*.*Test"
```

### Code Quality
- `./gradlew lint` - Run Android lint checks across all variants
- `./gradlew :app:lintDebug` - Run lint for debug variant of app module

## Project Structure

### Module Organization

#### Module Documentation
- **[app/AGENTS.md](app/AGENTS.md)** - Main application module guide
- **[base/AGENTS.md](base/AGENTS.md)** - Shared utilities module guide
- **[bookread/AGENTS.md](bookread/AGENTS.md)** - Reading interface module guide
- **[bookparser/AGENTS.md](bookparser/AGENTS.md)** - File format parsing module guide
- **[mobi/AGENTS.md](mobi/AGENTS.md)** - MOBI format support module guide
- **[jp2forandroid/AGENTS.md](jp2forandroid/AGENTS.md)** - JPEG2000 decoding module guide
- **[text2speech/AGENTS.md](text2speech/AGENTS.md)** - TTS functionality module guide

#### Module Overview
- **app/** - Main application with UI layer (Compose screens, ViewModels)
- **base/** - Shared utilities, base classes, common extensions
- **bookread/** - Reading interface components and logic
- **bookparser/** - File format parsing (EPUB, MOBI, AZW, etc.)
- **mobi/** - MOBI format support (C++/Java via libmobi)
- **jp2forandroid/** - JPEG2000 image decoding support
- **text2speech/** - TTS functionality and services

### Architecture Pattern
The project follows **Clean Architecture** with:
- **Presentation Layer**: Compose UI screens + ViewModels
- **Domain Layer**: Use cases (business logic), Repository interfaces
- **Data Layer**: Repository implementations, Room database, DataStore preferences

## Code Style Guidelines

### Kotlin Conventions
- **Style**: Official Kotlin style (`kotlin.code.style=official`)
- **Indentation**: 4 spaces (no tabs)
- **Line length**: No strict limit, prefer readability
- **Import ordering**:
  1. Android/AndroidX imports
  2. Third-party libraries (com., io., etc.)
  3. Project imports (com.wxn.)
  4. Blank line between groups

### Naming Conventions
- **Classes**: PascalCase (e.g., `AppThemeViewModel`, `GetBooksUseCase`)
- **Functions**: camelCase (e.g., `getCenterXCoordinate`, `onColorSelected`)
- **Variables**: camelCase (e.g., `selectedColor`, `rotationAngle`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_PAGE_SIZE`)
- **Backing fields**: underscore prefix (e.g., `private val _appPreferences`)
- **Composable functions**: PascalCase (e.g., `ColorPicker`, `StatisticsScreen`)

### Type Annotations
- Prefer explicit return types for public APIs
- Use nullable types (`Type?`) with null checks
- Avoid `!!` operator - use safe calls (`?.`) or `requireNotNull()`
- Use `val` over `var` for immutability

### Error Handling
- Throw exceptions for programmer errors (`IllegalArgumentException`, `IllegalStateException`)
- Use Result sealed class or Flow for expected failures
- Log errors with context (currently no Timber, use standard logging)
- Handle exceptions in ViewModels and update UI state accordingly

## Dependency Injection with Hilt

- Application-level: `@HiltAndroidApp` on Application class
- ViewModels: `@HiltViewModel` with `@Inject constructor`
- Use Cases: `@Inject constructor` with repository dependencies
- Repositories: `@Inject constructor` with DAO/service dependencies

## State Management

### Flow/StateFlow Pattern
```kotlin
private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()
```

### Compose State
- Use `remember` for local UI state
- Use `collectAsState()` to observe StateFlow in Composables
- Prefer state hoisting - lift state to caller when needed

## Room Database

- Schema location: `app/schemas/`
- Entities: `Book`, `Chapter`, `BookAnnotation`, `Shelf`, etc.
- DAOs: Provide interface with `@Dao` annotation
- Migrations: Version control with `@Database(version = N)`

## Theme System

### Architecture Overview

HandyReader uses a **data-isolated theme system** to prevent cache conflicts and ensure instant theme application across all screens.

### Key Components

#### 1. Data Layer

**ThemePreferencesUtil** (`app/src/main/java/com/wxn/reader/data/source/local/ThemePreferencesUtil.kt`)
- Dedicated DataStore for theme settings (`theme_prefs`)
- Isolated from general app preferences to avoid cache interference
- Provides Flow-based reactive updates

**ThemePreferences** (`app/src/main/java/com/wxn/reader/data/model/ThemePreferences.kt`)
```kotlin
data class ThemePreferences(
    val appTheme: AppTheme,           // SYSTEM, LIGHT, DARK
    val colorScheme: String,          // "Dynamic", "Light Blue", "Dark Teal", etc.
    val homeBackgroundImage: String   // Background image path
)
```

#### 2. Presentation Layer

**AppThemeViewModel** (`app/src/main/java/com/wxn/reader/ui/theme/AppThemeViewModel.kt`)
- Application-level ViewModel
- Observes `themePrefsFlow` from ThemePreferencesUtil
- Provides theme state to `ReadTheme()` composable

**ThemeViewModel** (`app/src/main/java/com/wxn/reader/presentation/settings/viewmodels/ThemeViewModel.kt`)
- Settings UI-specific ViewModel
- Provides update methods for theme changes
- Includes feedback messaging for user actions

#### 3. Theme Application

**ReadTheme** (`app/src/main/java/com/wxn/reader/ui/theme/Theme.kt:33`)
- Root theme wrapper in MainActivity
- Collects theme preferences from AppThemeViewModel
- Applies MaterialTheme with dynamic color schemes
- Handles system theme mode (dark/light)
- Supports Material You dynamic colors on Android 12+

### Theme Data Flow

```
User Action (ThemeScreen)
    ↓
ThemeViewModel.updateColorSchemePreferences()
    ↓
ThemePreferencesUtil.updateColorTheme()
    ↓
DataStore (theme_prefs) [persisted]
    ↓
themePrefsFlow.emit()
    ↓
AppThemeViewModel._themePreferences
    ↓
ReadTheme recomposes
    ↓
MaterialTheme applied to all screens
```

### Supported Color Schemes

**Dynamic Colors** (Android 12+):
- System-generated color schemes based on wallpaper

**Predefined Schemes**:
- Monochrome (Light/Dark Default)
- Twilight (Grey)
- Sepia
- Parchment
- Pastel Yellow
- Teal
- Lavender Blue
- Pastel Pink
- Violet (Purple)
- Crimson Red
- Emerald Green

### Theme Update Methods

**In ThemeViewModel**:
```kotlin
// Update both theme and color scheme
fun updateThemePreferences(newAppTheme: AppTheme, newColorScheme: String)

// Update only color scheme
fun updateColorSchemePreferences(newColorScheme: String)

// Update only app theme mode
fun updateAppThemePreferences(newAppTheme: AppTheme)
```

### Feedback System

Theme changes trigger **Snackbar notifications** for immediate user feedback:
- "Theme updated successfully"
- "Color scheme updated to {color name}"
- "App theme updated to {mode}"

### Important Notes

- **Data Isolation**: Theme settings stored in separate `theme_prefs` DataStore
- **Instant Application**: Changes apply immediately via Flow recomposition
- **Persistence**: All theme preferences survive app restarts
- **System Sync**: Automatic dark/light mode switching when using SYSTEM theme
- **Premium Integration**: Some color schemes require premium subscription

### Testing Theme Changes

```kotlin
// In ThemeScreen.kt
@Composable
fun ThemeScreen(viewModel: ThemeViewModel = hiltViewModel()) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
    
    LaunchedEffect(updateMessage) {
        updateMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearUpdateMessage()
        }
    }
}
```

### Migration from AppPreferences

When working with themes, **always use ThemePreferencesUtil** instead of AppPreferencesUtil:
- ✅ `ThemePreferencesUtil` - for theme-related settings
- ❌ `AppPreferencesUtil` - for general app settings (NOT themes)

This separation prevents cache conflicts and ensures reliable theme application.

## Jetpack Compose UI

### Material Design 3
- Use `MaterialTheme` for consistent styling
- Custom color schemes: `ColorSchemes.kt`
- Custom typography: `Type.kt`
- Dynamic theming with Material You support

### Navigation
- Single activity with Compose Navigation
- Routes defined in `navigation/Screens.kt` as sealed classes
- Use `LocalNavController` for navigation from nested composables

## Testing Guidelines

### Unit Tests
- Use JUnit 4 (junit:junit:4.13.2)
- Test Use Cases and ViewModels in isolation
- Mock repositories with Mockito or similar
- Example test location: `app/src/test/java/com/ricdev/uread/`

### Instrumented Tests
- Use AndroidJUnitRunner
- Test Compose UI with `composeTestRule`
- Test Room database migrations and DAOs
- Example location: `app/src/androidTest/java/com/ricdev/uread/`

## Build Configuration

### Gradle
- **Gradle version**: 8.11.1 (wrapper)
- **Kotlin**: 2.1.10
- **Compose Compiler**: 1.5.17
- **Java**: 17 (sourceCompatibility, targetCompatibility)
- **Android SDK**: compileSdk=36, minSdk=23, targetSdk=35

### Dependencies
- All versions in `gradle/libs.versions.toml` (Version Catalog)
- Prefer using catalog entries over hardcoded versions
- Example: `implementation(libs.androidx.core.ktx)` instead of direct version

### ProGuard
- Enabled for release builds
- Rules in `app/proguard-rules.pro`
- Keep rules for Hilt, Room, and reflection-heavy code

## Git Workflow

- Main branch: `master`
- Feature branches: `dev_*` prefix (e.g., `dev_tts_1.9`)
- Tags: `v*` for releases (e.g., `v1.9.260312`)
- CI/CD: GitHub Actions builds on push to master and tags

## Common Patterns

### Use Case Pattern
```kotlin
class GetBooksUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    operator fun invoke(params: Params): Flow<Result<Data>> {
        // Business logic here
    }
}
```

### ViewModel Pattern
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val useCase: MyUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}
```

### Composable Preview
```kotlin
@Preview(showBackground = true)
@Composable
fun PreviewMyScreen() {
    MyScreen(/* preview parameters */)
}
```

## File Formats Supported

EPUB, MOBI, AZW, AZW3, FB2, TXT, MD, PDF, MP3

## Key Dependencies

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **DI**: Hilt (Dagger)
- **Database**: Room
- **Async**: Coroutines, Flow
- **Image**: Coil 3
- **Parsing**: libmobi (native), jsoup, CSSParser
- **Media**: ExoPlayer (Media3)
- **Storage**: DataStore Preferences
- **TTS**: EdgeTTS, sherpa-onnx

## Important Notes

- **Never commit**: `key.properties`, `keystore.jks`, `google-services.json`, `local.properties`
- **NDK version**: 29.0.13599879 (for native modules)
- **Build Tools**: 36.0.0
- **Sentry integration**: Enabled for crash reporting
- **Firebase**: Analytics and Crashlytics enabled
