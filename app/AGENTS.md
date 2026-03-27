# App Module - Agent Development Guide

## Module Overview

The **app** module is the main application module of HandyReader, containing the UI layer, ViewModels, and core application logic. It serves as the entry point and orchestrates all other modules.

## Build Commands

### Module-specific Build
- `./gradlew :app:assembleDebug` - Build debug APK
- `./gradlew :app:assembleRelease` - Build release APK
- `./gradlew :app:bundleRelease` - Build release AAB for Play Store

### Testing
- `./gradlew :app:test` - Run unit tests
- `./gradlew :app:testDebugUnitTest` - Run debug unit tests
- `./gradlew :app:lintDebug` - Run lint checks for debug variant

### Single Test Execution
```bash
# Run specific test class
./gradlew :app:test --tests "*ExampleUnitTest"

# Run specific test method
./gradlew :app:test --tests "*ExampleUnitTest.testMethod"
```

## Architecture

### Layer Organization
The app module follows **Clean Architecture** principles:

**Presentation Layer**:
- **UI**: Jetpack Compose screens in `ui/screens/`
- **ViewModels**: `presentation/*/viewmodels/` - State management and business logic coordination
- **Navigation**: `ui/navigation/` - Compose Navigation setup

**Domain Layer**:
- **Use Cases**: `domain/usecase/` - Business logic abstractions
- **Repository Interfaces**: `domain/repository/` - Contracts for data operations

**Data Layer**:
- **Repository Implementations**: `data/repository/` - Concrete implementations
- **Data Sources**: `data/source/` - Local (Room, DataStore) and remote data sources
- **Models**: `data/model/` - Database entities and data transfer objects

### Key Components

#### Database (Room)
- **Schema Location**: `app/schemas/`
- **Database**: `AppDatabase`
- **Key Entities**:
  - `Book` - Book metadata and reading progress
  - `Chapter` - Chapter information
  - `BookAnnotation` - User annotations and highlights
  - `Shelf` - Book organization
  - `AppPreferences` - Application settings
  - `ThemePreferences` - Theme settings (isolated DataStore)

#### Dependency Injection
- **Application**: `HandyReaderApp` - `@HiltAndroidApp`
- **ViewModels**: `@HiltViewModel` with `@Inject constructor`
- **Use Cases**: Injected into ViewModels
- **Repositories**: Injected with DAO/service dependencies

#### Theme System
- **Isolated DataStore**: `ThemePreferencesUtil` - Separate from app preferences
- **ViewModel**: `AppThemeViewModel` - Application-level theme state
- **Theme Application**: `ReadTheme` composable - Root theme wrapper
- **Color Schemes**: `ColorSchemes.kt` - Material You support

#### TTS (Text-to-Speech)
- **Service**: `TtsPlaybackService` - Background playback
- **Controller**: `TtsServiceController` - Service lifecycle management
- **Engines**:
  - `EdgeTtsService` - Edge TTS integration
  - Built-in Android TTS support
- **Repository**: `SpeakerRepository` - Voice management

## Technology Stack

### Core Framework
- **Kotlin**: 2.1.10
- **Jetpack Compose**: 1.5.17 (Material 3)
- **Hilt**: Dependency injection
- **Room**: Local database
- **DataStore**: Preferences storage
- **Coroutines & Flow**: Async operations

### UI Libraries
- **Compose Navigation**: Type-safe navigation
- **Compose Material 3**: Material Design 3 components
- **Coil 3**: Image loading (`coil-compose`, `coil-network-okhttp`)
- **Material Icons Extended**: Extended icon set

### Media & TTS
- **Media3 (ExoPlayer)**: Audio playback
- **EdgeTTS**: Neural TTS voices
- **Android TTS**: System TTS fallback

### Analytics & Monitoring
- **Firebase**: Analytics and Crashlytics
- **Sentry**: Crash reporting with source context

### Networking
- **Ktor**: HTTP client for API calls
- **OkHttp**: HTTP client with logging

### Other Libraries
- **AboutLibraries**: Dependency licenses display
- **Play Review KTX**: In-app reviews
- **Markdown Renderer**: M3 markdown support

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
- **Classes**: PascalCase (e.g., `MainActivity`, `BookViewModel`)
- **Functions**: camelCase (e.g., `getBooks`, `updateTheme`)
- **Variables**: camelCase (e.g., `bookList`, `selectedTheme`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_PAGE_SIZE`)
- **Backing fields**: underscore prefix (e.g., `private val _uiState`)
- **Composable functions**: PascalCase (e.g., `BookListScreen`, `ThemeDialog`)

### Type Annotations
- Prefer explicit return types for public APIs
- Use nullable types (`Type?`) with null checks
- Avoid `!!` operator - use safe calls (`?.`) or `requireNotNull()`
- Use `val` over `var` for immutability

### Error Handling
- Throw exceptions for programmer errors
- Use Result sealed class or Flow for expected failures
- Log errors with context (no Timber, use standard logging)
- Handle exceptions in ViewModels and update UI state

## State Management

### Flow/StateFlow Pattern
```kotlin
private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

### Compose State
- Use `remember` for local UI state
- Use `collectAsState()` to observe StateFlow in Composables
- Prefer state hoisting - lift state to caller when needed

## Testing Guidelines

### Unit Tests
- **Location**: `app/src/test/java/com/wxn/reader/`
- **Framework**: JUnit 4
- **Mocking**: Mockito or similar
- **Test ViewModels and Use Cases** in isolation

### Instrumented Tests
- **Location**: `app/src/androidTest/java/com/wxn/reader/`
- **Framework**: AndroidJUnitRunner
- **Test Compose UI** with `composeTestRule`
- **Test Room** migrations and DAOs

## Key Patterns

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

### Use Case Pattern
```kotlin
class GetBooksUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    operator fun invoke(params: Params): Flow<Result<List<Book>>> {
        // Business logic
    }
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

## Important Notes

- **Never commit**: `key.properties`, `keystore.jks`, `google-services.json`, `local.properties`
- **ProGuard**: Enabled for release builds with full debug symbols
- **MultiDex**: Enabled for minSdk 23
- **Build Config**: Includes release date and feedback API URL
- **APK Naming**: `handyreader_{variant}_v{versionName}_{versionCode}.apk`
- **AAB Splitting**: Language splitting disabled, density and ABI splitting enabled
- **Desugaring**: Enabled for Java 8+ API support on older devices

## Module Dependencies

The app module depends on:
- **bookparser** - File format parsing
- **bookread** - Reading interface components
- **base** - Shared utilities
- **text2speech** - TTS functionality
- **mobi** - MOBI format support (transitive via bookparser)

## Navigation

### Route Definition
Routes defined in `ui/navigation/Screens.kt` as sealed classes:
```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object BookDetail : Screen("bookDetail/{bookId}")
}
```

### Navigation Usage
```kotlin
val navController = LocalNavController.current
navController.navigate(Screen.BookDetail.createRoute(bookId))
```

## Theme System Details

### Data Isolation
- **Theme DataStore**: `theme_prefs` (separate from general app preferences)
- **App Preferences DataStore**: `app_prefs`
- This prevents cache conflicts and ensures instant theme application

### Theme Flow
1. User changes theme in `ThemeScreen`
2. `ThemeViewModel` updates preferences
3. `ThemePreferencesUtil` saves to DataStore
4. `AppThemeViewModel` observes changes via Flow
5. `ReadTheme` recomposes with new theme
6. All screens receive updated MaterialTheme

### Color Schemes
- **Dynamic Colors** (Android 12+): System-generated from wallpaper
- **Predefined Schemes**: Monochrome, Twilight, Sepia, Teal, Lavender, etc.
- **Premium Integration**: Some schemes require premium subscription

## Common File Locations

- **Screens**: `app/src/main/java/com/wxn/reader/ui/screens/`
- **ViewModels**: `app/src/main/java/com/wxn/reader/presentation/*/viewmodels/`
- **Database**: `app/src/main/java/com/wxn/reader/data/database/`
- **Repositories**: `app/src/main/java/com/wxn/reader/data/repository/`
- **Navigation**: `app/src/main/java/com/wxn/reader/ui/navigation/`
- **Theme**: `app/src/main/java/com/wxn/reader/ui/theme/`
- **Utils**: `app/src/main/java/com/wxn/reader/util/`
