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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.android.synthetic.main.fragment_camera.*
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            (activity as? MainActivity)?.moveToPermission()
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .setTargetName("Preview")
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val analyzer = ObjectDetectionAnalyzer {
            overlay?.post {
                updateOverlay(it)
            }
        }

        imageAnalysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            analyzer
        )

        try {
            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
        }
    }

    private fun updateOverlay(detectedObjects: DetectedObjects) {

        if (overlay == null) {
            return
        }

        if (detectedObjects.objects.isEmpty()) {
            overlay.set(emptyList())
            return
        }

        overlay.setSize(detectedObjects.imageWidth, detectedObjects.imageHeight)

        val list = mutableListOf<BoxData>()

        for (obj in detectedObjects.objects) {

            val box = obj.boundingBox

            val label = obj.labels.joinToString { label ->
                val confidence: Int = label.confidence.times(100).toInt()
                "${label.text} $confidence%"
            }

            val text = if (label.isNotEmpty()) label else "unknown"

            list.add(BoxData(text, box))
        }

        overlay.set(list)
    }
}
