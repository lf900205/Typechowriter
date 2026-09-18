package com.example.typechowriter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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

    // ↓↓↓ 分类相关状态 ↓↓↓
    val categories = remember { mutableStateListOf<Category>() }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

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

        // 加载分类
        if (baseUrl.isNotBlank() && token.isNotBlank()) {
            try {
                val api = ApiClient.create(baseUrl)
                val res = api.getCategories(token)
                if (res.success == true) {
                    val list = res.results ?: emptyList()
                    categories.clear()
                    categories.addAll(list)
                    // 默认选中第一个
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
                                "写日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (draftSavedAt > 0L && !uploading) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "草稿已保存 ${formatDraftTime(draftSavedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                    },
                    actions = {
                        // ↓↓↓ 分类按钮（显示当前分类名） ↓↓↓
                        TextButton(onClick = { showCategoryDialog = true }) {
                            Text(
                                selectedCategory?.name ?: "默认分类",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // 设置按钮
                        TextButton(
                            onClick = onOpenConfig,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("设置", fontSize = 15.sp)
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
                                            // ↓↓↓ 传分类 ID ↓↓↓
                                            val res = api.publish(
                                                token, finalTitle, content,
                                                "publish", tags, "",
                                                selectedCategory?.mid ?: 0
                                            )
                                            if (res.success == true) {
                                                message = "已发布，cid=${res.cid}"
                                                Settings.clearDraft(context)
                                                title = ""; content = ""; tags = ""
                                                draftSavedAt = 0L
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
                                Text(if (sending) "发布中" else "发布", fontSize = 15.sp)
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
                    if (message.isNotEmpty() && !message.startsWith("草稿")) message = ""
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
                    if (message.isNotEmpty() && !message.startsWith("草稿")) message = ""
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
                            progress = if (uploadTotal > 0) {
                                uploadCurrent.toFloat() / uploadTotal.toFloat()
                            } else 0f,
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
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }

    // ↓↓↓ 分类选择对话框 ↓↓↓
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
            onPickUnsplash = { url ->
                val imgBlock = "\n[jpg]\n![图片]($url)\n[/jpg]\n"
                content += imgBlock
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
    onPickUnsplash: (String) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("本地图片", "搜索", "我的相册")

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
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    tabs.forEachIndexed { idx, t ->
                        Column(
                            Modifier
                                .clickable { tabIndex = idx }
                                .padding(vertical = 6.dp),
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
                        1 -> UnsplashSearchPane(baseUrl, token, onPickUnsplash)
                        2 -> UnsplashCollectionsPane(baseUrl, token, onPickUnsplash)
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
            "可多选，按选择顺序自动编号（图1、图2、图3…）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPickLocal) { Text("选择图片") }
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
            PhotoGrid(
                photos = photos,
                onPick = onPick,
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

@Composable
fun UnsplashCollectionsPane(
    baseUrl: String,
    token: String,
    onPick: (String) -> Unit
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
                PhotoGrid(
                    photos = photos,
                    onPick = onPick,
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

@Composable
fun PhotoGrid(
    photos: List<UnsplashPhoto>,
    onPick: (String) -> Unit,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val url = p.regular ?: p.small ?: p.thumb ?: return@clickable
                        onPick(url)
                    }
            ) {
                AsyncImage(
                    model = p.thumb ?: "",
                    contentDescription = p.alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
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
