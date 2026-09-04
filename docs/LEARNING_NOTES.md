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

## Milestone 3: On-device face detection and quality gates

### Detection, tracking, and recognition are different problems

Face detection answers: “Where are the faces in this frame?” ML Kit returns a bounding box, pose, optional landmarks/classifications, and an optional tracking ID.

Face tracking answers: “Which nearby detections probably belong to the same moving face in adjacent frames?” ML Kit tracking IDs are useful as short-term hints, but they can reset after a cut, missed frame, or detector state change.

Face recognition answers: “Do these appearances, possibly separated by cuts and time, show the same person?” ML Kit face detection does not solve that. A separate embedding model and clustering logic will handle recognition in later milestones.

### Why bundle the ML Kit model?

We use `com.google.mlkit:face-detection:16.1.7`. The bundled detector increases the application size, but it is immediately available after installation and does not wait for a first-use model download. This is the reliable choice for a fully on-device assignment demo.

The alternative Google Play Services dependency is smaller, but its model may need to download before detection returns useful results.

### Why use accurate mode?

This is an offline, user-initiated 30-second video analysis rather than a live camera preview. The assessment gives half its score to accuracy, so accurate mode is a better trade-off than maximizing real-time frame rate. Sampling already limits work to four frames per second.

### Why enable landmarks and classification but disable contours?

Landmarks support pose estimation. Classification provides left-eye-open, right-eye-open, and smile probabilities needed for representative-shot decisions.

Contours would add 133 detailed facial points, but ML Kit provides contours only for the most prominent face. The supplied videos intentionally contain frames with two people. Enabling contours would therefore add work while creating a dangerous single-face assumption. We keep contours disabled.

### Why is the detector's minimum face size 0.08?

ML Kit defines minimum face size as head width divided by input-image width, and treats it as a performance hint rather than a strict filter. At an input width of 540 px, `0.08f` asks the detector to search down to roughly 43 px. This permissive detector setting reduces false negatives; our explicit post-detection quality policy makes the strict decisions.

### Why have two quality decisions?

A face can be useful for identity matching without being attractive enough for the final collage. For example, a face close to the frame edge may help maintain an appearance track, but selecting it as the final portrait could produce a clipped head.

Current matching requirements are at least 80 px on the shortest edge, at least 85% visible inside the frame, and no extreme pose beyond ±35° pitch, ±45° yaw, or ±40° roll.

A representative candidate must also be at least 120 px, safely inside the frame, within ±20° pitch/roll and ±18° yaw, and have both eye-open probabilities at or above 0.55. The ±18° yaw threshold matches the documented range in which ML Kit's eye/smile classifiers are intended for frontal faces.

These are documented policy values, not identity-clustering thresholds. The analyzer emits raw observations and measured rejection reasons instead of silently hiding detections. The current UI shows aggregate counts; the next pipeline stage will consume the full per-frame observations while their source bitmap is valid.

### Why is smile not a hard requirement?

A neutral but sharp, frontal, open-eye portrait is acceptable. Making a smile mandatory could eliminate a person who never smiles in the video. Smile probability is retained for later ranking as a small positive signal.

### How does asynchronous ML Kit processing fit bitmap ownership?

ML Kit returns a Google `Task`. `Task.await()` converts it to a suspending call without blocking a thread. The current input bitmap must remain valid until that task completes, so one in-flight detector task is allowed at a time. The sampler recycles the bitmap only after `FaceAnalyzer.analyze()` returns.

Cancellation is observed between frames. We let the current detector task release its input before recycling the bitmap, then the cancelled sampling loop stops before decoding another frame.

### What did we validate?

On an API 36.1 emulator, Samples 1–3 produced respectively 128, 125, and 127 raw face observations. Matching-quality observations were 90, 92, and 99; representative candidates were 39, 48, and 49. Every video produced a maximum of two faces in one frame, confirming that simultaneous-person frames were not collapsed to one face. All 120 sampled frames were analyzed in each run, with no app, media, or detector errors in logcat.

### Interview check

1. What is the difference between face detection, tracking, and recognition?
2. Why did we choose the bundled ML Kit dependency?
3. Why is accurate mode appropriate here even though ML Kit recommends fast mode for live camera use?
4. Why would contour detection be harmful for this assignment?
5. Why are tracking IDs not sufficient for identifying a person across separate appearances?
6. Why are matching eligibility and representative eligibility separate?
7. Why are eye probabilities trusted only for reasonably frontal faces?
8. Why must the bitmap remain alive until ML Kit's task completes?
