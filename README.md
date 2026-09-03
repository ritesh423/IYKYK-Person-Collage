# IYKYK Person Collage

An offline Android app that turns a portrait video into a shareable collage with every unique person shown once and each person's separate appearance count.

## Status

The project is being built as a sequence of verified vertical slices. The current milestone reads video metadata and streams four sampled frames per second at a maximum long edge of 960 px. Sampling is cancellable, runs away from the main thread, and does not retain the video's frames in memory.

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

### Milestone 2 validation

On an API 36.1 Android emulator, each of the three supplied 1080 × 1920, 30-second MP4 files decoded all 120 requested sample frames. The live progress UI and cancellation path were also exercised, with no application or media-decoder errors in logcat.

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
