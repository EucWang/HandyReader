# HandyReader - Agent Development Guide

This guide helps agentic coding agents work effectively in the HandyReader Android project.

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
