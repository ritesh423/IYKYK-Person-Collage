# IYKYK Person Collage

An offline Android app that turns a portrait video into a shareable collage with every unique person shown once and each person's separate appearance count.

## Status

The project is being built as a sequence of verified vertical slices. The current milestone streams four sampled frames per second through bundled, on-device ML Kit face detection. Each result carries face geometry, short-term tracking IDs, head pose, eye-open probabilities, smile probability, and separate quality decisions for identity matching and representative portraits. The current screen keeps aggregate counts; the next milestone will consume the per-frame observations for appearance tracking.

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

## Face detection configuration

The app uses the bundled `com.google.mlkit:face-detection:16.1.7` dependency. Bundling adds APK size, but the detector is available immediately and does not depend on a first-run Google Play Services model download.

- Performance mode: accurate
- Landmarks: enabled
- Eye/smile classification: enabled
- Tracking IDs: enabled as short-term motion hints only
- Contours: disabled, because ML Kit returns contours only for the most prominent face
- Requested minimum face size: `0.08f` of input width
- Input analysis size for supplied videos: 540 × 960

ML Kit detects faces; it does not recognize identities. Cross-appearance identity matching will use a separate documented face-embedding model.

### Quality policy

Every raw face observation is emitted as measurement data, then classified for two different purposes. The current milestone aggregates those observations into validation counts; the next pipeline stage will consume their full per-frame details.

| Decision | Current requirements |
| --- | --- |
| Usable for matching | At least 80 px on the shortest face edge, at least 85% inside the frame, and pose within ±35° pitch, ±45° yaw, ±40° roll |
| Representative candidate | Matching-usable, at least 120 px, safely away from frame edges, within ±20° pitch/roll and ±18° yaw, with both eye-open probabilities at least 0.55 |

Smile probability is recorded but is not a hard filter. It will contribute to representative-shot ranking later.

### Milestone 3 validation

Measured with the real Photo Picker and bundled detector on an API 36.1 emulator:

| Supplied video | Frames analyzed | Frames with faces | Raw observations | Matching candidates | Portrait candidates | Max faces/frame |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Sample 1 | 120 | 115 | 128 | 90 | 39 | 2 |
| Sample 2 | 120 | 116 | 125 | 92 | 48 | 2 |
| Sample 3 | 120 | 116 | 127 | 99 | 49 | 2 |

All runs completed without application, media-decoder, or ML Kit errors in logcat.

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
