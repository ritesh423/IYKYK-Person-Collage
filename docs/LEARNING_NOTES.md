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

## Milestone 2: Video metadata and streaming frame sampling

### Why sample frames instead of decoding the full frame rate?

The supplied videos are 25 frames per second, but adjacent frames contain almost the same information. Face detection on all 750 frames of a 30-second clip would increase heat and processing time without giving a proportional accuracy improvement. The initial policy samples four frames per second, producing 120 analysis opportunities while still observing a person every 250 milliseconds.

The sampling rate is a documented policy rather than a hidden magic number. We will validate it against all three videos and change it only when measurements justify the change.

### Why stream one bitmap at a time?

A 540 × 960 ARGB bitmap uses roughly 2 MB of pixel memory. Keeping 120 such frames could require around 240 MB before face crops or model tensors are considered. The sampler therefore follows this ownership rule:

```text
decode one frame -> consumer processes it -> recycle it -> decode the next frame
```

The `onFrame` callback is suspending, so the next frame is not decoded until the current consumer finishes. A frame must not be retained after the callback returns.

### Why use `MediaMetadataRetriever`?

It can read video metadata and retrieve frames directly from the `content://` URI returned by Photo Picker. This avoids copying the entire selected video into app storage. It also supports the assignment's minimum API level without adding a full video-player dependency.

### How is the main thread protected?

The ViewModel starts work in `viewModelScope`, which manages the operation's lifetime. `AndroidVideoFrameSampler` moves metadata reading and frame decoding to `Dispatchers.IO` with `withContext`. UI state remains observable through `StateFlow`, and Compose redraws progress without performing the decoding itself.

### How does cancellation work?

The ViewModel retains the sampling `Job`. Choosing another video, clearing the selection, or pressing Cancel cancels that job. The sampling loop calls `ensureActive()` before each decode, and `MediaMetadataRetriever.release()` runs in `finally`, so its native resources are released on success, failure, or cancellation.

### What is separated for testing?

`FrameSamplingPlan` contains pure timestamp and scaling calculations that run in normal JVM tests. `VideoFrameSampler` is an interface, so ViewModel state transitions can be tested with a fake sampler without loading a real video or starting an emulator. `AndroidVideoFrameSampler` is the Android-specific implementation.

### What did we validate on Android?

All three supplied videos were selected through the real Photo Picker on an API 36.1 emulator. Each file reported 1080 × 1920 dimensions, a 30-second duration, and 120 decoded frames out of 120 requested frames. Progress visibly advanced while the interface remained responsive. Cancellation returned to the ready state, and logcat contained no application crash or `MediaMetadataRetriever` error.

### Interview check

1. Why is four frames per second a reasonable starting point for these videos?
2. What memory problem would occur if all sampled bitmaps were stored in a list?
3. What is the difference between `viewModelScope.launch` and `withContext(Dispatchers.IO)` here?
4. Why must `MediaMetadataRetriever.release()` be in a `finally` block?
5. How does the interface make the ViewModel testable?
