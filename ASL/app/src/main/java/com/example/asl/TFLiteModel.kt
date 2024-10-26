package com.example.asl

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteModel(context: Context) {
    private var interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context, "sign_language_model.tflite"))
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(input: Array<Array<Array<FloatArray>>>): FloatArray {
        val output = Array(1) { FloatArray(numClasses) }  // Adjust numClasses as needed
        interpreter.run(input, output)
        return output[0]
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val numClasses = 26 // Adjust based on the number of classes in your model
    }
}
