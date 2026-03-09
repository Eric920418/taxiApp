package com.hualien.taxidriver.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 語音錄製服務
 * 支援 VAD（Voice Activity Detection）自動偵測說話結束
 */
class VoiceRecorderService(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecorderService"

        // VAD 參數（針對司機語音接單優化）
        private const val SILENCE_THRESHOLD = 1500       // 靜音振幅閾值（降低以更好偵測輕聲說話）
        private const val SILENCE_DURATION_MS = 1500L    // 靜音持續時間（延長以給用戶更多時間）
        private const val MAX_RECORDING_MS = 8000L       // 最長錄音時間（8秒，接單指令通常很短）
        private const val MIN_RECORDING_MS = 300L        // 最短錄音時間（縮短，接單指令很短）
        private const val AMPLITUDE_CHECK_INTERVAL_MS = 50L // 振幅檢測間隔（更頻繁以提高響應速度）
    }

    // 錄音狀態
    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        object Processing : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var vadJob: Job? = null
    private var recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private var onRecordingComplete: ((File) -> Unit)? = null

    /**
     * 開始錄音
     * @param onComplete 錄音完成時的回調，傳入音檔
     */
    fun startRecording(onComplete: (File) -> Unit) {
        if (_state.value == RecordingState.Recording) {
            Log.w(TAG, "已經在錄音中")
            return
        }

        onRecordingComplete = onComplete

        try {
            // 準備輸出檔案
            outputFile = createOutputFile()

            // 初始化 MediaRecorder
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(outputFile?.absolutePath)

                prepare()
                start()
            }

            _state.value = RecordingState.Recording
            Log.d(TAG, "開始錄音: ${outputFile?.absolutePath}")

            // 啟動 VAD 監測
            startVAD()

        } catch (e: Exception) {
            Log.e(TAG, "開始錄音失敗", e)
            _state.value = RecordingState.Error("無法開始錄音: ${e.message}")
            cleanup()
        }
    }

    /**
     * 手動停止錄音
     */
    fun stopRecording() {
        if (_state.value != RecordingState.Recording) {
            return
        }

        vadJob?.cancel()
        finalizeRecording()
    }

    /**
     * 取消錄音
     */
    fun cancelRecording() {
        vadJob?.cancel()
        cleanup()
        _state.value = RecordingState.Idle
        Log.d(TAG, "錄音已取消")
    }

    /**
     * VAD 監測
     */
    private fun startVAD() {
        vadJob?.cancel()

        vadJob = recordingScope.launch {
            var silenceStartTime: Long? = null
            val recordingStartTime = System.currentTimeMillis()

            while (isActive && _state.value == RecordingState.Recording) {
                delay(AMPLITUDE_CHECK_INTERVAL_MS)

                val currentAmplitude = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }

                _amplitude.value = currentAmplitude

                val currentTime = System.currentTimeMillis()
                val recordingDuration = currentTime - recordingStartTime

                // 檢查是否超過最長錄音時間
                if (recordingDuration >= MAX_RECORDING_MS) {
                    Log.d(TAG, "已達最長錄音時間，自動停止")
                    withContext(Dispatchers.Main) {
                        finalizeRecording()
                    }
                    break
                }

                // VAD 偵測
                if (currentAmplitude < SILENCE_THRESHOLD) {
                    // 靜音中
                    if (silenceStartTime == null) {
                        silenceStartTime = currentTime
                        Log.d(TAG, "偵測到靜音開始，振幅: $currentAmplitude")
                    } else {
                        val silenceDuration = currentTime - silenceStartTime
                        if (silenceDuration >= SILENCE_DURATION_MS && recordingDuration > MIN_RECORDING_MS) {
                            // 靜音持續超過閾值，且已錄音超過最短時間
                            Log.d(TAG, "✅ VAD 偵測到說話結束（靜音 ${silenceDuration}ms，總錄音 ${recordingDuration}ms）")
                            withContext(Dispatchers.Main) {
                                finalizeRecording()
                            }
                            break
                        }
                    }
                } else {
                    // 有聲音，重置靜音計時
                    if (silenceStartTime != null) {
                        Log.d(TAG, "偵測到聲音，振幅: $currentAmplitude，重置靜音計時")
                    }
                    silenceStartTime = null
                }
            }
        }
    }

    /**
     * 完成錄音並回調
     */
    private fun finalizeRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            _state.value = RecordingState.Processing

            outputFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "錄音完成: ${file.absolutePath} (${file.length()} bytes)")
                    onRecordingComplete?.invoke(file)
                } else {
                    Log.e(TAG, "錄音檔案無效")
                    _state.value = RecordingState.Error("錄音檔案無效")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止錄音失敗", e)
            _state.value = RecordingState.Error("停止錄音失敗: ${e.message}")
            cleanup()
        }
    }

    /**
     * 重置狀態（處理完成後呼叫）
     */
    fun resetState() {
        _state.value = RecordingState.Idle
        _amplitude.value = 0
    }

    /**
     * 清理資源
     */
    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // 忽略清理錯誤
        }
        mediaRecorder = null

        // 刪除臨時檔案
        outputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        outputFile = null
    }

    /**
     * 建立 MediaRecorder
     */
    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    /**
     * 建立輸出檔案
     */
    private fun createOutputFile(): File {
        val dir = File(context.cacheDir, "voice_recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        return File(dir, "recording_${System.currentTimeMillis()}.m4a")
    }

    /**
     * 釋放資源
     */
    fun release() {
        vadJob?.cancel()
        recordingScope.cancel()
        cleanup()
    }
}
