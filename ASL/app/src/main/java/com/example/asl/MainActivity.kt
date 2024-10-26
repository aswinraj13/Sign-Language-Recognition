package com.example.asl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE = 22
    private lateinit var buttonNavigate: Button
    private lateinit var imageView: ImageView
    private lateinit var tflite: Interpreter

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    // Define the mapping of indices to ASL characters
    private val aslCharacters = arrayOf("A", "B", "C", "D", "E", "F", "G", "H", "I",
        "K", "L", "M", "N", "O", "P", "Q", "R", "S",
        "T", "U", "V", "W", "X", "Y") // No data for J and Z

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonNavigate = findViewById(R.id.buttonNavigate)
        imageView = findViewById(R.id.imageView)

        // Load the TFLite model
        tflite = Interpreter(loadModelFile())

        // Initialize the camera permission launcher
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures", Toast.LENGTH_SHORT).show()
            }
        }

        buttonNavigate.setOnClickListener {
            // Check for camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                // Request camera permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val photo: Bitmap? = data?.extras?.get("data") as? Bitmap
            if (photo != null) {
                imageView.setImageBitmap(photo)
                classifyImage(photo)
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun classifyImage(bitmap: Bitmap) {
        // Resize the bitmap to 64x64
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

        // Prepare input and output buffers
        val input = Array(1) { Array(64) { Array(64) { FloatArray(3) } } }
        val output = Array(1) { FloatArray(24) }  // Output size matches class count

        // Convert bitmap to float array (normalize to [0, 1])
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                val pixel = resizedBitmap.getPixel(x, y)
                input[0][x][y][0] = Color.red(pixel) / 255.0f
                input[0][x][y][1] = Color.green(pixel) / 255.0f
                input[0][x][y][2] = Color.blue(pixel) / 255.0f
            }
        }

        // Run inference
        tflite.run(input, output)

        // Apply softmax to output to get probabilities
        val probabilities = softmax(output[0])

        // Find the index of the highest probability
        val predictedIndex = probabilities.indexOfMax()

        // Get the predicted character
        val predictedCharacter = if (predictedIndex in aslCharacters.indices) {
            aslCharacters[predictedIndex]
        } else {
            "Unknown"
        }

        // Display the predicted character
        Toast.makeText(this, "Predicted character: $predictedCharacter", Toast.LENGTH_SHORT).show()
    }

    // Softmax function to convert logits into probabilities
    private fun softmax(logits: FloatArray): FloatArray {
        val expLogits = logits.map { exp(it) }
        val sumExp = expLogits.sum()
        return expLogits.map { it / sumExp }.toFloatArray()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd("sign_language_model.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Extension function to find the index of the maximum value in an array
    private fun FloatArray.indexOfMax(): Int {
        return this.indices.maxByOrNull { this[it] } ?: -1
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite.close()  // Close interpreter to free resources
    }
}
