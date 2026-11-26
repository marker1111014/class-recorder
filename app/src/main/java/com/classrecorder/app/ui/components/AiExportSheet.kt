package com.classrecorder.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiExportSheet(
    dateIso: String,
    recordingCount: Int,
    generatedPromptPreview: String,
    initialSummaryTitle: String,
    initialSummaryText: String,
    onCopyPrompt: () -> Unit,
    onShareAllAudioAndPrompt: () -> Unit,
    onShareZipBundle: () -> Unit,
    onExportToDownloads: () -> Unit,
    onSaveSummary: (title: String, summaryText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var summaryTitle by remember(dateIso, initialSummaryTitle) { mutableStateOf(initialSummaryTitle) }
    var summaryText by remember(dateIso, initialSummaryText) { mutableStateOf(initialSummaryText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "匯出與每日總結",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "$dateIso · $recordingCount 段錄音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onShareAllAudioAndPrompt,
                enabled = recordingCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape
            ) {
                Text("分享今日音檔與提示詞")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopyPrompt,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text("複製提示詞")
                }
                FilledTonalButton(
                    onClick = onShareZipBundle,
                    enabled = recordingCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text("匯出 ZIP")
                }
            }

            OutlinedButton(
                onClick = onExportToDownloads,
                enabled = recordingCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape
            ) {
                Text("存至下載 / ClassRecorder/$dateIso")
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "提示詞預覽",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = generatedPromptPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "今日總結",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = summaryTitle,
                onValueChange = { summaryTitle = it },
                label = { Text("主題（選填）") },
                singleLine = true,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = summaryText,
                onValueChange = { summaryText = it },
                label = { Text("總結內容") },
                minLines = 4,
                maxLines = 8,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSaveSummary(summaryTitle, summaryText)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape
            ) {
                Text("儲存總結")
            }
        }
    }
}
