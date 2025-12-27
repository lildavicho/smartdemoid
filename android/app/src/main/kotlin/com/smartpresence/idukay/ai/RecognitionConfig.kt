package com.smartpresence.idukay.ai

object RecognitionConfig {
    // Detection (SCRFD) - Model expects 480x640 (height x width)
    const val DETECTION_INPUT_WIDTH = 640
    const val DETECTION_INPUT_HEIGHT = 480
    const val DETECTION_THRESHOLD = 0.5f
    const val NMS_IOU_THRESHOLD = 0.4f
    
    // Recognition (ArcFace)
    const val RECOGNITION_INPUT_SIZE = 112

    enum class ThresholdMode {
        STRICT,
        NORMAL,
        LENIENT
    }

    enum class PowerMode {
        PERFORMANCE,
        BALANCED,
        ECO
    }

    const val COSINE_THRESHOLD_STRICT = 0.38f
    const val COSINE_THRESHOLD_NORMAL = 0.42f
    const val COSINE_THRESHOLD_LENIENT = 0.46f

    fun cosineDistanceThreshold(mode: ThresholdMode): Float {
        return when (mode) {
            ThresholdMode.STRICT -> COSINE_THRESHOLD_STRICT
            ThresholdMode.NORMAL -> COSINE_THRESHOLD_NORMAL
            ThresholdMode.LENIENT -> COSINE_THRESHOLD_LENIENT
        }
    }

    fun cosineSimilarityThreshold(mode: ThresholdMode): Float {
        return 1f - cosineDistanceThreshold(mode)
    }

    const val DISTANCE_MARGIN_MIN = 0.15f
    
    // Quality Assessment
    const val MIN_FACE_SIZE_PX = 90
    const val QUALITY_MIN = 0.55f
    const val QUALITY_MIN_ECO = 0.65f
    const val MIN_BRIGHTNESS = 50
    const val MAX_BRIGHTNESS = 200
    const val EDGE_MARGIN_RATIO = 0.02f
    
    // Tracking
    const val TRACKER_IOU_THRESHOLD = 0.3f
    const val MAX_FRAMES_MISSING = 10
    
    // Voting
    const val VOTING_WINDOW_FRAMES = 10
    const val VOTING_MIN_MATCHES = 4

    // Anti-duplicados / UX
    const val COOLDOWN_SECONDS = 30

    fun frameIntervalMs(powerMode: PowerMode): Long {
        return when (powerMode) {
            PowerMode.PERFORMANCE -> 0L
            PowerMode.BALANCED -> 100L
            PowerMode.ECO -> 200L
        }
    }

    fun effectiveQualityMin(powerMode: PowerMode): Float {
        return when (powerMode) {
            PowerMode.ECO -> maxOf(QUALITY_MIN, QUALITY_MIN_ECO)
            else -> QUALITY_MIN
        }
    }
    
    // Model paths (INT8 Quantized - Professional ONNX Runtime Compatible)
    const val DETECTOR_MODEL_PATH = "models/scrfd_10g_bnkps_int8.onnx"
    const val RECOGNIZER_MODEL_PATH = "models/w600k_r50_int8.onnx"

    // Debug
    const val DEBUG_OVERLAY = true
}
