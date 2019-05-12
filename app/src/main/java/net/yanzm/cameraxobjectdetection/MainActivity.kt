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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysisConfig
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSION
            )
        }
    }

    private fun startCamera() {
        val objectDetectionAnalyzer = ObjectDetectionAnalyzer(overlay)

        val previewConfig = PreviewConfig.Builder().build()

        val preview = AutoFitPreviewBuilder.build(
            previewConfig,
            viewFinder,
            bufferRotation = {
                objectDetectionAnalyzer.rotation = when (it) {
                    0 -> FirebaseVisionImageMetadata.ROTATION_0
                    90 -> FirebaseVisionImageMetadata.ROTATION_90
                    180 -> FirebaseVisionImageMetadata.ROTATION_180
                    270 -> FirebaseVisionImageMetadata.ROTATION_270
                    else -> FirebaseVisionImageMetadata.ROTATION_0
                }
            },
            scaledSize = { scaledWidth, scaledHeight ->
                objectDetectionAnalyzer.scaledWidth = scaledWidth
                objectDetectionAnalyzer.scaledHeight = scaledHeight
            }
        )

        val analyzerConfig = ImageAnalysisConfig.Builder()
            .apply {
                val analyzerThread = HandlerThread("ObjectDetection").apply { start() }
                setCallbackHandler(Handler(analyzerThread.looper))
            }
            .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            .build()

        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            analyzer = objectDetectionAnalyzer
        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSION = 10
    }
}
