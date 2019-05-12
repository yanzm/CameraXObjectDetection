/*
 * Copyright 2019 Yuki Anzai (@yanzm)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.yanzm.cameraxobjectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class BoxData(val text: String, val boundingBox: Rect)

class GraphicOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val lock = Any()

    private val graphics = mutableSetOf<BoxData>()

    private val rectPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20 * resources.displayMetrics.density
    }

    private val rect = RectF()
    private val offset = resources.displayMetrics.density * 8
    private val round = resources.displayMetrics.density * 4

    private var xScale: Float = 1f
    private var yScale: Float = 1f

    private var xOffset: Float = 1f
    private var yOffset: Float = 1f

    fun setSize(targetWidth: Int, targetHeight: Int, scaledWidth: Int, scaledHeight: Int) {
        xScale = scaledWidth / targetWidth.toFloat()
        yScale = scaledHeight / targetHeight.toFloat()

        xOffset = (scaledWidth - width) * 0.5f
        yOffset = (scaledHeight - height) * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            for (graphic in graphics) {
                rect.set(
                    graphic.boundingBox.left * xScale,
                    graphic.boundingBox.top * yScale,
                    graphic.boundingBox.right * xScale,
                    graphic.boundingBox.bottom * yScale
                )

                rect.offset(-xOffset, -yOffset)

                canvas.drawRect(rect, rectPaint)

                if (graphic.text.isNotEmpty()) {
                    canvas.drawRoundRect(
                        rect.left,
                        rect.bottom - offset,
                        rect.left + offset + textPaint.measureText(graphic.text) + offset,
                        rect.bottom + textPaint.textSize + offset,
                        round,
                        round,
                        textBackgroundPaint
                    )
                    canvas.drawText(
                        graphic.text,
                        rect.left + offset,
                        rect.bottom + textPaint.textSize,
                        textPaint
                    )
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(boxData: BoxData) {
        synchronized(lock) {
            graphics.add(boxData)
        }
        postInvalidate()
    }

    fun remove(boxData: BoxData) {
        synchronized(lock) {
            graphics.remove(boxData)
        }
        postInvalidate()
    }
}
