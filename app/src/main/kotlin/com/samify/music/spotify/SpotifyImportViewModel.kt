package com.samify.music.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import com.samify.music.db.entities.PlaylistEntity
import com.samify.music.db.entities.SongEntity
import com.samify.music.db.entities.ArtistEntity
import com.samify.music.db.entities.SongArtistMap
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SearchSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

data class SpotifyImportUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val accessToken: String? = null,
    val importingPlaylistId: String? = null,
    val importProgress: ImportProgress? = null,
    val importResult: ImportResult? = null
)

data class ImportProgress(
    val playlistName: String,
    val currentTrack: Int,
    val totalTracks: Int,
    val foundTracks: Int
)

sealed class ImportResult {
    data class Success(
        val playlistName: String,
        val foundTracks: Int,
        val totalTracks: Int
    ) : ImportResult()
    
    data class Error(
        val playlistName: String,
        val message: String
    ) : ImportResult()
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val trackCount: Int,
    val imageUrl: String?
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<String>,
    val durationMs: Int,
    val isrc: String?
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    private val authManager: SpotifyAuthManager,
    private val database: com.samify.music.db.MusicDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyImportUiState())
    val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

    // Public property for access token
    val accessToken: String?
        get() = _uiState.value.accessToken

    init {
        // Observe authentication state changes
        viewModelScope.launch {
            authManager.authState.collect { authState ->
                when (authState) {
                    is SpotifyAuthManager.AuthState.AUTHENTICATED -> {
                        android.util.Log.d("SpotifyImportViewModel", "Authentication successful")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            accessToken = authManager.accessToken.value,
                            error = null
                        )
                        // Automatically load playlists after successful authentication
                        loadUserPlaylists()
                    }
                    is SpotifyAuthManager.AuthState.ERROR -> {
                        android.util.Log.e("SpotifyImportViewModel", "Authentication error: ${authState.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = false,
                            accessToken = null,
                            error = authState.message
                        )
                    }
                    SpotifyAuthManager.AuthState.AUTHENTICATING -> {
                        android.util.Log.d("SpotifyImportViewModel", "Authentication loading")
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            error = null
                        )
                    }
                    SpotifyAuthManager.AuthState.IDLE -> {
                        android.util.Log.d("SpotifyImportViewModel", "Authentication idle")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = false,
                            accessToken = null,
                            error = null
                        )
                    }
                }
            }
        }
        
        // Check if already authenticated
        if (authManager.isAuthenticated()) {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                accessToken = authManager.accessToken.value
            )
            loadUserPlaylists()
        }
    }

    fun authenticate() {
        android.util.Log.d("SpotifyImportViewModel", "Starting authentication")
        // This will be called from the composable which has access to the activity
    }

    fun startAuthentication(activity: android.app.Activity) {
        android.util.Log.d("SpotifyImportViewModel", "Starting authentication with activity")
        authManager.authenticate(activity)
    }

    fun logout() {
        android.util.Log.d("SpotifyImportViewModel", "Logging out")
        authManager.logout()
        _uiState.value = SpotifyImportUiState()
    }

    private fun loadUserPlaylists() {
        val token = _uiState.value.accessToken
        if (token == null) {
            android.util.Log.w("SpotifyImportViewModel", "No access token available for loading playlists")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                android.util.Log.d("SpotifyImportViewModel", "Loading playlists with token: $token")
                
                // Fetch actual user playlists from Spotify Web API
                val playlists = fetchSpotifyUserPlaylists(token)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    playlists = playlists,
                    error = null
                )
                
                android.util.Log.d("SpotifyImportViewModel", "Loaded ${playlists.size} playlists from Spotify")
                
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImportViewModel", "Error loading playlists: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load playlists: ${e.message}"
                )
            }
        }
    }

    private suspend fun fetchSpotifyUserPlaylists(accessToken: String): List<SpotifyPlaylist> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists?limit=50")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Spotify API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                throw Exception("Empty response from Spotify API")
            }

            android.util.Log.d("SpotifyImportViewModel", "Spotify API response: $responseBody")

            // Parse JSON response manually (simple approach)
            val playlists = parseSpotifyPlaylistsResponse(responseBody)
            
            return@withContext playlists
        }
    }

    private fun parseSpotifyPlaylistsResponse(json: String): List<SpotifyPlaylist> {
        val playlists = mutableListOf<SpotifyPlaylist>()
        
        try {
            // Simple JSON parsing for playlist items
            // Look for "items" array in the response
            val itemsStart = json.indexOf("\"items\":[")
            if (itemsStart == -1) {
                android.util.Log.w("SpotifyImportViewModel", "No items array found in response")
                return emptyList()
            }
            
            // Find each playlist object within the items array
            val itemsJson = json.substring(itemsStart + 9) // Skip "items":[
            var currentIndex = 0
            
            while (currentIndex < itemsJson.length) {
                val playlistStart = itemsJson.indexOf("{", currentIndex)
                if (playlistStart == -1) break
                
                // Find the matching closing brace for this playlist object
                var braceCount = 0
                var playlistEnd = playlistStart
                
                for (i in playlistStart until itemsJson.length) {
                    when (itemsJson[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                playlistEnd = i
                                break
                            }
                        }
                    }
                }
                
                if (braceCount == 0) {
                    val playlistJson = itemsJson.substring(playlistStart, playlistEnd + 1)
                    val playlist = parsePlaylistObject(playlistJson)
                    if (playlist != null) {
                        playlists.add(playlist)
                    }
                }
                
                currentIndex = playlistEnd + 1
                
                // Break if we hit the end of items array
                if (currentIndex < itemsJson.length && itemsJson[currentIndex] == ']') {
                    break
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImportViewModel", "Error parsing playlist response: ${e.message}")
            throw Exception("Failed to parse Spotify playlists response")
        }
        
        return playlists
    }

    private fun parsePlaylistObject(playlistJson: String): SpotifyPlaylist? {
        try {
            val id = extractJsonString(playlistJson, "id") ?: return null
            val name = extractJsonString(playlistJson, "name") ?: "Untitled Playlist"
            val description = extractJsonString(playlistJson, "description")
            
            // Extract track count from tracks.total
            val tracksPattern = "\"tracks\"\\s*:\\s*\\{[^}]*\"total\"\\s*:\\s*(\\d+)".toRegex()
            val tracksMatch = tracksPattern.find(playlistJson)
            val trackCount = tracksMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            // Extract image URL from first image if available
            val imageUrl = extractFirstImageUrl(playlistJson)
            
            return SpotifyPlaylist(
                id = id,
                name = name,
                description = description,
                trackCount = trackCount,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImportViewModel", "Error parsing playlist object: ${e.message}")
            return null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*?)\"".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
    }

    private fun extractFirstImageUrl(json: String): String? {
        // Look for images array and extract first URL
        val imagesPattern = "\"images\"\\s*:\\s*\\[\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = imagesPattern.find(json)
        return match?.groupValues?.get(1)
    }

    fun importPlaylist(playlistId: String) {
        android.util.Log.d("SpotifyImportViewModel", "🚀 Starting import for playlist: $playlistId")
        
        val playlistToImport = _uiState.value.playlists.find { it.id == playlistId }
        if (playlistToImport == null) {
            android.util.Log.e("SpotifyImportViewModel", "❌ Playlist not found: $playlistId")
            _uiState.value = _uiState.value.copy(
                importResult = ImportResult.Error("Unknown", "Playlist not found")
            )
            return
        }
        
        android.util.Log.d("SpotifyImportViewModel", "📋 Found playlist: ${playlistToImport.name}")
        
        // Start import process with NonCancellable to prevent early cancellation
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            android.util.Log.d("SpotifyImportViewModel", "🔄 Import coroutine started")
            try {
                // Update UI state immediately on main thread
                _uiState.value = _uiState.value.copy(
                    importingPlaylistId = playlistId,
                    importProgress = ImportProgress(
                        playlistName = playlistToImport.name,
                        currentTrack = 0,
                        totalTracks = 0,
                        foundTracks = 0
                    ),
                    importResult = null,
                    error = null
                )
                
                val token = _uiState.value.accessToken
                if (token == null) {
                    throw Exception("No access token available")
                }
                
                android.util.Log.d("SpotifyImportViewModel", "Fetching tracks for playlist: ${playlistToImport.name}")
                
                // Fetch playlist tracks from Spotify (on IO thread)
                val spotifyTracks = withContext(Dispatchers.IO) {
                    fetchSpotifyPlaylistTracks(token, playlistId)
                }
                
                android.util.Log.d("SpotifyImportViewModel", "Found ${spotifyTracks.size} tracks in Spotify playlist")
                
                if (spotifyTracks.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        importingPlaylistId = null,
                        importProgress = null,
                        importResult = ImportResult.Error(
                            playlistToImport.name,
                            "No tracks found in this playlist"
                        )
                    )
                    return@launch
                }
                
                // Update progress with total tracks
                _uiState.value = _uiState.value.copy(
                    importProgress = _uiState.value.importProgress?.copy(
                        totalTracks = spotifyTracks.size
                    )
                )
                
                // Create local playlist (on IO thread)
                val localPlaylistId = withContext(Dispatchers.IO) {
                    val localPlaylist = PlaylistEntity(
                        name = "${playlistToImport.name} (from Spotify)",
                        browseId = null,
                        bookmarkedAt = java.time.LocalDateTime.now()
                    )
                    database.query { insert(localPlaylist) }
                    localPlaylist.id
                }
                
                android.util.Log.d("SpotifyImportViewModel", "Created local playlist with ID: $localPlaylistId")
                
                // Process tracks with aggressive search and proper threading
                val foundSongIds = processTracksWithAdvancedSearch(spotifyTracks, playlistToImport.name)
                
                // Add found songs to playlist (on IO thread)
                if (foundSongIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            android.util.Log.d("SpotifyImportViewModel", "🎵 Adding ${foundSongIds.size} songs to playlist...")
                            val playlist = database.playlist(localPlaylistId).first()
                            if (playlist != null) {
                                // Add songs in smaller batches to prevent database overload
                                val batchSize = 10
                                foundSongIds.chunked(batchSize).forEachIndexed { index, batch ->
                                    try {
                                        database.addSongToPlaylist(playlist, batch)
                                        android.util.Log.d("SpotifyImportViewModel", "✅ Added batch ${index + 1} (${batch.size} songs)")
                                        delay(50) // Small delay between batches
                                    } catch (batchException: Exception) {
                                        android.util.Log.e("SpotifyImportViewModel", "Error adding batch ${index + 1}", batchException)
                                    }
                                }
                                android.util.Log.d("SpotifyImportViewModel", "🎉 Successfully added all ${foundSongIds.size} songs to playlist")
                            } else {
                                android.util.Log.e("SpotifyImportViewModel", "❌ Playlist not found for ID: $localPlaylistId")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SpotifyImportViewModel", "💥 Critical error adding songs to playlist", e)
                        }
                    }
                } else {
                    android.util.Log.w("SpotifyImportViewModel", "⚠️ No songs found to add to playlist")
                }
                
                // Final success state - CRITICAL: Update UI on Main thread
                withContext(Dispatchers.Main) {
                    try {
                        _uiState.value = _uiState.value.copy(
                            importingPlaylistId = null,
                            importProgress = null,
                            importResult = ImportResult.Success(
                                playlistName = playlistToImport.name,
                                foundTracks = foundSongIds.size,
                                totalTracks = spotifyTracks.size
                            )
                        )
                        
                        val message = "Successfully imported playlist '${playlistToImport.name}' with ${foundSongIds.size} out of ${spotifyTracks.size} tracks found"
                        android.util.Log.d("SpotifyImportViewModel", message)
                        
                        // Small delay to ensure UI state is properly set before any navigation
                        delay(100)
                        
                    } catch (e: Exception) {
                        android.util.Log.e("SpotifyImportViewModel", "Error updating final UI state", e)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImportViewModel", "Error importing playlist: ${e.message}", e)
                
                // Error state - CRITICAL: Update UI on Main thread  
                withContext(Dispatchers.Main) {
                    try {
                        _uiState.value = _uiState.value.copy(
                            importingPlaylistId = null,
                            importProgress = null,
                            importResult = ImportResult.Error(
                                playlistName = playlistToImport.name,
                                message = e.message ?: "Unknown error occurred"
                            )
                        )
                    } catch (uiException: Exception) {
                        android.util.Log.e("SpotifyImportViewModel", "Error updating error UI state", uiException)
                    }
                }
            }
        }
    }

    private suspend fun processTracksWithAdvancedSearch(
        spotifyTracks: List<SpotifyTrack>,
        playlistName: String
    ): List<String> {
        val foundSongIds = mutableListOf<String>()
        var successCount = 0
        
        // Process tracks in smaller chunks to prevent memory issues and crashes
        val chunkSize = 3 // Smaller chunks for better stability
        val chunks = spotifyTracks.chunked(chunkSize)
        
        android.util.Log.d("SpotifyImportViewModel", "📦 Processing ${spotifyTracks.size} tracks in ${chunks.size} chunks of $chunkSize")
        
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            android.util.Log.d("SpotifyImportViewModel", "🔄 Processing chunk ${chunkIndex + 1}/${chunks.size}")
            
            for ((localIndex, track) in chunk.withIndex()) {
                val globalIndex = chunkIndex * chunkSize + localIndex
                
                try {
                    // Force garbage collection and longer pause between chunks
                    if (localIndex == 0 && chunkIndex > 0) {
                        System.gc()
                        delay(300L) // Longer delay between chunks
                    }
                    
                    // Update progress on main thread
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            importProgress = _uiState.value.importProgress?.copy(
                                currentTrack = globalIndex + 1,
                                foundTracks = successCount
                            )
                        )
                    }
                    
                    android.util.Log.d("SpotifyImportViewModel", "🔍 Searching track ${globalIndex + 1}/${spotifyTracks.size}: ${track.name} by ${track.artists.joinToString(", ")}")
                    
                    // Use aggressive search with multiple strategies
                    val songId = findSongWithAggressiveSearch(track)
                    
                    if (songId != null) {
                        foundSongIds.add(songId)
                        successCount++
                        android.util.Log.d("SpotifyImportViewModel", "✅ Found: ${track.name} -> $songId")
                    } else {
                        android.util.Log.w("SpotifyImportViewModel", "❌ No match: ${track.name} by ${track.artists.joinToString(", ")}")
                    }
                    
                    // Longer delay to prevent overwhelming and reduce crash risk
                    delay(150L)
                    
                } catch (e: kotlinx.coroutines.CancellationException) {
                    android.util.Log.w("SpotifyImportViewModel", "🚫 Search cancelled for track: ${track.name}")
                    break // Stop the loop on cancellation
                } catch (e: Exception) {
                    android.util.Log.e("SpotifyImportViewModel", "💥 Error searching for track: ${track.name}", e)
                } catch (e: Exception) {
                    android.util.Log.e("SpotifyImportViewModel", "💥 Error searching for track: ${track.name}", e)
                    // Continue with next track on other errors
                }
            }
            
            // Log chunk completion
            android.util.Log.d("SpotifyImportViewModel", "✅ Completed chunk ${chunkIndex + 1}/${chunks.size} - Total found: $successCount")
        }
        
        android.util.Log.d("SpotifyImportViewModel", "🎉 Import complete! Found $successCount/${spotifyTracks.size} tracks")
        
        return foundSongIds
    }

    private suspend fun fetchSpotifyPlaylistTracks(accessToken: String, playlistId: String): List<SpotifyTrack> {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val tracks = mutableListOf<SpotifyTrack>()
            var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=50"
            
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("Spotify API error: ${response.code} ${response.message}")
                }
                
                val responseBody = response.body?.string()
                if (responseBody == null) {
                    throw Exception("Empty response from Spotify tracks API")
                }

                android.util.Log.d("SpotifyImportViewModel", "Fetched tracks page from Spotify API")

                // Parse tracks from this page
                val pageTracks = parseSpotifyTracks(responseBody)
                tracks.addAll(pageTracks)
                
                // Check for next page
                nextUrl = extractJsonString(responseBody, "next")
            }
            
            return@withContext tracks
        }
    }

    private fun parseSpotifyTracks(json: String): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        
        try {
            // Find items array
            val itemsStart = json.indexOf("\"items\":[")
            if (itemsStart == -1) {
                return emptyList()
            }
            
            val itemsJson = json.substring(itemsStart + 9)
            var currentIndex = 0
            
            while (currentIndex < itemsJson.length) {
                val itemStart = itemsJson.indexOf("{", currentIndex)
                if (itemStart == -1) break
                
                // Find the matching closing brace for this item object
                var braceCount = 0
                var itemEnd = itemStart
                
                for (i in itemStart until itemsJson.length) {
                    when (itemsJson[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                itemEnd = i
                                break
                            }
                        }
                    }
                }
                
                if (braceCount == 0) {
                    val itemJson = itemsJson.substring(itemStart, itemEnd + 1)
                    val track = parseSpotifyTrack(itemJson)
                    if (track != null) {
                        tracks.add(track)
                    }
                }
                
                currentIndex = itemEnd + 1
                
                // Break if we hit the end of items array
                if (currentIndex < itemsJson.length && itemsJson[currentIndex] == ']') {
                    break
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImportViewModel", "Error parsing tracks response: ${e.message}")
        }
        
        return tracks
    }

    private fun parseSpotifyTrack(itemJson: String): SpotifyTrack? {
        try {
            // Extract track object from item
            val trackStart = itemJson.indexOf("\"track\":{")
            if (trackStart == -1) {
                return null // This might be a local track or episode
            }
            
            // Find the track object
            var braceCount = 0
            var trackEnd = trackStart + 8 // Skip "track":{
            
            for (i in (trackStart + 8) until itemJson.length) {
                when (itemJson[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            trackEnd = i
                            break
                        }
                    }
                }
            }
            
            val trackJson = itemJson.substring(trackStart + 8, trackEnd + 1)
            
            val id = extractJsonString(trackJson, "id") ?: return null
            val name = extractJsonString(trackJson, "name") ?: "Unknown Track"
            val durationMs = extractDuration(trackJson)
            val isrc = extractISRC(trackJson)
            
            // Extract artists
            val artists = extractArtists(trackJson)
            
            return SpotifyTrack(
                id = id,
                name = name,
                artists = artists,
                durationMs = durationMs,
                isrc = isrc
            )
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImportViewModel", "Error parsing track object: ${e.message}")
            return null
        }
    }

    private fun extractDuration(trackJson: String): Int {
        val pattern = "\"duration_ms\"\\s*:\\s*(\\d+)".toRegex()
        val match = pattern.find(trackJson)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractISRC(trackJson: String): String? {
        val pattern = "\"isrc\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = pattern.find(trackJson)
        return match?.groupValues?.get(1)
    }

    private fun extractArtists(trackJson: String): List<String> {
        val artists = mutableListOf<String>()
        
        try {
            val artistsStart = trackJson.indexOf("\"artists\":[")
            if (artistsStart == -1) {
                return emptyList()
            }
            
            val artistsJson = trackJson.substring(artistsStart + 11)
            var currentIndex = 0
            
            while (currentIndex < artistsJson.length) {
                val artistStart = artistsJson.indexOf("{", currentIndex)
                if (artistStart == -1) break
                
                var braceCount = 0
                var artistEnd = artistStart
                
                for (i in artistStart until artistsJson.length) {
                    when (artistsJson[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                artistEnd = i
                                break
                            }
                        }
                    }
                }
                
                if (braceCount == 0) {
                    val artistJson = artistsJson.substring(artistStart, artistEnd + 1)
                    val artistName = extractJsonString(artistJson, "name")
                    if (artistName != null) {
                        artists.add(artistName)
                    }
                }
                
                currentIndex = artistEnd + 1
                
                // Break if we hit the end of artists array
                if (currentIndex < artistsJson.length && artistsJson[currentIndex] == ']') {
                    break
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImportViewModel", "Error parsing artists: ${e.message}")
        }
        
        return artists
    }

    private suspend fun findSongWithAggressiveSearch(spotifyTrack: SpotifyTrack): String? {
        // Ultra-aggressive search with 8 different strategies
        val searchStrategies = mutableListOf<String>()
        
        // Clean the song name first
        val cleanSongName = cleanSongTitle(spotifyTrack.name)
        val mainArtist = spotifyTrack.artists.firstOrNull() ?: ""
        val allArtists = spotifyTrack.artists.joinToString(" ")
        
        // Strategy 1: Exact as-is
        searchStrategies.add("${spotifyTrack.name} ${allArtists}")
        
        // Strategy 2: Cleaned song + main artist
        searchStrategies.add("$cleanSongName $mainArtist")
        
        // Strategy 3: Just cleaned song name (for popular songs)
        searchStrategies.add(cleanSongName)
        
        // Strategy 4: Song + "official" (helps find official versions)
        searchStrategies.add("$cleanSongName $mainArtist official")
        
        // Strategy 5: Song + "audio" (helps find audio-only versions)
        searchStrategies.add("$cleanSongName $mainArtist audio")
        
        // Strategy 6: Without featuring artists (remove ft./feat.)
        val noFeatArtist = spotifyTrack.artists.first()
        searchStrategies.add("$cleanSongName $noFeatArtist")
        
        // Strategy 7: Reverse order (Artist Song)
        searchStrategies.add("$mainArtist $cleanSongName")
        
        // Strategy 8: Song + "music video" (sometimes only MV available)
        searchStrategies.add("$cleanSongName $mainArtist music video")
        
        android.util.Log.d("SpotifyImportViewModel", "🎯 Trying ${searchStrategies.size} search strategies for: ${spotifyTrack.name}")
        
        for ((strategyIndex, query) in searchStrategies.withIndex()) {
            try {
                val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
                if (cleanQuery.length < 3) continue // Skip too short queries
                
                android.util.Log.d("SpotifyImportViewModel", "  Strategy ${strategyIndex + 1}: '$cleanQuery'")
                
                val result = searchYouTubeWithRetry(cleanQuery, spotifyTrack)
                if (result != null) {
                    android.util.Log.d("SpotifyImportViewModel", "  ✅ Strategy ${strategyIndex + 1} SUCCESS!")
                    return result
                }
                
                // Slightly longer delay between strategies to reduce load
                delay(50L)
                
            } catch (e: Exception) {
                android.util.Log.w("SpotifyImportViewModel", "  ❌ Strategy ${strategyIndex + 1} failed", e)
                // Force garbage collection on errors to prevent memory leaks
                if (strategyIndex % 3 == 0) System.gc()
            }
        }
        
        return null
    }

    private fun cleanSongTitle(title: String): String {
        return title
            // Remove common suffixes that might interfere
            .replace(Regex("\\s*\\(.*?\\)\\s*"), " ") // Remove parentheses
            .replace(Regex("\\s*\\[.*?\\]\\s*"), " ") // Remove brackets
            .replace(Regex("\\s*-\\s*(feat|ft|featuring)\\.?.*", RegexOption.IGNORE_CASE), "") // Remove featuring
            .replace(Regex("\\s*-\\s*remaster.*", RegexOption.IGNORE_CASE), "") // Remove remaster info
            .replace(Regex("\\s*-\\s*remix.*", RegexOption.IGNORE_CASE), "") // Remove remix info
            .replace(Regex("\\s+"), " ") // Multiple spaces to single
            .trim()
    }

    private suspend fun searchYouTubeWithRetry(query: String, spotifyTrack: SpotifyTrack): String? {
        var attempt = 0
        val maxAttempts = 2
        
        while (attempt < maxAttempts) {
            try {
                val searchResult = YouTube.search(query, filter = com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                
                if (searchResult?.items?.isNotEmpty() == true) {
                    // Use more relaxed matching for better results
                    val bestMatch = findBestMatchFromResults(searchResult.items, spotifyTrack)
                    if (bestMatch != null) {
                        // Save to database and return
                        saveSongToDatabase(bestMatch)
                        return bestMatch.id
                    }
                }
                
                break // Success (even if no match found)
                
            } catch (e: Exception) {
                attempt++
                android.util.Log.w("SpotifyImportViewModel", "Search attempt $attempt failed for: $query", e)
                if (attempt < maxAttempts) {
                    delay(200L * attempt) // Progressive delay
                }
            }
        }
        
        return null
    }

    private fun findBestMatchFromResults(
        items: List<com.metrolist.innertube.models.YTItem>, 
        spotifyTrack: SpotifyTrack
    ): com.metrolist.innertube.models.SongItem? {
        
        val songItems = items.filterIsInstance<com.metrolist.innertube.models.SongItem>()
        if (songItems.isEmpty()) return null
        
        // Use more aggressive matching - lower thresholds for better discovery
        val scoredMatches = songItems.take(15).mapNotNull { item ->
            val score = calculateRelaxedMatchScore(item, spotifyTrack)
            if (score > 0.2) { // Much lower threshold (was 0.3)
                item to score
            } else null
        }
        
        // Return the best match
        return scoredMatches.maxByOrNull { it.second }?.first
    }

    private fun calculateRelaxedMatchScore(ytSong: com.metrolist.innertube.models.SongItem, spotifyTrack: SpotifyTrack): Double {
        var score = 0.0
        
        // Title similarity (60% weight - increased importance)
        val titleSimilarity = calculateRelaxedStringSimilarity(ytSong.title, spotifyTrack.name)
        score += titleSimilarity * 0.6
        
        // Artist similarity (35% weight)
        val maxArtistSimilarity = spotifyTrack.artists.maxOfOrNull { spotifyArtist ->
            ytSong.artists.maxOfOrNull { ytArtist ->
                calculateRelaxedStringSimilarity(ytArtist.name, spotifyArtist)
            } ?: 0.0
        } ?: 0.0
        score += maxArtistSimilarity * 0.35
        
        // Duration bonus (5% weight) - only if very close
        if (ytSong.duration != null && spotifyTrack.durationMs > 0) {
            val ytDurationMs = ytSong.duration!! * 1000
            val durationDiff = kotlin.math.abs(ytDurationMs - spotifyTrack.durationMs).toDouble()
            if (durationDiff <= 10000) { // Within 10 seconds
                score += 0.05
            }
        }
        
        return score
    }

    private fun calculateRelaxedStringSimilarity(str1: String, str2: String): Double {
        val clean1 = cleanString(str1)
        val clean2 = cleanString(str2)
        
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0
        
        // Exact match
        if (clean1 == clean2) return 1.0
        
        // Contains match (very high score)
        if (clean1.contains(clean2) || clean2.contains(clean1)) return 0.9
        
        // Word-based matching (high priority for song matching)
        val words1 = clean1.split(" ").filter { it.length > 2 }
        val words2 = clean2.split(" ").filter { it.length > 2 }
        
        if (words1.isNotEmpty() && words2.isNotEmpty()) {
            val commonWords = words1.intersect(words2.toSet()).size
            val totalUniqueWords = (words1 + words2).toSet().size
            val wordSimilarity = commonWords.toDouble() / totalUniqueWords.toDouble() * 2.0 // Boost word matching
            
            if (wordSimilarity > 0.5) return wordSimilarity
        }
        
        // Fallback to character similarity but with lower standards
        val maxLength = kotlin.math.max(clean1.length, clean2.length)
        if (maxLength == 0) return 0.0
        
        val distance = levenshteinDistance(clean1, clean2)
        return kotlin.math.max(0.0, 1.0 - (distance.toDouble() / maxLength.toDouble()))
    }

    private suspend fun searchYouTubeForSong(query: String, spotifyTrack: SpotifyTrack): String? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("SpotifyImportViewModel", "Searching YouTube for: $query")
                
                // Search YouTube Music with timeout and retry logic
                var searchResult: com.metrolist.innertube.pages.SearchResult? = null
                var retryCount = 0
                val maxRetries = 2 // Reduced retries for faster processing
                
                while (searchResult == null && retryCount < maxRetries) {
                    try {
                        searchResult = YouTube.search(query, filter = com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        break
                    } catch (e: Exception) {
                        retryCount++
                        android.util.Log.w("SpotifyImportViewModel", "YouTube search attempt $retryCount failed for: $query", e)
                        if (retryCount < maxRetries) {
                            kotlinx.coroutines.delay(500L * retryCount) // Shorter backoff
                        }
                    }
                }
                
                if (searchResult != null && searchResult.items.isNotEmpty()) {
                    // Try to find the best match with improved scoring
                    val scoredResults = mutableListOf<Pair<com.metrolist.innertube.models.SongItem, Double>>()
                    
                    for (item in searchResult.items.take(10)) { // Check top 10 results
                        if (item is com.metrolist.innertube.models.SongItem) {
                            val score = calculateMatchScore(item, spotifyTrack)
                            if (score > 0.3) { // Lower threshold for more matches
                                scoredResults.add(item to score)
                            }
                        }
                    }
                    
                    // Sort by score and take the best match
                    val bestMatch = scoredResults.maxByOrNull { it.second }?.first
                    
                    if (bestMatch != null) {
                        android.util.Log.d("SpotifyImportViewModel", "Found match: ${bestMatch.title} by ${bestMatch.artists.joinToString { it.name }} (score: ${scoredResults.maxByOrNull { it.second }?.second})")
                        
                        // Convert to local song and save
                        return@withContext try {
                            saveSongToDatabase(bestMatch)
                            bestMatch.id
                        } catch (e: Exception) {
                            android.util.Log.e("SpotifyImportViewModel", "Error saving song to database: ${bestMatch.title}", e)
                            null
                        }
                    }
                }
                
                return@withContext null
                
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImportViewModel", "Error searching YouTube for song: $query", e)
                return@withContext null
            }
        }
    }

    private fun calculateMatchScore(ytSong: com.metrolist.innertube.models.SongItem, spotifyTrack: SpotifyTrack): Double {
        var score = 0.0
        
        // Title similarity (most important - 50% weight)
        val titleSimilarity = calculateStringSimilarity(ytSong.title, spotifyTrack.name)
        score += titleSimilarity * 0.5
        
        // Artist similarity (40% weight)
        val maxArtistSimilarity = spotifyTrack.artists.maxOfOrNull { spotifyArtist ->
            ytSong.artists.maxOfOrNull { ytArtist ->
                calculateStringSimilarity(ytArtist.name, spotifyArtist)
            } ?: 0.0
        } ?: 0.0
        score += maxArtistSimilarity * 0.4
        
        // Duration similarity (10% weight) - if available
        if (ytSong.duration != null && spotifyTrack.durationMs > 0) {
            val ytDurationMs = ytSong.duration!! * 1000
            val durationDiff = kotlin.math.abs(ytDurationMs - spotifyTrack.durationMs).toDouble()
            val maxDuration = kotlin.math.max(ytDurationMs, spotifyTrack.durationMs).toDouble()
            val durationSimilarity = kotlin.math.max(0.0, 1.0 - (durationDiff / maxDuration))
            score += durationSimilarity * 0.1
        }
        
        return score
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val clean1 = cleanString(str1)
        val clean2 = cleanString(str2)
        
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0
        
        // Exact match
        if (clean1 == clean2) return 1.0
        
        // Contains match
        if (clean1.contains(clean2) || clean2.contains(clean1)) return 0.8
        
        // Levenshtein distance based similarity
        val maxLength = kotlin.math.max(clean1.length, clean2.length)
        val distance = levenshteinDistance(clean1, clean2)
        val similarity = 1.0 - (distance.toDouble() / maxLength.toDouble())
        
        // Boost for partial word matches
        val words1 = clean1.split(" ")
        val words2 = clean2.split(" ")
        val commonWords = words1.intersect(words2.toSet()).size
        val totalWords = kotlin.math.max(words1.size, words2.size)
        val wordSimilarity = commonWords.toDouble() / totalWords.toDouble()
        
        return kotlin.math.max(similarity, wordSimilarity * 0.7)
    }

    private fun cleanString(str: String): String {
        return str.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Replace special chars with space
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
            .trim()
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                dp[i][j] = if (str1[i - 1] == str2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[len1][len2]
    }

    private suspend fun saveSongToDatabase(item: com.metrolist.innertube.models.SongItem) {
        withContext(Dispatchers.IO) {
            // Check if song already exists
            val existingSong = database.song(item.id).first()
            if (existingSong == null) {
                val songEntity = SongEntity(
                    id = item.id,
                    title = item.title,
                    duration = item.duration ?: -1,
                    thumbnailUrl = item.thumbnail
                )
                
                database.query { insert(songEntity) }
                
                // Insert artists
                item.artists.forEachIndexed { index, artist ->
                    try {
                        val artistEntity = ArtistEntity(
                            id = artist.id ?: ArtistEntity.generateArtistId(),
                            name = artist.name
                        )
                        val existingArtist = database.artist(artistEntity.id).first()
                        if (existingArtist == null) {
                            database.query { insert(artistEntity) }
                        }
                        database.query {
                            insert(SongArtistMap(
                                songId = item.id,
                                artistId = artistEntity.id,
                                position = index
                            ))
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SpotifyImportViewModel", "Error inserting artist: ${artist.name}", e)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun importMultiplePlaylists(playlistIds: List<String>) {
        if (playlistIds.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            for (playlistId in playlistIds) {
                importPlaylist(playlistId)
                // Wait for the current import to complete before starting the next one
                while (_uiState.value.importingPlaylistId != null) {
                    kotlinx.coroutines.delay(500L)
                }
                // Small delay between imports
                kotlinx.coroutines.delay(1000L)
            }
        }
    }
}
