/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.app.SearchManager
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.AiProviderKey
import com.metrolist.music.constants.DeeplApiKey
import com.metrolist.music.constants.DeeplFormalityKey
import com.metrolist.music.constants.LyricsSearchShowProviderSelectionKey
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.constants.PreferSyncedLyricsKey
import com.metrolist.music.constants.TranslateLanguageKey
import com.metrolist.music.constants.TranslateModeKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsMenuViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    songProvider: () -> SongEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    onDismiss: () -> Unit,
    onShowOffsetDialog: () -> Unit = {},
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    
    val openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val deeplApiKey by rememberPreference(DeeplApiKey, "")
    val aiProvider by rememberPreference(AiProviderKey, "OpenRouter")
    val translateLanguage by rememberPreference(TranslateLanguageKey, "en")
    val translateMode by rememberPreference(TranslateModeKey, "Literal")
    val openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, "https://openrouter.ai/api/v1/chat/completions")
    val openRouterModel by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")
    val deeplFormality by rememberPreference(DeeplFormalityKey, "default")
    
    // Provider selection feature toggle
    val lyricsSearchShowProviderSelection by rememberPreference(LyricsSearchShowProviderSelectionKey, true)

    val hasApiKey = if (aiProvider == "DeepL") deeplApiKey.isNotBlank() else openRouterApiKey.isNotBlank()

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadataProvider().id,
                            lyrics = it,
                            provider = lyricsProvider()?.provider ?: "Manual",
                        ),
                    )
                }
            },
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadata =
        remember(showSearchDialog) {
            mediaMetadataProvider()
        }
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().title,
                ),
            )
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().artists.joinToString { it.name },
                ),
            )
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()

    if (showSearchDialog) {
        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showSearchDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.search_lyrics)) },
            buttons = {
                TextButton(
                    onClick = { showSearchDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        onDismiss()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(
                                        SearchManager.QUERY,
                                        "${artistField.text} ${titleField.text} lyrics"
                                    )
                                },
                            )
                        } catch (_: Exception) {
                        }
                    },
                ) {
                    Text(stringResource(R.string.search_online))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        
                        if (lyricsSearchShowProviderSelection) {
                            // Open provider selection dialog with search parameters
                            viewModel.openProviderSelectionDialog(
                                searchMediaMetadata.id,
                                titleField.text,
                                artistField.text,
                                searchMediaMetadata.duration,
                                searchMediaMetadata.album?.title
                            )
                        } else {
                            // Execute search immediately with all providers
                            viewModel.search(
                                searchMediaMetadata.id,
                                titleField.text,
                                artistField.text,
                                searchMediaMetadata.duration,
                                searchMediaMetadata.album?.title
                            )
                            showSearchResultDialog = true
                            
                            // Show warning only if network is definitely unavailable
                            if (!isNetworkAvailable) {
                                Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artistField,
                onValueChange = onArtistFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) },
            )
        }
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        var expandedItemIndex by rememberSaveable {
            mutableIntStateOf(-1)
        }

        ListDialog(
            onDismiss = { showSearchResultDialog = false },
        ) {
            itemsIndexed(results) { index, result ->
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            viewModel.cancelSearch()
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = searchMediaMetadata.id,
                                        lyrics = result.lyrics,
                                        provider = result.providerName,
                                    ),
                                )
                            }
                        }
                        .padding(12.dp)
                        .animateContentSize(),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = result.lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (index == expandedItemIndex) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = result.providerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                            )
                            if (result.lyrics.startsWith("[")) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier =
                                    Modifier
                                        .padding(start = 4.dp)
                                        .size(18.dp),
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            expandedItemIndex = if (expandedItemIndex == index) -1 else index
                        },
                    ) {
                        Icon(
                            painter = painterResource(if (index == expandedItemIndex) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null,
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!isLoading && results.isEmpty()) {
                item {
                    Text(
                        text = context.getString(R.string.lyrics_not_found),
                        textAlign = TextAlign.Center,
                        modifier =
                        Modifier
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    // Provider Selection Dialog
    if (viewModel.showProviderSelectionDialog) {
        val availableProviders by remember { viewModel.availableProviders }
        val selectedProviders by remember { viewModel.selectedProviders }
        val preferSyncedLyrics by rememberPreference(PreferSyncedLyricsKey, true)
        
        DefaultDialog(
            onDismiss = { viewModel.closeProviderSelectionDialog() },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.select_lyrics_provider)) },
            buttons = {
                TextButton(
                    onClick = { viewModel.closeProviderSelectionDialog() },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        viewModel.searchWithSelectedProviders()
                        showSearchResultDialog = true
                        
                        // Show warning if network is unavailable
                        if (!isNetworkAvailable) {
                            Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedProviders.isNotEmpty()
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Provider checkboxes
                availableProviders.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = provider.isEnabled) {
                                viewModel.toggleProviderSelection(provider.name)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedProviders.contains(provider.name),
                            onCheckedChange = { viewModel.toggleProviderSelection(provider.name) },
                            enabled = provider.isEnabled
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (provider.isEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                            if (!provider.isEnabled) {
                                Text(
                                    text = stringResource(R.string.provider_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Prefer synced toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                context.dataStore.edit { settings ->
                                    settings[PreferSyncedLyricsKey] = !preferSyncedLyrics
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.prefer_synced_lyrics),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.prefer_synced_lyrics_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = preferSyncedLyrics,
                        onCheckedChange = {
                            coroutineScope.launch {
                                context.dataStore.edit { settings ->
                                    settings[PreferSyncedLyricsKey] = it
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    var showRomanizationDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showRomanization by rememberSaveable { mutableStateOf(false) }
    var isChecked by remember { mutableStateOf(songProvider()?.romanizeLyrics ?: true) }
    var isInstrumental by remember { mutableStateOf(songProvider()?.isInstrumental ?: false) }

    var lyricsOffset by rememberSaveable { mutableIntStateOf(songProvider()?.lyricsOffset ?: 0) }

    // Sync isChecked with song changes
    LaunchedEffect(songProvider()) {
        isChecked = songProvider()?.romanizeLyrics ?: true
    }
    
    // Sync isInstrumental with song changes
    LaunchedEffect(songProvider()) {
        isInstrumental = songProvider()?.isInstrumental ?: false
    }

    LaunchedEffect(songProvider()) {
        lyricsOffset = songProvider()?.lyricsOffset ?: 0
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.edit),
                        onClick = {
                            showEditDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.cached),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.refetch),
                        onClick = {
                            onDismiss()
                            viewModel.refetchLyrics(mediaMetadataProvider(), lyricsProvider())
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.search),
                        onClick = {
                            showSearchDialog = true
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            Material3MenuGroup(
                items = buildList {
                    // Add "Translate with AI" option if API key is configured
                    if (hasApiKey) {
                        add(
                            Material3MenuItemData(
                                title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.translate),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    LyricsTranslationHelper.triggerManualTranslation()
                                },
                            )
                        )
                    }
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.lyrics_offset)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.fast_forward),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                onShowOffsetDialog()
                            },
                            trailingContent = {
                                Text(
                                    text = "${if (lyricsOffset >= 0) "+" else ""}${lyricsOffset}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    )
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.romanize_current_track)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.language_korean_latin),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isChecked = !isChecked
                                songProvider()?.let { song ->
                                    database.query {
                                        upsert(song.copy(romanizeLyrics = isChecked))
                                    }
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { newCheckedState ->
                                        isChecked = newCheckedState
                                        songProvider()?.let { song ->
                                            database.query {
                                                upsert(song.copy(romanizeLyrics = newCheckedState))
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    )
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.tag_as_instrumental)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isInstrumental = !isInstrumental
                                // Cancel any ongoing lyrics search
                                if (isInstrumental) {
                                    viewModel.cancelSearch()
                                }
                                songProvider()?.let { song ->
                                    database.query {
                                        upsert(song.copy(isInstrumental = isInstrumental))
                                        // Clear lyrics when tagged as instrumental
                                        if (isInstrumental) {
                                            delete(LyricsEntity(song.id, "", ""))
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = isInstrumental,
                                    onCheckedChange = { newCheckedState ->
                                        isInstrumental = newCheckedState
                                        // Cancel any ongoing lyrics search
                                        if (newCheckedState) {
                                            viewModel.cancelSearch()
                                        }
                                        songProvider()?.let { song ->
                                            database.query {
                                                upsert(song.copy(isInstrumental = newCheckedState))
                                                // Clear lyrics when tagged as instrumental
                                                if (newCheckedState) {
                                                    delete(LyricsEntity(song.id, "", ""))
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    )
                }
            )
        }
    }
    
    /* if (showRomanizationDialog) {
        var isChecked by remember { mutableStateOf(songProvider()?.romanizeLyrics ?: true) }

        // Sync with song changes
        LaunchedEffect(songProvider()) {
            isChecked = songProvider()?.romanizeLyrics ?: true
        }

        DefaultDialog(
            onDismiss = { showRomanizationDialog = false },
            title = { Text(stringResource(R.string.romanization)) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Toggle isChecked when the row is clicked
                        isChecked = !isChecked
                        songProvider()?.let { song ->
                            database.query {
                                upsert(song.copy(romanizeLyrics = isChecked))
                            }
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.romanize_current_track),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = { newCheckedState ->
                        isChecked = newCheckedState
                        songProvider()?.let { song ->
                            database.query {
                                upsert(song.copy(romanizeLyrics = newCheckedState))
                            }
                        }
                    }
                )
            }
        }
    } */
}
