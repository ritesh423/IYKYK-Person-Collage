# IYKYK Person Collage

An offline Android app that turns a portrait video into a shareable collage with every unique person shown once and each person's separate appearance count.

## Status

The project is being built as a sequence of verified vertical slices. The current milestone separates hard scene transitions and associates frame-level face detections into conservative temporal tracklets. Tracklets retain timestamps, geometry, ML Kit tracking-ID evidence, and quality measurements without retaining the video bitmaps. They are intentionally provisional until on-device face embeddings can reconnect same-person fragments.

## Planned on-device pipeline

```text
Selected video
  -> sampled frames
  -> face detection and visibility filtering
  -> scene boundaries and temporal face tracklets
  -> on-device face embeddings
  -> constrained identity clustering
  -> identity-aware appearance merging and counting
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

## Temporal face tracking

ML Kit tracking IDs are position-and-motion hints for nearby frames, not identity labels. A hard cut, missed detection, or internal tracker reset can change an ID for the same person. The tracker therefore applies evidence in this order:

1. A detected scene transition closes every active tracklet.
2. A plausible matching ML Kit tracking ID receives the strongest association score.
3. When an ID is unavailable or stale after a detector gap, bounding-box intersection-over-union, center distance, and size similarity provide a conservative fallback.
4. Candidate associations are applied highest-score first with one-to-one constraints, so one detection cannot update two tracklets.
5. A tracklet remains active across at most 750 ms, which bridges up to two missed 4 FPS samples.

Scene detection scales the current frame to a temporary 12 × 20 RGB signature and computes normalized mean absolute color difference from the previous signature. A score of at least `0.14` marks a transition, with a three-frame cooldown to avoid repeated boundaries during one visual transition. The transition frame separates scenes but does not seed a tracklet because motion blur or cross-fades make it unstable. The 240-pixel signature is immediately discarded.

The association policy is deliberately biased toward splitting an uncertain track instead of merging different people. A split can be repaired later using face embeddings; an incorrect mixed-person track would contaminate identity evidence. For that reason, the tracklet count shown now is not yet the final appearance count.

### Milestone 4 validation

Measured in final runs through the real Photo Picker on an API 36.1 emulator:

| Supplied video | Face observations | Frames with faces | Scene boundaries | Temporal tracklets | Tracklets with preferred matching frames | Single-frame tracklets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Sample 1 | 126 | 114 | 17 | 23 | 17 | 1 |
| Sample 2 | 127 | 117 | 17 | 20 | 19 | 0 |
| Sample 3 | 129 | 116 | 17 | 23 | 18 | 1 |

All 120 frames were processed in each run and no application crashes occurred. Sample 1's known ground truth of 20 appearances will be asserted after embeddings and identity-aware temporal merging; treating the current 23 conservative tracklets as final appearances would be conceptually incorrect.

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
