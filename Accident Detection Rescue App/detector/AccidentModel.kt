package com.accident.detector

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class AccidentModel(private val context: Context) {

    private var interpreter: Interpreter? = null

    // Load model from assets folder
    fun loadModel() {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("accident_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    // Main prediction function
    // Returns: score between 0.0 and 1.0
    // Above 0.5 = accident detected
    fun predict(ax: Float, ay: Float, az: Float,
                gx: Float, gy: Float, gz: Float): Float {

        val magnitude = sqrt(ax*ax + ay*ay + az*az)

        // Pre-filter — save battery
        // if magnitude too low, skip model entirely
        if (magnitude < 1.5f) return 0.0f

        val input  = Array(1) { floatArrayOf(ax, ay, az, gx, gy, gz, magnitude) }
        val output = Array(1) { floatArrayOf(0f) }

        interpreter?.run(input, output)

        return output[0][0]
    }

    fun isAccident(score: Float): Boolean = score > 0.5f

    fun close() {
        interpreter?.close()
    }
}