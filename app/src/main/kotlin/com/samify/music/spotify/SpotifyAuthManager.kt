package com.samify.music.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.ComponentActivity
import com.samify.music.BuildConfig
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Secure credentials from BuildConfig
        private const val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
        private const val CLIENT_SECRET = BuildConfig.SPOTIFY_CLIENT_SECRET
        private const val REDIRECT_URI = "com.samify.music://callback"
        private const val REQUEST_CODE = 1337
        private const val PREFS_NAME = "spotify_auth"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val TOKEN_EXPIRY_KEY = "token_expiry"
        private const val CODE_VERIFIER_KEY = "code_verifier"
        
        // Updated scopes for 2025
        private val SCOPES = arrayOf(
            "user-read-private",
            "user-read-email",
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-top-read"
        )
    }

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val httpClient = OkHttpClient()

    private val _authState = MutableStateFlow<AuthState>(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    // No longer using activity result launcher since we're using onActivityResult

    init {
        loadStoredToken()
    }

    fun registerActivityResultLauncher(activity: ComponentActivity) {
        // This method is kept for compatibility but is not used
        // since we're using onActivityResult directly
    }

    fun isAuthenticated(): Boolean {
        val token = _accessToken.value
        val expiry = sharedPreferences.getLong(TOKEN_EXPIRY_KEY, 0)
        return !token.isNullOrEmpty() && System.currentTimeMillis() < expiry
    }

    fun authenticate(activity: Activity) {
        android.util.Log.d("SpotifyAuthManager", "Starting Authorization Code with PKCE flow")
        _authState.value = AuthState.AUTHENTICATING

        try {
            // Generate PKCE code verifier and challenge
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            
            // Store code verifier for later use
            sharedPreferences.edit()
                .putString(CODE_VERIFIER_KEY, codeVerifier)
                .apply()
            
            // Use Authorization Code flow with PKCE
            val authUrl = buildAuthUrl(codeChallenge)
            android.util.Log.d("SpotifyAuthManager", "Opening browser with URL: $authUrl")
            
            // Show instructions to user
            android.widget.Toast.makeText(
                activity,
                "You'll be redirected to Spotify. After login, you'll return to the app automatically.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            activity.startActivity(intent)
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyAuthManager", "Failed to start authentication: ${e.message}")
            _authState.value = AuthState.ERROR("Failed to start Spotify login: ${e.message}")
        }
    }

    private fun buildAuthUrl(codeChallenge: String): String {
        val scopes = SCOPES.joinToString(" ")
        android.util.Log.d("SpotifyAuthManager", "Building auth URL with challenge: ${codeChallenge.take(10)}...")
        val authUrl = "https://accounts.spotify.com/authorize?" +
                "client_id=$CLIENT_ID" +
                "&response_type=code" +
                "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                "&scope=${Uri.encode(scopes)}" +
                "&code_challenge_method=S256" +
                "&code_challenge=${Uri.encode(codeChallenge)}" +
                "&show_dialog=true"
        android.util.Log.d("SpotifyAuthManager", "Complete auth URL: $authUrl")
        return authUrl
    }

    private fun generateCodeVerifier(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val secureRandom = SecureRandom()
        val verifier = StringBuilder()
        
        // Generate exactly 128 characters as recommended by OAuth 2.1
        repeat(128) {
            verifier.append(allowedChars[secureRandom.nextInt(allowedChars.length)])
        }
        
        val result = verifier.toString()
        android.util.Log.d("SpotifyAuthManager", "Generated code verifier: ${result.take(10)}... (length: ${result.length})")
        return result
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digest = messageDigest.digest(bytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        android.util.Log.d("SpotifyAuthManager", "Generated code challenge: ${challenge.take(10)}...")
        return challenge
    }

    fun handleWebAuthSuccess(accessToken: String, expiresIn: Int) {
        android.util.Log.d("SpotifyAuthManager", "Direct token received: ${accessToken.take(10)}..., expiresIn=$expiresIn")
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
        saveToken(accessToken, expiryTime)
        _accessToken.value = accessToken
        _authState.value = AuthState.AUTHENTICATED
    }

    fun handleAuthorizationCode(code: String) {
        android.util.Log.d("SpotifyAuthManager", "Received authorization code: ${code.take(10)}...")
        
        // Get stored code verifier
        val codeVerifier = sharedPreferences.getString(CODE_VERIFIER_KEY, null)
        if (codeVerifier == null) {
            android.util.Log.e("SpotifyAuthManager", "No code verifier found")
            _authState.value = AuthState.ERROR("Authentication error: missing code verifier")
            return
        }
        
        // Exchange authorization code for access token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                exchangeCodeForToken(code, codeVerifier)
            } catch (e: Exception) {
                android.util.Log.e("SpotifyAuthManager", "Token exchange failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.ERROR("Failed to exchange code for token: ${e.message}")
                }
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String) {
        android.util.Log.d("SpotifyAuthManager", "Exchanging authorization code for access token")
        android.util.Log.d("SpotifyAuthManager", "Code: ${code.take(10)}..., Verifier: ${codeVerifier.take(10)}...")
        android.util.Log.d("SpotifyAuthManager", "Client ID: $CLIENT_ID")
        android.util.Log.d("SpotifyAuthManager", "Redirect URI: $REDIRECT_URI")
        android.util.Log.d("SpotifyAuthManager", "Code verifier length: ${codeVerifier.length}")
        android.util.Log.d("SpotifyAuthManager", "Full authorization code: $code")
        
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code_verifier", codeVerifier)
            .build()

        // Log all the form parameters being sent
        android.util.Log.d("SpotifyAuthManager", "Form parameters:")
        android.util.Log.d("SpotifyAuthManager", "  grant_type: authorization_code")
        android.util.Log.d("SpotifyAuthManager", "  code: ${code.take(10)}...")
        android.util.Log.d("SpotifyAuthManager", "  redirect_uri: $REDIRECT_URI")
        android.util.Log.d("SpotifyAuthManager", "  client_id: $CLIENT_ID")
        android.util.Log.d("SpotifyAuthManager", "  client_secret: [HIDDEN]")
        android.util.Log.d("SpotifyAuthManager", "  code_verifier: ${codeVerifier.take(10)}... (length: ${codeVerifier.length})")

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(formBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .build()

        android.util.Log.d("SpotifyAuthManager", "Making token exchange request to: https://accounts.spotify.com/api/token")
        
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            android.util.Log.d("SpotifyAuthManager", "Token exchange response: ${response.code}")
            android.util.Log.d("SpotifyAuthManager", "Response headers: ${response.headers}")
            android.util.Log.d("SpotifyAuthManager", "Response body: $responseBody")
            
            if (response.isSuccessful && responseBody != null) {
                try {
                    val json = JSONObject(responseBody)
                    val accessToken = json.getString("access_token")
                    val expiresIn = json.getInt("expires_in")
                    val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
                    
                    android.util.Log.d("SpotifyAuthManager", "Successfully exchanged code for token")
                    
                    withContext(Dispatchers.Main) {
                        saveToken(accessToken, expiryTime)
                        _accessToken.value = accessToken
                        _authState.value = AuthState.AUTHENTICATED
                        
                        // Clean up code verifier
                        sharedPreferences.edit().remove(CODE_VERIFIER_KEY).apply()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SpotifyAuthManager", "Failed to parse token response: ${e.message}")
                    withContext(Dispatchers.Main) {
                        _authState.value = AuthState.ERROR("Failed to parse token response: ${e.message}")
                    }
                }
            } else {
                android.util.Log.e("SpotifyAuthManager", "Token exchange failed: ${response.code} - $responseBody")
                
                // Try to parse error details
                var errorMessage = "Token exchange failed: ${response.code}"
                try {
                    if (responseBody != null) {
                        val errorJson = JSONObject(responseBody)
                        val error = errorJson.optString("error", "")
                        val errorDescription = errorJson.optString("error_description", "")
                        if (error.isNotEmpty()) {
                            errorMessage = "Spotify error: $error"
                            if (errorDescription.isNotEmpty()) {
                                errorMessage += " - $errorDescription"
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SpotifyAuthManager", "Could not parse error response: ${e.message}")
                }
                
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.ERROR(errorMessage)
                }
            }
        }
    }

    fun handleWebAuthError(error: String) {
        android.util.Log.e("SpotifyAuthManager", "Web auth error: $error")
        _authState.value = AuthState.ERROR("Authentication failed: $error")
    }

    fun handleAuthResponse(resultCode: Int, response: AuthorizationResponse?) {
        android.util.Log.d("SpotifyAuthManager", "handleAuthResponse called: resultCode=$resultCode, responseType=${response?.type}")
        
        when (response?.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                val expiresIn = response.expiresIn
                val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
                
                android.util.Log.d("SpotifyAuthManager", "Received token: ${token?.take(10)}..., expiresIn: $expiresIn")
                
                if (token != null) {
                    saveToken(token, expiryTime)
                    _accessToken.value = token
                    _authState.value = AuthState.AUTHENTICATED
                } else {
                    _authState.value = AuthState.ERROR("No access token received")
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                val error = response.error ?: "Unknown authentication error"
                android.util.Log.e("SpotifyAuthManager", "Auth error: $error")
                _authState.value = AuthState.ERROR(error)
            }
            null -> {
                android.util.Log.w("SpotifyAuthManager", "Authentication cancelled by user")
                _authState.value = AuthState.ERROR("Authentication cancelled")
            }
            else -> {
                android.util.Log.e("SpotifyAuthManager", "Unknown response type: ${response.type}")
                _authState.value = AuthState.ERROR("Unknown response type")
            }
        }
    }

    fun logout() {
        android.util.Log.d("SpotifyAuthManager", "Logging out")
        clearToken()
        _accessToken.value = null
        _authState.value = AuthState.IDLE
    }

    private fun loadStoredToken() {
        val token = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        val expiry = sharedPreferences.getLong(TOKEN_EXPIRY_KEY, 0)
        
        if (!token.isNullOrEmpty() && System.currentTimeMillis() < expiry) {
            android.util.Log.d("SpotifyAuthManager", "Loaded stored token")
            _accessToken.value = token
            _authState.value = AuthState.AUTHENTICATED
        } else {
            android.util.Log.d("SpotifyAuthManager", "No valid stored token found")
            clearToken()
        }
    }

    private fun saveToken(token: String, expiryTime: Long) {
        sharedPreferences.edit()
            .putString(ACCESS_TOKEN_KEY, token)
            .putLong(TOKEN_EXPIRY_KEY, expiryTime)
            .apply()
        android.util.Log.d("SpotifyAuthManager", "Token saved")
    }

    private fun clearToken() {
        sharedPreferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(TOKEN_EXPIRY_KEY)
            .apply()
    }

    sealed class AuthState {
        object IDLE : AuthState()
        object AUTHENTICATING : AuthState()
        object AUTHENTICATED : AuthState()
        data class ERROR(val message: String) : AuthState()
    }
}
