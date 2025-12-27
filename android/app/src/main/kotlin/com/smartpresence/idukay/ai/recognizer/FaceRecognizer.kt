package com.smartpresence.idukay.ai.recognizer

import android.graphics.Bitmap

/**
 * Interface for face recognition models
 */
interface FaceRecognizer {
    /**
     * Generate face embedding from aligned face image
     * @param alignedFace 112x112 aligned face bitmap
     * @return 512-dimensional face embedding vector
     */
    suspend fun recognize(alignedFace: Bitmap): FloatArray
    
    /**
     * Calculate cosine similarity between two embeddings
     * @param emb1 First embedding vector
     * @param emb2 Second embedding vector
     * @return Similarity score between -1 and 1 (higher is more similar)
     */
    fun cosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float
    
    /**
     * Release model resources
     */
    fun close()
}
