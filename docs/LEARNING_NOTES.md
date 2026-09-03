# Learning notes

These notes capture the reasoning behind the implementation so every important decision can be explained in an interview.

## Milestone 1: UI state and video selection

### Why store a content URI instead of a file path?

The Android Photo Picker grants this app access to one user-selected media item through a `content://` URI. The video may live in local storage or a cloud media provider, so assuming that it has a normal filesystem path is incorrect. Android APIs such as `ContentResolver` and `MediaMetadataRetriever` can read from the URI directly.

### What is unidirectional data flow?

The screen sends actions upward, such as “the user selected this video.” The ViewModel handles that action and publishes a new immutable `CollageUiState`. State flows down to Compose, which redraws the relevant UI.

```text
User action -> ViewModel -> StateFlow<CollageUiState> -> Compose UI
```

The UI does not modify shared state directly. This creates one source of truth and makes the state transitions testable without launching Android.

### Why is `CollageUiState` a sealed interface?

The major screen conditions are mutually exclusive: waiting for a video, ready, processing, or failed. A sealed type lets the compiler require the UI to handle every condition. Adding the result state later will produce a compile error until the screen renders it, which is useful protection.

### Why is video processing not in the composable?

Composable functions may run many times. Starting expensive work from normal composable code could restart it during recomposition and block the UI. The ViewModel will launch the pipeline, while the processing classes will move CPU and I/O work to appropriate coroutine dispatchers.

### Interview check

1. Why can a Photo Picker result not safely be converted to a filesystem path?
2. What is the single source of truth on this screen?
3. What would go wrong if face detection ran directly inside a composable?
4. Why use a sealed state type instead of several unrelated Boolean flags?

