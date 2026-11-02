package com.devxdojo.mrrss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MrRssApi(private val serverAddress: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val baseUrl: HttpUrl = normalizeBaseUrl(serverAddress)
    val serverIdentity: String get() = baseUrl.toString().trimEnd('/')

    suspend fun testConnection() {
        request("GET", "api/version")
    }

    suspend fun getFeeds(): List<Feed> {
        val array = request("GET", "api/feeds") as? JSONArray
            ?: throw IOException("服务端返回的订阅数据格式不正确")
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    Feed(
                        id = item.optLong("id"),
                        title = item.optString("title").ifBlank { "未命名订阅" },
                        url = item.optString("url"),
                        category = item.optString("category"),
                        imageUrl = item.optString("image_url"),
                        serverId = item.optLong("id").takeIf { it > 0 },
                        link = item.optString("link"),
                        description = item.optString("description"),
                        position = item.optInt("position"),
                        scriptPath = item.optString("script_path"),
                        hideFromTimeline = item.optBoolean("hide_from_timeline"),
                        proxyUrl = item.optString("proxy_url"),
                        proxyEnabled = item.optBoolean("proxy_enabled"),
                        refreshInterval = item.optInt("refresh_interval"),
                        isImageMode = item.optBoolean("is_image_mode"),
                        type = item.optString("type"),
                        xpathItem = item.optString("xpath_item"),
                        xpathItemTitle = item.optString("xpath_item_title"),
                        xpathItemContent = item.optString("xpath_item_content"),
                        xpathItemUri = item.optString("xpath_item_uri"),
                        xpathItemAuthor = item.optString("xpath_item_author"),
                        xpathItemTimestamp = item.optString("xpath_item_timestamp"),
                        xpathItemTimeFormat = item.optString("xpath_item_time_format"),
                        xpathItemThumbnail = item.optString("xpath_item_thumbnail"),
                        xpathItemCategories = item.optString("xpath_item_categories"),
                        xpathItemUid = item.optString("xpath_item_uid"),
                        articleViewMode = item.optString("article_view_mode"),
                        autoExpandContent = item.optString("auto_expand_content"),
                        emailAddress = item.optString("email_address"),
                        emailImapServer = item.optString("email_imap_server"),
                        emailImapPort = item.optInt("email_imap_port"),
                        emailUsername = item.optString("email_username"),
                        emailFolder = item.optString("email_folder"),
                    ),
                )
            }
        }
    }

    suspend fun getArticles(filter: ArticleFilter, feedId: Long? = null): List<Article> {
        val pageSize = 250
        val result = mutableListOf<Article>()
        var page = 1
        while (page <= 100) {
            val url = baseUrl.newBuilder()
                .addPathSegments("api/articles")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", pageSize.toString())
                .addQueryParameter("sort_order", "newest")
                .apply {
                    filter.apiValue?.let { addQueryParameter("filter", it) }
                    feedId?.let { addQueryParameter("feed_id", it.toString()) }
                }
                .build()
            val array = requestUrl("GET", url) as? JSONArray
                ?: throw IOException("服务端返回的文章数据格式不正确")
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val remoteId = item.optLong("id").takeIf { it > 0 }
                result += Article(
                    id = remoteId ?: 0,
                    feedId = item.optLong("feed_id"),
                    title = item.optString("title").ifBlank { "（无标题）" },
                    translatedTitle = item.optString("translated_title"),
                    url = item.optString("url"),
                    publishedAt = item.optString("published_at"),
                    isRead = item.optBoolean("is_read"),
                    isFavorite = item.optBoolean("is_favorite"),
                    isReadLater = item.optBoolean("is_read_later"),
                    feedTitle = item.optString("feed_title"),
                    author = item.optString("author"),
                    summary = item.optString("summary"),
                    originalSummary = item.optString("original_summary"),
                    serverId = remoteId,
                )
            }
            if (array.length() < pageSize) break
            page++
        }
        return result
    }

    suspend fun getArticleContent(articleId: Long): ArticleContent {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/articles/content")
            .addQueryParameter("id", articleId.toString())
            .build()
        val json = requestUrl("GET", url) as? JSONObject
            ?: throw IOException("服务端返回的正文格式不正确")
        return ArticleContent(
            html = json.optString("content"),
            feedUrl = json.optString("feed_url"),
        )
    }

    suspend fun setRead(articleId: Long, read: Boolean) {
        request("POST", "api/articles/read", query = mapOf("id" to articleId.toString(), "read" to read.toString()))
    }

    suspend fun toggleFavorite(articleId: Long) {
        request("POST", "api/articles/favorite", query = mapOf("id" to articleId.toString()))
    }

    suspend fun toggleReadLater(articleId: Long) {
        request("POST", "api/articles/toggle-read-later", query = mapOf("id" to articleId.toString()))
    }

    /** The server exposes a toggle endpoint only; inspect first so local desired state stays authoritative. */
    suspend fun setFavorite(articleId: Long, favorite: Boolean) {
        val remote = getArticles(ArticleFilter.ALL).firstOrNull { it.serverId == articleId }
            ?: throw IOException("服务器上找不到这篇文章；本机状态已保留")
        if (remote.isFavorite != favorite) toggleFavorite(articleId)
    }

    /** Avoid blindly retrying the non-idempotent read-later toggle. */
    suspend fun setReadLater(articleId: Long, readLater: Boolean) {
        val remote = getArticles(ArticleFilter.ALL).firstOrNull { it.serverId == articleId }
            ?: throw IOException("服务器上找不到这篇文章；本机状态已保留")
        if (remote.isReadLater != readLater) toggleReadLater(articleId)
    }

    suspend fun refreshAll() {
        request("POST", "api/refresh")
    }

    suspend fun addFeed(url: String, category: String) {
        request(
            method = "POST",
            path = "api/feeds/add",
            json = JSONObject().put("url", url.trim()).put("category", category.trim()),
        )
    }

    suspend fun updateFeed(feed: Feed, title: String, url: String, category: String) {
        val json = JSONObject()
            .put("id", feed.serverId ?: throw IOException("服务器订阅编号缺失"))
            .put("title", title.trim())
            .put("url", url.trim())
            .put("category", category.trim())
            .put("script_path", feed.scriptPath)
            .put("hide_from_timeline", feed.hideFromTimeline)
            .put("proxy_url", feed.proxyUrl)
            .put("proxy_enabled", feed.proxyEnabled)
            .put("refresh_interval", feed.refreshInterval)
            .put("is_image_mode", feed.isImageMode)
            .put("type", feed.type)
            .put("xpath_item", feed.xpathItem)
            .put("xpath_item_title", feed.xpathItemTitle)
            .put("xpath_item_content", feed.xpathItemContent)
            .put("xpath_item_uri", feed.xpathItemUri)
            .put("xpath_item_author", feed.xpathItemAuthor)
            .put("xpath_item_timestamp", feed.xpathItemTimestamp)
            .put("xpath_item_time_format", feed.xpathItemTimeFormat)
            .put("xpath_item_thumbnail", feed.xpathItemThumbnail)
            .put("xpath_item_categories", feed.xpathItemCategories)
            .put("xpath_item_uid", feed.xpathItemUid)
            .put("article_view_mode", feed.articleViewMode)
            .put("auto_expand_content", feed.autoExpandContent)
            .put("email_address", feed.emailAddress)
            .put("email_imap_server", feed.emailImapServer)
            .put("email_imap_port", feed.emailImapPort)
            .put("email_username", feed.emailUsername)
            .put("email_folder", feed.emailFolder)
        request("POST", "api/feeds/update", json = json)
    }

    suspend fun deleteFeed(serverId: Long) {
        request("POST", "api/feeds/delete", query = mapOf("id" to serverId.toString()))
    }

    suspend fun summarize(articleId: Long, html: String): String {
        val result = request(
            method = "POST",
            path = "api/articles/summarize",
            json = JSONObject()
                .put("article_id", articleId)
                .put("length", "medium")
                .put("content", html),
        ) as? JSONObject ?: throw IOException("摘要结果格式不正确")
        val summary = result.optString("summary")
        if (summary.isBlank()) throw IOException(result.optString("error").ifBlank { "暂时无法生成摘要" })
        return summary
    }

    suspend fun translateTitle(articleId: Long, title: String, targetLanguage: String): String {
        val result = request(
            method = "POST",
            path = "api/articles/translate",
            json = JSONObject()
                .put("article_id", articleId)
                .put("title", title)
                .put("target_language", targetLanguage),
        ) as? JSONObject ?: throw IOException("翻译结果格式不正确")
        return result.optString("translated_title").ifBlank { throw IOException("服务端没有返回译文") }
    }

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        json: JSONObject? = null,
    ): Any? {
        val url = baseUrl.newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        return requestUrl(method, url, json)
    }

    private suspend fun requestUrl(method: String, url: HttpUrl, json: JSONObject? = null): Any? =
        withContext(Dispatchers.IO) {
            val body = if (method == "GET") {
                null
            } else {
                (json?.toString() ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
            }
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .method(method, body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            val error = JSONObject(text)
                            error.optString("error").ifBlank { error.optString("message") }
                        }.getOrDefault("")
                        throw IOException(message.ifBlank { "请求失败（HTTP ${response.code}）" })
                    }
                    if (text.isBlank()) return@withContext null
                    when (text.trimStart().firstOrNull()) {
                        '[' -> JSONArray(text)
                        '{' -> JSONObject(text)
                        else -> null
                    }
                }
            } catch (error: IOException) {
                throw IOException(error.message?.ifBlank { "无法连接到 MrRSS 服务" } ?: "无法连接到 MrRSS 服务", error)
            }
        }

    companion object {
        fun normalizeBaseUrl(value: String): HttpUrl {
            val input = value.trim().trimEnd('/')
            val url = (if (input.endsWith("/")) input else "$input/").toHttpUrlOrNull()
                ?: throw IllegalArgumentException("请输入有效的 http:// 或 https:// 服务地址")
            if (url.scheme != "http" && url.scheme != "https") {
                throw IllegalArgumentException("服务地址必须使用 HTTP 或 HTTPS")
            }
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                throw IllegalArgumentException("请勿将账号密码放在服务地址中")
            }
            return url
        }
    }
}
