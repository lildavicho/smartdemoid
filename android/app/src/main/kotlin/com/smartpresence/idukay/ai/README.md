# SmartPresence AI Recognition Pipeline

## Overview

Complete facial recognition pipeline using ONNX Runtime for Android.

## Components

### 1. Face Detection (SCRFD)
- **Model**: `scrfd_10g_bnkps.onnx` (16MB)
- **Input**: 640x640 RGB image
- **Output**: Bounding boxes + 5 facial landmarks
- **Threshold**: 0.5 confidence, 0.4 NMS IoU

### 2. Face Recognition (ArcFace)
- **Model**: `w600k_r50.onnx` (166MB)
- **Input**: 112x112 aligned face
- **Output**: 512-dimensional embedding
- **Similarity**: Cosine similarity ≥ 0.60

### 3. Face Alignment
- Similarity transform using 5 landmarks
- Output: 112x112 aligned face

### 4. Quality Assessment
- Size check (min 80px)
- Blur detection (Laplacian variance)
- Brightness check (50-200 range)
- Threshold: 0.6 quality score

### 5. Face Tracking
- IoU-based matching (threshold 0.3)
- Max 10 frames missing
- Track persistence across frames

### 6. Temporal Voting
- Window: 5 frames
- Min consecutive: 3 frames
- Consensus required for confirmation

## Pipeline Flow

```
Camera Frame
    ↓
SCRFD Detector (640x640)
    ↓
Quality Filter
    ↓
Face Alignment (5 landmarks → 112x112)
    ↓
ArcFace Recognizer (512-dim embedding)
    ↓
Matcher (cosine similarity vs roster)
    ↓
Tracker (IoU-based tracking)
    ↓
Voter (temporal consensus)
    ↓
Recognition Result
```

## Usage

### Initialize Pipeline

```kotlin
@Inject
lateinit var recognitionPipeline: RecognitionPipeline

// Set student roster
val studentEmbeddings = mapOf(
    "student-id-1" to floatArrayOf(...),  // 512-dim embedding
    "student-id-2" to floatArrayOf(...)
)
recognitionPipeline.setStudentRoster(studentEmbeddings)
```

### Process Frame

```kotlin
val results = recognitionPipeline.processFrame(cameraBitmap)

for (result in results) {
    println("Student: ${result.studentId}")
    println("Confidence: ${result.confidence}")
    println("Track ID: ${result.trackId}")
}
```

### Reset

```kotlin
recognitionPipeline.reset()  // Clear tracker state
```

### Cleanup

```kotlin
recognitionPipeline.close()  // Release ONNX resources
```

## Configuration

All parameters in `RecognitionConfig.kt`:

```kotlin
// Detection
DETECTION_INPUT_SIZE = 640
DETECTION_THRESHOLD = 0.5f
NMS_IOU_THRESHOLD = 0.4f

// Recognition
RECOGNITION_INPUT_SIZE = 112
SIMILARITY_THRESHOLD = 0.60f
MARGIN_THRESHOLD = 0.15f

// Quality
MIN_FACE_SIZE = 80
QUALITY_THRESHOLD = 0.6f

// Tracking
TRACKER_IOU_THRESHOLD = 0.3f
MAX_FRAMES_MISSING = 10

// Voting
VOTING_WINDOW = 5
MIN_CONSECUTIVE_FRAMES = 3
```

## Performance

- **Detection**: ~100-150ms per frame
- **Recognition**: ~50-80ms per face
- **Total latency**: ~200-300ms for 1-2 faces

## Memory

- **SCRFD Model**: 16MB
- **ArcFace Model**: 166MB
- **Runtime overhead**: ~50MB
- **Total**: ~250MB

## Dependencies

```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")
implementation("com.jakewharton.timber:timber:5.0.1")
```

## Model Files

Place in `app/src/main/assets/models/`:
- `scrfd_10g_bnkps.onnx`
- `w600k_r50.onnx`

## Thread Safety

All operations use `Dispatchers.Default` for CPU-intensive work.
ONNX sessions are thread-safe for inference.

## Error Handling

- Returns empty list on detection failure
- Returns zero embedding on recognition failure
- Logs all errors with Timber

## Future Enhancements

- [ ] GPU acceleration (NNAPI)
- [ ] Model quantization (INT8)
- [ ] Multi-face batch processing
- [ ] Adaptive quality thresholds
- [ ] Face anti-spoofing
