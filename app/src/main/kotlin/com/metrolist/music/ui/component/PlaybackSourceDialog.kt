/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.R

enum class PlaybackSource {
    LOCAL,
    STREAMING
}

@Composable
fun PlaybackSourceDialog(
    songTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (source: PlaybackSource, rememberChoice: Boolean) -> Unit
) {
    var selectedSource by remember { mutableStateOf(PlaybackSource.LOCAL) }
    var rememberChoice by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.choose_playback_source),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Local option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedSource == PlaybackSource.LOCAL,
                            onClick = { selectedSource = PlaybackSource.LOCAL }
                        )
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedSource == PlaybackSource.LOCAL,
                        onClick = { selectedSource = PlaybackSource.LOCAL }
                    )
                    Text(
                        text = stringResource(R.string.play_local),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                // Streaming option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedSource == PlaybackSource.STREAMING,
                            onClick = { selectedSource = PlaybackSource.STREAMING }
                        )
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedSource == PlaybackSource.STREAMING,
                        onClick = { selectedSource = PlaybackSource.STREAMING }
                    )
                    Text(
                        text = stringResource(R.string.play_streaming),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Remember choice checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = rememberChoice,
                            onClick = { rememberChoice = !rememberChoice }
                        )
                        .padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text(
                        text = stringResource(R.string.remember_choice),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.align(Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(selectedSource, rememberChoice) }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}
