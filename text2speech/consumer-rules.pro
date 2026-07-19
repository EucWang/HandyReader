# Consumer ProGuard rules for the :text2speech module.
# Applied automatically by consuming modules (the :app) at minification time.

# sherpa-onnx: every class under com.k2fsa.sherpa.onnx declares external fun
# JNI methods (newFromAsset, delete, generateImpl, ...) bound by name to C++
# symbols, and they return/accept data classes (e.g. OfflineRecognizerResult,
# DenoisedAudio, KeywordSpotterResult) whose field layout must match the native
# side. Keep the whole package: there are 20+ JNI-bearing classes with many
# native descriptor types, so narrowing would be fragile.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# net.gotev.speech: TTS engine adapter surface (TextToSpeechCallbackAdapter,
# SherpaOnnxEngine, AudioTrackPlayer). Keep public API; engine impl classes are
# instantiated by name.
-keep class net.gotev.speech.** { *; }
