package com.hualien.taxidriver.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 語音助理
 * 提供語音提示功能，協助司機專注駕駛
 */
class VoiceAssistant(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val pendingMessages = mutableListOf<String>()

    // 語音完成回調映射（utteranceId -> callback）
    private val completionCallbacks = mutableMapOf<String, () -> Unit>()

    // 語音設定
    private var speechRate = 1.0f  // 語速（0.5-2.0）
    private var pitch = 1.0f       // 音調（0.5-2.0）
    private var volume = 1.0f      // 音量（0.0-1.0）

    // 重要性等級
    enum class Priority {
        LOW,      // 一般提示
        NORMAL,   // 正常提醒
        HIGH,     // 重要訊息
        URGENT    // 緊急通知
    }

    init {
        initializeTTS()
    }

    /**
     * 初始化TTS引擎
     */
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context, this)
    }

    /**
     * TTS初始化回調
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true

            // 設定語言為中文
            val result = textToSpeech?.setLanguage(Locale.TAIWAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 如果不支援台灣中文，嘗試簡體中文
                textToSpeech?.setLanguage(Locale.CHINA)
            }

            // 設定語音參數
            textToSpeech?.setSpeechRate(speechRate)
            textToSpeech?.setPitch(pitch)

            // 設定進度監聽器
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("VoiceAssistant", "開始播放: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("VoiceAssistant", "播放完成: $utteranceId")
                    // 執行完成回調
                    utteranceId?.let { id ->
                        completionCallbacks.remove(id)?.invoke()
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("VoiceAssistant", "播放錯誤: $utteranceId")
                    // 錯誤時也移除回調
                    utteranceId?.let { id ->
                        completionCallbacks.remove(id)
                    }
                }
            })

            // 播放等待中的訊息
            processPendingMessages()
        } else {
            Log.e("VoiceAssistant", "TTS初始化失敗")
        }
    }

    /**
     * 播放語音（預設正常優先級）
     */
    fun speak(message: String, priority: Priority = Priority.NORMAL) {
        speakWithCallback(message, priority, null)
    }

    /**
     * 播放語音並在完成後執行回調
     * @param message 要播放的訊息
     * @param priority 優先級
     * @param onComplete 播放完成後的回調（在背景線程執行）
     */
    fun speakWithCallback(
        message: String,
        priority: Priority = Priority.NORMAL,
        onComplete: (() -> Unit)?
    ) {
        if (message.isBlank()) return

        if (!isInitialized) {
            pendingMessages.add(message)
            return
        }

        // 生成唯一的 utteranceId
        val utteranceId = "tts_${System.currentTimeMillis()}_${message.hashCode()}"

        // 註冊完成回調
        onComplete?.let {
            completionCallbacks[utteranceId] = it
        }

        // 根據優先級調整播放策略
        when (priority) {
            Priority.LOW -> {
                // 低優先級：排隊播放
                speakWithQueueAndId(message, utteranceId)
            }
            Priority.NORMAL -> {
                // 正常優先級：排隊播放
                speakWithQueueAndId(message, utteranceId)
            }
            Priority.HIGH -> {
                // 高優先級：清空隊列後播放
                speakWithFlushAndId(message, utteranceId)
            }
            Priority.URGENT -> {
                // 緊急優先級：立即播放，音量加大
                speakUrgentWithId(message, utteranceId)
            }
        }
    }

    /**
     * 播放訂單相關語音
     */
    fun announceOrder(pickup: String, destination: String?) {
        val message = buildString {
            append("新訂單，")
            append("上車點：$pickup，")
            destination?.let {
                append("目的地：$it")
            } ?: append("目的地未指定")
        }
        speak(message, Priority.HIGH)
    }

    /**
     * 播放導航提示
     */
    fun announceNavigation(distance: Double, direction: String? = null) {
        val message = when {
            distance < 50 -> "即將到達目的地"
            distance < 100 -> "距離目的地100公尺"
            distance < 500 -> "距離目的地500公尺"
            distance < 1000 -> "距離目的地1公里"
            else -> {
                val km = (distance / 1000).toInt()
                "距離目的地${km}公里"
            }
        }

        direction?.let {
            speak("$message，$it", Priority.NORMAL)
        } ?: speak(message, Priority.NORMAL)
    }

    /**
     * 播放狀態變化
     */
    fun announceStatusChange(newStatus: String) {
        val message = when (newStatus) {
            "ACCEPTED" -> "訂單已接受，開始導航"
            "ARRIVED" -> "已到達上車點，等待乘客"
            "ON_TRIP" -> "行程開始，祝您旅途愉快"
            "SETTLING" -> "行程結束，請收取車資"
            "DONE" -> "訂單完成，感謝您的服務"
            else -> newStatus
        }
        speak(message, Priority.HIGH)
    }

    /**
     * 播放警告訊息
     */
    fun announceWarning(warning: String) {
        speak(warning, Priority.URGENT)
    }

    /**
     * 排隊播放（舊方法，保持兼容）
     */
    private fun speakWithQueue(message: String) {
        speakWithQueueAndId(message, message.hashCode().toString())
    }

    /**
     * 排隊播放（帶 utteranceId）
     */
    private fun speakWithQueueAndId(message: String, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_ADD,
                params,
                utteranceId
            )
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_ADD,
                null
            )
        }
    }

    /**
     * 清空隊列後播放（舊方法，保持兼容）
     */
    private fun speakWithFlush(message: String) {
        speakWithFlushAndId(message, message.hashCode().toString())
    }

    /**
     * 清空隊列後播放（帶 utteranceId）
     */
    private fun speakWithFlushAndId(message: String, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null
            )
        }
    }

    /**
     * 緊急播放（舊方法，保持兼容）
     */
    private fun speakUrgent(message: String) {
        speakUrgentWithId(message, "urgent_${message.hashCode()}")
    }

    /**
     * 緊急播放（帶 utteranceId）
     */
    private fun speakUrgentWithId(message: String, utteranceId: String) {
        // 暫時提高音量
        val originalVolume = volume
        volume = 1.0f

        // 加入警示音前綴
        val urgentMessage = "注意！$message"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            }
            textToSpeech?.speak(
                urgentMessage,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
        } else {
            @Suppress("DEPRECATION")
            val params = HashMap<String, String>().apply {
                put(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM.toString())
            }
            textToSpeech?.speak(
                urgentMessage,
                TextToSpeech.QUEUE_FLUSH,
                params
            )
        }

        // 恢復原音量
        volume = originalVolume
    }

    /**
     * 處理等待中的訊息
     */
    private fun processPendingMessages() {
        if (pendingMessages.isNotEmpty()) {
            pendingMessages.forEach { message ->
                speak(message)
            }
            pendingMessages.clear()
        }
    }

    /**
     * 停止播放並清除所有待執行的回調
     */
    fun stop() {
        textToSpeech?.stop()
        // 清除所有待執行的回調，避免被中斷的語音播報後仍執行回調
        completionCallbacks.clear()
    }

    /**
     * 設定語速
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(speechRate)
    }

    /**
     * 設定音調
     */
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        textToSpeech?.setPitch(this.pitch)
    }

    /**
     * 設定音量
     */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
    }

    /**
     * 檢查是否正在播放
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * 設定為中老年人模式（語速較慢、音量較大）
     */
    fun setSeniorMode(enabled: Boolean) {
        if (enabled) {
            setSpeechRate(0.8f)  // 語速放慢
            setVolume(1.0f)      // 音量最大
            setPitch(0.9f)       // 音調稍低
        } else {
            setSpeechRate(1.0f)  // 正常語速
            setVolume(0.8f)      // 正常音量
            setPitch(1.0f)       // 正常音調
        }
    }

    /**
     * 釋放資源
     */
    fun release() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }

    companion object {
        // 預設語音提示
        const val MSG_NEW_ORDER = "您有新訂單"
        const val MSG_ORDER_ACCEPTED = "訂單已接受"
        const val MSG_ARRIVED_PICKUP = "已到達上車點"
        const val MSG_TRIP_STARTED = "行程開始"
        const val MSG_NEAR_DESTINATION = "即將到達目的地"
        const val MSG_TRIP_ENDED = "行程結束"
        const val MSG_PAYMENT_PENDING = "請收取車資"

        // 警告訊息
        const val MSG_SPEED_WARNING = "請注意車速"
        const val MSG_WRONG_DIRECTION = "您可能走錯方向"
        const val MSG_TRAFFIC_AHEAD = "前方路況壅塞"
    }
}