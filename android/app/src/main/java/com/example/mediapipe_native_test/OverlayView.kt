package com.example.mediapipe_native_test

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = poseLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // Force a redraw
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            // Menyesuaikan skala gambar dengan view
            scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

            for (landmark in poseLandmarkerResult.landmarks()) {
                // Menggambar titik landmark
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }

                // Menggambar garis koneksi
                PoseLandmarker.POSE_LANDMARKS.forEach {
                    val start = landmark[it.start()]
                    val end = landmark[it.end()]
                    canvas.drawLine(
                        start.x() * imageWidth * scaleFactor,
                        start.y() * imageHeight * scaleFactor,
                        end.x() * imageWidth * scaleFactor,
                        end.y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }
        }
    }

    fun clear() {
        results = null
        pointPaint.color = Color.GREEN
        linePaint.color = Color.RED
        invalidate()
    }
}
