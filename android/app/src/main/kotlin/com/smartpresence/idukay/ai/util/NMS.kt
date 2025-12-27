package com.smartpresence.idukay.ai.util

import com.smartpresence.idukay.ai.detector.Detection
import kotlin.math.max
import kotlin.math.min

object NMS {
    
    /**
     * Non-Maximum Suppression to remove overlapping detections
     * @param detections List of detections to filter
     * @param iouThreshold IoU threshold for suppression
     * @return Filtered list of detections
     */
    fun apply(detections: List<Detection>, iouThreshold: Float = 0.4f): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence descending
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val suppressed = mutableSetOf<Int>()
        
        for (i in sorted.indices) {
            if (i in suppressed) continue
            
            keep.add(sorted[i])
            
            // Suppress overlapping boxes
            for (j in (i + 1) until sorted.size) {
                if (j in suppressed) continue
                
                val iou = calculateIoU(sorted[i].bbox, sorted[j].bbox)
                if (iou > iouThreshold) {
                    suppressed.add(j)
                }
            }
        }
        
        return keep
    }
    
    /**
     * Calculate Intersection over Union between two bounding boxes
     */
    fun calculateIoU(box1: FloatArray, box2: FloatArray): Float {
        val x1 = max(box1[0], box2[0])
        val y1 = max(box1[1], box2[1])
        val x2 = min(box1[2], box2[2])
        val y2 = min(box1[3], box2[3])
        
        val intersectionWidth = max(0f, x2 - x1)
        val intersectionHeight = max(0f, y2 - y1)
        val intersection = intersectionWidth * intersectionHeight
        
        val area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
        val area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
        val union = area1 + area2 - intersection
        
        return if (union > 0f) intersection / union else 0f
    }
}
