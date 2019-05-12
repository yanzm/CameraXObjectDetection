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
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class AutoFitPreviewBuilder private constructor(
    config: PreviewConfig,
    viewFinderRef: WeakReference<TextureView>,
    bufferRotation: (Int) -> Unit,
    private val scaledSize: (Int, Int) -> Unit
) {
    val preview: Preview

    private var viewFinderRotation: Int
    private var bufferSize: Size = Size(0, 0)
    private var viewFinderSize: Size = Size(0, 0)
    private val viewFinderDisplayId: Int

    private lateinit var displayManager: DisplayManager

    init {
        val viewFinder = checkNotNull(viewFinderRef.get())

        viewFinderDisplayId = viewFinder.display.displayId
        viewFinderRotation = getDisplaySurfaceRotation(viewFinder.display) ?: 0

        preview = Preview(config)

        preview.onPreviewOutputUpdateListener = Preview.OnPreviewOutputUpdateListener {
            val textureView = viewFinderRef.get() ?: return@OnPreviewOutputUpdateListener

            // To update the SurfaceTexture, we have to remove it and re-add it
            (textureView.parent as ViewGroup).apply {
                removeView(textureView)
                addView(textureView, 0)
            }

            textureView.surfaceTexture = it.surfaceTexture
            bufferRotation(it.rotationDegrees)

            val rotation = getDisplaySurfaceRotation(textureView.display)
            updateTransform(textureView, rotation, it.textureSize, viewFinderSize)
        }

        viewFinder.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val textureView = view as TextureView
            val newViewFinderDimens = Size(right - left, bottom - top)
            val rotation = getDisplaySurfaceRotation(textureView.display)
            updateTransform(textureView, rotation, bufferSize, newViewFinderDimens)
        }

        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val textureView = viewFinderRef.get() ?: return
                if (displayId != viewFinderDisplayId) {
                    val display = displayManager.getDisplay(displayId)
                    val rotation = getDisplaySurfaceRotation(display)
                    updateTransform(textureView, rotation, bufferSize, viewFinderSize)
                }
            }
        }

        displayManager =
            viewFinder.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        viewFinder.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View?) = Unit
            override fun onViewDetachedFromWindow(view: View?) {
                displayManager.unregisterDisplayListener(displayListener)
            }
        })
    }

    private fun updateTransform(
        textureView: TextureView,
        rotation: Int?,
        newBufferSize: Size,
        newViewFinderSize: Size
    ) {
        if (rotation == viewFinderRotation
            && newBufferSize == bufferSize
            && newViewFinderSize == viewFinderSize
        ) {
            return
        }

        var valid = true

        if (rotation == null) {
            valid = false
        } else {
            viewFinderRotation = rotation
        }

        if (newBufferSize.width == 0 || newBufferSize.height == 0) {
            valid = false
        } else {
            bufferSize = newBufferSize
        }

        if (newViewFinderSize.width == 0 || newViewFinderSize.height == 0) {
            valid = false
        } else {
            viewFinderSize = newViewFinderSize
        }

        if (!valid) return

        val matrix = Matrix()

        val centerX = viewFinderSize.centerWidth
        val centerY = viewFinderSize.centerHeight

        matrix.postRotate(-viewFinderRotation.toFloat(), centerX, centerY)

        val (scaledWidth, scaledHeight) = bufferSize.scaledToViewFinder(
            viewFinderSize,
            viewFinderRotation
        )

        scaledSize(scaledWidth, scaledHeight)

        val scaleX = scaledWidth / viewFinderSize.width.toFloat()
        val scaleY = scaledHeight / viewFinderSize.height.toFloat()

        matrix.preScale(scaleX, scaleY, centerX, centerY)

        textureView.setTransform(matrix)
    }

    companion object {

        private fun getDisplaySurfaceRotation(display: Display?) = when (display?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> null
        }

        fun build(
            config: PreviewConfig,
            viewFinder: TextureView,
            bufferRotation: (Int) -> Unit,
            scaledSize: (Int, Int) -> Unit
        ) = AutoFitPreviewBuilder(
            config,
            WeakReference(viewFinder),
            bufferRotation,
            scaledSize
        ).preview
    }
}

val Size.centerWidth: Float
    get() = width / 2f

val Size.centerHeight: Float
    get() = height / 2f

fun Size.scaledToViewFinder(viewFinderSize: Size, rotation: Int): Pair<Int, Int> {

    // Buffers are rotated relative to the device's 'natural' orientation: swap width and height
    val bufferRatio = width / height.toFloat() // longSide / shortSide

    val w: Int
    val h: Int
    if (rotation % 180 == 0) {
        w = viewFinderSize.width
        h = viewFinderSize.height
    } else {
        w = viewFinderSize.height
        h = viewFinderSize.width
    }

    val viewFinderRatio = h / w.toFloat()

    val scaledHeight: Int
    val scaledWidth: Int

    if (bufferRatio > viewFinderRatio) {
        scaledWidth = w
        scaledHeight = (w * bufferRatio).roundToInt()
    } else {
        scaledWidth = (h / bufferRatio).roundToInt()
        scaledHeight = h
    }

    return scaledWidth to scaledHeight
}
