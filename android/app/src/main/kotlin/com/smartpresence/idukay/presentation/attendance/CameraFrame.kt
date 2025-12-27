package com.smartpresence.idukay.presentation.attendance

import android.graphics.Bitmap

data class CameraFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val release: () -> Unit
) {
    fun close() = release()
}

