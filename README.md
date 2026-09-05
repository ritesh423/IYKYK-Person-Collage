# IYKYK Person Collage

An offline Android app that turns a portrait video into a shareable collage with every unique person shown once and each person's separate appearance count.

## Status

The project is being built as a sequence of verified vertical slices. The current pipeline detects and tracks faces, creates normalized MobileFaceNet embeddings entirely on-device, groups matching tracklets into anonymous identities, and reports identity-aware appearance counts. Full video frames and temporary face crops are released during streaming so analysis remains memory-safe.

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

Every raw face observation is emitted as measurement data, then classified for two different purposes. Only matching-usable, non-transition observations are embedded; all observations remain available for diagnostics, and representative eligibility is kept separate from identity evidence.

| Decision | Current requirements |
| --- | --- |
| Usable for matching | At least 80 px on the shortest face edge, at least 60% inside the frame, and pose within ±35° pitch, ±45° yaw, ±40° roll |
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

## On-device face embeddings

Face detection and face recognition are separate stages. ML Kit locates a face and supplies landmarks; a bundled MobileFaceNet model converts a consistently aligned face crop into a compact numerical descriptor. It does not produce a person's name. Later, the app will compare descriptors to decide whether two tracklets probably show the same anonymous person.

### Runtime and model contract

The app uses the standalone `com.google.ai.edge.litert:litert:1.4.2` runtime with the Interpreter API, four CPU threads, and XNNPACK enabled. CPU inference is a predictable baseline across the assignment's API 26+ device range, and a roughly 5 MB model does not justify adding device-specific GPU or NPU setup before profiling demonstrates a need.

The model is bundled as an uncompressed asset so LiteRT can memory-map it instead of copying the complete binary into a Java heap array. Before the first inference, the app checks the tensor contract rather than assuming an arbitrary `.tflite` file is compatible:

- Input: one float32 tensor shaped `[1, 112, 112, 3]`
- Output: one float32 tensor shaped `[1, 192]`
- Input preprocessing: RGB channels mapped from `[0, 255]` to `[-1, 1]`
- Output postprocessing: L2 normalization to a unit-length vector

The exact model revision, SHA-256 checksum, byte size, licence, and research reference are recorded in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). A unit test guards the checked binary against an unnoticed replacement.

### Alignment and comparison

ML Kit reports landmarks in the upright image coordinate system. The cropper first brings the sampled bitmap into that same orientation. When both eyes are trustworthy, a two-point similarity transform rotates, scales, and translates the face into a `112 × 112` crop with the eyes at the model's expected locations. ML Kit names eyes from the subject's perspective, so the planner explicitly sorts the two points by image x-coordinate before building the transform. If either eye is missing or their distance is implausibly small, an expanded square around the face bounds provides a deterministic fallback.

The resulting vector is normalized as:

```text
unitEmbedding = rawEmbedding / sqrt(sum(rawEmbedding[i]^2))
```

After normalization, cosine similarity is simply the dot product of two embeddings. A value nearer `1` means the model considers the faces more similar. Identity clustering uses a conservative `0.80` primary threshold, plus temporal constraints: faces visible in the same scene can never be assigned to the same identity. A `0.30` recovery threshold is used only for persistent side-by-side-layout fragments whose alignment is less reliable; it cannot override the simultaneous-visibility constraint. Single-observation identity fragments are left unassigned instead of increasing the reported person count.

### Streaming and failure policy

Embedding runs inside the sampler callback while its bitmap is valid. A temporary aligned crop is recycled immediately after inference, and the tracklet keeps only the small 192-float descriptor. Unusable faces and high-change transition frames are not embedded because poor inputs add misleading identity evidence. Model loading is lazy, inference is serialized behind a lock, cancellation remains distinct from failure, and both ML Kit and LiteRT are closed with the ViewModel lifecycle.

### Milestone 5 validation

Measured through the real Photo Picker on a Samsung SM-M336BU running API 36:

| Supplied video | Frames processed | Face observations | On-device embeddings | Tracklets with embeddings | Scene boundaries | Temporal tracklets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Sample 1 | 120 | 129 | 80 | 18 | 17 | 21 |
| Sample 2 | 120 | 129 | 84 | 18 | 17 | 21 |
| Sample 3 | 120 | 127 | 82 | 17 | 17 | 22 |

LiteRT initialized successfully in every run, and XNNPACK delegated 230 of the model's 231 operations. The result and progress layouts were visually checked at 1080 × 2408. No application crash, inference error, or media-decoder error appeared in the app process logs. The full automated gate contains 40 JVM tests across nine suites, debug APK assembly, and Android lint.

## Identity calibration and appearance-count validation

The supplied videos include rapid transitions, partially cropped faces, and two-person layouts. Transition frames with low full-frame sharpness are excluded from embedding. When exactly two faces occupy opposite sides of a split layout, alignment accounts for the horizontal stretch introduced by the edit before MobileFaceNet inference. Duplicate detections in the same frame are also removed before they can create tracklets.

Final manual validation on a Samsung SM-M336BU running API 36 produced:

| Supplied video | Unique people | Per-person appearances | Total appearances |
| --- | ---: | --- | ---: |
| Sample 1 | 5 | 4, 4, 4, 4, 4 | 20 |
| Sample 2 | 5 | 4, 5, 4, 4, 4 | 21 |
| Sample 3 | 5 | 5, 4, 4, 4, 3 | 20 |

Sample 1 matches the assignment's exact ground truth: five people, four appearances each, and twenty appearances overall. The other supplied videos validate generalization without hardcoded filenames, timestamps, identities, or expected counts.

## Build

Prerequisites:

- Android Studio with JDK 17 or newer
- Android SDK 36
- An Android device or emulator running API 26 or newer

Run the checks from the repository root:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Representative selection and collage-output validation will be added as their respective milestones are completed.
