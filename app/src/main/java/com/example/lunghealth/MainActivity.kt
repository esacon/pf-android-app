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
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*


const val REQUEST_CODE = 200
const val SERVER_URL : String = "https://api-breath.herokuapp.com/audio/upload"

class MainActivity : AppCompatActivity(), Timer.OnTimerTickListener {

    private lateinit var amplitudes: ArrayList<Float>
    private var permissions = arrayOf(android.Manifest.permission.RECORD_AUDIO)
    private var permissionGranted = false

    private lateinit var recorder: MediaRecorder
    private var dirPath = ""
    private var filename = ""
    private var isRecording = false
    private var isStopped = false
    private lateinit var vibrator: Vibrator
    private lateinit var timer: Timer
    private var dialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AmplifyInit().intializeAmplify(this)

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

        //Check Permissions.

        if (!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
            return
        }

        //Start Recording.
        recorder = MediaRecorder()
        dirPath = "${externalCacheDir?.absolutePath}/"

        var simpleDateFormat = SimpleDateFormat("yyyy.MM.DD_hh.mm.ss")
        var date = simpleDateFormat.format(Date())
        filename = "audio_record_$date"

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile("$dirPath$filename.mp3")

            try {
                prepare()
            } catch (e: IOException) {
            }

            start()
        }

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

        //Graphic Variables.

        isRecording = false
        isStopped = true
        audioTimer.setTextColor(getColor(R.color.colorText))
        btnRecord.setImageResource(R.drawable.ic_upload)
        btnDelete.setImageResource(R.drawable.ic_loop)
        timer.stop()
    }

    private fun uploadRecorder() {

        //Upload the saved audio to Webpage.
        //UploadUtility(this).uploadFile("$dirPath$filename.mp3") // Either Uri, File or String file path
        uploadFile(File("$dirPath$filename.mp3"), filename)
        //Graphic Variables.
        timer.stop()

        /*
        Toast.makeText(this, "Record successfully uploaded.", Toast.LENGTH_SHORT).show()
         */

        isStopped = false
        isRecording = false
        btnRecord.setImageResource(R.drawable.ic_mic)
        amplitudes = waveformView.clear()
        audioTimer.text = "00:00.00"
        btnDelete.isClickable = false
        btnDelete.setImageResource(R.drawable.ic_delete)
        btnDelete.visibility = View.INVISIBLE
    }

    private fun cancelRecorder() {

        //Cancel the recording and delete file (if it was being recorded).

        if (isRecording)
            recorder.apply {
                stop()
                release()
            }

        File("$dirPath$filename.mp3").delete()

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

    private fun dropMenu() {
        Toast.makeText(this, "Menu will be available soon...", Toast.LENGTH_SHORT).show()
        btnMenu.isClickable = false
    }

    override fun onTimerTick(duration: String) {
        waveformView.addAmplitude(recorder.maxAmplitude.toFloat())
        audioTimer.text = duration
        if (duration == "00:02:00") stopRecorder()
    }

    private fun uploadFile(audioFile: File, name: String) {
        toggleProgressDialog(true)
        val options = StorageUploadFileOptions.defaultInstance()

        Amplify.Storage.uploadFile(name, audioFile, options,
            { Log.i("MyAmplifyApp", "Fraction completed: ${it.fractionCompleted}") },
            {
                Log.i("MyAmplifyApp", "Successfully uploaded: ${it.key}")
                showToast("File uploaded.")
                generateDonwloadURL(it.key)
            },
            {
                Log.e("MyAmplifyApp", "Upload failed", it)
                showToast("Upload failed.")
            }
        )
    }

    private fun generateDonwloadURL(fileName: String) {
        Amplify.Storage.getUrl(
            fileName,
            {
                Log.i("MyAmplifyApp", "Successfully generated: ${it.url}")
                val response = postDataServer("audio_file", it.url.toString())
                showToast(response)
            },
            { Log.e("MyAmplifyApp", "URL generation failure", it) }
        )
        toggleProgressDialog(false)
    }

    private fun postDataServer(key: String, value: String) : String {
        val client = OkHttpClient()

        val requestBody: RequestBody = MultipartBody.Builder()
            .addFormDataPart(key, value)
            .build()
        try {
            val request: Request = Request.Builder().url(SERVER_URL).post(requestBody).addHeader("content-type", "multipart/form-data;").build()
            val response : Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d("Data upload", "success")
                Log.i("Info response", response.message)
                return response.message
            } else {
                Log.e("Data upload", "failed")
                Log.e("Response error", response.toString())
            }
            response.close()
        } catch (e : Exception) {
            Log.e("Response error", "Post failed")
        }

        return ""
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
