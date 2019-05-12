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

import android.media.Image
import androidx.annotation.GuardedBy
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetector
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions

class ObjectDetectionAnalyzer(private val overlay: GraphicOverlay) : ImageAnalysis.Analyzer {

    @GuardedBy("this")
    private var processingImage: Image? = null

    private val detector: FirebaseVisionObjectDetector

    @GuardedBy("this")
    @FirebaseVisionImageMetadata.Rotation
    var rotation = FirebaseVisionImageMetadata.ROTATION_90

    @GuardedBy("this")
    var scaledWidth = 0

    @GuardedBy("this")
    var scaledHeight = 0

    init {
        val options = FirebaseVisionObjectDetectorOptions.Builder()
            .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()

        detector = FirebaseVision.getInstance().getOnDeviceObjectDetector(options)
    }

    override fun analyze(imageProxy: ImageProxy, rotationDegrees: Int) {
        val image = imageProxy.image ?: return

        if (processingImage == null) {
            processingImage = image
            processLatestFrame()
        }
    }

    @Synchronized
    private fun processLatestFrame() {
        val processingImage = processingImage
        if (processingImage != null) {
            val image = FirebaseVisionImage.fromMediaImage(
                processingImage,
                rotation
            )

            when (rotation) {
                FirebaseVisionImageMetadata.ROTATION_0,
                FirebaseVisionImageMetadata.ROTATION_180 -> {
                    overlay.setSize(
                        processingImage.width,
                        processingImage.height,
                        scaledHeight,
                        scaledWidth
                    )
                }
                FirebaseVisionImageMetadata.ROTATION_90,
                FirebaseVisionImageMetadata.ROTATION_270 -> {
                    overlay.setSize(
                        processingImage.height,
                        processingImage.width,
                        scaledWidth,
                        scaledHeight
                    )
                }
            }

            detector.processImage(image)
                .addOnSuccessListener { results ->
                    debugPrint(results)

                    overlay.clear()

                    for (obj in results) {
                        val box = obj.boundingBox

                        val name = "${categoryNames[obj.classificationCategory]}"

                        val confidence =
                            if (obj.classificationCategory != FirebaseVisionObject.CATEGORY_UNKNOWN) {
                                val confidence: Int =
                                    obj.classificationConfidence!!.times(100).toInt()
                                " $confidence%"
                            } else ""

                        overlay.add(BoxData("$name$confidence", box))
                    }

                    this.processingImage = null
                }
                .addOnFailureListener {
                    println("failure")

                    this.processingImage = null
                }
        }
    }
}

val categoryNames: Map<Int, String> = mapOf(
    FirebaseVisionObject.CATEGORY_UNKNOWN to "Unknown",
    FirebaseVisionObject.CATEGORY_HOME_GOOD to "Home Goods",
    FirebaseVisionObject.CATEGORY_FASHION_GOOD to "Fashion Goods",
    FirebaseVisionObject.CATEGORY_FOOD to "Food",
    FirebaseVisionObject.CATEGORY_PLACE to "Place",
    FirebaseVisionObject.CATEGORY_PLANT to "Plant"
)

private fun debugPrint(visionObjects: List<FirebaseVisionObject>) {
    for ((idx, obj) in visionObjects.withIndex()) {
        val box = obj.boundingBox

        println("Detected object: $idx")
        println("  Category: ${categoryNames[obj.classificationCategory]}")
        println("  trackingId: ${obj.trackingId}")
        println("  entityId: ${obj.entityId}")
        println("  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")
        if (obj.classificationCategory != FirebaseVisionObject.CATEGORY_UNKNOWN) {
            val confidence: Int = obj.classificationConfidence!!.times(100).toInt()
            println("  Confidence: $confidence%")
        }
    }
}
