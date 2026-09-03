# IYKYK Person Collage

An offline Android app that turns a portrait video into a shareable collage with every unique person shown once and each person's separate appearance count.

## Status

The project is being built as a sequence of verified vertical slices. The current milestone establishes the Compose app, safe video selection, and unidirectional UI state.

## Planned on-device pipeline

```text
Selected video
  -> sampled frames
  -> face detection and visibility filtering
  -> continuous face tracks (appearances)
  -> FaceNet embeddings
  -> constrained identity clustering
  -> representative-shot scoring
  -> collage rendering, saving, and sharing
```

No video or face data is uploaded.

## Build

Prerequisites:

- Android Studio with JDK 17 or newer
- Android SDK 36
- An Android device or emulator running API 26 or newer

Run the checks from the repository root:

```bash
./gradlew testDebugUnitTest assembleDebug
```

More details, model attribution, measured thresholds, and sample-video results will be added as their respective milestones are completed.

