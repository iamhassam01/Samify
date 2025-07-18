package com.samify.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.samify.music.R
import com.samify.music.spotify.SpotifyImportViewModel
import com.samify.music.spotify.ImportResult
import com.samify.music.ui.component.IconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var contextError by remember { mutableStateOf<String?>(null) }
    var selectedPlaylists by remember { mutableStateOf(setOf<String>()) }
    var showImportResult by remember { mutableStateOf(false) }

    // Show error as snackbar if available
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            contextError = uiState.error
        }
    }

    // Show import result dialog
    LaunchedEffect(uiState.importResult) {
        if (uiState.importResult != null) {
            showImportResult = true
        }
    }

    // Import Result Dialog
    if (showImportResult && uiState.importResult != null) {
        AlertDialog(
            onDismissRequest = {
                showImportResult = false
                viewModel.clearImportResult()
            },
            title = {
                Text(
                    text = when (uiState.importResult) {
                        is ImportResult.Success -> "Import Successful!"
                        is ImportResult.Error -> "Import Failed"
                        else -> "Import Complete"
                    }
                )
            },
            text = {
                Column {
                    when (val result = uiState.importResult) {
                        is ImportResult.Success -> {
                            Text("Successfully imported \"${result.playlistName}\"")
                            
                            val matchPercentage = if (result.totalTracks > 0) {
                                (result.foundTracks * 100) / result.totalTracks
                            } else 0
                            
                            Text(
                                text = "Found ${result.foundTracks} out of ${result.totalTracks} tracks (${matchPercentage}% match rate)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            if (result.foundTracks < result.totalTracks) {
                                Text(
                                    text = "${result.totalTracks - result.foundTracks} tracks couldn't be matched on YouTube Music",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Text(
                                text = "✓ Your imported playlist is now available in Library → Playlists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        is ImportResult.Error -> {
                            Text("Failed to import \"${result.playlistName}\"")
                            Text(
                                text = "Error: ${result.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        null -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportResult = false
                        viewModel.clearImportResult()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Import from Spotify") },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateUp,
                    onLongClick = {},
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !uiState.isAuthenticated -> {
                    Text(
                        text = "Connect your Spotify account to import playlists",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (contextError != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = contextError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Button(
                            onClick = { 
                                viewModel.clearError()
                                contextError = null
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("Dismiss Error")
                        }
                    }
                    
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text("Authenticating with Spotify...", modifier = Modifier.padding(bottom = 16.dp))
                    } else {
                        Button(
                            onClick = {
                                val activity = context as? androidx.activity.ComponentActivity
                                if (activity != null) {
                                    try {
                                        contextError = null
                                        // Directly call authentication via Hilt-injected auth manager
                                        viewModel.startAuthentication(activity)
                                    } catch (e: Exception) {
                                        contextError = "Failed to start Spotify authentication: ${e.message}"
                                    }
                                } else {
                                    contextError = "Spotify login requires an activity context. Please launch from a main screen."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Connect to Spotify")
                        }
                    }
                }
                
                uiState.isAuthenticated -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "✓ Connected to Spotify",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (uiState.accessToken != null) {
                                val token = uiState.accessToken
                                Text(
                                    text = "Token: ${token?.take(12)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Logout")
                        }
                    }
                    
                    // Show import progress if importing
                    if (uiState.importingPlaylistId != null && uiState.importProgress != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Importing: ${uiState.importProgress?.playlistName ?: "Unknown"}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                val progress = if (uiState.importProgress?.totalTracks ?: 0 > 0) {
                                    (uiState.importProgress?.currentTrack ?: 0).toFloat() / (uiState.importProgress?.totalTracks ?: 1).toFloat()
                                } else 0f
                                
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                                
                                Text(
                                    text = "Processing track ${uiState.importProgress?.currentTrack ?: 0} of ${uiState.importProgress?.totalTracks ?: 0}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                Text(
                                    text = "Found ${uiState.importProgress?.foundTracks ?: 0} tracks so far",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    if (uiState.isLoading && uiState.importingPlaylistId == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Loading playlists...",
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    } else {
                        if (uiState.playlists.isEmpty()) {
                            Text(
                                text = "No playlists found or playlists haven't loaded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select playlists to import:",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    
                                    if (selectedPlaylists.isNotEmpty()) {
                                        Text(
                                            text = "${selectedPlaylists.size} selected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                // Action buttons
                                if (selectedPlaylists.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (selectedPlaylists.size == 1) {
                                                    viewModel.importPlaylist(selectedPlaylists.first())
                                                } else {
                                                    viewModel.importMultiplePlaylists(selectedPlaylists.toList())
                                                }
                                                selectedPlaylists = emptySet()
                                            },
                                            enabled = uiState.importingPlaylistId == null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                if (selectedPlaylists.size == 1) "Import Selected"
                                                else "Import ${selectedPlaylists.size} Playlists"
                                            )
                                        }
                                        
                                        OutlinedButton(
                                            onClick = { selectedPlaylists = emptySet() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Clear Selection")
                                        }
                                    }
                                }
                                
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.playlists) { playlist ->
                                        val isSelected = selectedPlaylists.contains(playlist.id)
                                        val isImporting = uiState.importingPlaylistId == playlist.id
                                        
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .selectable(
                                                    selected = isSelected,
                                                    enabled = !isImporting,
                                                    onClick = {
                                                        selectedPlaylists = if (isSelected) {
                                                            selectedPlaylists - playlist.id
                                                        } else {
                                                            selectedPlaylists + playlist.id
                                                        }
                                                    }
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isImporting -> MaterialTheme.colorScheme.tertiaryContainer
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = playlist.name,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    if (playlist.description != null) {
                                                        Text(
                                                            text = playlist.description,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${playlist.trackCount} tracks",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                    
                                                    if (isImporting) {
                                                        Text(
                                                            text = "Importing...",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                }
                                                
                                                if (isImporting) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = null // Handled by card click
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
