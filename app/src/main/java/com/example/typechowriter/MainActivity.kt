package com.example.typechowriter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

fun formatDraftTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatPostDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp * 1000L))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
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
    var uploadCurrent by remember { mutableStateOf(0) }
    var uploadTotal by remember { mutableStateOf(0) }
    var showProgressBar by remember { mutableStateOf(false) }

    var draftSavedAt by remember { mutableStateOf(0L) }

    val categories = remember { mutableStateListOf<Category>() }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    var editingCid by remember { mutableStateOf(0) }
    var showPostList by remember { mutableStateOf(false) }

    var polishResult by remember { mutableStateOf<PolishResponse?>(null) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }

    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(Unit) {
        baseUrl = Settings.baseUrl(context).first()
        token = Settings.token(context).first()
        title = Settings.draftTitle(context).first()
        content = Settings.draftContent(context).first()
        tags = Settings.draftTags(context).first()
        draftSavedAt = Settings.draftSavedAt(context).first()
        if (title.isNotBlank() || content.isNotBlank()) {
            message = "已恢复上次未完成的草稿"
        }

        if (baseUrl.isNotBlank() && token.isNotBlank()) {
            try {
                val api = ApiClient.create(baseUrl)
                val res = api.getCategories(token)
                if (res.success == true) {
                    val list = res.results ?: emptyList()
                    categories.clear()
                    categories.addAll(list)
                    if (list.isNotEmpty() && selectedCategory == null) {
                        selectedCategory = list.first()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(title, content, tags) }
            .debounce(800)
            .distinctUntilChanged()
            .collect { (t, c, tg) ->
                if (t.isNotBlank() || c.isNotBlank()) {
                    Settings.saveDraft(context, t, c, tg)
                    draftSavedAt = System.currentTimeMillis()
                }
            }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            showImageDialog = false
            scope.launch {
                uploading = true
                uploadTotal = uris.size
                uploadCurrent = 0
                showProgressBar = true

                val imgLines = mutableListOf<String>()
                var successCount = 0
                var failCount = 0

                uris.forEachIndexed { index, uri ->
                    uploadCurrent = index + 1
                    val res = uploadImage(context, uri)
                    if (res?.success == true && res.url != null) {
                        imgLines.add("![图${index + 1}](${res.url})")
                        successCount++
                    } else {
                        failCount++
                    }
                }

                uploading = false
                showProgressBar = false

                if (imgLines.isNotEmpty()) {
                    val imgBlock = "\n[jpg]\n" + imgLines.joinToString("\n") + "\n[/jpg]\n"
                    content += imgBlock
                    message = if (failCount == 0) {
                        "已插入 $successCount 张图片"
                    } else {
                        "已插入 $successCount 张，失败 $failCount 张"
                    }
                } else {
                    message = "上传失败，$failCount 张"
                }

                delay(3000)
                if (!uploading) message = ""
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!imeVisible) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (editingCid > 0) "编辑文章" else "写日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (draftSavedAt > 0L && !uploading && editingCid == 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "草稿 ${formatDraftTime(draftSavedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                    },
                    actions = {
                        if (editingCid > 0) {
                            TextButton(onClick = {
                                scope.launch {
                                    editingCid = 0
                                    title = ""; content = ""; tags = ""
                                    selectedCategory = categories.firstOrNull()
                                    Settings.clearDraft(context)
                                    draftSavedAt = 0L
                                    message = "已取消编辑"
                                }
                            }) {
                                Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            TextButton(onClick = { showPostList = true }) {
                                Text("文章", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { showCategoryDialog = true }) {
                            Text(
                                selectedCategory?.name ?: "分类",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (!imeVisible) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = !polishing && !sending && !uploading && content.isNotBlank(),
                                onClick = {
                                    message = ""
                                    showStyleDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (polishing) "润色中" else "润色", fontSize = 15.sp)
                            }

                            TextButton(
                                enabled = !polishing && !sending && !uploading,
                                onClick = {
                                    message = ""
                                    showImageDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (uploading) "上传中" else "插图", fontSize = 15.sp)
                            }

                            Button(
                                enabled = !polishing && !sending && !uploading
                                        && (title.isNotBlank() || content.isNotBlank()),
                                onClick = {
                                    scope.launch {
                                        sending = true
                                        message = ""

                                        val finalTitle = if (title.isNotBlank()) {
                                            title.trim()
                                        } else {
                                            content.trim()
                                                .replace("\n", " ")
                                                .take(20)
                                                .ifBlank { "无标题" }
                                        }

                                        try {
                                            val api = ApiClient.create(baseUrl)
                                            val res = api.publish(
                                                token, finalTitle, content,
                                                "publish", tags, "",
                                                selectedCategory?.mid ?: 0,
                                                editingCid
                                            )
                                            if (res.success == true) {
                                                message = if (editingCid > 0) {
                                                    "已更新，cid=${res.cid}"
                                                } else {
                                                    "已发布，cid=${res.cid}"
                                                }
                                                Settings.clearDraft(context)
                                                title = ""; content = ""; tags = ""
                                                draftSavedAt = 0L
                                                editingCid = 0
                                            } else {
                                                message = "失败：${res.error}"
                                            }
                                        } catch (e: Exception) {
                                            message = "出错：${e.message}"
                                        } finally {
                                            sending = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (sending) {
                                        if (editingCid > 0) "更新中" else "发布中"
                                    } else {
                                        if (editingCid > 0) "更新" else "发布"
                                    },
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (message.isNotEmpty() && !message.contains("草稿")) message = ""
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                "标题",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                        inner()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = content,
                onValueChange = {
                    content = it
                    if (message.isNotEmpty() && !message.contains("草稿")) message = ""
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                decorationBox = { inner ->
                    Box {
                        if (content.isEmpty()) {
                            Text(
                                "开始写点什么……",
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                        inner()
                    }
                }
            )

            if (showProgressBar) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "正在上传 $uploadCurrent/$uploadTotal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (uploadTotal > 0) uploadCurrent.toFloat() / uploadTotal.toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (message.isNotBlank() && !showProgressBar) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (message.startsWith("出错") ||
                        message.contains("Token") ||
                        message.contains("失败")) {
                        Spacer(Modifier.width(12.dp))
                        TextButton(
                            onClick = onOpenConfig,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("重新配置", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showPostList) {
        PostListDialog(
            baseUrl = baseUrl,
            token = token,
            onDismiss = { showPostList = false },
            onSelect = { cid ->
                showPostList = false
                scope.launch {
                    try {
                        val api = ApiClient.create(baseUrl)
                        val res = api.getPost(token, cid)
                        if (res.success == true) {
                            editingCid = cid
                            title = res.title ?: ""
                            content = res.text ?: ""
                            tags = res.tags ?: ""
                            val catMid = res.category ?: 0
                            selectedCategory = categories.firstOrNull { it.mid == catMid }
                                ?: categories.firstOrNull()
                            Settings.clearDraft(context)
                            draftSavedAt = 0L
                            message = "正在编辑：${res.title ?: ""}"
                        } else {
                            message = "加载失败：${res.error}"
                        }
                    } catch (e: Exception) {
                        message = "出错：${e.message}"
                    }
                }
            }
        )
    }

    if (showCategoryDialog) {
        CategoryDialog(
            categories = categories,
            selected = selectedCategory,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { c ->
                selectedCategory = c
                showCategoryDialog = false
            }
        )
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
            onPickUnsplashMultiple = { photos ->
                if (photos.isNotEmpty()) {
                    // 1. 图片（alt 里塞纯文本署名，悬停显示用）
                    val imgLines = photos.mapIndexed { i, p ->
                        val url = p.regular ?: p.small ?: p.thumb ?: ""
                        val author = p.author ?: "Unknown"
                        "![图${i + 1} · Photo by $author on Unsplash]($url)"
                    }
                    val imgBlock = "\n[jpg]\n" + imgLines.joinToString("\n") + "\n[/jpg]\n"

                    // 2. 署名行直接用 HTML（作者去重）
                    val seenAuthors = mutableSetOf<String>()
                    val creditNames = mutableListOf<String>()
                    photos.forEach { p ->
                        val author = p.author ?: "Unknown"
                        if (seenAuthors.add(author)) {
                            val authorLink = p.authorLink
                                ?: "https://unsplash.com/?utm_source=TypechoWriter&utm_medium=referral"
                            creditNames.add("<a href=\"$authorLink\" target=\"_blank\" rel=\"noopener\">$author</a>")
                        }
                    }
                    val prefix = if (creditNames.size > 1) "Photos by " else "Photo by "
                    val creditLine = "\n<p style=\"font-size:13px;color:#666;margin-top:12px;\">" +
                            prefix + creditNames.joinToString(", ") +
                            " on <a href=\"https://unsplash.com/?utm_source=TypechoWriter&utm_medium=referral\" " +
                            "target=\"_blank\" rel=\"noopener\">Unsplash</a></p>\n"
                    content += imgBlock + creditLine
                    message = "已插入 ${photos.size} 张图片"

                    // 3. 静默上报 Unsplash
                    val idsToTrack = photos.mapNotNull { it.id }
                    scope.launch {
                        val api = ApiClient.create(baseUrl)
                        idsToTrack.forEach { id ->
                            try { api.unsplashTrackDownload(token, id) } catch (_: Exception) {}
                        }
                    }
                }
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
// 文章列表对话框（每行：编辑 / 删除）
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListDialog(
    baseUrl: String,
    token: String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<Post>() }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var deletingPost by remember { mutableStateOf<Post?>(null) }
    var deleteMessage by remember { mutableStateOf("") }

    suspend fun doLoad(pageNum: Int, append: Boolean) {
        try {
            val api = ApiClient.create(baseUrl)
            val res = api.listPosts(token, pageNum)
            if (res.success == true) {
                val list = res.results ?: emptyList()
                if (append) {
                    posts.addAll(list)
                } else {
                    posts.clear()
                    posts.addAll(list)
                }
                hasMore = list.size >= 20
            } else {
                error = res.error ?: "加载失败"
            }
        } catch (e: Exception) {
            error = "出错：${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        doLoad(1, false)
        loading = false
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (hasMore && !loading && !loadingMore && posts.isNotEmpty()) {
                    scope.launch {
                        loadingMore = true
                        val nextPage = page + 1
                        doLoad(nextPage, true)
                        page = nextPage
                        loadingMore = false
                    }
                }
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "我的文章",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                if (deleteMessage.isNotBlank()) {
                    Text(
                        deleteMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                when {
                    loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    error.isNotBlank() && posts.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }

                    posts.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有已发布的文章",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(posts) { post ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        post.title ?: "无标题",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!post.category.isNullOrBlank()) {
                                            Text(
                                                post.category,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Text(
                                            formatPostDate(post.created ?: 0L),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { onSelect(post.cid ?: 0) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("编辑", fontSize = 14.sp)
                                }
                                TextButton(
                                    onClick = { deletingPost = post },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("删除", fontSize = 14.sp)
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        if (!hasMore && posts.isNotEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "没有更多了",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { deletingPost = null },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除这篇文章吗？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        post.title ?: "无标题",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "删除后无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cid = post.cid ?: 0
                        deletingPost = null
                        if (cid <= 0) return@TextButton
                        scope.launch {
                            try {
                                val api = ApiClient.create(baseUrl)
                                val res = api.deletePost(token, cid)
                                if (res.success == true) {
                                    posts.removeAll { it.cid == cid }
                                    deleteMessage = "已删除：${post.title ?: ""}"
                                } else {
                                    deleteMessage = "删除失败：${res.error ?: "未知错误"}"
                                }
                            } catch (e: Exception) {
                                deleteMessage = "出错：${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPost = null }) { Text("取消") }
            }
        )
    }
}

// ============================================================
// 分类选择对话框
// ============================================================

@Composable
fun CategoryDialog(
    categories: List<Category>,
    selected: Category?,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit
) {
    var pending by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分类") },
        text = {
            if (categories.isEmpty()) {
                Text("加载中…或没有分类", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    categories.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (pending?.mid == c.mid),
                                    onClick = { pending = c }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (pending?.mid == c.mid),
                                onClick = { pending = c }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(c.name ?: "未命名")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pending != null,
                onClick = { pending?.let { onConfirm(it) } }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
    onPickUnsplashMultiple: (List<UnsplashPhoto>) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("本地图片", "搜索图片", "我的相册")
    val selectedPhotos = remember { mutableStateListOf<UnsplashPhoto>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.82f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选择图片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    tabs.forEachIndexed { idx, t ->
                        val interaction = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null
                                ) { tabIndex = idx }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                t,
                                fontSize = 15.sp,
                                fontWeight = if (tabIndex == idx) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (tabIndex == idx) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .width(if (tabIndex == idx) 18.dp else 0.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (tabIndex) {
                        0 -> LocalImageTab(onPickLocal = onPickLocal)
                        1 -> UnsplashSearchPane(
                            baseUrl = baseUrl,
                            token = token,
                            selectedPhotos = selectedPhotos
                        )
                        2 -> UnsplashCollectionsPane(
                            baseUrl = baseUrl,
                            token = token,
                            selectedPhotos = selectedPhotos
                        )
                    }
                }

                if (selectedPhotos.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选 ${selectedPhotos.size} 张",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selectedPhotos.clear() }) {
                            Text("清空")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onPickUnsplashMultiple(selectedPhotos.toList()) }) {
                            Text("插入")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalImageTab(onPickLocal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "从手机相册选择图片上传到 R2",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "可多选，按选择顺序自动编号",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPickLocal) { Text("选择图片") }
    }
}

// ============================================================
// 搜索图片面板
// ============================================================

@Composable
fun UnsplashSearchPane(
    baseUrl: String,
    token: String,
    selectedPhotos: MutableList<UnsplashPhoto>
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<UnsplashPhoto>() }
    val gridState = rememberLazyGridState()

    suspend fun doSearch(keyword: String) {
        loading = true
        error = ""
        photos.clear()
        page = 1
        hasMore = true
        try {
            val api = ApiClient.create(baseUrl)
            val res = api.unsplashSearch(token, keyword, 1)
            if (res.success == true) {
                val list = res.results ?: emptyList()
                photos.addAll(list)
                hasMore = list.size >= 12
            } else {
                error = res.error ?: "搜索失败"
            }
        } catch (e: Exception) {
            error = "出错：${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun loadMore() {
        if (loadingMore || !hasMore || loading || query.isBlank()) return
        loadingMore = true
        try {
            val api = ApiClient.create(baseUrl)
            val nextPage = page + 1
            val res = api.unsplashSearch(token, query, nextPage)
            if (res.success == true) {
                val list = res.results ?: emptyList()
                photos.addAll(list)
                page = nextPage
                hasMore = list.size >= 12
            } else error = res.error ?: "加载失败"
        } catch (e: Exception) {
            error = "出错：${e.message}"
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(500)
            .distinctUntilChanged()
            .collect { q ->
                if (q.isNotBlank()) {
                    doSearch(q)
                } else {
                    photos.clear()
                    error = ""
                }
            }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (hasMore && !loading && !loadingMore && photos.isNotEmpty()) {
                    scope.launch { loadMore() }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(44.dp)
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "搜索图片，支持中文",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (photos.isNotEmpty()) {
            MultiSelectPhotoGrid(
                photos = photos,
                selectedPhotos = selectedPhotos,
                showAuthor = true,
                gridState = gridState,
                modifier = Modifier.weight(1f)
            )
            BottomStatus(loadingMore = loadingMore, hasMore = hasMore)
        } else if (!loading && query.isBlank()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "输入关键词搜索图片（支持中文）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ============================================================
// 我的相册面板
// ============================================================

@Composable
fun UnsplashCollectionsPane(
    baseUrl: String,
    token: String,
    selectedPhotos: MutableList<UnsplashPhoto>
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedCollection by remember { mutableStateOf<UnsplashCollection?>(null) }
    val collections = remember { mutableStateListOf<UnsplashCollection>() }
    val photos = remember { mutableStateListOf<UnsplashPhoto>() }
    val gridState = rememberLazyGridState()

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

    LaunchedEffect(gridState, selectedCollection?.id) {
        if (selectedCollection == null) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (hasMore && !loading && !loadingMore && photos.isNotEmpty()) {
                    scope.launch {
                        loadingMore = true
                        try {
                            val api = ApiClient.create(baseUrl)
                            val nextPage = page + 1
                            val res = api.unsplashCollectionPhotos(
                                token, selectedCollection?.id ?: "", nextPage
                            )
                            if (res.success == true) {
                                val list = res.results ?: emptyList()
                                photos.addAll(list)
                                page = nextPage
                                hasMore = list.size >= 12
                            } else error = res.error ?: "加载失败"
                        } catch (e: Exception) {
                            error = "出错：${e.message}"
                        } finally { loadingMore = false }
                    }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (selectedCollection == null) {
            Spacer(Modifier.height(12.dp))
            if (collections.isEmpty() && !loading && error.isBlank()) {
                Text(
                    "该账号下没有相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                collections.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                scope.launch {
                                    loading = true
                                    photos.clear()
                                    page = 1; hasMore = true
                                    try {
                                        val api = ApiClient.create(baseUrl)
                                        val res = api.unsplashCollectionPhotos(token, c.id ?: "", 1)
                                        if (res.success == true) {
                                            val list = res.results ?: emptyList()
                                            photos.addAll(list)
                                            hasMore = list.size >= 12
                                            selectedCollection = c
                                        } else error = res.error ?: "加载失败"
                                    } catch (e: Exception) {
                                        error = "出错：${e.message}"
                                    } finally { loading = false }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!c.cover.isNullOrBlank()) {
                            AsyncImage(
                                model = c.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "图",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title ?: "未命名",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${c.total_photos ?: 0} 张",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        Text(
                            "›",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = {
                    selectedCollection = null
                    photos.clear()
                }) { Text("← 相册列表") }
            }

            if (photos.isNotEmpty()) {
                MultiSelectPhotoGrid(
                    photos = photos,
                    selectedPhotos = selectedPhotos,
                    showAuthor = false,
                    gridState = gridState,
                    modifier = Modifier.weight(1f)
                )
                BottomStatus(loadingMore = loadingMore, hasMore = hasMore)
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ============================================================
// 多选图片网格
// ============================================================

@Composable
fun MultiSelectPhotoGrid(
    photos: List<UnsplashPhoto>,
    selectedPhotos: MutableList<UnsplashPhoto>,
    showAuthor: Boolean = true,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(photos) { p ->
            val selectedIndex = selectedPhotos.indexOfFirst { it.id == p.id }
            val isSelected = selectedIndex >= 0

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isSelected) {
                            selectedPhotos.removeAll { it.id == p.id }
                        } else {
                            selectedPhotos.add(p)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    AsyncImage(
                        model = p.thumb ?: "",
                        contentDescription = p.alt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )

                    if (isSelected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${selectedIndex + 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (showAuthor) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = p.author ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BottomStatus(loadingMore: Boolean, hasMore: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "加载中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            !hasMore -> Text(
                "没有更多了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            else -> Spacer(Modifier.height(8.dp))
        }
    }
}