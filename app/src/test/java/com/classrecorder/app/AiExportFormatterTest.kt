package com.classrecorder.app.domain

import com.classrecorder.app.data.db.DailySummaryEntity
import com.classrecorder.app.data.db.RecordingEntity
import com.classrecorder.app.data.db.RecordingStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class AiExportFormatterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildAiPromptAndMarkdown_includesScheduleAndNotes() {
        val recs = listOf(
            RecordingEntity(
                id = 1,
                dateIso = "2026-11-25",
                dayOfWeek = 3,
                periodId = 3L,
                periodName = "第 3 節",
                courseName = "計算機概論",
                scheduledStartMinute = 610,
                scheduledEndMinute = 660,
                actualStartEpochMs = 1_000_000L,
                durationMs = 3_000_000L,
                filePath = "/tmp/p3.m4a",
                fileName = "2026-11-25_P03_1010-1100_計算機概論.m4a",
                status = RecordingStatus.COMPLETED,
                classNote = "二進位補數與浮點數表示",
                homeworkNote = "下週三交作業二"
            )
        )
        val summary = DailySummaryEntity(
            dateIso = "2026-11-25",
            title = "計概重點日",
            summaryText = "今天複習了 IEEE 754。"
        )

        val prompt = AiExportFormatter.buildAiPromptForDay("2026-11-25", 3, recs, summary)
        assertTrue(prompt.contains("10:10~11:00"))
        assertTrue(prompt.contains("計算機概論"))
        assertTrue(prompt.contains("二進位補數與浮點數表示"))
        assertTrue(prompt.contains("下週三交作業二"))

        val md = AiExportFormatter.buildDailyMarkdownReport("2026-11-25", 3, recs, summary)
        assertTrue(md.contains("# 2026-11-25（週三）課堂錄音與每日總結"))
        assertTrue(md.contains("## 今日主題：計概重點日"))
        assertTrue(md.contains("今天複習了 IEEE 754。"))
    }

    @Test
    fun createDailyZipBundle_packagesAudioPromptAndMarkdown() {
        val audioFile = tempFolder.newFile("2026-11-25_P03_1010-1100_國文.m4a")
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val rec = RecordingEntity(
            id = 1,
            dateIso = "2026-11-25",
            dayOfWeek = 3,
            periodId = 3L,
            periodName = "第 3 節",
            courseName = "國文",
            scheduledStartMinute = 610,
            scheduledEndMinute = 660,
            actualStartEpochMs = 1_000_000L,
            durationMs = 2_900_000L,
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            status = RecordingStatus.COMPLETED
        )

        val zipOut = File(tempFolder.root, "bundle.zip")
        AiExportFormatter.createDailyZipBundle(
            outputZipFile = zipOut,
            dateIso = "2026-11-25",
            dayOfWeek = 3,
            recordings = listOf(rec),
            dailySummary = null,
            promptTemplate = "請總結今日課程"
        )

        assertTrue(zipOut.exists() && zipOut.length() > 0L)
        ZipFile(zipOut).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue("00_AI_總結提示詞_Prompt.txt" in names)
            assertTrue("00_今日筆記與時間軸.md" in names)
            assertTrue("2026-11-25_P03_1010-1100_國文.m4a" in names)
        }
    }
}
