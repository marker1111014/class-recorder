package com.classrecorder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilenameHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun formatHelpers_returnExpectedStrings() {
        assertEquals("10:10", FilenameHelper.formatMinuteOfDay(10 * 60 + 10))
        assertEquals("1010", FilenameHelper.formatTimeCompact(10 * 60 + 10))
        assertEquals("49:58", FilenameHelper.formatDuration(49 * 60_000L + 58_000L))
        assertEquals("1:05:03", FilenameHelper.formatDuration(3903_000L))
        assertEquals("2.0 MB", FilenameHelper.formatFileSize(2 * 1024L * 1024L))
    }

    @Test
    fun buildScheduledFilename_sanitizesAndFormatsStandardName() {
        val name = FilenameHelper.buildScheduledFilename(
            dateIso = "2026-11-25",
            periodOrder = 3,
            startMinute = 610,
            endMinute = 660,
            courseOrPeriodName = "微積分 / A班:線性"
        )
        assertEquals("2026-11-25_P03_1010-1100_微積分_A班_線性.m4a", name)
    }

    @Test
    fun resolveUniqueFile_avoidsOverwritingExistingFiles() {
        val dir = tempFolder.newFolder("2026-11-25")
        val f1 = FilenameHelper.resolveUniqueFile(dir, "2026-11-25_P03_1010-1100_國文.m4a")
        f1.writeText("audio1")
        val f2 = FilenameHelper.resolveUniqueFile(dir, "2026-11-25_P03_1010-1100_國文.m4a")
        assertEquals("2026-11-25_P03_1010-1100_國文_2.m4a", f2.name)
    }
}
