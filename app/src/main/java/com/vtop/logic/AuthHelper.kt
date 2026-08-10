package com.vtop.logic

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

object AuthHelper {
    fun getGoogleSignInClient(context: Context, webClientId: String): GoogleSignInClient {

        // PASTE YOUR EXACT CLIENT ID HERE FOR NOW
        val hardcodedClientId = "1006105677727-utmqc9263kj1cfmupvpk2tv6vi8nus10.apps.googleusercontent.com"


        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .requestIdToken(hardcodedClientId)
            .build()

        return GoogleSignIn.getClient(context, gso)
    }
}