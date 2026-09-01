package com.elarvic.admin

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.security.SecureRandom
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private data class AccessKey(val value: String, val days: Long, val active: Boolean, val expiresAt: Date?)

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.firebase_web_client_id))
            .requestEmail()
            .build()
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

    companion object { private const val RC_GOOGLE = 9002 }
}

@Composable
private fun AdminApp(auth: FirebaseAuth, googleClient: GoogleSignInClient, launchGoogle: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    var authorized by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var keys by remember { mutableStateOf<List<AccessKey>>(emptyList()) }
    var selectedDays by remember { mutableStateOf(3L) }
    var generatedKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(firebaseUser?.uid) {
        checking = true
        authorized = false
        val uid = firebaseUser?.uid
        if (uid == null) {
            checking = false
            return@LaunchedEffect
        }
        db.collection("admins").document(uid).get()
            .addOnSuccessListener { doc ->
                authorized = doc.exists() && (doc.getBoolean("active") ?: true)
                checking = false
                if (!authorized) error = "This Google account is not an authorized admin."
            }
            .addOnFailureListener {
                checking = false
                error = it.message ?: "Unable to verify admin access."
            }
    }

    LaunchedEffect(authorized) {
        if (!authorized) return@LaunchedEffect
        db.collection("keys").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { error = e.message; return@addSnapshotListener }
                keys = snapshot?.documents?.map { d ->
                    AccessKey(d.id, d.getLong("durationDays") ?: 0, d.getBoolean("active") ?: false, d.getTimestamp("expiresAt")?.toDate())
                } ?: emptyList()
            }
    }

    if (firebaseUser == null) {
        LoginScreen(launchGoogle, error)
        return
    }
    if (checking) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!authorized) {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Access denied", style = MaterialTheme.typography.headlineSmall)
            Text(error ?: "Your account is not an authorized administrator.", modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { auth.signOut(); googleClient.signOut(); error = null }) { Text("Use another account") }
        }
        return
    }

    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("ELARVIC", style = MaterialTheme.typography.headlineSmall); Text("Admin") }
            TextButton(onClick = { auth.signOut(); googleClient.signOut() }) { Text("Logout") }
        }
        Spacer(Modifier.height(18.dp))
        Text("Generate access key", style = MaterialTheme.typography.titleLarge)
        Text("Choose how long the key remains valid.", modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3L, 6L, 15L).forEach { days ->
                FilterChip(selected = selectedDays == days, onClick = { selectedDays = days }, label = { Text("${days} days") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val value = generateKey()
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, selectedDays.toInt()) }
            val data = hashMapOf<String, Any>(
                "active" to true,
                "durationDays" to selectedDays,
                "createdAt" to Timestamp.now(),
                "expiresAt" to Timestamp(calendar.time),
                "createdBy" to auth.currentUser!!.uid
            )
            db.collection("keys").document(value).set(data)
                .addOnSuccessListener { generatedKey = value; error = null }
                .addOnFailureListener { error = it.message ?: "Could not create key." }
        }, modifier = Modifier.fillMaxWidth()) { Text("Generate Elarvic Key") }

        generatedKey?.let { value ->
            Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("New key", style = MaterialTheme.typography.labelLarge)
                    Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Elarvic key", value))
                    }) { Text("Copy key") }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Issued keys", style = MaterialTheme.typography.titleLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(keys) { key ->
                val expired = key.expiresAt?.before(Date()) == true
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(key.value, style = MaterialTheme.typography.titleMedium)
                        Text("${key.days} days · ${if (expired) "Expired" else if (key.active) "Active" else "Revoked"}")
                        Text("Expires: ${key.expiresAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "—"}")
                        if (key.active) {
                            TextButton(onClick = { db.collection("keys").document(key.value).update("active", false) }) { Text("Revoke") }
                        }
                    }
                }
            }
        }
    }
}

private fun generateKey(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return buildString {
        append("ELARVIC_")
        repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

@Composable
private fun LoginScreen(onGoogle: () -> Unit, error: String?) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.elarvic_mark), contentDescription = "Elarvic logo", modifier = Modifier.size(110.dp))
        Spacer(Modifier.height(8.dp))
        Text("ELARVIC", style = MaterialTheme.typography.headlineLarge)
        Text("Admin Panel", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        Text("Google sign-in is required for administrators.", modifier = Modifier.padding(vertical = 12.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp)) }
        Button(onClick = onGoogle, Modifier.fillMaxWidth()) { Text("Continue with Google") }
    }
}
