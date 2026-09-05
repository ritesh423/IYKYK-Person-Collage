# Third-party notices

## MobileFaceNet face-embedding model

- Bundled file: `app/src/main/assets/models/mobilefacenet.tflite`
- Purpose: converts an aligned `112 × 112` RGB face image into a 192-dimensional embedding
- Source: [hugocornellier/face_detection_tflite](https://github.com/hugocornellier/face_detection_tflite/blob/50c784adaa9f40c722affb1d4412674f25e1fe0c/assets/models/mobilefacenet.tflite)
- Source revision: `50c784adaa9f40c722affb1d4412674f25e1fe0c`
- SHA-256: `be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854`
- Size: 5,233,552 bytes
- Architecture reference: [MobileFaceNets: Efficient CNNs for Accurate Real-Time Face Verification on Mobile Devices](https://arxiv.org/abs/1804.07573)
- License: Apache License 2.0; an unmodified copy is stored beside the model as `mobilefacenet.LICENSE.txt`

The model binary is redistributed without modification. Its checked runtime contract is one float32 input tensor shaped `[1, 112, 112, 3]` and one float32 output tensor shaped `[1, 192]`. The app performs RGB normalization to `[-1, 1]` and L2-normalizes the output vector before comparison.
