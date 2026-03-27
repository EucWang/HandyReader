# Text2Speech Module - Agent Development Guide

## Module Overview

The **text2speech** module provides text-to-speech functionality for the HandyReader application. It offers a simple API for converting text to speech using Android's built-in TTS engine and serves as a foundation for more advanced TTS features.

## Build Commands

### Module-specific Build
- `./gradlew :text2speech:assembleDebug` - Build debug AAR
- `./gradlew :text2speech:assembleRelease` - Build release AAR

### Testing
- `./gradlew :text2speech:test` - Run unit tests
- `./gradlew :text2speech:lint` - Run lint checks

## Architecture

### Module Organization

The text2speech module follows a **service-based TTS architecture**:

```
text2speech/
├── src/main/java/net/gotev/speech/
│   ├── Speech.kt              # Main TTS interface
│   ├── SpeechSingleton.kt     # Singleton instance
│   ├── model/                 # Data models
│   │   ├── Voice.kt          # Voice data model
│   │   └── Utterance.kt      # Utterance data model
│   └── exception/             # Exceptions
│       ├── NotInitializedException.kt
│       └── ...
```

**Note**: This module is based on the open-source [speech](https://github.com/gotev/speech) library by gotev, adapted for HandyReader's needs.

### Key Components

#### Main Interface
- **Speech**: Primary TTS interface for speech synthesis
- **SpeechSingleton**: Singleton instance for easy access

#### Data Models
- **Voice**: Represents available TTS voices
- **Utterance**: Represents text to be spoken

#### Exceptions
- **NotInitializedException**: Thrown when TTS is not initialized
- **Other exceptions**: Various TTS-related errors

## Technology Stack

### Core Dependencies
- **Java**: Pure Java implementation (no Kotlin-specific features)
- **AndroidX**: AppCompat
- **Material Components**: Material Design components

### TTS Engine
- **Android TTS**: Uses Android's built-in TextToSpeech engine
- **Google TTS**: Default TTS engine on most devices
- **Third-party TTS**: Supports any TTS engine installed on device

### Module Dependencies
- **base**: Shared utilities

## Code Style Guidelines

### Java Conventions
- **Style**: Standard Java style
- **Indentation**: 4 spaces
- **Naming**: Standard Java naming conventions
- **Package**: `net.gotev.speech` (preserved from original library)

### Naming Conventions
- **Classes**: PascalCase (e.g., `Speech`, `Voice`, `Utterance`)
- **Methods**: camelCase (e.g., `speak()`, `stop()`, `pause()`)
- **Constants**: UPPER_SNAKE_CASE
- **Interfaces**: Descriptive names (e.g., `SpeakListener`)

### Builder Pattern
```java
public class Utterance {
    private String text;
    private String utteranceId;
    private HashMap<String, String> params;
    
    private Utterance(Builder builder) {
        this.text = builder.text;
        this.utteranceId = builder.utteranceId;
        this.params = builder.params;
    }
    
    public static class Builder {
        // Builder implementation
    }
}
```

## Common Patterns

### Singleton Pattern
```java
public class SpeechSingleton {
    private static Speech instance;
    
    public static synchronized Speech getInstance(Context context) {
        if (instance == null) {
            instance = new Speech(context);
        }
        return instance;
    }
}
```

### Listener Pattern
```java
public interface SpeakListener {
    void onStarted(String utteranceId);
    void onCompleted(String utteranceId);
    void onError(String utteranceId, String error);
}
```

### Builder Pattern Usage
```java
Speech.getInstance(context)
    .speak("Hello, world!")
    .utteranceId("greeting")
    .listener(new SpeakListener() {
        @Override
        public void onCompleted(String utteranceId) {
            // Handle completion
        }
        // ... other methods
    });
```

## Testing Guidelines

### Unit Tests
- **Location**: `text2speech/src/test/java/net/gotev/speech/`
- **Framework**: JUnit 4
- **Mock TTS engine**: Mock Android TextToSpeech
- **Test listeners**: Verify callback behavior
- **Test error cases**: Exception handling

### Instrumented Tests
- **Location**: `text2speech/src/androidTest/java/net/gotev/speech/`
- **Framework**: AndroidJUnitRunner
- **Test TTS engine**: Real TTS engine interaction
- **Test voices**: Voice enumeration
- **Test playback**: Actual speech synthesis

## Key Features

### Basic TTS Functions
- **Speak**: Convert text to speech
- **Stop**: Stop current speech
- **Pause**: Pause speech (if supported)
- **Resume**: Resume paused speech
- **Silence**: Add silence between utterances

### Voice Management
- **Get Voices**: Enumerate available voices
- **Set Voice**: Select specific voice
- **Voice Language**: Filter voices by language
- **Voice Quality**: Voice quality and features

### Speech Control
- **Speed**: Adjust speech rate
- **Pitch**: Adjust speech pitch
- **Volume**: Adjust speech volume
- **Language**: Set speech language

### Utterance Management
- **Utterance ID**: Unique ID for each utterance
- **Callbacks**: Listen for utterance events
- **Chaining**: Chain multiple utterances
- **Priority**: Control utterance priority

### Error Handling
- **Initialization Errors**: TTS engine initialization failures
- **Playback Errors**: Speech synthesis errors
- **Language Errors**: Missing language data
- **Resource Errors**: TTS resource unavailable

## Module Dependencies

### Internal Dependencies
- **base**: Utility functions and extensions

### External Dependencies
- **AndroidX**: AppCompat, Material

### System Dependencies
- **Android TTS**: TextToSpeech system service
- **Audio Manager**: Audio focus management

## Design Principles

### Simplicity
- **Easy API**: Simple, intuitive interface
- **Minimal Setup**: Quick initialization
- **Clear Documentation**: Well-documented methods

### Flexibility
- **Customizable**: Extensive configuration options
- **Extensible**: Easy to add features
- **Compatible**: Works with any TTS engine

### Reliability
- **Error Handling**: Robust error management
- **Resource Cleanup**: Proper resource release
- **Thread Safety**: Safe concurrent access

## Common File Locations

- **Main Interface**: `text2speech/src/main/java/net/gotev/speech/Speech.java`
- **Data Models**: `text2speech/src/main/java/net/gotev/speech/model/`
- **Exceptions**: `text2speech/src/main/java/net/gotev/speech/exception/`
- **Singleton**: `text2speech/src/main/java/net/gotev/speech/SpeechSingleton.java`

## Usage Examples

### Basic Speech
```java
// Get instance
Speech speech = SpeechSingleton.getInstance(context);

// Speak text
speech.speak("Hello, world!");
```

### Advanced Speech with Listener
```java
Speech speech = SpeechSingleton.getInstance(context);

speech.speak("This is a test.")
    .setLanguage(Locale.US)
    .setSpeechRate(1.0f)
    .setPitch(1.0f)
    .setListener(new SpeakListener() {
        @Override
        public void onStarted(String utteranceId) {
            Log.d("TTS", "Started speaking");
        }

        @Override
        public void onCompleted(String utteranceId) {
            Log.d("TTS", "Finished speaking");
        }

        @Override
        public void onError(String utteranceId, String error) {
            Log.e("TTS", "Error: " + error);
        }
    });
```

### Voice Selection
```java
Speech speech = SpeechSingleton.getInstance(context);

// Get available voices
Set<Voice> voices = speech.getVoices();

// Filter by language
Voice englishVoice = voices.stream()
    .filter(v -> v.getLocale().equals(Locale.US))
    .findFirst()
    .orElse(null);

if (englishVoice != null) {
    speech.setVoice(englishVoice);
}
```

### Multiple Utterances
```java
Speech speech = SpeechSingleton.getInstance(context);

speech.speak("First sentence.")
    .utteranceId("sentence1");

speech.speak("Second sentence.")
    .utteranceId("sentence2");

speech.speak("Third sentence.")
    .utteranceId("sentence3");
```

## Best Practices

### When Using Text2Speech Module
1. **Initialize early**: Initialize TTS during app startup
2. **Handle errors**: Always check for TTS availability
3. **Release resources**: Release TTS when done
4. **Use listeners**: Monitor speech progress
5. **Test voices**: Verify voice availability

### When Implementing TTS Features
1. **Check language support**: Verify language data is installed
2. **Handle interruptions**: Properly handle audio focus
3. **Provide feedback**: Update UI based on TTS state
4. **Allow customization**: Let users customize TTS settings
5. **Test on devices**: Test on various devices and TTS engines

## Important Notes

- **Original Library**: Based on gotev/speech open-source library
- **Package Name**: Preserved as `net.gotev.speech`
- **Java Implementation**: Written in Java (not Kotlin)
- **Thread Safety**: Thread-safe implementation
- **Resource Management**: Proper cleanup required
- **TTS Engine**: Uses Android's built-in TTS
- **Voice Availability**: Depends on device and installed TTS engines

## Integration with App Module

The text2speech module is used by the app module's advanced TTS features:

- **EdgeTTS**: More advanced TTS with neural voices
- **TtsService**: Background TTS playback service
- **Speaker Management**: Voice and speaker management
- **TTS Navigation**: Chapter and sentence navigation

This module provides the basic TTS foundation, while the app module extends it with more advanced features.

## Advanced TTS (App Module)

The app module provides more advanced TTS features beyond this module:

### EdgeTTS Integration
- Neural TTS voices from Microsoft Edge
- Higher quality speech synthesis
- More voice options

### Background Playback
- TtsPlaybackService for background playback
- MediaSession integration
- Audio focus management

### Speaker Management
- SpeakerRepository for voice management
- Speaker info and metadata
- Voice customization

### Navigation
- Chapter-based navigation
- Sentence-based navigation
- Reading progress tracking

## Migration from Basic TTS

When migrating from this module to the app module's advanced TTS:

1. **Replace Speech with TtsService**: Use the service-based approach
2. **Use SpeakerRepository**: For voice management
3. **Handle MediaSession**: For media controls
4. **Implement Callbacks**: Use app module's callback system

## Future Considerations

- **Kotlin Migration**: Migrate from Java to Kotlin
- **Coroutines**: Use coroutines instead of callbacks
- **Flow**: Use Flow for reactive TTS updates
- **More Features**: Add more TTS features and options
- **Testing**: Increase test coverage
