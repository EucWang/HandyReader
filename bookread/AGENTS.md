# BookRead Module - Agent Development Guide

## Module Overview

The **bookread** module provides the reading interface components and logic for HandyReader. It handles page rendering, page turning animations, reading progress tracking, and text selection for the reading experience.

## Build Commands

### Module-specific Build
- `./gradlew :bookread:assembleDebug` - Build debug AAR
- `./gradlew :bookread:assembleRelease` - Build release AAR

### Testing
- `./gradlew :bookread:test` - Run unit tests
- `./gradlew :bookread:lint` - Run lint checks

## Architecture

### Module Organization

The bookread module follows a **page-based reading architecture**:

```
bookread/
├── ui/                    # UI components
│   ├── PageView.kt       # Main reading view (Canvas-based)
│   ├── PageViewDataProvider.kt  # Data provider for pages
│   ├── IPageFactory.kt   # Page factory interface
│   ├── TextPageFactory.kt # Text page factory
│   ├── IDataSource.kt    # Data source interface
│   ├── PageCallback.kt   # Page rendering callbacks
│   ├── PageViewCallback.kt # View-level callbacks
│   ├── SelectTextCallback.kt # Text selection callbacks
│   ├── TextPageFactoryCallback.kt # Factory callbacks
│   ├── delegate/         # Page turning animations
│   │   ├── PageDelegate.kt        # Base interface
│   │   ├── NoAnimPageDelegate.kt  # No animation
│   │   ├── SimulationPageDelegate.kt  # Simulation/cover animation
│   │   ├── SlidePageDelegate.kt  # Slide animation
│   │   ├── SlideVerticalPageDelegate.kt  # Vertical slide
│   │   └── VerticalPageDelegate.kt  # Vertical scroll
│   └── widget/           # Custom widgets
│       └── BatteryView.kt  # Battery indicator
```

### Key Components

#### Core Reading View
- **PageView**: Custom Canvas-based view for rendering book pages
- **PageViewDataProvider**: Supplies page content and configuration
- **IPageFactory**: Factory interface for creating page content
- **TextPageFactory**: Implementation for text-based pages

#### Data Flow
- **IDataSource**: Interface for providing book content
- **PageCallback**: Callbacks for page rendering events
- **PageViewCallback**: User interaction callbacks
- **SelectTextCallback**: Text selection handling

#### Page Turning Animations
- **PageDelegate**: Base interface for page turn effects
- **SimulationPageDelegate**: Realistic page flip animation
- **SlidePageDelegate**: Horizontal slide animation
- **SlideVerticalPageDelegate**: Vertical slide animation
- **VerticalPageDelegate**: Continuous vertical scroll
- **NoAnimPageDelegate**: Instant page change (no animation)

## Technology Stack

### Core Dependencies
- **Kotlin**: Standard library with serialization
- **Jetpack Compose**: UI components
- **AndroidX**: Core KTX, AppCompat, Lifecycle
- **Hilt**: Dependency injection

### UI Components
- **Canvas API**: Custom page rendering
- **View-based**: Traditional Android views for performance
- **Compose Integration**: Can be used in Compose via `AndroidView`

### Storage
- **DataStore Preferences**: Reading settings persistence

### Module Dependencies
- **base**: Shared utilities

## Code Style Guidelines

### Kotlin Conventions
- **Style**: Official Kotlin style
- **Indentation**: 4 spaces
- **Import ordering**: AndroidX, third-party, project

### Naming Conventions
- **Views**: `<Name>View` (e.g., `PageView`, `BatteryView`)
- **Delegates**: `<Type>PageDelegate` (e.g., `SimulationPageDelegate`)
- **Interfaces**: `I<Name>` (e.g., `IPageFactory`, `IDataSource`)
- **Callbacks**: `<Name>Callback` (e.g., `PageCallback`)

### Delegate Pattern
```kotlin
interface PageDelegate {
    fun drawPage(canvas: Canvas, paint: Paint)
    fun onTouchEvent(event: MotionEvent): Boolean
    fun nextPage()
    fun previousPage()
}

class SimulationPageDelegate(
    private val view: PageView
) : PageDelegate {
    // Implementation
}
```

## Common Patterns

### Page Rendering
```kotlin
class PageView(context: Context) : View(context) {
    private val paint = Paint()
    private var currentPage: PageData? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        currentPage?.let { page ->
            drawPageContent(canvas, page)
        }
    }

    private fun drawPageContent(canvas: Canvas, page: PageData) {
        // Draw text, images, etc.
    }
}
```

### Delegate Usage
```kotlin
class PageView(context: Context) : View(context) {
    private var delegate: PageDelegate = NoAnimPageDelegate(this)

    fun setPageAnimation(animationType: PageAnimation) {
        delegate = when (animationType) {
            PageAnimation.SIMULATION -> SimulationPageDelegate(this)
            PageAnimation.SLIDE -> SlidePageDelegate(this)
            PageAnimation.NONE -> NoAnimPageDelegate(this)
        }
    }
}
```

### Data Provider Pattern
```kotlin
interface PageViewDataProvider {
    fun getPageText(position: Int): String
    fun getPageConfig(): PageConfig
    fun getTotalPages(): Int
}

class TextPageFactory(
    private val dataSource: IDataSource
) : IPageFactory, PageViewDataProvider {
    override fun createPage(position: Int): PageData {
        val text = dataSource.getChapterText(position)
        return PageData(text, position)
    }
}
```

## Testing Guidelines

### Unit Tests
- **Location**: `bookread/src/test/java/com/wxn/bookread/`
- **Framework**: JUnit 4
- **Test page calculation**: Word wrapping, line breaking
- **Test delegates**: Animation behavior
- **Test callbacks**: Event handling

### Instrumented Tests
- **Location**: `bookread/src/androidTest/java/com/wxn/bookread/`
- **Framework**: AndroidJUnitRunner
- **Test rendering**: Canvas drawing output
- **Test touch events**: User interaction simulation

## Key Features

### Page Rendering
- **Canvas-based**: High-performance custom rendering
- **Text Layout**: Advanced text wrapping and formatting
- **Images**: Embedded image support
- **Styling**: Custom fonts, colors, spacing

### Page Turning Animations
- **Simulation**: Realistic page flip effect
- **Slide**: Smooth horizontal slide
- **Vertical Scroll**: Continuous scrolling
- **No Animation**: Instant page changes
- **Customizable**: User can select preferred animation

### Text Selection
- **Long Press**: Activate selection mode
- **Drag Handles**: Adjust selection bounds
- **Copy**: Copy selected text to clipboard
- **Share**: Share selected text
- **Highlight**: Annotate selected text

### Reading Progress
- **Page Tracking**: Current page position
- **Chapter Progress**: Progress within chapter
- **Book Progress**: Overall book completion
- **Bookmarks**: Save positions
- **Last Read**: Auto-save position

### Customization
- **Font Settings**: Type, size, line height
- **Margins**: Page margin adjustment
- **Background**: Background color/image
- **Brightness**: Screen brightness control

## Module Dependencies

### Internal Dependencies
- **base**: Utility functions and extensions

### External Dependencies
- **Hilt**: Dependency injection
- **Jetpack Compose**: UI components
- **DataStore**: Preferences storage
- **KotlinX Serialization**: Data serialization

## Design Principles

### Performance
- **Canvas Rendering**: Direct drawing for performance
- **View Recycling**: Reuse views when possible
- **Lazy Loading**: Load pages on demand
- **Memory Management**: Release unused resources

### Customization
- **Pluggable Delegates**: Easy to add new animations
- **Configurable**: Extensive configuration options
- **Theme Support**: Dark/light mode compatible
- **Accessibility**: Screen reader support

### User Experience
- **Smooth Animations**: 60fps rendering
- **Responsive**: Instant touch feedback
- **Intuitive**: Natural page turning gestures
- **Customizable**: User preferences respected

## Common File Locations

- **Core UI**: `bookread/src/main/java/com/wxn/bookread/ui/`
- **Delegates**: `bookread/src/main/java/com/wxn/bookread/ui/delegate/`
- **Widgets**: `bookread/src/main/java/com/wxn/bookread/ui/widget/`
- **Data Source**: `bookread/src/main/java/com/wxn/bookread/ui/IDataSource.kt`

## Usage Examples

### Basic Reading View
```kotlin
@Composable
fun ReadingScreen(
    bookId: String,
    chapterIndex: Int
) {
    val pageView = remember { PageView(context) }
    
    AndroidView(
        factory = { pageView },
        update = { view ->
            view.setBookId(bookId)
            view.setChapterIndex(chapterIndex)
        }
    )
}
```

### Animation Selection
```kotlin
enum class PageAnimation {
    SIMULATION,
    SLIDE,
    SLIDE_VERTICAL,
    VERTICAL,
    NONE
}

fun PageView.setAnimationType(type: PageAnimation) {
    this.delegate = when (type) {
        PageAnimation.SIMULATION -> SimulationPageDelegate(this)
        PageAnimation.SLIDE -> SlidePageDelegate(this)
        PageAnimation.SLIDE_VERTICAL -> SlideVerticalPageDelegate(this)
        PageAnimation.VERTICAL -> VerticalPageDelegate(this)
        PageAnimation.NONE -> NoAnimPageDelegate(this)
    }
}
```

### Text Selection
```kotlin
interface SelectTextCallback {
    fun onTextSelected(text: String, start: Int, end: Int)
    fun onSelectionCopy(text: String)
    fun onSelectionShare(text: String)
    fun onSelectionHighlight(text: String, color: Int)
}
```

## Best Practices

### When Using PageView
1. **Set data provider**: Always provide PageViewDataProvider
2. **Configure animation**: Set preferred page animation
3. **Handle callbacks**: Implement PageViewCallback for events
4. **Manage lifecycle**: Properly start/stop in lifecycle
5. **Save progress**: Persist reading position

### When Implementing Delegate
1. **Extend PageDelegate**: Implement all required methods
2. **Handle touch events**: Properly process motion events
3. **Draw efficiently**: Optimize canvas drawing
4. **Animate smoothly**: Use proper interpolation
5. **Test thoroughly**: Various screen sizes and orientations

## Important Notes

- **Canvas-based**: Uses traditional Android Canvas, not Compose
- **Performance Optimized**: Designed for smooth 60fps rendering
- **Custom View**: Not a Compose composable (use AndroidView to embed)
- **Memory Efficient**: Releases resources when not visible
- **Thread Safety**: Most operations on main thread
- **Large Books**: Handles books with thousands of pages

## Performance Considerations

### Page Rendering
- **Caching**: Cache rendered pages when possible
- **Lazy Loading**: Only load visible pages
- **Pre-rendering**: Pre-render next/previous pages
- **Memory Management**: Release off-screen pages

### Animation Performance
- **Hardware Acceleration**: Enabled by default
- **Bitmap Recycling**: Properly recycle bitmaps
- **Frame Rate**: Target 60fps
- **Optimization**: Profile and optimize bottlenecks

## Future Considerations

- **Compose Migration**: Potentially migrate to Compose Canvas
- **More Animations**: Additional page turn effects
- **RTL Support**: Right-to-left language support
- **Accessibility**: Enhanced screen reader support
- **Performance**: Further optimization for large books
