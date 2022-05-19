package com.example.lunghealth.audio_record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 *@author : Enrique Niebles
 *@date : 17/05/2022
 *@description : Audio only class
 */
class AudioRecorder(dirPath: String) {

    private val TAG: String = "AudioRecorder"
    private val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
    private val SAMPLING_RATE_IN_HZ = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val PCMPath: String = "${dirPath}/audio_files/raw_audio.pcm"
    private val WAVPath: String = "${dirPath}/audio_files/audio.wav"
    private val BUFFER_SIZE_FACTOR = 2
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLING_RATE_IN_HZ,
        CHANNEL_CONFIG, AUDIO_ENCODING
    ) * BUFFER_SIZE_FACTOR

    private lateinit var audioRecorder: AudioRecord
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecord() {
        Log.i(TAG, "Record started")
        audioRecorder = AudioRecord(
            AUDIO_SOURCE, SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG,
            AUDIO_ENCODING, BUFFER_SIZE
        )
        audioRecorder.startRecording()
        isRecording = true
        Thread {
            writeDateTOFile()
            copyWaveFile(PCMPath, WAVPath)
        }.start()
    }

    fun stopRecord() {
        audioRecorder.apply {
            stop()
            release()
        }
        isRecording = false
    }

    private fun writeDateTOFile() {
        Log.i(TAG, "WriteDateTOFile started")
        val audioData = ByteArray(BUFFER_SIZE)
        val file = File(PCMPath)
        if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        file.createNewFile()
        val out = BufferedOutputStream(FileOutputStream(file))
        var length: Int
        while (isRecording) {
            length = audioRecorder.read(audioData, 0, BUFFER_SIZE)//Get audio data
            if (AudioRecord.ERROR_INVALID_OPERATION != length) {
                out.write(audioData, 0, length)//write file
                out.flush()
            }
        }
        out.close()
    }

    //Converting pcm files to WAV files
    private fun copyWaveFile(pcmPath: String, wavPath: String) {
        Log.i(TAG, "CopyWaveFile started")
        val fileIn = FileInputStream(pcmPath)
        val fileOut = FileOutputStream(wavPath)
        val data = ByteArray(BUFFER_SIZE)
        val totalAudioLen = fileIn.channel.size()
        val totalDataLen = totalAudioLen + 36
        writeWaveFileHeader(fileOut, totalAudioLen, totalDataLen)
        var count = fileIn.read(data, 0, BUFFER_SIZE)
        while (count != -1) {
            fileOut.write(data, 0, count)
            fileOut.flush()
            count = fileIn.read(data, 0, BUFFER_SIZE)
        }
        fileIn.close()
        fileOut.close()
    }

    //Add file header in WAV format
    private fun writeWaveFileHeader(
        out: FileOutputStream, totalAudioLen: Long,
        totalDataLen: Long
    ) {
        val channels = 1
        val byteRate = 16 * SAMPLING_RATE_IN_HZ * channels / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (SAMPLING_RATE_IN_HZ and 0xff).toByte()
        header[25] = (SAMPLING_RATE_IN_HZ shr 8 and 0xff).toByte()
        header[26] = (SAMPLING_RATE_IN_HZ shr 16 and 0xff).toByte()
        header[27] = (SAMPLING_RATE_IN_HZ shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}