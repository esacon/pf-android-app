package com.example.lunghealth

import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.amplifyframework.core.Amplify
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.example.lunghealth.audio_record.AmplifyInit
import com.example.lunghealth.audio_record.AudioRecorder
import com.example.lunghealth.audio_visualization.Timer
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


const val REQUEST_CODE = 200
const val SERVER_URL: String = "https://api-breath.herokuapp.com/audio/upload"

class MainActivity : AppCompatActivity(), Timer.OnTimerTickListener {

    private lateinit var amplitudes: ArrayList<Float>
    private var permissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )
    private var permissionGranted = false

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var recorder: MediaRecorder
    private var dirPath: String = ""
    private var fileName: String = ""
    private var wavFileName: String = ""
    private var isRecording: Boolean = false
    private var isStopped: Boolean = false
    private lateinit var vibrator: Vibrator
    private lateinit var timer: Timer
    private var dialog: ProgressDialog? = null

    private var userID: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intentInfo = intent.extras

        if (intentInfo != null) {
            userID = intentInfo["user_id"] as String
            Log.i("User ID", userID)
        }

        AmplifyInit().intializeAmplify(this)
        dirPath = "${externalCacheDir?.absolutePath}/"
        audioRecorder = AudioRecorder(dirPath)

        permissionGranted = ActivityCompat.checkSelfPermission(
            this,
            permissions[0]
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)

        timer = Timer(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        btnRecord.setOnClickListener {
            when {
                isStopped -> uploadRecorder()
                isRecording -> stopRecorder()
                else -> startRecorder()
            }

            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        btnDelete.setOnClickListener {
            cancelRecorder()
        }

        btnMenu.setOnClickListener {
            dropMenu()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE)
            permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecorder() {
        //Check Permissions
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
            return
        }

        //Start Recording.
        recorder = MediaRecorder()

        var simpleDateFormat = SimpleDateFormat("ddMMyyyy_hhmmss")
        var date = simpleDateFormat.format(Date())
        fileName = "$date.mp3"
        wavFileName = "$date.wav"

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile("$dirPath$date.mp3")
            setMaxDuration(20000)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("Record error", e.toString())
            }

            setOnInfoListener { mr, what, extra ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecorder()
                }
            }

            start()
        }

        audioRecorder.startRecord()

        //Graphic Variables.
        isRecording = true
        isStopped = false
        audioTimer.setTextColor(getColor(R.color.mainBlue))
        btnRecord.setImageResource(R.drawable.ic_stop)
        btnDelete.visibility = View.VISIBLE
        btnDelete.isClickable = true
        timer.start()
    }

    private fun stopRecorder() {
        //Stop recording.
        recorder.apply {
            stop()
            release()
        }

        audioRecorder.stopRecord()

        //Graphic Variables.
        isRecording = false
        isStopped = true
        audioTimer.setTextColor(getColor(R.color.colorText))
        btnRecord.setImageResource(R.drawable.ic_upload)
        btnDelete.setImageResource(R.drawable.ic_loop)
        timer.stop()
    }


    private fun cancelRecorder() {
        //Cancel the recording and delete file (if it was being recorded).
        if (isRecording) {
            audioRecorder.stopRecord()
            recorder.apply {
                stop()
                release()
            }
        }

        File("$dirPath$fileName").delete()

        //Graphic Variables.
        isStopped = false
        isRecording = false
        timer.stop()
        btnRecord.setImageResource(R.drawable.ic_mic)
        audioTimer.setTextColor(getColor(R.color.colorText))
        amplitudes = waveformView.clear()
        audioTimer.text = "00:00.00"
        Toast.makeText(this, "Recording cancelled.", Toast.LENGTH_SHORT).show()
        btnDelete.isClickable = false
        btnDelete.setImageResource(R.drawable.ic_delete)
        btnDelete.visibility = View.INVISIBLE
    }

    private fun uploadRecorder() {
        //Upload the saved audio to Webpage.
        uploadFile(File("${dirPath}/audio_files/audio.wav"), wavFileName)
        //Graphic Variables.
        timer.stop()

        isStopped = false
        isRecording = false
        btnRecord.setImageResource(R.drawable.ic_mic)
        amplitudes = waveformView.clear()
        audioTimer.text = "00:00.00"
        btnDelete.isClickable = false
        btnDelete.setImageResource(R.drawable.ic_delete)
        btnDelete.visibility = View.INVISIBLE
    }

    private fun uploadFile(audioFile: File, name: String) {
        toggleProgressDialog(true)
        val options = StorageUploadFileOptions.defaultInstance()

        Amplify.Storage.uploadFile(name, audioFile, options,
            { Log.i("MyAmplifyApp", "Fraction completed: ${it.fractionCompleted}") },
            {
                Log.i("MyAmplifyApp", "Successfully uploaded: ${it.key}")
                showToast("File uploaded.")
                Thread {
                    val requestBody: RequestBody = FormBody.Builder()
                        .add("audio_filename", name)
                        .add("user_id", userID)
                        .build()
                    postDataServer(requestBody)
                    toggleProgressDialog(false)
                }.start()
            },
            {
                Log.e("MyAmplifyApp", "Upload failed", it)
                showToast("Upload failed.")
                toggleProgressDialog(false)
            }
        )
    }

    private fun buildRequest(key: String, value: String): RequestBody {
        return FormBody.Builder()
            .add(key, value)
            .build()
    }

    private fun postDataServer(requestBody: RequestBody): String {
        val client = OkHttpClient()

        try {
            val request: Request = Request.Builder()
                .url(SERVER_URL)
                .post(requestBody)
                .addHeader("content-type", "multipart/form-data;").build()

            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d("Data upload", "success")
                val responseBodyString = response.body!!.string()
                val jsonObject = JSONTokener(responseBodyString).nextValue() as JSONObject
                val message = jsonObject.getString("data")
                Log.i("Info response", message)
                return message
            } else {
                Log.e("Data upload", "failed")
                Log.e("Response error", response.toString())
            }
            response.close()
        } catch (e: Exception) {
            Log.e("Response error", "Post failed")
        }
        return ""
    }

    private fun dropMenu() {
        Toast.makeText(this, "Menu will be available soon...", Toast.LENGTH_SHORT).show()
        btnMenu.isClickable = false
    }

    override fun onTimerTick(duration: String) {
        waveformView.addAmplitude(recorder.maxAmplitude.toFloat())
        audioTimer.text = duration
    }

    private fun toggleProgressDialog(show: Boolean) {
        this.runOnUiThread {
            if (show) {
                dialog = ProgressDialog.show(this, "", "Uploading file...", true);
            } else {
                dialog?.dismiss();
            }
        }
    }

    private fun showToast(message: String) {
        this.runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}