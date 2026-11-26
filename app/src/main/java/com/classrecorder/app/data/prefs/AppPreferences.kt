package com.classrecorder.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "class_recorder_prefs")

enum class AudioQualityPreset(
    val label: String,
    val description: String,
    val sampleRate: Int,
    val bitRate: Int
) {
    COMPACT("省空間語音", "16 kHz / 32 kbps AAC（約 14 MB/時）", 16000, 32000),
    STANDARD("標準清晰（推薦）", "32 kHz / 48 kbps AAC（約 21 MB/時）", 32000, 48000),
    HIGH("高音質課堂", "44.1 kHz / 96 kbps AAC（約 43 MB/時）", 44100, 96000);

    companion object {
        fun fromName(name: String?): AudioQualityPreset =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

data class UserSettings(
    val scheduleEnabled: Boolean = true,
    val keepArmedOvernight: Boolean = false,
    val showWeekends: Boolean = false,
    val audioQuality: AudioQualityPreset = AudioQualityPreset.STANDARD,
    val startBufferSeconds: Int = 0,
    val endBufferSeconds: Int = 0,
    val aiPromptTemplate: String = DEFAULT_AI_PROMPT_TEMPLATE
) {
    companion object {
        const val DEFAULT_AI_PROMPT_TEMPLATE = """請根據我今天附上的各節課堂錄音檔與上課筆記，用繁體中文幫我整理一份「今日課堂與生活總結」：

1. 【今日時間軸總覽】依節次與時間順序列出今天上了什麼課、發生了什麼事。
2. 【各節課重點整理】逐一整理每段錄音的核心概念、老師強調的重點與補充內容。
3. 【作業、考試與待辦清單】提取所有老師交代的作業期限、小考範圍或重要提醒（條列式）。
4. 【一句話今日回顧】幫我用簡短幾句話總結今天。"""
    }
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val KEEP_ARMED_OVERNIGHT = booleanPreferencesKey("keep_armed_overnight")
        val SHOW_WEEKENDS = booleanPreferencesKey("show_weekends")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val START_BUFFER_SEC = intPreferencesKey("start_buffer_sec")
        val END_BUFFER_SEC = intPreferencesKey("end_buffer_sec")
        val AI_PROMPT_TEMPLATE = stringPreferencesKey("ai_prompt_template")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            scheduleEnabled = prefs[Keys.SCHEDULE_ENABLED] ?: true,
            keepArmedOvernight = prefs[Keys.KEEP_ARMED_OVERNIGHT] ?: false,
            showWeekends = prefs[Keys.SHOW_WEEKENDS] ?: false,
            audioQuality = AudioQualityPreset.fromName(prefs[Keys.AUDIO_QUALITY]),
            startBufferSeconds = prefs[Keys.START_BUFFER_SEC] ?: 0,
            endBufferSeconds = prefs[Keys.END_BUFFER_SEC] ?: 0,
            aiPromptTemplate = prefs[Keys.AI_PROMPT_TEMPLATE] ?: UserSettings.DEFAULT_AI_PROMPT_TEMPLATE
        )
    }

    suspend fun getSettings(): UserSettings = settingsFlow.first()

    suspend fun setScheduleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCHEDULE_ENABLED] = enabled }
    }

    suspend fun setKeepArmedOvernight(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ARMED_OVERNIGHT] = keep }
    }

    suspend fun setShowWeekends(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_WEEKENDS] = show }
    }

    suspend fun setAudioQuality(preset: AudioQualityPreset) {
        context.dataStore.edit { it[Keys.AUDIO_QUALITY] = preset.name }
    }

    suspend fun setBuffers(startSeconds: Int, endSeconds: Int) {
        context.dataStore.edit {
            it[Keys.START_BUFFER_SEC] = startSeconds.coerceIn(0, 180)
            it[Keys.END_BUFFER_SEC] = endSeconds.coerceIn(0, 300)
        }
    }

    suspend fun setAiPromptTemplate(template: String) {
        context.dataStore.edit {
            it[Keys.AI_PROMPT_TEMPLATE] = template.ifBlank { UserSettings.DEFAULT_AI_PROMPT_TEMPLATE }
        }
    }
}
