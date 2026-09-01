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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

private val ElarvicBlack = Color(0xFF050505)
private val ElarvicSurface = Color(0xFF111111)
private val ElarvicSilver = Color(0xFFE7E7E7)
private val ElarvicMuted = Color(0xFF9A9A9A)

private data class AccessKey(val value: String, val days: Long, val active: Boolean, val expiresAt: Date?, val createdAt: Date?)

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(this, options)
        setContent { ElarvicAdminTheme { AdminApp(auth, googleClient, ::launchGoogle) } }
    }

    private fun launchGoogle() = startActivityForResult(googleClient.signInIntent, RC_GOOGLE)

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE || resultCode != Activity.RESULT_OK) return
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).result ?: return
        val token = account.idToken ?: return
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
    }

    companion object { private const val RC_GOOGLE = 9002 }
}

@Composable
private fun ElarvicAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ElarvicSilver,
            onPrimary = Color.Black,
            secondary = Color(0xFFBDBDBD),
            background = ElarvicBlack,
            surface = ElarvicSurface,
            onBackground = ElarvicSilver,
            onSurface = ElarvicSilver,
            outline = Color(0xFF404040)
        ),
        content = content
    )
}

@Composable
private fun AdminApp(auth: FirebaseAuth, googleClient: GoogleSignInClient, launchGoogle: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    var authorized by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var keys by remember { mutableStateOf<List<AccessKey>>(emptyList()) }
    var selectedDays by remember { mutableStateOf(3L) }
    var generatedKey by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(firebaseUser?.uid) {
        checking = true
        authorized = false
        error = null
        val uid = firebaseUser?.uid
        if (uid == null) {
            checking = false
            return@LaunchedEffect
        }
        db.collection("admins").document(uid).get()
            .addOnSuccessListener { doc ->
                val active = doc.getBoolean("active") ?: false
                val role = doc.getString("role")
                authorized = doc.exists() && active && (role == "admin" || role == null)
                checking = false
                if (!authorized) {
                    error = "This Google account is not an active Elarvic administrator."
                }
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
                if (e != null) {
                    error = "Key list error: ${e.message ?: "Permission denied"}"
                    return@addSnapshotListener
                }
                keys = snapshot?.documents?.map { d ->
                    AccessKey(
                        d.id,
                        d.getLong("durationDays") ?: 0,
                        d.getBoolean("active") ?: false,
                        d.getTimestamp("expiresAt")?.toDate(),
                        d.getTimestamp("createdAt")?.toDate()
                    )
                } ?: emptyList()
            }
    }

    fun signOut() {
        auth.signOut()
        googleClient.signOut()
        generatedKey = null
        error = null
    }

    if (firebaseUser == null) {
        AdminLoginScreen(launchGoogle, error)
        return
    }
    if (checking) {
        LoadingScreen()
        return
    }
    if (!authorized) {
        AccessDeniedScreen(error, ::signOut)
        return
    }

    val filtered = keys.filter { query.isBlank() || it.value.contains(query.trim(), ignoreCase = true) }
    val activeCount = keys.count { it.active && it.expiresAt?.after(Date()) == true }
    val expiredCount = keys.count { it.expiresAt?.before(Date()) == true }

    Scaffold(containerColor = ElarvicBlack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminHeader(::signOut) }
            item { StatsRow(keys.size, activeCount, expiredCount) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElarvicSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Generate access key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Keys start with ELARVIC_ and expire automatically.",
                            color = ElarvicMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3L, 6L, 15L).forEach { days ->
                                FilterChip(
                                    selected = selectedDays == days,
                                    onClick = { selectedDays = days },
                                    label = { Text("${days} days") }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !generating,
                            onClick = {
                                generating = true
                                val value = generateKey()
                                val calendar = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, selectedDays.toInt())
                                }
                                val data = hashMapOf<String, Any>(
                                    "active" to true,
                                    "durationDays" to selectedDays,
                                    "createdAt" to Timestamp.now(),
                                    "expiresAt" to Timestamp(calendar.time),
                                    "createdBy" to auth.currentUser!!.uid
                                )
                                db.collection("keys").document(value).set(data)
                                    .addOnSuccessListener {
                                        generatedKey = value
                                        generating = false
                                        error = null
                                    }
                                    .addOnFailureListener {
                                        generating = false
                                        error = it.message ?: "Could not create key."
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (generating) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Key, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Elarvic Key")
                            }
                        }
                    }
                }
            }
            generatedKey?.let { value ->
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Key created", color = ElarvicMuted)
                            Text(
                                value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Elarvic key", value))
                            }) {
                                Icon(Icons.Default.ContentCopy, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Copy key")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search keys") }
                )
            }
            item {
                Text(
                    "Issued keys",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (filtered.isEmpty()) {
                item { EmptyState() }
            } else {
                items(filtered, key = { it.value }) { key ->
                    KeyCard(key) {
                        db.collection("keys").document(key.value).update("active", false)
                            .addOnFailureListener { error = it.message ?: "Could not revoke key." }
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AdminHeader(onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.elarvic_mark), "Elarvic logo", Modifier.size(52.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("ELARVIC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("ADMIN PANEL · V1", color = ElarvicMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Logout") }
    }
}

@Composable
private fun StatsRow(total: Int, active: Int, expired: Int) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        StatCard("Total", total.toString(), Modifier.weight(1f))
        StatCard("Active", active.toString(), Modifier.weight(1f))
        StatCard("Expired", expired.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ElarvicSurface)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = ElarvicMuted, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KeyCard(key: AccessKey, onRevoke: () -> Unit) {
    val expired = key.expiresAt?.before(Date()) == true
    val status = when {
        expired -> "Expired"
        key.active -> "Active"
        else -> "Revoked"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = ElarvicSurface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(key.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    status,
                    color = if (key.active && !expired) ElarvicSilver else ElarvicMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text("Duration: ${key.days} days", color = ElarvicMuted)
            Text("Expires: ${key.expiresAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "—"}", color = ElarvicMuted)
            if (key.active && !expired) TextButton(onClick = onRevoke) { Text("Revoke key") }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElarvicSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.KeyOff, null, tint = ElarvicMuted, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("No keys found", color = ElarvicMuted)
        }
    }
}

@Composable
private fun AdminLoginScreen(onGoogle: () -> Unit, error: String?) {
    Column(
        Modifier.fillMaxSize().background(ElarvicBlack).padding(24.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.elarvic_mark), "Elarvic logo", Modifier.size(135.dp))
        Spacer(Modifier.height(14.dp))
        Text("ELARVIC", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("ADMIN PANEL", style = MaterialTheme.typography.titleMedium, color = ElarvicMuted)
        Text("Administrator access", color = ElarvicMuted, modifier = Modifier.padding(top = 10.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(22.dp))
        Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccountCircle, null)
            Spacer(Modifier.width(8.dp))
            Text("Continue with Google")
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(ElarvicBlack), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.elarvic_mark), null, Modifier.size(90.dp))
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = ElarvicSilver, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text("Verifying administrator…", color = ElarvicMuted)
        }
    }
}

@Composable
private fun AccessDeniedScreen(error: String?, onUseAnother: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(ElarvicBlack).padding(24.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, null, tint = ElarvicSilver, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text("Access denied", style = MaterialTheme.typography.headlineSmall)
        Text(
            error ?: "Your Google account is not authorized.",
            color = ElarvicMuted,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = onUseAnother) { Text("Use another account") }
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
