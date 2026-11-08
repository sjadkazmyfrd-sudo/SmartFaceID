package com.example.smartfaceid // پکیج پروژه‌ی خودتان را اینجا بگذارید

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private val graphics = mutableListOf<Graphic>()
    
    // مقادیر برای مقیاس‌گذاری
    var scaleFactor = 1.0f
    var postScaleWidthOffset = 0f
    var postScaleHeightOffset = 0f
    var isFacingFront = true // برای تشخیص دوربین جلو و حالت آینه‌ای

    abstract class Graphic(val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        fun scale(horizontal: Float): Float = horizontal * overlay.scaleFactor
        fun translateY(vertical: Float): Float = vertical * overlay.scaleFactor

        // تنظیم مختصات X با در نظر گرفتن دوربین جلو (آینه‌ای)
        fun translateX(x: Float): Float {
            return if (overlay.isFacingFront) {
                overlay.width - (x * overlay.scaleFactor)
            } else {
                x * overlay.scaleFactor
            }
        }

        fun postInvalidate() {
            overlay.postInvalidate()
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}
