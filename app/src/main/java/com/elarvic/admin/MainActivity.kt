package com.elarvic.admin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

private data class UserRecord(val id: String, val email: String, val displayName: String, val active: Boolean, val expiry: Date?)

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.firebase_web_client_id))
            .requestEmail().build()
        googleClient = GoogleSignIn.getClient(this, options)
        setContent { MaterialTheme { AdminApp(auth, googleClient, ::launchGoogle) } }
    }

    private fun launchGoogle() = startActivityForResult(googleClient.signInIntent, RC_GOOGLE)

    @Deprecated("Use Activity Result APIs in a later cleanup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE && resultCode == Activity.RESULT_OK) {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).result
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
        }
    }
    companion object { const val RC_GOOGLE = 9002 }
}

@Composable
private fun AdminApp(auth: FirebaseAuth, googleClient: GoogleSignInClient, launchGoogle: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<UserRecord>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = auth.currentUser

    LaunchedEffect(current?.uid) {
        if (current != null) {
            db.collection("admins").document(current.uid).get().addOnSuccessListener { doc ->
                if (!doc.exists()) error = "This Google account is not an authorized admin."
            }
            db.collection("users").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snap, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                users = snap?.documents?.map { d ->
                    UserRecord(d.id, d.getString("email") ?: "", d.getString("displayName") ?: "", d.getBoolean("active") ?: true, d.getTimestamp("expiryAt")?.toDate())
                } ?: emptyList()
            }
        }
    }

    if (current == null) {
        LoginScreen(launchGoogle)
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Elarvic Admin", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { auth.signOut(); googleClient.signOut() }) { Text("Logout") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Users: ${users.size}", style = MaterialTheme.typography.titleMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(users) { user ->
                val expired = user.expiry?.before(Date()) == true
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(user.displayName.ifBlank { user.email }, style = MaterialTheme.typography.titleMedium)
                        Text(user.email)
                        Text(if (expired) "Expired" else if (user.active) "Active" else "Inactive")
                        Text("Expiry: ${user.expiry ?: "Not set"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(onGoogle: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Elarvic Admin", style = MaterialTheme.typography.headlineLarge)
        Text("Authorized administrators only", modifier = Modifier.padding(8.dp))
        Button(onClick = onGoogle, Modifier.fillMaxWidth()) { Text("Continue with Google") }
    }
}
