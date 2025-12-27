package com.smartpresence.idukay.ai.pipeline

import android.graphics.Bitmap
import com.smartpresence.idukay.ai.ModelLoadingState
import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.alignment.FaceAligner
import com.smartpresence.idukay.ai.detector.Detection
import com.smartpresence.idukay.ai.detector.FaceDetector
import com.smartpresence.idukay.ai.detector.SCRFDDetector
import com.smartpresence.idukay.ai.quality.QualityFilter
import com.smartpresence.idukay.ai.recognizer.ArcFaceRecognizer
import com.smartpresence.idukay.ai.recognizer.FaceRecognizer
import com.smartpresence.idukay.ai.tracker.FaceTracker
import com.smartpresence.idukay.ai.tracker.TrackVotingState
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Result of recognition for a single face
 */
data class RecognitionResult(
    val trackId: Int,
    val bbox: FloatArray,
    val studentId: String?,
    val confidence: Float,
    val qualityScore: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RecognitionResult
        if (trackId != other.trackId) return false
        if (!bbox.contentEquals(other.bbox)) return false
        if (studentId != other.studentId) return false
        if (confidence != other.confidence) return false
        if (qualityScore != other.qualityScore) return false
        return true
    }

    override fun hashCode(): Int {
        var result = trackId
        result = 31 * result + bbox.contentHashCode()
        result = 31 * result + (studentId?.hashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        result = 31 * result + qualityScore.hashCode()
        return result
    }
}

enum class TrackRecognitionStatus {
    ANALYZING,
    CONFIRMED
}

data class TrackRecognition(
    val trackId: Int,
    val bbox: FloatArray,
    val status: TrackRecognitionStatus,
    val studentId: String?,
    val confidence: Float,
    val distance: Float,
    val qualityScore: Float,
    val votesForCandidate: Int,
    val requiredVotes: Int,
    val windowSize: Int
)

data class FrameRecognition(
    val tracks: List<TrackRecognition>
) {
    val confirmed: List<RecognitionResult> = tracks
        .filter { it.status == TrackRecognitionStatus.CONFIRMED && it.studentId != null }
        .map {
            RecognitionResult(
                trackId = it.trackId,
                bbox = it.bbox,
                studentId = it.studentId,
                confidence = it.confidence,
                qualityScore = it.qualityScore
            )
        }
}

/**
 * Complete facial recognition pipeline
 * Pipeline: Frame -> Detect -> Quality -> Align -> Recognize -> Match -> Track -> Vote -> Result
 */
class RecognitionPipeline(
    private val detector: FaceDetector,
    private val recognizer: FaceRecognizer,
    private val tracker: FaceTracker
) {

    val modelLoadingErrors: List<String> by lazy {
        val errors = mutableListOf<String>()
        
        if (detector is SCRFDDetector && !detector.loadingState.isSuccess()) {
            detector.loadingState.getErrorMessage()?.let { errors.add("Detector: $it") }
        }
        
        if (recognizer is ArcFaceRecognizer && !recognizer.loadingState.isSuccess()) {
            recognizer.loadingState.getErrorMessage()?.let { errors.add("Recognizer: $it") }
        }
        
        errors
    }

    private val inferenceDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "SmartPresence-Inference")
        }.asCoroutineDispatcher()

    private var studentEmbeddings: Map<String, FloatArray> = emptyMap()

    @Volatile
    private var thresholdMode: RecognitionConfig.ThresholdMode = RecognitionConfig.ThresholdMode.NORMAL

    @Volatile
    private var qualityMin: Float = RecognitionConfig.QUALITY_MIN

    fun setThresholdMode(mode: RecognitionConfig.ThresholdMode) {
        thresholdMode = mode
    }

    fun setQualityMin(min: Float) {
        qualityMin = min
    }

    fun setStudentRoster(embeddings: Map<String, FloatArray>) {
        studentEmbeddings = embeddings
        Timber.d("Loaded ${embeddings.size} student embeddings")
    }

    suspend fun processFrame(frame: Bitmap): FrameRecognition = withContext(inferenceDispatcher) {
        try {
            val nowMs = System.currentTimeMillis()
            val mode = thresholdMode

            val detections = detector.detect(frame)
            Timber.d("Detected ${detections.size} faces")

            val trackedDetections = tracker.update(detections)
            val updatedTrackIds = trackedDetections.map { it.trackId }.toSet()

            if (detections.isNotEmpty()) {
                for ((index, detection) in detections.withIndex()) {
                    val trackId = trackedDetections.getOrNull(index)?.trackId ?: -1
                    processDetection(frame, detection, trackId, nowMs, mode)
                }
            }

            for (track in tracker.getActiveTracks()) {
                if (track.id !in updatedTrackIds) {
                    tracker.vote(track.id, null, 1f, 0f, nowMs)
                }
            }

            val votingStates = tracker.getVotingStates(mode)
            val tracksOut = votingStates.map { it.toTrackRecognition() }

            Timber.d(
                "Track states: ${tracksOut.size} active, " +
                    "${tracksOut.count { it.status == TrackRecognitionStatus.CONFIRMED }} confirmed"
            )

            FrameRecognition(tracks = tracksOut)
        } catch (e: Exception) {
            Timber.e(e, "Pipeline processing failed")
            FrameRecognition(tracks = emptyList())
        }
    }

    private suspend fun processDetection(
        frame: Bitmap,
        detection: Detection,
        trackId: Int,
        nowMs: Long,
        mode: RecognitionConfig.ThresholdMode
    ) {
        try {
            if (trackId < 0) return

            if (!passesGeometryGates(frame, detection.bbox)) {
                tracker.vote(trackId, null, 1f, 0f, nowMs)
                return
            }

            val qualityScore = QualityFilter.assessQuality(frame, detection.bbox)
            if (!QualityFilter.isGoodQuality(qualityScore, qualityMin)) {
                tracker.vote(trackId, null, 1f, qualityScore, nowMs)
                return
            }

            val alignedFace = FaceAligner.alignFace(frame, detection.landmarks)
            val embedding = recognizer.recognize(alignedFace)
            alignedFace.recycle()

            val match = matchEmbedding(embedding, mode)

            if (match.studentId != null) {
                tracker.vote(trackId, match.studentId, match.distance, qualityScore, nowMs)
            } else {
                tracker.vote(trackId, null, match.distance, qualityScore, nowMs)
            }
        } catch (e: Exception) {
            Timber.e(e, "Detection processing failed")
            if (trackId >= 0) {
                tracker.vote(trackId, null, 1f, 0f, nowMs)
            }
        }
    }

    private data class EmbeddingMatch(
        val studentId: String?,
        val distance: Float,
        val similarity: Float
    )

    private fun matchEmbedding(embedding: FloatArray, mode: RecognitionConfig.ThresholdMode): EmbeddingMatch {
        if (studentEmbeddings.isEmpty()) {
            return EmbeddingMatch(null, 1f, 0f)
        }

        var bestMatch: String? = null
        var bestDistance = Float.POSITIVE_INFINITY
        var secondBestDistance = Float.POSITIVE_INFINITY
        var bestSimilarity = 0f

        for ((studentId, studentEmbedding) in studentEmbeddings) {
            val similarity = recognizer.cosineSimilarity(embedding, studentEmbedding)
            val distance = 1f - similarity

            if (distance < bestDistance) {
                secondBestDistance = bestDistance
                bestDistance = distance
                bestSimilarity = similarity
                bestMatch = studentId
            } else if (distance < secondBestDistance) {
                secondBestDistance = distance
            }
        }

        val margin = secondBestDistance - bestDistance
        val distanceThreshold = RecognitionConfig.cosineDistanceThreshold(mode)

        return if (
            bestMatch != null &&
            bestDistance <= distanceThreshold &&
            margin >= RecognitionConfig.DISTANCE_MARGIN_MIN
        ) {
            EmbeddingMatch(bestMatch, bestDistance, bestSimilarity)
        } else {
            EmbeddingMatch(null, bestDistance.coerceAtMost(1f), bestSimilarity)
        }
    }

    private fun passesGeometryGates(frame: Bitmap, bbox: FloatArray): Boolean {
        val x1 = min(bbox[0], bbox[2])
        val y1 = min(bbox[1], bbox[3])
        val x2 = max(bbox[0], bbox[2])
        val y2 = max(bbox[1], bbox[3])

        val width = x2 - x1
        val height = y2 - y1

        if (width < RecognitionConfig.MIN_FACE_SIZE_PX || height < RecognitionConfig.MIN_FACE_SIZE_PX) {
            return false
        }

        val frameW = frame.width.toFloat()
        val frameH = frame.height.toFloat()

        val edgeMargin = max(4f, min(frameW, frameH) * RecognitionConfig.EDGE_MARGIN_RATIO)
        if (x1 < edgeMargin || y1 < edgeMargin || x2 > frameW - edgeMargin || y2 > frameH - edgeMargin) {
            return false
        }

        return true
    }

    fun reset() {
        tracker.clear()
        Timber.d("Pipeline reset")
    }
    
    /**
     * Clear all pipeline state (for course switching)
     */
    fun clearAll() {
        tracker.clear()
        studentEmbeddings = emptyMap()
        Timber.d("Pipeline cleared (all state reset)")
    }

    fun close() {
        detector.close()
        recognizer.close()
        inferenceDispatcher.close()
        Timber.d("Pipeline closed")
    }

    private fun TrackVotingState.toTrackRecognition(): TrackRecognition {
        val distance = averageDistanceForBest ?: 1f
        val confidence = (1f - distance).coerceIn(0f, 1f)

        return TrackRecognition(
            trackId = trackId,
            bbox = bbox,
            status = if (isConfirmed) TrackRecognitionStatus.CONFIRMED else TrackRecognitionStatus.ANALYZING,
            studentId = bestStudentId,
            confidence = confidence,
            distance = distance,
            qualityScore = lastQuality,
            votesForCandidate = votesForBest,
            requiredVotes = RecognitionConfig.VOTING_MIN_MATCHES,
            windowSize = windowSize
        )
    }
}
