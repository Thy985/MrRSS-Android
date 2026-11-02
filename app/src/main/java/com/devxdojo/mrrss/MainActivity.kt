package com.devxdojo.mrrss

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MrRssGreen = Color(0xFF087F6E)
private val SoftCanvas = Color(0xFFF5F8F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MrRssTheme { MrRssApp() } }
    }
}

@Composable
private fun MrRssTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = MrRssGreen,
            onPrimary = Color.White,
            secondary = Color(0xFF42655F),
            background = SoftCanvas,
            surface = Color.White,
            surfaceVariant = Color(0xFFE8F0ED),
            primaryContainer = Color(0xFFD6F2E9),
            onPrimaryContainer = Color(0xFF073B31),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MrRssApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("mrrss_mobile", Context.MODE_PRIVATE) }
    val aiConfigStore = remember { AiProviderConfigStore(preferences) }
    val store = remember { LocalRssStore(context.applicationContext) }
    val directReader = remember { DirectRssReader() }
    val fullTextReader = remember { FullTextReader() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var serverAddress by remember { mutableStateOf(preferences.getString("server", "").orEmpty()) }
    var addressDraft by remember { mutableStateOf(serverAddress) }
    var targetLanguage by remember { mutableStateOf(preferences.getString("target_language", "zh-CN") ?: "zh-CN") }
    var languageDraft by remember { mutableStateOf(targetLanguage) }
    var aiConfig by remember { mutableStateOf(aiConfigStore.load()) }
    var aiEndpointDraft by remember { mutableStateOf(aiConfig.endpoint) }
    var aiModelDraft by remember { mutableStateOf(aiConfig.model) }
    var aiApiKeyDraft by remember { mutableStateOf("") }
    var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiConnectionBusy by remember { mutableStateOf(false) }
    var aiConnectionStatus by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(AppDestination.ARTICLES) }
    var filter by remember { mutableStateOf(ArticleFilter.ALL) }
    var selectedFeedId by remember { mutableStateOf<Long?>(null) }
    var feeds by remember { mutableStateOf<List<Feed>>(emptyList()) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    var articleHtml by remember { mutableStateOf("") }
    var fullTextLoading by remember { mutableStateOf(false) }
    var fullTextFetched by remember { mutableStateOf(false) }
    var fullTextError by remember { mutableStateOf("") }
    var generatedSummary by remember { mutableStateOf("") }
    var showRecommendations by remember { mutableStateOf(false) }
    var recommendationLoading by remember { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf<List<RecommendedArticle>>(emptyList()) }
    var recommendationHistoryCount by remember { mutableStateOf(0) }
    var recommendationHistory by remember { mutableStateOf<List<RecommendationHistoryEntry>>(emptyList()) }
    var recommendationHistoryLoading by remember { mutableStateOf(false) }
    var showingRecommendationHistory by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var addingFeed by remember { mutableStateOf(false) }
    var importingOpml by remember { mutableStateOf(false) }
    var savingFeed by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var showAddFeed by remember { mutableStateOf(false) }
    var editingFeed by remember { mutableStateOf<Feed?>(null) }
    var deletingFeed by remember { mutableStateOf<Feed?>(null) }
    var feedUrlDraft by remember { mutableStateOf("") }
    var feedCategoryDraft by remember { mutableStateOf("") }
    var editTitleDraft by remember { mutableStateOf("") }
    var editUrlDraft by remember { mutableStateOf("") }
    var editCategoryDraft by remember { mutableStateOf("") }

    val api = remember(serverAddress) {
        if (serverAddress.isBlank()) null else runCatching { MrRssApi(serverAddress) }.getOrNull()
    }
    val aiClient = remember(aiConfig.endpoint, aiConfig.model, aiConfig.apiKey) {
        if (!aiConfig.isReady) null else runCatching {
            AiProviderApi(aiConfig.endpoint, aiConfig.model, aiConfig.apiKey)
        }.getOrNull()
    }

    fun showError(error: Throwable) {
        scope.launch {
            snackbar.showSnackbar(error.message?.takeIf { it.isNotBlank() } ?: "操作失败")
        }
    }

    fun inspectAiProvider(loadModels: Boolean) {
        scope.launch {
            aiConnectionBusy = true
            aiConnectionStatus = "正在检查 AI 服务…"
            try {
                val client = AiProviderApi(
                    aiEndpointDraft,
                    aiModelDraft,
                    aiApiKeyDraft.ifBlank { aiConfig.apiKey },
                )
                if (loadModels) {
                    val discoveredModels = client.fetchModels()
                    aiModels = discoveredModels
                    aiConnectionStatus = "已获取 ${discoveredModels.size} 个模型；点“选择模型”即可应用"
                } else {
                    client.testConnection()
                    aiConnectionStatus = "当前模型连接成功；此测试请求可能产生少量费用"
                }
            } catch (error: Throwable) {
                aiConnectionStatus = error.message?.takeIf { it.isNotBlank() } ?: "连接失败"
                showError(error)
            } finally {
                aiConnectionBusy = false
            }
        }
    }

    fun updateArticle(articleId: Long, transform: (Article) -> Article) {
        articles = articles.map { if (it.id == articleId) transform(it) else it }
        selectedArticle = selectedArticle?.let { if (it.id == articleId) transform(it) else it }
    }

    suspend fun loadLocal() {
        feeds = store.getFeeds()
        articles = store.getArticles(filter, selectedFeedId)
        recommendationHistoryCount = store.getRecommendationCount()
    }

    suspend fun syncWithServer(client: MrRssApi) {
        val queuedDeletes = preferences.getStringSet("deleted_server_feed_ids", emptySet()).orEmpty().toSet()
        if (queuedDeletes.isNotEmpty()) {
            val remainingDeletes = queuedDeletes.toMutableSet()
            for (entry in queuedDeletes) {
                val separator = entry.lastIndexOf('|')
                if (separator <= 0 || entry.substring(0, separator) != client.serverIdentity) continue
                val id = entry.substring(separator + 1).toLongOrNull() ?: continue
                client.deleteFeed(id)
                remainingDeletes.remove(entry)
            }
            preferences.edit().putStringSet("deleted_server_feed_ids", remainingDeletes).apply()
        }
        var remoteFeeds = client.getFeeds()
        val localFeeds = store.getFeeds()
        val remoteById = remoteFeeds.mapNotNull { feed -> feed.serverId?.let { it to feed } }.toMap()
        val remoteUrls = remoteFeeds.map { it.url.trim().trimEnd('/').lowercase(Locale.ROOT) }.toMutableSet()
        var pushedFeeds = 0
        var updatedFeeds = 0
        for (feed in localFeeds) {
            val key = feed.url.trim().trimEnd('/').lowercase(Locale.ROOT)
            val remote = feed.serverId?.let(remoteById::get)
            if (remote != null && (remote.title != feed.title || remote.url != feed.url || remote.category != feed.category)) {
                client.updateFeed(feed, feed.title, feed.url, feed.category)
                updatedFeeds++
            } else if (remote == null && key !in remoteUrls) {
                runCatching { client.addFeed(feed.url, feed.category); pushedFeeds++ }
                remoteUrls += key
            }
        }
        if (pushedFeeds > 0 || updatedFeeds > 0) {
            client.refreshAll()
            remoteFeeds = client.getFeeds()
        }
        val localByRemoteId = mutableMapOf<Long, Feed>()
        for (remote in remoteFeeds) {
            val local = store.upsertFeed(remote)
            remote.serverId?.let { localByRemoteId[it] = local }
        }
        val remoteArticles = client.getArticles(ArticleFilter.ALL)
        for (remote in remoteArticles) {
            val localFeed = localByRemoteId[remote.feedId]
                ?: localFeeds.firstOrNull { it.title.equals(remote.feedTitle, ignoreCase = true) }
                ?: continue
            val merged = store.upsertArticle(
                localFeed.id,
                remote.copy(feedId = localFeed.id, feedTitle = remote.feedTitle.ifBlank { localFeed.title }),
            )
            val remoteId = merged.serverId
            if (remoteId != null) {
                if (merged.isRead != remote.isRead) runCatching { client.setRead(remoteId, merged.isRead) }
                if (merged.isFavorite != remote.isFavorite) runCatching { client.toggleFavorite(remoteId) }
                if (merged.isReadLater != remote.isReadLater) runCatching { client.toggleReadLater(remoteId) }
            }
        }
        loadLocal()
    }

    fun reload(showSpinner: Boolean = true) {
        scope.launch {
            if (showSpinner) loading = true
            try {
                loadLocal()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                loading = false
            }
        }
    }

    fun openRecommendationHistory() {
        showingRecommendationHistory = true
        recommendationHistoryLoading = true
        scope.launch {
            try {
                recommendationHistory = store.getRecommendationHistory()
                recommendationHistoryCount = store.getRecommendationCount()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                recommendationHistoryLoading = false
            }
        }
    }

    fun connect(address: String) {
        scope.launch {
            loading = true
            try {
                val client = MrRssApi(address)
                client.testConnection()
                val previousServer = preferences.getString("last_server", preferences.getString("server", "").orEmpty()).orEmpty()
                val previousIdentity = previousServer.takeIf(String::isNotBlank)?.let { runCatching { MrRssApi(it).serverIdentity }.getOrNull() }
                if (previousIdentity != client.serverIdentity) store.clearServerIds()
                syncWithServer(client)
                serverAddress = address.trim().trimEnd('/')
                addressDraft = serverAddress
                preferences.edit().putString("server", serverAddress).putString("last_server", client.serverIdentity).apply()
                snackbar.showSnackbar("已连接；服务器现有数据已导入，本机阅读数据仍保留")
            } catch (error: Throwable) {
                showError(error)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            try {
                val result = directReader.refresh(feeds, store)
                var serverIssue: String? = null
                if (api != null) {
                    try {
                        api.refreshAll()
                        syncWithServer(api)
                    } catch (error: Throwable) {
                        serverIssue = error.message
                        loadLocal()
                    }
                } else {
                    loadLocal()
                }
                val localResult = if (result.failures.isEmpty()) "本地订阅已刷新（${result.articleCount} 篇文章）"
                    else "已刷新，${result.failures.size} 个订阅暂时无法读取"
                snackbar.showSnackbar(serverIssue?.let { "$localResult；服务器同步失败：$it" } ?: localResult)
            } catch (error: Throwable) {
                showError(error)
            } finally {
                refreshing = false
            }
        }
    }

    val opmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importingOpml = true
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
                    OpmlImporter.parse(input)
                }
                val previousUrls = store.getFeeds().map { it.url.lowercase(Locale.ROOT) }.toSet()
                val addedCount = store.importFeeds(parsed.subscriptions)
                loadLocal()
                val addedUrls = parsed.subscriptions.map { it.url.lowercase(Locale.ROOT) }
                    .filterNot(previousUrls::contains).toSet()
                val newlyImported = feeds.filter { it.url.lowercase(Locale.ROOT) in addedUrls }
                if (newlyImported.isNotEmpty()) directReader.refresh(newlyImported, store)
                var syncFailure: String? = null
                if (api != null && addedCount > 0) {
                    try { syncWithServer(api) } catch (error: Throwable) { syncFailure = error.message }
                }
                val skippedDuplicates = parsed.duplicateCount + (parsed.subscriptions.size - addedCount).coerceAtLeast(0)
                val summary = "OPML 导入完成：新增 $addedCount 个，重复订阅跳过 $skippedDuplicates 个，无效地址忽略 ${parsed.invalidCount} 个"
                snackbar.showSnackbar(syncFailure?.let { "$summary；服务器同步失败：$it" } ?: summary)
            } catch (error: Throwable) {
                showError(error)
            } finally {
                importingOpml = false
            }
        }
    }

    fun beginEditFeed(feed: Feed) {
        if (feed.emailAddress.isNotBlank() || feed.type.equals("email", ignoreCase = true)) {
            scope.launch { snackbar.showSnackbar("邮件订阅的账号配置请在服务端管理") }
            return
        }
        editTitleDraft = feed.title
        editUrlDraft = feed.url
        editCategoryDraft = feed.category
        editingFeed = feed
    }

    fun saveFeedChanges(feed: Feed) {
        scope.launch {
            savingFeed = true
            try {
                if (api != null && feed.serverId != null) {
                    api.updateFeed(feed, editTitleDraft, editUrlDraft, editCategoryDraft)
                }
                val updated = store.updateFeed(feed, editTitleDraft, editUrlDraft, editCategoryDraft)
                editingFeed = null
                loadLocal()
                directReader.refresh(listOf(updated), store)
                loadLocal()
                snackbar.showSnackbar("订阅已更新${if (api != null && feed.serverId != null) "并同步到服务器" else ""}")
            } catch (error: Throwable) { showError(error) }
            finally { savingFeed = false }
        }
    }

    fun confirmDeleteFeed(feed: Feed) {
        scope.launch {
            try {
                store.deleteFeed(feed.id)
                if (feed.serverId != null) {
                    val targetServer = api?.serverIdentity
                        ?: preferences.getString("last_server", preferences.getString("server", "").orEmpty()).orEmpty()
                    val pendingKey = "$targetServer|${feed.serverId}"
                    if (api != null) {
                        try {
                            api.deleteFeed(feed.serverId)
                            val remaining = preferences.getStringSet("deleted_server_feed_ids", emptySet()).orEmpty().toMutableSet()
                            remaining.remove(pendingKey)
                            preferences.edit().putStringSet("deleted_server_feed_ids", remaining).apply()
                        } catch (_: Throwable) {
                            val queued = preferences.getStringSet("deleted_server_feed_ids", emptySet()).orEmpty().toMutableSet()
                            queued.add(pendingKey)
                            preferences.edit().putStringSet("deleted_server_feed_ids", queued).apply()
                        }
                    } else {
                        val queued = preferences.getStringSet("deleted_server_feed_ids", emptySet()).orEmpty().toMutableSet()
                        queued.add(pendingKey)
                        preferences.edit().putStringSet("deleted_server_feed_ids", queued).apply()
                    }
                }
                if (selectedFeedId == feed.id) selectedFeedId = null
                if (selectedArticle?.feedId == feed.id) selectedArticle = null
                deletingFeed = null
                loadLocal()
                val targetServer = api?.serverIdentity
                    ?: preferences.getString("last_server", preferences.getString("server", "").orEmpty()).orEmpty()
                val queuedDelete = feed.serverId != null && preferences.getStringSet("deleted_server_feed_ids", emptySet()).orEmpty().contains("$targetServer|${feed.serverId}")
                snackbar.showSnackbar(if (queuedDelete) "已从本机删除；服务器删除将在下次连接时同步" else "订阅及本机已保存文章已删除")
            } catch (error: Throwable) { showError(error) }
        }
    }

    fun toggleFavorite(article: Article) {
        scope.launch {
            try {
                val next = store.toggleFavorite(article.id)
                updateArticle(article.id) { it.copy(isFavorite = next) }
                val client = api
                if (client != null && article.serverId != null) {
                    try { client.setFavorite(article.serverId, next) }
                    catch (error: Throwable) { snackbar.showSnackbar("已在本机收藏；服务器同步失败：${error.message}") }
                }
            } catch (error: Throwable) { showError(error) }
        }
    }

    fun openArticle(article: Article) {
        selectedArticle = article
        articleHtml = article.contentHtml
        fullTextLoading = false
        fullTextFetched = false
        fullTextError = ""
        generatedSummary = article.summary.ifBlank { article.originalSummary }
        scope.launch {
            try {
                if (!article.isRead) {
                    store.setRead(article.id, true)
                    updateArticle(article.id) { it.copy(isRead = true) }
                    if (api != null && article.serverId != null) {
                        runCatching { api.setRead(article.serverId, true) }
                    }
                }
                if (selectedArticle?.id == article.id && articleHtml.isBlank() && api != null && article.serverId != null) {
                    val body = api.getArticleContent(article.serverId).html
                    if (selectedArticle?.id == article.id && articleHtml.isBlank()) {
                        articleHtml = body
                        store.updateContent(article.id, body)
                        updateArticle(article.id) { it.copy(contentHtml = body) }
                    }
                }
            } catch (error: Throwable) { showError(error) }
        }
    }

    fun fetchFullText(article: Article) {
        fullTextLoading = true
        fullTextError = ""
        scope.launch {
            try {
                val body = fullTextReader.fetch(article.url)
                if (selectedArticle?.id != article.id) return@launch
                store.updateContent(article.id, body)
                articleHtml = body
                fullTextFetched = true
                updateArticle(article.id) { it.copy(contentHtml = body) }
                snackbar.showSnackbar("已从原站提取全文，并保存在本机")
            } catch (error: Throwable) {
                if (selectedArticle?.id == article.id) {
                    fullTextError = error.message?.takeIf(String::isNotBlank)
                        ?: "提取失败；可改用下方“在浏览器打开原文”。"
                }
            } finally {
                if (selectedArticle?.id == article.id) fullTextLoading = false
            }
        }
    }

    fun generateRecommendations() {
        val client = aiClient
        if (client == null) {
            scope.launch { snackbar.showSnackbar("请先在设置中配置兼容 OpenAI Chat Completions 的 AI 服务") }
            return
        }
        showRecommendations = true
        recommendationLoading = true
        recommendations = emptyList()
        scope.launch {
            try {
                val allArticles = store.getArticles(ArticleFilter.ALL)
                val previouslyRecommendedUrls = store.getRecommendedArticleUrls()
                val history = (allArticles.filter { it.isFavorite || it.isReadLater } + allArticles.filter { it.isRead })
                    .distinctBy { it.id }
                    .take(28)
                val notYetRecommended = allArticles.filter { it.url !in previouslyRecommendedUrls }
                val candidates = notYetRecommended.filter { !it.isRead && !it.isFavorite && !it.isReadLater }.take(60)
                    .ifEmpty { notYetRecommended.filter { !it.isRead }.take(60) }
                if (candidates.isEmpty()) throw IOException("没有符合条件且未推荐过的文章；请刷新订阅获取新文章后再试")
                val ranked = client.recommendArticles(feeds, history, candidates)
                val byId = candidates.associateBy { it.id }
                val freshRecommendations = ranked.distinctBy { it.articleId }.mapNotNull { item ->
                    byId[item.articleId]?.takeIf { it.url !in previouslyRecommendedUrls }
                        ?.let { RecommendedArticle(it, item.reason) }
                }
                val savedUrls = store.recordRecommendations(freshRecommendations)
                recommendations = freshRecommendations.filter { it.article.url in savedUrls }
                recommendationHistoryCount = store.getRecommendationCount()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                recommendationLoading = false
            }
        }
    }

    LaunchedEffect(serverAddress) {
        loading = true
        try {
            loadLocal()
            if (api != null) syncWithServer(api)
        } catch (error: Throwable) {
            showError(error)
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = showingRecommendationHistory) {
        showingRecommendationHistory = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showingRecommendationHistory) {
                        IconButton(onClick = { showingRecommendationHistory = false }) {
                            Icon(Icons.Default.Close, contentDescription = "返回文章")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (showingRecommendationHistory) "推荐历史" else when (destination) {
                                AppDestination.ARTICLES -> selectedFeedId?.let { id -> feeds.firstOrNull { it.id == id }?.title } ?: "MrRSS"
                                AppDestination.FEEDS -> "订阅源"
                                AppDestination.SETTINGS -> "设置"
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (destination == AppDestination.ARTICLES && !showingRecommendationHistory) {
                            Text(if (serverAddress.isBlank()) "本地阅读 · 服务端可选" else "本地阅读 · 已连接可选同步", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (destination == AppDestination.ARTICLES && !showingRecommendationHistory) {
                        IconButton(onClick = ::refresh) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "刷新订阅")
                        }
                    }
                    if (destination == AppDestination.FEEDS) {
                        IconButton(onClick = { showAddFeed = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加订阅")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftCanvas),
            )
        },
        bottomBar = {
            if (!showingRecommendationHistory) NavigationBar(containerColor = Color.White) {
                AppDestination.entries.forEach { item ->
                    val icon = when (item) {
                        AppDestination.ARTICLES -> Icons.AutoMirrored.Filled.Article
                        AppDestination.FEEDS -> Icons.Default.RssFeed
                        AppDestination.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            destination = item
                            if (item == AppDestination.SETTINGS) {
                                addressDraft = serverAddress
                                languageDraft = targetLanguage
                aiEndpointDraft = aiConfig.endpoint
                aiModelDraft = aiConfig.model
                aiApiKeyDraft = ""
                aiConnectionStatus = ""
                            } else if (item == AppDestination.ARTICLES) reload()
                        },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            AppDestination.ARTICLES -> if (showingRecommendationHistory) RecommendationHistoryScreen(
                modifier = Modifier.padding(padding),
                history = recommendationHistory,
                historyCount = recommendationHistoryCount,
                loading = recommendationHistoryLoading,
                onOpenArticle = ::openArticle,
                onOpenExternal = { openExternal(context, it) },
                onBack = { showingRecommendationHistory = false },
            ) else ArticleScreen(
                modifier = Modifier.padding(padding),
                articles = articles,
                filter = filter,
                loading = loading,
                feeds = feeds,
                selectedFeedId = selectedFeedId,
                onFilterChange = { filter = it; reload() },
                onRefresh = ::refresh,
                onOpenArticle = ::openArticle,
                onToggleFavorite = ::toggleFavorite,
                onShowAll = { selectedFeedId = null; reload() },
                onRecommendations = ::generateRecommendations,
                onRecommendationHistory = ::openRecommendationHistory,
            )
            AppDestination.FEEDS -> FeedScreen(
                modifier = Modifier.padding(padding),
                feeds = feeds,
                selectedFeedId = selectedFeedId,
                onSelect = { feed ->
                    selectedFeedId = feed?.id
                    filter = ArticleFilter.ALL
                    destination = AppDestination.ARTICLES
                    reload()
                },
                onAdd = { showAddFeed = true },
                onImport = { opmlPicker.launch(arrayOf("application/xml", "text/xml", "application/opml+xml", "text/plain", "*/*")) },
                importing = importingOpml,
                onEdit = ::beginEditFeed,
                onDelete = { deletingFeed = it },
            )
            AppDestination.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                address = addressDraft,
                connected = serverAddress.isNotBlank(),
                onAddressChange = { addressDraft = it },
                language = languageDraft,
                onLanguageChange = { languageDraft = it },
                busy = loading,
                onConnect = { connect(addressDraft) },
                onSaveLanguage = {
                    targetLanguage = languageDraft.trim().ifBlank { "zh-CN" }
                    preferences.edit().putString("target_language", targetLanguage).apply()
                    scope.launch { snackbar.showSnackbar("翻译目标语言已保存") }
                },
                aiEndpoint = aiEndpointDraft,
                onAiEndpointChange = { aiEndpointDraft = it; aiConnectionStatus = "" },
                aiModel = aiModelDraft,
                onAiModelChange = { aiModelDraft = it },
                aiApiKey = aiApiKeyDraft,
                onAiApiKeyChange = { aiApiKeyDraft = it; aiConnectionStatus = "" },
                aiKeySaved = aiConfig.keySaved,
                aiModels = aiModels,
                aiConnectionBusy = aiConnectionBusy,
                aiConnectionStatus = aiConnectionStatus,
                onTestAiConnection = { inspectAiProvider(loadModels = false) },
                onFetchAiModels = { inspectAiProvider(loadModels = true) },
                onSaveAiConfig = {
                    try {
                        aiConfig = aiConfigStore.save(aiEndpointDraft, aiModelDraft, aiApiKeyDraft)
                        aiApiKeyDraft = ""
                        scope.launch { snackbar.showSnackbar("AI 配置已保存；文章内容将发送到所选 AI 服务") }
                    } catch (error: Throwable) { showError(error) }
                },
                onClearAiKey = {
                    aiConfigStore.clearApiKey()
                    aiConfig = aiConfig.copy(apiKey = "", keySaved = false)
                    aiApiKeyDraft = ""
                    scope.launch { snackbar.showSnackbar("已清除本机保存的 AI 密钥") }
                },
                onDisconnect = {
                    preferences.edit().putString("last_server", api?.serverIdentity ?: serverAddress.trimEnd('/')).remove("server").apply()
                    serverAddress = ""
                    addressDraft = ""
                    scope.launch { snackbar.showSnackbar("已断开服务器；本机订阅和阅读数据已保留") }
                },
            )
        }
    }

    selectedArticle?.let { article ->
        ArticleDetailDialog(
            article = article,
            html = articleHtml,
            summary = generatedSummary,
            fullTextLoading = fullTextLoading,
            fullTextError = fullTextError,
            fullTextFetched = fullTextFetched,
            targetLanguage = targetLanguage,
            aiEnabled = aiClient != null || (api != null && article.serverId != null),
            questionApi = aiClient,
            aiBusy = aiBusy,
            onDismiss = { selectedArticle = null },
            onToggleFavorite = { toggleFavorite(article) },
            onToggleReadLater = {
                scope.launch {
                    try {
                        val next = store.toggleReadLater(article.id)
                        updateArticle(article.id) { it.copy(isReadLater = next) }
                        if (api != null && article.serverId != null) {
                            try { api.setReadLater(article.serverId, next) }
                            catch (error: Throwable) { snackbar.showSnackbar("已在本机更新；服务器同步失败：${error.message}") }
                        }
                        snackbar.showSnackbar(if (next) "已加入稍后读" else "已从稍后读移除")
                    } catch (error: Throwable) { showError(error) }
                }
            },
            onSummarize = {
                val localClient = aiClient
                val serverClient = api
                val remoteId = article.serverId
                if (localClient == null && (serverClient == null || remoteId == null)) {
                    scope.launch { snackbar.showSnackbar("请先在设置中配置兼容 OpenAI 的 AI 服务，或连接 MrRSS 服务端") }
                } else scope.launch {
                    aiBusy = true
                    try {
                        generatedSummary = if (localClient != null) {
                            localClient.summarizeArticle(
                                title = article.title,
                                html = articleHtml,
                                fallbackText = article.summary.ifBlank { article.originalSummary },
                            )
                        } else {
                            serverClient!!.summarize(remoteId!!, html = articleHtml)
                        }
                        store.updateSummary(article.id, generatedSummary)
                        updateArticle(article.id) { it.copy(summary = generatedSummary) }
                    } catch (error: Throwable) { showError(error) }
                    finally { aiBusy = false }
                }
            },
            onTranslate = {
                val localClient = aiClient
                val serverClient = api
                val remoteId = article.serverId
                if (localClient == null && (serverClient == null || remoteId == null)) {
                    scope.launch { snackbar.showSnackbar("请先在设置中配置兼容 OpenAI 的 AI 服务，或连接 MrRSS 服务端") }
                } else scope.launch {
                    aiBusy = true
                    try {
                        val translated = if (localClient != null) {
                            localClient.translateTitle(article.title, targetLanguage)
                        } else {
                            serverClient!!.translateTitle(remoteId!!, article.title, targetLanguage)
                        }
                        store.updateTranslatedTitle(article.id, translated)
                        updateArticle(article.id) { it.copy(translatedTitle = translated) }
                    } catch (error: Throwable) { showError(error) }
                    finally { aiBusy = false }
                }
            },
            onFetchFullText = { fetchFullText(article) },
            onOpenOriginal = { openExternal(context, article.url) },
        )
    }

    if (showRecommendations) {
        RecommendationsDialog(
            recommendations = recommendations,
            loading = recommendationLoading,
            historyCount = recommendationHistoryCount,
            onDismiss = { showRecommendations = false },
            onOpenArticle = { article ->
                showRecommendations = false
                openArticle(article)
            },
        )
    }

    if (showAddFeed) {
        AlertDialog(
            onDismissRequest = { showAddFeed = false },
            title = { Text("添加 RSS 订阅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = feedUrlDraft,
                        onValueChange = { feedUrlDraft = it },
                        label = { Text("RSS / Atom 地址") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = feedCategoryDraft,
                        onValueChange = { feedCategoryDraft = it },
                        label = { Text("分类（可选）") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = feedUrlDraft.isNotBlank() && !addingFeed,
                    onClick = {
                        scope.launch {
                            addingFeed = true
                            try {
                                val url = feedUrlDraft.trim()
                                val parsedUrl = Uri.parse(url)
                                require(parsedUrl.scheme in listOf("http", "https") && !parsedUrl.host.isNullOrBlank()) {
                                    "请输入有效的 http:// 或 https:// RSS / Atom 地址"
                                }
                                val feed = store.addFeed(url, feedCategoryDraft.trim())
                                feedUrlDraft = ""
                                feedCategoryDraft = ""
                                showAddFeed = false
                                val directResult = directReader.refresh(listOf(feed), store)
                                if (api != null) {
                                    runCatching { api.addFeed(feed.url, feed.category); api.refreshAll(); syncWithServer(api) }
                                }
                                loadLocal()
                                val message = if (directResult.failures.isEmpty()) "订阅已添加并保存到本机"
                                    else "订阅已保存到本机，但暂时无法抓取：${directResult.failures.first()}"
                                snackbar.showSnackbar(message)
                            } catch (error: Throwable) { showError(error) }
                            finally { addingFeed = false }
                        }
                    },
                ) { Text(if (addingFeed) "正在读取…" else "添加") }
            },
            dismissButton = { TextButton(onClick = { showAddFeed = false }) { Text("取消") } },
        )
    }

    editingFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { if (!savingFeed) editingFeed = null },
            title = { Text("编辑订阅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editTitleDraft, onValueChange = { editTitleDraft = it }, label = { Text("订阅名称") }, singleLine = true)
                    OutlinedTextField(value = editUrlDraft, onValueChange = { editUrlDraft = it }, label = { Text("RSS / Atom 地址") }, singleLine = true)
                    OutlinedTextField(value = editCategoryDraft, onValueChange = { editCategoryDraft = it }, label = { Text("分类（可选）") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = editTitleDraft.isNotBlank() && editUrlDraft.isNotBlank() && !savingFeed, onClick = { saveFeedChanges(feed) }) {
                    Text(if (savingFeed) "正在保存…" else "保存")
                }
            },
            dismissButton = { TextButton(enabled = !savingFeed, onClick = { editingFeed = null }) { Text("取消") } },
        )
    }

    deletingFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { deletingFeed = null },
            title = { Text("删除订阅？") },
            text = {
                Text("将删除“${feed.title}”及本机已保存的文章。${if (feed.serverId != null) "已连接时会同时删除服务器订阅及其文章；离线时会在下次连接到该服务器后同步删除。" else ""}此操作无法撤销。")
            },
            confirmButton = { TextButton(onClick = { confirmDeleteFeed(feed) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingFeed = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ArticleScreen(
    modifier: Modifier = Modifier,
    articles: List<Article>,
    filter: ArticleFilter,
    loading: Boolean,
    feeds: List<Feed>,
    selectedFeedId: Long?,
    onFilterChange: (ArticleFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenArticle: (Article) -> Unit,
    onToggleFavorite: (Article) -> Unit,
    onShowAll: () -> Unit,
    onRecommendations: () -> Unit,
    onRecommendationHistory: () -> Unit,
) {
    Column(modifier.fillMaxSize().background(SoftCanvas)) {
        if (selectedFeedId != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onShowAll) { Text("显示全部订阅") }
                Text(feeds.firstOrNull { it.id == selectedFeedId }?.category.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArticleFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    label = { Text(item.label) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRecommendations, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("为我推荐", maxLines = 1)
            }
            OutlinedButton(onClick = onRecommendationHistory, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("推荐历史", maxLines = 1)
            }
        }
        when {
            loading && articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MrRssGreen)
            }
            articles.isEmpty() -> EmptyState(
                title = "还没有文章",
                detail = "添加订阅后，下拉刷新即可获取最新内容。",
                action = "重新加载",
                onAction = onRefresh,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleCard(article = article, onClick = { onOpenArticle(article) }, onFavorite = { onToggleFavorite(article) })
                }
            }
        }
    }
}

@Composable
private fun RecommendationHistoryScreen(
    modifier: Modifier = Modifier,
    history: List<RecommendationHistoryEntry>,
    historyCount: Int,
    loading: Boolean,
    onOpenArticle: (Article) -> Unit,
    onOpenExternal: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier.fillMaxSize().background(SoftCanvas)) {
        when {
            loading && history.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MrRssGreen)
            }
            history.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MrRssGreen, modifier = Modifier.size(42.dp))
                    Text("暂时还没有推荐历史", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "完成一次“为我推荐”后，文章和推荐理由会保存在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onBack) { Text("返回文章") }
                }
            }
            else -> {
                Text(
                    if (historyCount > history.size) "共 $historyCount 条 · 显示最近 ${history.size} 条"
                    else "共 $historyCount 条历史推荐",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(history, key = { it.articleUrl }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val article = entry.article
                                if (article != null) onOpenArticle(article) else onOpenExternal(entry.articleUrl)
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        entry.feedTitle.ifBlank { "MrRSS 订阅" },
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MrRssGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatRecommendationDate(entry.recommendedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val currentTitle = entry.article?.let { article ->
                                    article.translatedTitle.takeIf { it.isNotBlank() && it != article.title } ?: article.title
                                } ?: entry.articleTitle
                                Text(
                                    currentTitle,
                                    modifier = Modifier.padding(top = 7.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.reason.isNotBlank()) {
                                    Text(
                                        entry.reason,
                                        modifier = Modifier.padding(top = 7.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    if (entry.article != null) "点击在 MrRSS 中阅读" else "文章已不在本机 · 点击打开原文",
                                    modifier = Modifier.padding(top = 9.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (article.isRead) Color(0xFFD0D9D6) else MrRssGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(article.feedTitle.ifBlank { "MrRSS 订阅" }, style = MaterialTheme.typography.labelMedium, color = MrRssGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatDate(article.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(if (article.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = if (article.isFavorite) "取消收藏" else "收藏", tint = if (article.isFavorite) Color(0xFFE8A400) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = article.translatedTitle.takeIf { it.isNotBlank() && it != article.title } ?: article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isRead) FontWeight.Medium else FontWeight.Bold,
                color = if (article.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            val summary = article.summary.ifBlank { article.originalSummary }
            if (summary.isNotBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            }
            if (article.author.isNotBlank()) {
                Text(article.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun FeedScreen(
    modifier: Modifier = Modifier,
    feeds: List<Feed>,
    selectedFeedId: Long?,
    onSelect: (Feed?) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    importing: Boolean,
    onEdit: (Feed) -> Unit,
    onDelete: (Feed) -> Unit,
) {
    val grouped = feeds.groupBy { it.category.ifBlank { "未分类" } }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(SoftCanvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport, enabled = !importing, modifier = Modifier.weight(1f)) {
                    Text(if (importing) "正在导入…" else "导入 OPML")
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加订阅")
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(null) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MrRssGreen)
                    Spacer(Modifier.width(12.dp))
                    Text("全部文章", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${feeds.size} 个订阅", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (feeds.isEmpty()) {
            item {
                Text("还没有订阅源。可以导入 OPML 文件，或添加一个 RSS / Atom 地址。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
        grouped.forEach { (category, categoryFeeds) ->
            item(key = "group-$category") { Text(category, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            items(categoryFeeds, key = { it.id }) { feed ->
                FeedRow(
                    feed = feed,
                    selected = selectedFeedId == feed.id,
                    onSelect = { onSelect(feed) },
                    onEdit = { onEdit(feed) },
                    onDelete = { onDelete(feed) },
                )
            }
        }
    }
}

@Composable
private fun FeedRow(
    feed: Feed,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(feed.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onSelect).padding(16.dp)) {
                Text(feed.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (feed.url.isNotBlank()) Text(feed.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "管理 ${feed.title}") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    address: String,
    connected: Boolean,
    onAddressChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    busy: Boolean,
    onConnect: () -> Unit,
    onSaveLanguage: () -> Unit,
    aiEndpoint: String,
    onAiEndpointChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    aiApiKey: String,
    onAiApiKeyChange: (String) -> Unit,
    aiKeySaved: Boolean,
    aiModels: List<String>,
    aiConnectionBusy: Boolean,
    aiConnectionStatus: String,
    onTestAiConnection: () -> Unit,
    onFetchAiModels: () -> Unit,
    onSaveAiConfig: () -> Unit,
    onClearAiKey: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(SoftCanvas).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MrRssGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("可选服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("不连接服务器也可以直接订阅 RSS / Atom；订阅、文章、收藏和阅读状态保存在本机。连接 MrRSS 可同步订阅与状态，并为服务端已有的文章使用 AI。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务地址") },
                    placeholder = { Text("http://192.168.1.10:1234") },
                    singleLine = true,
                )
                Button(onClick = onConnect, enabled = address.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "正在连接…" else "连接并同步（可选）")
                }
                Text("服务端没有内置登录保护；仅在可信局域网或受保护的 HTTPS / VPN 环境中使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MrRssGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("翻译", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = language,
                    onValueChange = onLanguageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标语言代码") },
                    supportingText = { Text("例如 zh-CN、en、ja") },
                    singleLine = true,
                )
                OutlinedButton(onClick = onSaveLanguage, modifier = Modifier.fillMaxWidth()) { Text("保存语言") }
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MrRssGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("可选 AI 服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "配置兼容 OpenAI Chat Completions 的服务后，可翻译或总结文章、根据订阅与阅读线索获取推荐，也可围绕当前文章提问。模型可手动填写，也可从服务端列表中选择。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = aiEndpoint,
                    onValueChange = onAiEndpointChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                )
                Column {
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = onAiModelChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称") },
                        placeholder = { Text("可手动填写，也可从服务端拉取") },
                        singleLine = true,
                    )
                    if (aiModels.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { modelMenuExpanded = true }) {
                                Text("选择模型（${aiModels.size}）")
                            }
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                            ) {
                                aiModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            onAiModelChange(model)
                                            modelMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = aiApiKey,
                    onValueChange = onAiApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (aiKeySaved) "API 密钥（已保存；留空保持不变）" else "API 密钥（可选）") },
                    placeholder = { Text(if (aiKeySaved) "已加密保存在此设备" else "本地服务可留空") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTestAiConnection,
                        enabled = aiEndpoint.isNotBlank() && aiModel.isNotBlank() && !aiConnectionBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (aiConnectionBusy) "检查中…" else "测试连接")
                    }
                    Button(
                        onClick = onFetchAiModels,
                        enabled = aiEndpoint.isNotBlank() && !aiConnectionBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (aiConnectionBusy) "获取中…" else "获取模型")
                    }
                }
                if (aiConnectionStatus.isNotBlank()) {
                    val statusColor = when {
                        aiConnectionStatus.startsWith("连接成功") || aiConnectionStatus.startsWith("已获取") -> MrRssGreen
                        aiConnectionStatus.startsWith("正在") -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                    Text(aiConnectionStatus, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
                Text(
                    "测试连接会向所选模型发送一条极短请求，服务商可能计费；获取模型只读取 /models 接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSaveAiConfig, modifier = Modifier.fillMaxWidth()) { Text("保存 AI 配置") }
                if (aiKeySaved) {
                    TextButton(onClick = onClearAiKey, modifier = Modifier.align(Alignment.End)) {
                        Text("清除已保存的 API 密钥")
                    }
                }
                Text(
                    "密钥使用 Android Keystore 加密保存在本机。执行翻译、总结、推荐或提问时，相关文章标题、摘要、正文以及推荐所需的订阅和阅读/收藏线索会发送至你配置的 AI 服务；仅填写可信服务地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (connected) TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) { Text("断开服务器（保留本机数据）") }
    }
}

@Composable
private fun ArticleDetailDialog(
    article: Article,
    html: String,
    summary: String,
    fullTextLoading: Boolean,
    fullTextError: String,
    fullTextFetched: Boolean,
    targetLanguage: String,
    aiEnabled: Boolean,
    questionApi: AiProviderApi?,
    aiBusy: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReadLater: () -> Unit,
    onSummarize: () -> Unit,
    onTranslate: () -> Unit,
    onFetchFullText: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    var showQuestionAgent by remember(article.id) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            if (showQuestionAgent) {
                ArticleQuestionPanel(
                    article = article,
                    html = if (fullTextFetched) html else "",
                    fallbackSummary = summary.ifBlank { article.summary.ifBlank { article.originalSummary } },
                    aiClient = questionApi,
                    onBack = { showQuestionAgent = false },
                )
            } else Column {
                Row(
                    Modifier.fillMaxWidth().background(SoftCanvas).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                    Text(article.feedTitle.ifBlank { "文章详情" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleFavorite) {
                        Icon(if (article.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "收藏", tint = if (article.isFavorite) Color(0xFFE8A400) else MrRssGreen)
                    }
                    IconButton(onClick = onToggleReadLater) {
                        Icon(if (article.isReadLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "稍后读", tint = MrRssGreen)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(article.feedTitle.ifBlank { "MrRSS" }.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = MrRssGreen, fontWeight = FontWeight.Bold)
                        Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        if (article.translatedTitle.isNotBlank() && article.translatedTitle != article.title) {
                            Text(article.translatedTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(formatDate(article.publishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (article.author.isNotBlank()) Text(article.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { showQuestionAgent = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("问这篇")
                            }
                            Text(
                                if (questionApi == null) "需要先在设置中配置兼容 OpenAI Chat Completions 的 AI 服务。"
                                else "围绕当前文章提问；回答会尽量标出引用段落。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSummarize, enabled = aiEnabled && !aiBusy) {
                                if (aiBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("AI 总结")
                            }
                            OutlinedButton(onClick = onTranslate, enabled = aiEnabled && !aiBusy) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("翻译标题")
                            }
                        }
                        if (!aiEnabled) Text("请在设置中配置 AI 服务，或连接包含此文章的 MrRSS 服务端。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.isNotBlank()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text("摘要", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(summary, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onFetchFullText,
                                enabled = !fullTextLoading && (article.url.startsWith("http://") || article.url.startsWith("https://")),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (fullTextLoading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (fullTextLoading) "正在提取原站全文…" else if (html.isBlank()) "从原站提取全文（阅读模式）" else "重新提取原站全文")
                            }
                            Text("在本机抓取并整理网页正文；若网站要求登录或阻止抓取，可在下方打开原文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (fullTextError.isNotBlank()) {
                                Text(fullTextError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        if (html.isBlank()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MrRssGreen)
                                Spacer(Modifier.width(10.dp)); Text("正在加载正文…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            SelectionContainer {
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth(),
                                    factory = { viewContext ->
                                        TextView(viewContext).apply {
                                            textSize = 17f
                                            setTextColor(android.graphics.Color.rgb(36, 48, 44))
                                            movementMethod = LinkMovementMethod.getInstance()
                                            setLineSpacing(4f, 1.1f)
                                        }
                                    },
                                    update = { view -> view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT) },
                                )
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onOpenOriginal, modifier = Modifier.fillMaxWidth(), enabled = article.url.startsWith("http")) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(8.dp)); Text("在浏览器打开原文")
                        }
                    }
                    item { Text("翻译目标：$targetLanguage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsDialog(
    recommendations: List<RecommendedArticle>,
    loading: Boolean,
    historyCount: Int,
    onDismiss: () -> Unit,
    onOpenArticle: (Article) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = SoftCanvas) {
            Column {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭推荐") }
                    Text("为你推荐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = MrRssGreen)
                            Text("正在挑选新文章…（历史已记录 $historyCount 篇）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    recommendations.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("暂时没有符合条件的新文章。历史已记录 $historyCount 篇推荐；刷新订阅后再试。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                "本次只展示此前未推荐过的文章；历史累计记录 $historyCount 篇。点选卡片可直接在 MrRSS 中打开文章。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        items(recommendations, key = { it.article.id }) { recommendation ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenArticle(recommendation.article) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(recommendation.article.feedTitle.ifBlank { "MrRSS 订阅" }, style = MaterialTheme.typography.labelMedium, color = MrRssGreen)
                                    Text(recommendation.article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(recommendation.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleQuestionPanel(
    article: Article,
    html: String,
    fallbackSummary: String,
    aiClient: AiProviderApi?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var turns by remember(article.id) { mutableStateOf<List<ArticleChatTurn>>(emptyList()) }
    var draft by remember(article.id) { mutableStateOf("") }
    var busy by remember(article.id) { mutableStateOf(false) }
    val hasBody = html.isNotBlank()
    val hasContext = hasBody || fallbackSummary.isNotBlank()

    fun submitQuestion() {
        val question = draft.trim()
        val client = aiClient
        if (question.isBlank() || client == null || busy || !hasContext) return
        val previous = turns.mapNotNull { turn -> turn.answer?.let { turn.question to it.text } }
        val position = turns.size
        turns = turns + ArticleChatTurn(question = question, pending = true)
        draft = ""
        busy = true
        scope.launch {
            try {
                val answer = client.answerArticleQuestion(
                    article = article,
                    html = html,
                    fallbackSummary = fallbackSummary,
                    question = question,
                    conversationHistory = previous,
                )
                turns = turns.mapIndexed { index, turn -> if (index == position) turn.copy(answer = answer, pending = false) else turn }
            } catch (error: Throwable) {
                turns = turns.mapIndexed { index, turn ->
                    if (index == position) turn.copy(error = error.message?.takeIf(String::isNotBlank) ?: "回答失败，请稍后重试", pending = false)
                    else turn
                }
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().background(SoftCanvas).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "返回文章") }
            Column(Modifier.weight(1f)) {
                Text("问这篇", fontWeight = FontWeight.SemiBold)
                Text(article.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                when {
                    !hasContext -> "当前没有可用的正文或摘要。可先返回文章页尝试提取原站全文。"
                    !hasBody -> "当前只有订阅摘要可用；AI 只能基于现有摘要回答，无法覆盖全文未提供的信息。"
                    else -> "回答基于应用当前可用的文章内容，并尽量引用段落；材料未提及的信息会明确说明。"
                },
                modifier = Modifier.padding(13.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (aiClient == null) {
            Text(
                "请先到设置配置兼容 OpenAI Chat Completions 的 AI 服务，才能开始提问。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (turns.isEmpty()) {
                item {
                    Text(
                        "可以问文章的核心观点、关键事实或某个段落的含义。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(turns) { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
                            Text(turn.question, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    when {
                        turn.pending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MrRssGreen)
                            Text("正在阅读文章并组织回答…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        turn.error != null -> Text(turn.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        turn.answer != null -> {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(turn.answer.text, style = MaterialTheme.typography.bodyMedium)
                                    val citationLabels = Regex("\\[(P\\d+|摘要)]")
                                        .findAll(turn.answer.text)
                                        .map { it.groupValues[1] }
                                        .toSet()
                                    val citedPassages = if (citationLabels.isEmpty()) turn.answer.passages.take(2)
                                        else turn.answer.passages.filter { it.label in citationLabels }
                                    if (citedPassages.isNotEmpty()) {
                                        Card(colors = CardDefaults.cardColors(containerColor = SoftCanvas), shape = RoundedCornerShape(14.dp)) {
                                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Text("引用材料", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                                citedPassages.forEach { passage ->
                                                    Text("[${passage.label}] ${passage.text.take(420)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("输入问题") },
                enabled = aiClient != null && hasContext && !busy,
                maxLines = 4,
            )
            Button(onClick = ::submitQuestion, enabled = aiClient != null && hasContext && draft.isNotBlank() && !busy) {
                Text(if (busy) "思考中" else "发送")
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(SoftCanvas).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MrRssGreen, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

private fun formatDate(value: String): String {
    if (value.isBlank()) return ""
    val formatter = DateTimeFormatter.ofPattern("MM-dd · HH:mm", Locale.getDefault())
    return runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).format(formatter) }
        .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(formatter) }
        .getOrDefault(value.take(10))
}

private fun formatRecommendationDate(value: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return runCatching { Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(formatter) }
        .getOrDefault("")
}

private fun openExternal(context: Context, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
