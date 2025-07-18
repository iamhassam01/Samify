package com.samify.music.spotify

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SpotifyAuthActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: SpotifyAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the auth manager with this activity
        authManager.registerActivityResultLauncher(this)
        
        // Start authentication
        authManager.authenticate(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 1337) { // REQUEST_CODE from SpotifyAuthManager
            val response = AuthorizationClient.getResponse(resultCode, data)
            authManager.handleAuthResponse(resultCode, response)
            finish()
        }
    }
}
