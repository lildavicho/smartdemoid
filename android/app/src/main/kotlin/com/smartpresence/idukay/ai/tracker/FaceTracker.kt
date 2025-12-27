package com.smartpresence.idukay.ai.tracker

import com.smartpresence.idukay.ai.RecognitionConfig
import com.smartpresence.idukay.ai.detector.Detection
import com.smartpresence.idukay.ai.util.NMS

data class TrackedDetection(
    val trackId: Int,
    val bbox: FloatArray
)

data class TrackVote(
    val studentId: String?,
    val distance: Float,
    val quality: Float,
    val timestampMs: Long
)

data class TrackVotingState(
    val trackId: Int,
    val bbox: FloatArray,
    val bestStudentId: String?,
    val votesForBest: Int,
    val windowSize: Int,
    val averageDistanceForBest: Float?,
    val lastQuality: Float,
    val isConfirmed: Boolean
)

/**
 * Represents a tracked face across frames
 */
data class Track(
    val id: Int,
    var bbox: FloatArray,
    var framesSinceUpdate: Int = 0,
    val votes: MutableList<TrackVote> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Track
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id
}

/**
 * Tracks faces across frames using IoU-based matching
 */
class FaceTracker(
    private val iouThreshold: Float = RecognitionConfig.TRACKER_IOU_THRESHOLD,
    private val maxFramesMissing: Int = RecognitionConfig.MAX_FRAMES_MISSING
) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 0
    
    /**
     * Update tracker with new detections
     * @param detections List of detections from current frame
     * @return List of detections assigned to a trackId (same order as detections)
     */
    fun update(detections: List<Detection>): List<TrackedDetection> {
        // 1. Increment framesSinceUpdate for all tracks
        tracks.forEach { it.framesSinceUpdate++ }
        
        if (detections.isEmpty()) {
            // Remove old tracks
            tracks.removeAll { it.framesSinceUpdate > maxFramesMissing }
            return emptyList()
        }
        
        // 2. Match detections with existing tracks using IoU
        val assigned = mutableSetOf<Int>()
        val detectionToTrackId = IntArray(detections.size) { -1 }
        
        for ((detIdx, detection) in detections.withIndex()) {
            var bestMatch: Pair<Int, Float>? = null
            
            for ((trackIdx, track) in tracks.withIndex()) {
                if (trackIdx in assigned) continue
                
                val iou = NMS.calculateIoU(detection.bbox, track.bbox)
                if (iou > iouThreshold) {
                    if (bestMatch == null || iou > bestMatch.second) {
                        bestMatch = trackIdx to iou
                    }
                }
            }
            
            if (bestMatch != null) {
                val trackIdx = bestMatch.first
                tracks[trackIdx].bbox = detection.bbox
                tracks[trackIdx].framesSinceUpdate = 0
                assigned.add(trackIdx)
                detectionToTrackId[detIdx] = tracks[trackIdx].id
            }
        }
        
        // 3. Create new tracks for unmatched detections
        for ((detIdx, detection) in detections.withIndex()) {
            if (detectionToTrackId[detIdx] == -1) {
                val newTrack = Track(id = nextId++, bbox = detection.bbox)
                tracks.add(newTrack)
                detectionToTrackId[detIdx] = newTrack.id
            }
        }
        
        // 4. Remove old tracks
        tracks.removeAll { it.framesSinceUpdate > maxFramesMissing }
        
        return detections.mapIndexed { index, detection ->
            TrackedDetection(
                trackId = detectionToTrackId[index],
                bbox = detection.bbox
            )
        }
    }
    
    /**
     * Add a vote for a track
     * @param trackId Track ID
     * @param studentId Recognized student ID (null if unknown)
     * @param distance Cosine distance (lower is better)
     * @param quality Face quality score (0..1)
     * @param timestampMs Timestamp for this vote
     */
    fun vote(trackId: Int, studentId: String?, distance: Float, quality: Float, timestampMs: Long) {
        val track = tracks.find { it.id == trackId } ?: return
        track.votes.add(
            TrackVote(
                studentId = studentId,
                distance = distance,
                quality = quality,
                timestampMs = timestampMs
            )
        )
        
        // Keep only last N votes (voting window)
        if (track.votes.size > RecognitionConfig.VOTING_WINDOW_FRAMES) {
            track.votes.removeAt(0)
        }
    }
    
    /**
     * Get current voting state per active track
     */
    fun getVotingStates(mode: RecognitionConfig.ThresholdMode): List<TrackVotingState> {
        val distanceThreshold = RecognitionConfig.cosineDistanceThreshold(mode)
        val requiredMatches = RecognitionConfig.VOTING_MIN_MATCHES
        val windowSize = RecognitionConfig.VOTING_WINDOW_FRAMES

        val states = mutableListOf<TrackVotingState>()
        for (track in tracks) {
            val votesWindow = track.votes.takeLast(windowSize)
            val lastQuality = votesWindow.lastOrNull()?.quality ?: 0f

            val counts = votesWindow
                .mapNotNull { it.studentId }
                .groupingBy { it }
                .eachCount()

            val bestStudentId = counts.maxByOrNull { it.value }?.key
            val votesForBest = bestStudentId?.let { counts[it] } ?: 0

            val avgDistanceForBest = if (bestStudentId != null) {
                val distances = votesWindow.filter { it.studentId == bestStudentId }.map { it.distance }
                if (distances.isNotEmpty()) distances.average().toFloat() else null
            } else {
                null
            }

            val isConfirmed = bestStudentId != null &&
                votesForBest >= requiredMatches &&
                avgDistanceForBest != null &&
                avgDistanceForBest <= distanceThreshold

            states.add(
                TrackVotingState(
                    trackId = track.id,
                    bbox = track.bbox,
                    bestStudentId = bestStudentId,
                    votesForBest = votesForBest,
                    windowSize = windowSize,
                    averageDistanceForBest = avgDistanceForBest,
                    lastQuality = lastQuality,
                    isConfirmed = isConfirmed
                )
            )
        }

        return states
    }
    
    /**
     * Get track by ID
     */
    fun getTrack(trackId: Int): Track? {
        return tracks.find { it.id == trackId }
    }
    
    /**
     * Clear all tracks
     */
    fun clear() {
        tracks.clear()
        nextId = 0
    }
    
    /**
     * Get all active tracks
     */
    fun getActiveTracks(): List<Track> = tracks.toList()
}
