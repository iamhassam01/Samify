package com.samify.music.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val owner: SpotifyOwner,
    @SerialName("public") val isPublic: Boolean = true,
    val tracks: SpotifyTracksPaging
)

@Serializable
data class SpotifyTracksPaging(
    val href: String,
    val items: List<SpotifyPlaylistTrack>,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int
)

@Serializable
data class SpotifyPlaylistTrack(
    @SerialName("added_at") val addedAt: String,
    @SerialName("added_by") val addedBy: SpotifyOwner,
    @SerialName("is_local") val isLocal: Boolean = false,
    val track: SpotifyTrack?
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,
    @SerialName("duration_ms") val durationMs: Int,
    val explicit: Boolean = false,
    @SerialName("external_urls") val externalUrls: SpotifyExternalUrls,
    val popularity: Int = 0,
    @SerialName("preview_url") val previewUrl: String? = null
)

@Serializable
data class SpotifyArtist(
    val id: String,
    val name: String,
    @SerialName("external_urls") val externalUrls: SpotifyExternalUrls
)

@Serializable
data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)

@Serializable
data class SpotifyOwner(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("external_urls") val externalUrls: SpotifyExternalUrls
)

@Serializable
data class SpotifyExternalUrls(
    val spotify: String
)

@Serializable
data class SpotifyUserPlaylists(
    val href: String,
    val items: List<SpotifyPlaylist>,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int
)

@Serializable
data class SpotifyAccessToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String
)
