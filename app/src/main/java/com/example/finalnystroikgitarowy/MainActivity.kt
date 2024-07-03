package com.example.finalnystroikgitarowy
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.abs
import kotlin.math.sqrt




class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var frequencyTextView: TextView
    private lateinit var stringTextView: TextView
    private lateinit var tuningTextView: TextView
    private lateinit var guitarImageView: ImageView
    private lateinit var stringImageView: ImageView
    private var isRecording = false
    private val sampleRate = 44100 // Częstotliwość próbkowania

    // Częstotliwości dobrze nastrojonych strun
    private val stringFrequencies = mapOf(
        "E2" to 82.41,   //(poprawnie) 87.41 z tą struną wszystkie aplikacje mają problem dlatego zmienie wartość pod siebie
        "A2" to 110.00,
        "D3" to 146.83,
        "G3" to 198.00,
        "B3" to 246.94,
        "E4" to 329.63
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        frequencyTextView = findViewById(R.id.frequencyTextView)
        stringTextView = findViewById(R.id.stringTextView)
        tuningTextView = findViewById(R.id.tuningTextView)
        guitarImageView = findViewById(R.id.guitarImageView)
        stringImageView = findViewById(R.id.stringImageView)

        // Ustawienie obrazu gitary na początku
        guitarImageView.setImageResource(R.drawable.gryf)

        startButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionsAndStartRecording()
            }
        }
    }

    private fun checkPermissionsAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startRecording()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        startButton.text = getString(R.string.stop_tuning)

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Dostęp nie przyznany")
            }

            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize)

            audioRecord.startRecording()

            Thread {
                val audioBuffer = ShortArray(bufferSize)

                while (isRecording) {
                    val read = audioRecord.read(audioBuffer, 0, bufferSize)

                    // Tu przeprowadź analizę częstotliwości
                    val frequency = calculateFrequency(audioBuffer, read, sampleRate)

                    runOnUiThread {
                        frequencyTextView.text = getString(R.string.frequency_label, frequency)

                        val detectedString = detectString(frequency)
                        stringTextView.text = getString(R.string.detected_string_label, detectedString)

                        val tuning = checkTuning(frequency, detectedString)
                        tuningTextView.text = getString(R.string.tuning_label, tuning)

                        // Ustawienie obrazka struny na podstawie wykrytej struny
                        setStringImage(detectedString)
                    }
                }

                audioRecord.stop()
                audioRecord.release()
            }.start()

        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_SHORT).show()
            isRecording = false
            startButton.text = getString(R.string.strojenie)
        }
    }

    private fun stopRecording() {
        isRecording = false
        startButton.text = getString(R.string.strojenie)
    }

    private fun calculateFrequency(buffer: ShortArray, read: Int, sampleRate: Int): Double {
        val fft = DoubleFFT_1D(read.toLong())

        val audioData = DoubleArray(read)
        for (i in buffer.indices) {
            audioData[i] = buffer[i].toDouble()
        }

        val filteredData = lowPassFilter(audioData)

        fft.realForward(filteredData)

        val magnitude = DoubleArray(read / 2)
        for (i in magnitude.indices) {
            magnitude[i] = sqrt(filteredData[2 * i] * filteredData[2 * i] + filteredData[2 * i + 1] * filteredData[2 * i + 1])
        }

        var maxIndex = 0
        var maxMagnitude = magnitude[0]
        for (i in 1 until magnitude.size) {
            if (magnitude[i] > maxMagnitude) {
                maxMagnitude = magnitude[i]
                maxIndex = i
            }
        }

        return maxIndex * sampleRate.toDouble() / read.toDouble()
    }

    private fun lowPassFilter(input: DoubleArray): DoubleArray {
        // Implementacja filtru dolnoprzepustowego (LPF)
        return input
    }

    private fun detectString(frequency: Double): String {
        var minDistance = Double.MAX_VALUE
        var detectedString = ""

        for ((string, freq) in stringFrequencies) {
            val distance = abs(freq - frequency)
            if (distance < minDistance) {
                minDistance = distance
                detectedString = string
            }
        }

        return detectedString
    }

    private fun checkTuning(frequency: Double, detectedString: String): String {
        val targetFrequency = stringFrequencies[detectedString] ?: return ""

        val percentDifference = (frequency / targetFrequency - 1) * 100

        return when {
            percentDifference < -3.0 -> "Naciągnij"   //-2.0
            percentDifference > 3.0 -> "Popuść"       //2.0
            else -> "Perfekcyjnie"
        }
    }

    private fun setStringImage(detectedString: String) {
        // Ustawienie odpowiedniego obrazka struny na podstawie wykrytej struny
        val imageResource = when (detectedString) {
            "E2" -> R.drawable.gryf_e
            "A2" -> R.drawable.gryf_a
            "D3" -> R.drawable.gryf_d
            "G3" -> R.drawable.gryf_g
            "B3" -> R.drawable.gryf_h
            "E4" -> R.drawable.gryf_e_4
            else -> R.drawable.gryf
        }

        stringImageView.setImageResource(imageResource)
    }
}
