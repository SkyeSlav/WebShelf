package dev.webshelf.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { WebShelfTheme { WebShelfRoot() } }
    }
}

@Composable
private fun WebShelfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebShelfRoot() {
    val context = LocalContext.current
    val db = remember { LibraryDatabase.get(context) }
    val importer = remember { ImportManager(context) }
    val apps by db.webAppDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { snackbarHostState.showSnackbar(it); message = null } }
    var selected by remember { mutableStateOf<WebApp?>(null) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var showAdd by remember { mutableStateOf(false) }
    var showOnline by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf<WebApp?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch {
                runCatching {
                    val name = queryDisplayName(context, uri) ?: "Imported.html"
                    importer.importFile(uri, name, "Uncategorized").also { db.webAppDao().upsert(it) }
                }.onSuccess { message = "Added ${it.name}" }
                    .onFailure { message = it.message ?: "Import failed" }
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch {
                runCatching {
                    val name = DocumentName.from(context, uri)
                    importer.importFolder(uri, name, "Uncategorized").also { db.webAppDao().upsert(it) }
                }.onSuccess { message = "Added ${it.name}" }
                    .onFailure { message = it.message ?: "Folder import failed" }
            }
        }
    }

    BackHandler(enabled = selected != null) { selected = null }
    BackHandler(enabled = showAbout) { showAbout = false }
    BackHandler(enabled = showSettings && !showAbout) { showSettings = false }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
    } else if (showSettings) {
        SettingsScreen(appCount = apps.size, onBack = { showSettings = false },
            onAbout = { showAbout = true })
    } else if (selected != null) {
        AppRunnerScreen(app = selected!!, importer = importer, onBack = { selected = null })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("WebShelf", fontWeight = FontWeight.Bold)
                            Text("${apps.size} apps in your library", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add app")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add to library")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your library") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                val categories = listOf("All") + apps.map { it.category }.distinct().sorted()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item },
                            label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                val visible = apps.filter {
                    (category == "All" || it.category == category) &&
                    (it.name.contains(search, true) || it.category.contains(search, true))
                }
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Widgets, null, Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(if (apps.isEmpty()) "Your shelf is empty" else "No matching apps",
                                style = MaterialTheme.typography.titleMedium)
                            Text(if (apps.isEmpty()) "Import HTML, a project folder, a ZIP, or add a URL."
                                else "Try another search or category.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (apps.isEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { showAdd = true }) { Text("Add your first app") }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visible, key = { it.id }) { app ->
                            AppCard(app, onClick = { selected = app }, onLongClick = { showRename = app })
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add to WebShelf") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import local content or add an online web app.")
                    OutlinedButton(onClick = {
                        showAdd = false
                        filePicker.launch(arrayOf("text/html", "application/zip", "application/x-zip-compressed", "*/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(8.dp))
                        Text("HTML file or ZIP archive")
                    }
                    OutlinedButton(onClick = {
                        showAdd = false
                        folderPicker.launch(null)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp))
                        Text("Project folder")
                    }
                    OutlinedButton(onClick = { showAdd = false; showOnline = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp))
                        Text("Online web app")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close") } }
        )
    }

    if (showOnline) {
        OnlineAppDialog(onDismiss = { showOnline = false }) { name, url, cat ->
            scope.launch {
                db.webAppDao().upsert(WebApp(java.util.UUID.randomUUID().toString(), name, cat,
                    "ONLINE", normalizeUrl(url)))
                showOnline = false
                message = "Added $name"
            }
        }
    }

    showRename?.let { app ->
        EditAppDialog(app, onDismiss = { showRename = null }, onSave = { name, cat ->
            scope.launch { db.webAppDao().upsert(app.copy(name = name, category = cat)); showRename = null }
        }, onDelete = {
            scope.launch {
                db.webAppDao().delete(app)
                importer.deleteLocal(app)
                showRename = null
                message = "Removed ${app.name}"
            }
        })
    }
}

@Composable
private fun AppCard(app: WebApp, onClick: () -> Unit, onLongClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.height(150.dp)) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (app.kind == "LOCAL") Icons.Default.Code else Icons.Default.Language,
                            null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(app.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.category, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onLongClick, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                Icon(Icons.Default.MoreVert, "App options", Modifier.size(18.dp))
            }
            Text(if (app.kind == "LOCAL") "OFFLINE" else "ONLINE",
                modifier = Modifier.align(Alignment.BottomEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OnlineAppDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var category by remember { mutableStateOf("Online") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add online web app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("App name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Website URL") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && url.startsWith("http"),
                onClick = { onAdd(name.trim(), url.trim(), category.trim()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditAppDialog(
    app: WebApp, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit, onDelete: () -> Unit
) {
    var name by remember(app.id) { mutableStateOf(app.name) }
    var category by remember(app.id) { mutableStateOf(app.category) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), category.trim()) },
            enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(appCount: Int, onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    var storageText by remember { mutableStateOf("Calculating…") }
    LaunchedEffect(Unit) {
        storageText = formatStorageUsed(File(context.filesDir, "webapps"))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Library", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("$appCount apps in your library") },
                supportingContent = { Text(storageText) },
                leadingContent = { Icon(Icons.Default.Widgets, null) }
            )
            Spacer(Modifier.height(12.dp))
            Text("Appearance", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text("Follows your system setting") },
                leadingContent = { Icon(Icons.Default.DarkMode, null) }
            )
            Spacer(Modifier.height(12.dp))
            Text("About", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("About WebShelf") },
                supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Default.Info, null) },
                modifier = Modifier.clickable { onAbout() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About WebShelf") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F766E),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "WebShelf logo",
                        modifier = Modifier.size(72.dp),
                        tint = Color.Unspecified
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("WebShelf", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                "A local-first library for your web apps and offline HTML projects. " +
                    "Import a website by URL, or a standalone HTML file, a ZIP archive, " +
                    "or a whole project folder, and launch everything from one shelf.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/SkyeSlav/WebShelf")))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Code, null)
                Spacer(Modifier.width(8.dp))
                Text("Source code on GitHub")
            }
            Spacer(Modifier.height(16.dp))
            Text("MIT License", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("WebShelf is open source. The logo is an original design.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatStorageUsed(root: File): String {
    val bytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    return when {
        bytes < 1024 -> "$bytes B stored"
        bytes < 1024 * 1024 -> "%.1f KB stored".format(bytes / 1024.0)
        else -> "%.1f MB stored".format(bytes / 1024.0 / 1024.0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRunnerScreen(app: WebApp, importer: ImportManager, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(app.name) }
    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                    IconButton(onClick = { webView?.let {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.url ?: app.location))
                        context.startActivity(intent)
                    } }) { Icon(Icons.Default.OpenInBrowser, "Open externally") }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            return if (app.kind == "ONLINE" &&
                                (uri.scheme == "http" || uri.scheme == "https")) false
                            else if (app.kind == "LOCAL" && uri.host == "app-${app.id}.local") false
                            else true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, pageTitle: String?) {
                            if (!pageTitle.isNullOrBlank()) title = pageTitle
                        }
                    }
                    if (app.kind == "LOCAL") {
                        val appDir = importer.localDirectory(app)
                        val loader = WebViewAssetLoader.Builder()
                            .setDomain("app-${app.id}.local")
                            .addPathHandler("/appassets/", WebViewAssetLoader.InternalStoragePathHandler(ctx, appDir))
                            .build()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                                loader.shouldInterceptRequest(request?.url ?: Uri.EMPTY)
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                return uri.host != "app-${app.id}.local"
                            }
                        }
                        loadUrl("https://app-${app.id}.local/appassets/${encodePath(app.location)}")
                    } else {
                        loadUrl(app.location)
                    }
                }
            },
            update = { }
        )
    }
}

private fun encodePath(path: String): String =
    path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

private fun normalizeUrl(value: String): String =
    if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment
}

private object DocumentName {
    fun from(context: android.content.Context, uri: Uri): String =
        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: "Imported project"
}
