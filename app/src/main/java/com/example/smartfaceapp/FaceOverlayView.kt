package com.example.smartfaceapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class DetectedFaceInfo(val boundingBox: RectF, val name: String)

class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var faces: List<DetectedFaceInfo> = emptyList()
    private val boxPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 6f }
    private val unknownBoxPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 42f; style = Paint.Style.FILL }
    private val bgPaint = Paint().apply { color = Color.parseColor("#99000000"); style = Paint.Style.FILL }

    fun setFaces(newFaces: List<DetectedFaceInfo>) {
        faces = newFaces
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (face in faces) {
            val isUnknown = face.name == "Unknown"
            val paint = if (isUnknown) unknownBoxPaint else boxPaint
            canvas.drawRect(face.boundingBox, paint)
            val textWidth = textPaint.measureText(face.name)
            val textHeight = 50f
            val bgRect = RectF(face.boundingBox.left, face.boundingBox.top - textHeight - 10f, face.boundingBox.left + textWidth + 20f, face.boundingBox.top)
            canvas.drawRect(bgRect, bgPaint)
            canvas.drawText(face.name, face.boundingBox.left + 10f, face.boundingBox.top - 15f, textPaint)
        }
    }
}
