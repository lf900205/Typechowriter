package com.example.typechowriter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

sealed interface Screen {
    data object Loading : Screen
    data object Config : Screen
    data object Write : Screen
}

@Composable
fun App() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }

    LaunchedEffect(Unit) {
        val url = Settings.baseUrl(context).first()
        val tk = Settings.token(context).first()
        screen = if (url.isNotBlank() && tk.isNotBlank()) Screen.Write else Screen.Config
    }

    when (screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Screen.Config -> ConfigScreen(onSaved = { screen = Screen.Write })
        Screen.Write -> WriteScreen(onOpenConfig = { screen = Screen.Config })
    }
}

@Composable
fun ConfigScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        url = Settings.baseUrl(context).first()
        token = Settings.token(context).first()
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("连接你的 Typecho", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("博客地址，如 https://blog.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("API Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    Settings.save(context, url.trim(), token.trim())
                    onSaved()
                }
            },
            enabled = url.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteScreen(onOpenConfig: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var polishing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    var polishResult by remember { mutableStateOf<PolishResponse?>(null) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        baseUrl = Settings.baseUrl(context).first()
        token = Settings.token(context).first()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploading = true
                message = "上传中..."
                val res = uploadImage(context, uri)
                uploading = false
                if (res?.success == true && res.url != null) {
                    val imgBlock = "\n[jpg]\n![图片](${res.url})\n[/jpg]\n"
                    content = content + imgBlock
                    message = "已插入图片"
                    showImageDialog = false
                } else {
                    message = "上传失败：${res?.error ?: "未知错误"}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("写日志") },
                actions = { TextButton(onClick = onOpenConfig) { Text("设置") } }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !polishing && !sending && !uploading && content.isNotBlank(),
                        onClick = { showStyleDialog = true }
                    ) {
                        Text(if (polishing) "润色中…" else "✨ 润色")
                    }

                    TextButton(
                        enabled = !polishing && !sending && !uploading,
                        onClick = { showImageDialog = true }
                    ) {
                        Text(if (uploading) "上传中…" else "🖼 插图")
                    }

                    Button(
                        enabled = !polishing && !sending && !uploading && title.isNotBlank(),
                        onClick = {
                            scope.launch {
                                sending = true
                                message = ""
                                try {
                                    val api = ApiClient.create(baseUrl)
                                    val res = api.publish(token, title, content, "publish", tags)
                                    if (res.success == true) {
                                        message = "已发布，cid=${res.cid}"
                                        title = ""; content = ""; tags = ""
                                    } else {
                                        message = "失败：${res.error}"
                                    }
                                } catch (e: Exception) {
                                    message = "出错：${e.message}"
                                } finally {
                                    sending = false
                                }
                            }
                        }
                    ) {
                        Text(if (sending) "发布中…" else "发布")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text("正文（Markdown，图片用 [jpg] 包裹）") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showStyleDialog) {
        StyleDialog(
            onDismiss = { showStyleDialog = false },
            onConfirm = { style ->
                showStyleDialog = false
                scope.launch {
                    polishing = true
                    message = "润色中，请稍候..."
                    try {
                        val api = ApiClient.create(baseUrl)
                        val res = api.polish(token, content, style)
                        if (res.success == true) {
                            polishResult = res
                            message = ""
                        } else {
                            message = "润色失败：${res.error}"
                        }
                    } catch (e: Exception) {
                        message = "出错：${e.message}"
                    } finally {
                        polishing = false
                    }
                }
            }
        )
    }

    if (showImageDialog) {
        ImageDialog(
            baseUrl = baseUrl,
            token = token,
            onDismiss = { showImageDialog = false },
            onPickLocal = { imagePicker.launch("image/*") },
            onPickUnsplash = { url ->
                val imgBlock = "\n[jpg]\n![图片]($url)\n[/jpg]\n"
                content = content + imgBlock
                message = "已插入 Unsplash 图片"
                showImageDialog = false
            }
        )
    }

    polishResult?.let { res ->
        AlertDialog(
            onDismissRequest = { polishResult = null },
            title = { Text("润色结果") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("分类：${res.category ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("标题：${res.title ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("标签：${res.tags ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("正文预览：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (res.content ?: "").take(500) +
                                if ((res.content?.length ?: 0) > 500) "..." else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!res.title.isNullOrBlank()) title = res.title
                    if (!res.tags.isNullOrBlank()) tags = res.tags
                    if (!res.content.isNullOrBlank()) content = res.content
                    polishResult = null
                    message = "已采用润色结果"
                }) { Text("采用") }
            },
            dismissButton = {
                TextButton(onClick = { polishResult = null }) { Text("放弃") }
            }
        )
    }
}

// ============================================================
// 润色风格对话框
// ============================================================

data class StyleOption(val key: String, val label: String)

val styleOptions = listOf(
    StyleOption("murakami", "村上春树（细腻隐喻）"),
    StyleOption("yu hua",   "余华（简洁冷峻）"),
    StyleOption("mo yan",   "莫言（魔幻乡土）"),
    StyleOption("none",     "通用润色（不模仿特定作家）")
)

@Composable
fun StyleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selected by remember { mutableStateOf("murakami") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择润色风格") },
        text = {
            Column {
                styleOptions.forEach { opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selected == opt.key),
                                onClick = { selected = opt.key }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selected == opt.key),
                            onClick = { selected = opt.key }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(opt.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("开始润色") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ============================================================
// 插图对话框
// ============================================================

@Composable
fun ImageDialog(
    baseUrl: String,
    token: String,
    onDismiss: () -> Unit,
    onPickLocal: () -> Unit,
    onPickUnsplash: (String) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("本地图片", "Unsplash")

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { idx, t ->
                        Tab(
                            selected = tabIndex == idx,
                            onClick = { tabIndex = idx },
                            text = { Text(t) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                when (tabIndex) {
                    0 -> LocalImageTab(onPickLocal = onPickLocal)
                    1 -> UnsplashTab(
                        baseUrl = baseUrl,
                        token = token,
                        onPick = onPickUnsplash
                    )
                }
            }
        }
    }
}

@Composable
fun LocalImageTab(onPickLocal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("从手机相册选择图片上传到 R2", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPickLocal) { Text("选择本地图片") }
    }
}

@Composable
fun UnsplashTab(
    baseUrl: String,
    token: String,
    onPick: (String) -> Unit
) {
    var subTab by remember { mutableStateOf(0) }
    val subTabs = listOf("搜索", "我的相册")

    Column(Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = subTab) {
            subTabs.forEachIndexed { idx, t ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(t) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (subTab) {
            0 -> UnsplashSearchPane(baseUrl, token, onPick)
            1 -> UnsplashCollectionsPane(baseUrl, token, onPick)
        }
    }
}

@Composable
fun UnsplashSearchPane(
    baseUrl: String,
    token: String,
    onPick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<UnsplashPhoto>() }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("英文关键词，如 nature") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !loading && query.isNotBlank(),
                onClick = {
                    scope.launch {
                        loading = true; error = ""; photos.clear()
                        try {
                            val api = ApiClient.create(baseUrl)
                            val res = api.unsplashSearch(token, query, 1)
                            if (res.success == true) {
                                photos.addAll(res.results ?: emptyList())
                                if (photos.isEmpty()) error = "没有结果"
                            } else error = res.error ?: "搜索失败"
                        } catch (e: Exception) {
                            error = "出错：${e.message}"
                        } finally { loading = false }
                    }
                }
            ) { Text("搜索") }
        }

        if (loading) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PhotoGrid(photos, onPick)
        }
    }
}

@Composable
fun UnsplashCollectionsPane(
    baseUrl: String,
    token: String,
    onPick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selectedCollection by remember { mutableStateOf<UnsplashCollection?>(null) }
    val collections = remember { mutableStateListOf<UnsplashCollection>() }
    val photos = remember { mutableStateListOf<UnsplashPhoto>() }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val api = ApiClient.create(baseUrl)
            val res = api.unsplashCollections(token)
            if (res.success == true) collections.addAll(res.results ?: emptyList())
            else error = res.error ?: "加载失败"
        } catch (e: Exception) {
            error = "出错：${e.message}"
        } finally { loading = false }
    }

    Column(Modifier.fillMaxWidth()) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (selectedCollection == null) {
            if (collections.isEmpty() && !loading && error.isBlank()) {
                Text("该账号下没有相册", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.fillMaxWidth()) {
                collections.forEach { c ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    loading = true; photos.clear()
                                    try {
                                        val api = ApiClient.create(baseUrl)
                                        val res = api.unsplashCollectionPhotos(token, c.id ?: "")
                                        if (res.success == true) {
                                            photos.addAll(res.results ?: emptyList())
                                            selectedCollection = c
                                        } else error = res.error ?: "加载失败"
                                    } catch (e: Exception) {
                                        error = "出错：${e.message}"
                                    } finally { loading = false }
                                }
                            }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title ?: "未命名", modifier = Modifier.weight(1f))
                            Text("${c.total_photos ?: 0} 张", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = {
                selectedCollection = null
                photos.clear()
            }) { Text("← 返回相册列表") }

            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PhotoGrid(photos, onPick)
            }
        }
    }
}

@Composable
fun PhotoGrid(
    photos: List<UnsplashPhoto>,
    onPick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(380.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos) { p ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val url = p.regular ?: p.small ?: p.thumb ?: return@clickable
                        onPick(url)
                    }
            ) {
                Column {
                    AsyncImage(
                        model = p.thumb ?: "",
                        contentDescription = p.alt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Text(
                        text = p.author ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}