package com.devxdojo.mrrss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Device-local persistence for subscriptions, articles, and reading state. */
class LocalRssStore(context: Context) : SQLiteOpenHelper(context, "mrrss-local.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE feeds (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                category TEXT NOT NULL DEFAULT '',
                image_url TEXT NOT NULL DEFAULT '',
                server_id INTEGER,
                link TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                position INTEGER NOT NULL DEFAULT 0,
                script_path TEXT NOT NULL DEFAULT '',
                hide_from_timeline INTEGER NOT NULL DEFAULT 0,
                proxy_url TEXT NOT NULL DEFAULT '',
                proxy_enabled INTEGER NOT NULL DEFAULT 0,
                refresh_interval INTEGER NOT NULL DEFAULT 0,
                is_image_mode INTEGER NOT NULL DEFAULT 0,
                type TEXT NOT NULL DEFAULT '',
                xpath_item TEXT NOT NULL DEFAULT '',
                xpath_item_title TEXT NOT NULL DEFAULT '',
                xpath_item_content TEXT NOT NULL DEFAULT '',
                xpath_item_uri TEXT NOT NULL DEFAULT '',
                xpath_item_author TEXT NOT NULL DEFAULT '',
                xpath_item_timestamp TEXT NOT NULL DEFAULT '',
                xpath_item_time_format TEXT NOT NULL DEFAULT '',
                xpath_item_thumbnail TEXT NOT NULL DEFAULT '',
                xpath_item_categories TEXT NOT NULL DEFAULT '',
                xpath_item_uid TEXT NOT NULL DEFAULT '',
                article_view_mode TEXT NOT NULL DEFAULT '',
                auto_expand_content TEXT NOT NULL DEFAULT '',
                email_address TEXT NOT NULL DEFAULT '',
                email_imap_server TEXT NOT NULL DEFAULT '',
                email_imap_port INTEGER NOT NULL DEFAULT 0,
                email_username TEXT NOT NULL DEFAULT '',
                email_folder TEXT NOT NULL DEFAULT '',
                title_custom INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE articles (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                feed_id INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                translated_title TEXT NOT NULL DEFAULT '',
                published_at TEXT NOT NULL DEFAULT '',
                is_read INTEGER NOT NULL DEFAULT 0,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                is_read_later INTEGER NOT NULL DEFAULT 0,
                feed_title TEXT NOT NULL DEFAULT '',
                author TEXT NOT NULL DEFAULT '',
                summary TEXT NOT NULL DEFAULT '',
                original_summary TEXT NOT NULL DEFAULT '',
                content_html TEXT NOT NULL DEFAULT '',
                server_id INTEGER,
                UNIQUE(feed_id, url),
                FOREIGN KEY(feed_id) REFERENCES feeds(_id) ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX articles_published_idx ON articles(published_at DESC)")
        db.execSQL("CREATE INDEX articles_feed_idx ON articles(feed_id, published_at DESC)")
        createRecommendationHistoryTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val additions = listOf(
                "link TEXT NOT NULL DEFAULT ''",
                "description TEXT NOT NULL DEFAULT ''",
                "position INTEGER NOT NULL DEFAULT 0",
                "script_path TEXT NOT NULL DEFAULT ''",
                "hide_from_timeline INTEGER NOT NULL DEFAULT 0",
                "proxy_url TEXT NOT NULL DEFAULT ''",
                "proxy_enabled INTEGER NOT NULL DEFAULT 0",
                "refresh_interval INTEGER NOT NULL DEFAULT 0",
                "is_image_mode INTEGER NOT NULL DEFAULT 0",
                "type TEXT NOT NULL DEFAULT ''",
                "xpath_item TEXT NOT NULL DEFAULT ''",
                "xpath_item_title TEXT NOT NULL DEFAULT ''",
                "xpath_item_content TEXT NOT NULL DEFAULT ''",
                "xpath_item_uri TEXT NOT NULL DEFAULT ''",
                "xpath_item_author TEXT NOT NULL DEFAULT ''",
                "xpath_item_timestamp TEXT NOT NULL DEFAULT ''",
                "xpath_item_time_format TEXT NOT NULL DEFAULT ''",
                "xpath_item_thumbnail TEXT NOT NULL DEFAULT ''",
                "xpath_item_categories TEXT NOT NULL DEFAULT ''",
                "xpath_item_uid TEXT NOT NULL DEFAULT ''",
                "article_view_mode TEXT NOT NULL DEFAULT ''",
                "auto_expand_content TEXT NOT NULL DEFAULT ''",
                "email_address TEXT NOT NULL DEFAULT ''",
                "email_imap_server TEXT NOT NULL DEFAULT ''",
                "email_imap_port INTEGER NOT NULL DEFAULT 0",
                "email_username TEXT NOT NULL DEFAULT ''",
                "email_folder TEXT NOT NULL DEFAULT ''",
            )
            additions.forEach { db.execSQL("ALTER TABLE feeds ADD COLUMN $it") }
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE feeds ADD COLUMN title_custom INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 4) createRecommendationHistoryTable(db)
    }

    private fun createRecommendationHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS recommendation_history (
                article_url TEXT PRIMARY KEY,
                article_id INTEGER NOT NULL,
                article_title TEXT NOT NULL,
                feed_title TEXT NOT NULL,
                reason TEXT NOT NULL,
                recommended_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS recommendation_history_time_idx ON recommendation_history(recommended_at DESC)")
    }

    suspend fun getFeeds(): List<Feed> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            """SELECT _id,title,url,category,image_url,server_id,link,description,position,script_path,
                hide_from_timeline,proxy_url,proxy_enabled,refresh_interval,is_image_mode,type,
                xpath_item,xpath_item_title,xpath_item_content,xpath_item_uri,xpath_item_author,
                xpath_item_timestamp,xpath_item_time_format,xpath_item_thumbnail,xpath_item_categories,
                xpath_item_uid,article_view_mode,auto_expand_content,email_address,email_imap_server,
                email_imap_port,email_username,email_folder,title_custom FROM feeds ORDER BY category,title""".trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    Feed(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        url = cursor.getString(2),
                        category = cursor.getString(3),
                        imageUrl = cursor.getString(4),
                        serverId = cursor.nullableLong(5),
                        link = cursor.getString(6),
                        description = cursor.getString(7),
                        position = cursor.getInt(8),
                        scriptPath = cursor.getString(9),
                        hideFromTimeline = cursor.getInt(10) != 0,
                        proxyUrl = cursor.getString(11),
                        proxyEnabled = cursor.getInt(12) != 0,
                        refreshInterval = cursor.getInt(13),
                        isImageMode = cursor.getInt(14) != 0,
                        type = cursor.getString(15),
                        xpathItem = cursor.getString(16),
                        xpathItemTitle = cursor.getString(17),
                        xpathItemContent = cursor.getString(18),
                        xpathItemUri = cursor.getString(19),
                        xpathItemAuthor = cursor.getString(20),
                        xpathItemTimestamp = cursor.getString(21),
                        xpathItemTimeFormat = cursor.getString(22),
                        xpathItemThumbnail = cursor.getString(23),
                        xpathItemCategories = cursor.getString(24),
                        xpathItemUid = cursor.getString(25),
                        articleViewMode = cursor.getString(26),
                        autoExpandContent = cursor.getString(27),
                        emailAddress = cursor.getString(28),
                        emailImapServer = cursor.getString(29),
                        emailImapPort = cursor.getInt(30),
                        emailUsername = cursor.getString(31),
                        emailFolder = cursor.getString(32),
                        titleCustom = cursor.getInt(33) != 0,
                    ),
                )
            }
        }
    }

    suspend fun getArticles(filter: ArticleFilter, feedId: Long? = null): List<Article> = withContext(Dispatchers.IO) {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        feedId?.let { clauses += "a.feed_id=?"; args += it.toString() }
        when (filter) {
            ArticleFilter.UNREAD -> clauses += "a.is_read=0"
            ArticleFilter.FAVORITES -> clauses += "a.is_favorite=1"
            ArticleFilter.READ_LATER -> clauses += "a.is_read_later=1"
            ArticleFilter.ALL -> Unit
        }
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        readableDatabase.rawQuery(
            """SELECT a._id,a.feed_id,a.title,a.translated_title,a.url,a.published_at,a.is_read,
                a.is_favorite,a.is_read_later,a.feed_title,a.author,a.summary,a.original_summary,
                a.content_html,a.server_id
                FROM articles a $where ORDER BY a.published_at DESC,a._id DESC LIMIT 2000""".trimIndent(),
            args.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toArticle())
            }
        }
    }

    suspend fun getArticle(id: Long): Article? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            """SELECT _id,feed_id,title,translated_title,url,published_at,is_read,is_favorite,
                is_read_later,feed_title,author,summary,original_summary,content_html,server_id
                FROM articles WHERE _id=?""".trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toArticle() else null }
    }

    suspend fun getRecommendedArticleUrls(): Set<String> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT article_url FROM recommendation_history", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    suspend fun getRecommendationCount(): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM recommendation_history", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun getRecommendationHistory(limit: Int = 500): List<RecommendationHistoryEntry> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            """SELECT h.article_url,h.article_id,h.article_title,h.feed_title,h.reason,h.recommended_at,
                a._id,a.feed_id,a.title,a.translated_title,a.url,a.published_at,a.is_read,
                a.is_favorite,a.is_read_later,a.feed_title,a.author,a.summary,a.original_summary,
                a.content_html,a.server_id
                FROM recommendation_history h
                LEFT JOIN articles a ON a._id=h.article_id AND a.url=h.article_url
                ORDER BY h.recommended_at DESC LIMIT ?""".trimIndent(),
            arrayOf(limit.coerceIn(1, 1000).toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val article = if (cursor.isNull(6)) null else Article(
                        id = cursor.getLong(6),
                        feedId = cursor.getLong(7),
                        title = cursor.getString(8),
                        translatedTitle = cursor.getString(9),
                        url = cursor.getString(10),
                        publishedAt = cursor.getString(11),
                        isRead = cursor.getInt(12) != 0,
                        isFavorite = cursor.getInt(13) != 0,
                        isReadLater = cursor.getInt(14) != 0,
                        feedTitle = cursor.getString(15),
                        author = cursor.getString(16),
                        summary = cursor.getString(17),
                        originalSummary = cursor.getString(18),
                        contentHtml = cursor.getString(19),
                        serverId = cursor.nullableLong(20),
                    )
                    add(
                        RecommendationHistoryEntry(
                            articleUrl = cursor.getString(0),
                            articleId = cursor.getLong(1),
                            articleTitle = cursor.getString(2),
                            feedTitle = cursor.getString(3),
                            reason = cursor.getString(4),
                            recommendedAt = cursor.getLong(5),
                            article = article,
                        ),
                    )
                }
            }
        }
    }

    /** Save only first-time recommendations; URLs remain as history even if their feed is later removed. */
    suspend fun recordRecommendations(items: List<RecommendedArticle>): Set<String> = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val insertedUrls = mutableSetOf<String>()
        db.beginTransaction()
        try {
            for (item in items) {
                val article = item.article
                val articleUrl = article.url.trim().ifBlank { "urn:mrrss:article:${article.id}" }
                val values = ContentValues().apply {
                    put("article_url", articleUrl)
                    put("article_id", article.id)
                    put("article_title", article.title)
                    put("feed_title", article.feedTitle)
                    put("reason", item.reason)
                    put("recommended_at", System.currentTimeMillis())
                }
                if (db.insertWithOnConflict("recommendation_history", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    insertedUrls += articleUrl
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        insertedUrls
    }

    suspend fun upsertFeed(feed: Feed): Feed = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val existing = feed.serverId?.let { serverId ->
            db.rawQuery("SELECT _id,server_id,title_custom FROM feeds WHERE server_id=?", arrayOf(serverId.toString())).use { cursor ->
                if (cursor.moveToFirst()) Triple(cursor.getLong(0), cursor.nullableLong(1), cursor.getInt(2) != 0) else null
            }
        } ?: db.rawQuery("SELECT _id,server_id,title_custom FROM feeds WHERE url=?", arrayOf(feed.url)).use { cursor ->
            if (cursor.moveToFirst()) Triple(cursor.getLong(0), cursor.nullableLong(1), cursor.getInt(2) != 0) else null
        }
        val values = ContentValues().apply {
            put("title", feed.title.ifBlank { feed.url })
            put("url", feed.url)
            put("category", feed.category)
            put("image_url", feed.imageUrl)
            putNullableLong("server_id", feed.serverId ?: existing?.second)
            put("link", feed.link)
            put("description", feed.description)
            put("position", feed.position)
            put("script_path", feed.scriptPath)
            put("hide_from_timeline", if (feed.hideFromTimeline) 1 else 0)
            put("proxy_url", feed.proxyUrl)
            put("proxy_enabled", if (feed.proxyEnabled) 1 else 0)
            put("refresh_interval", feed.refreshInterval)
            put("is_image_mode", if (feed.isImageMode) 1 else 0)
            put("type", feed.type)
            put("xpath_item", feed.xpathItem)
            put("xpath_item_title", feed.xpathItemTitle)
            put("xpath_item_content", feed.xpathItemContent)
            put("xpath_item_uri", feed.xpathItemUri)
            put("xpath_item_author", feed.xpathItemAuthor)
            put("xpath_item_timestamp", feed.xpathItemTimestamp)
            put("xpath_item_time_format", feed.xpathItemTimeFormat)
            put("xpath_item_thumbnail", feed.xpathItemThumbnail)
            put("xpath_item_categories", feed.xpathItemCategories)
            put("xpath_item_uid", feed.xpathItemUid)
            put("article_view_mode", feed.articleViewMode)
            put("auto_expand_content", feed.autoExpandContent)
            put("email_address", feed.emailAddress)
            put("email_imap_server", feed.emailImapServer)
            put("email_imap_port", feed.emailImapPort)
            put("email_username", feed.emailUsername)
            put("email_folder", feed.emailFolder)
            put("title_custom", if (feed.titleCustom || existing?.third == true) 1 else 0)
        }
        val localId = if (existing == null) db.insertOrThrow("feeds", null, values) else {
            db.update("feeds", values, "_id=?", arrayOf(existing.first.toString()))
            existing.first
        }
        feed.copy(id = localId, serverId = feed.serverId ?: existing?.second, titleCustom = feed.titleCustom || existing?.third == true)
    }

    suspend fun importFeeds(feeds: List<OpmlSubscription>): Int = withContext(Dispatchers.IO) {
        val db = writableDatabase
        var imported = 0
        db.beginTransaction()
        try {
            for (feed in feeds) {
                val values = ContentValues().apply {
                    put("title", feed.title.ifBlank { feed.url })
                    put("url", feed.url)
                    put("category", feed.category)
                    put("image_url", "")
                }
                if (db.insertWithOnConflict("feeds", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) imported++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        imported
    }

    suspend fun updateFeed(feed: Feed, title: String, url: String, category: String): Feed = withContext(Dispatchers.IO) {
        val parsedUrl = url.trim().toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: throw IllegalArgumentException("请输入有效的 http:// 或 https:// RSS / Atom 地址")
        val canonicalUrl = parsedUrl.toString()
        val cleanTitle = title.trim().ifBlank { throw IllegalArgumentException("订阅名称不能为空") }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val duplicate = db.rawQuery("SELECT _id FROM feeds WHERE url=? AND _id<>?", arrayOf(canonicalUrl, feed.id.toString())).use { it.moveToFirst() }
            if (duplicate) throw IllegalArgumentException("已有相同地址的订阅")
            val values = ContentValues().apply {
                put("title", cleanTitle)
                put("url", canonicalUrl)
                put("category", category.trim())
                put("image_url", feed.imageUrl)
                putNullableLong("server_id", feed.serverId)
                put("link", feed.link)
                put("description", feed.description)
                put("position", feed.position)
                put("script_path", feed.scriptPath)
                put("hide_from_timeline", if (feed.hideFromTimeline) 1 else 0)
                put("proxy_url", feed.proxyUrl)
                put("proxy_enabled", if (feed.proxyEnabled) 1 else 0)
                put("refresh_interval", feed.refreshInterval)
                put("is_image_mode", if (feed.isImageMode) 1 else 0)
                put("type", feed.type)
                put("xpath_item", feed.xpathItem)
                put("xpath_item_title", feed.xpathItemTitle)
                put("xpath_item_content", feed.xpathItemContent)
                put("xpath_item_uri", feed.xpathItemUri)
                put("xpath_item_author", feed.xpathItemAuthor)
                put("xpath_item_timestamp", feed.xpathItemTimestamp)
                put("xpath_item_time_format", feed.xpathItemTimeFormat)
                put("xpath_item_thumbnail", feed.xpathItemThumbnail)
                put("xpath_item_categories", feed.xpathItemCategories)
                put("xpath_item_uid", feed.xpathItemUid)
                put("article_view_mode", feed.articleViewMode)
                put("auto_expand_content", feed.autoExpandContent)
                put("email_address", feed.emailAddress)
                put("email_imap_server", feed.emailImapServer)
                put("email_imap_port", feed.emailImapPort)
                put("email_username", feed.emailUsername)
                put("email_folder", feed.emailFolder)
                put("title_custom", 1)
            }
            db.update("feeds", values, "_id=?", arrayOf(feed.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        feed.copy(title = cleanTitle, titleCustom = true, url = canonicalUrl, category = category.trim())
    }

    suspend fun deleteFeed(feedId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("articles", "feed_id=?", arrayOf(feedId.toString()))
            db.delete("feeds", "_id=?", arrayOf(feedId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearServerIds() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE feeds SET server_id=NULL")
            db.execSQL("UPDATE articles SET server_id=NULL")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun upsertArticle(feedId: Long, article: Article): Article = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val stableUrl = article.url.ifBlank { "urn:mrrss:${article.serverId ?: article.id}:${article.title.hashCode()}" }
        val existing = db.rawQuery(
            """SELECT _id,is_read,is_favorite,is_read_later,translated_title,server_id,content_html,summary,original_summary,author
                FROM articles WHERE feed_id=? AND url=?""".trimIndent(),
            arrayOf(feedId.toString(), stableUrl),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else ExistingArticle(
                id = cursor.getLong(0),
                read = cursor.getInt(1) != 0,
                favorite = cursor.getInt(2) != 0,
                readLater = cursor.getInt(3) != 0,
                translatedTitle = cursor.getString(4),
                serverId = cursor.nullableLong(5),
                contentHtml = cursor.getString(6),
                summary = cursor.getString(7),
                originalSummary = cursor.getString(8),
                author = cursor.getString(9),
            )
        }
        val values = ContentValues().apply {
            put("feed_id", feedId)
            put("url", stableUrl)
            put("title", article.title)
            put("translated_title", article.translatedTitle.ifBlank { existing?.translatedTitle.orEmpty() })
            put("published_at", article.publishedAt)
            put("is_read", if (existing != null) existing.read else article.isRead)
            put("is_favorite", if (existing != null) existing.favorite else article.isFavorite)
            put("is_read_later", if (existing != null) existing.readLater else article.isReadLater)
            put("feed_title", article.feedTitle)
            put("author", article.author.ifBlank { existing?.author.orEmpty() })
            put("summary", article.summary.ifBlank { existing?.summary.orEmpty() })
            put("original_summary", article.originalSummary.ifBlank { existing?.originalSummary.orEmpty() })
            put("content_html", article.contentHtml.ifBlank { existing?.contentHtml.orEmpty() })
            putNullableLong("server_id", article.serverId ?: existing?.serverId)
        }
        val localId = if (existing == null) db.insertOrThrow("articles", null, values) else {
            db.update("articles", values, "_id=?", arrayOf(existing.id.toString()))
            existing.id
        }
        article.copy(
            id = localId,
            feedId = feedId,
            url = stableUrl,
            isRead = existing?.read ?: article.isRead,
            isFavorite = existing?.favorite ?: article.isFavorite,
            isReadLater = existing?.readLater ?: article.isReadLater,
            translatedTitle = article.translatedTitle.ifBlank { existing?.translatedTitle.orEmpty() },
            summary = article.summary.ifBlank { existing?.summary.orEmpty() },
            originalSummary = article.originalSummary.ifBlank { existing?.originalSummary.orEmpty() },
            contentHtml = article.contentHtml.ifBlank { existing?.contentHtml.orEmpty() },
            serverId = article.serverId ?: existing?.serverId,
        )
    }

    suspend fun addFeed(url: String, category: String): Feed {
        val parsed = url.trim().toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: throw IllegalArgumentException("请输入有效的 http:// 或 https:// RSS / Atom 地址")
        val canonicalUrl = parsed.toString()
        return upsertFeed(Feed(id = 0, title = parsed.host, url = canonicalUrl, category = category, imageUrl = ""))
    }

    suspend fun setRead(id: Long, read: Boolean) = withContext(Dispatchers.IO) {
        writableDatabase.update("articles", ContentValues().apply { put("is_read", if (read) 1 else 0) }, "_id=?", arrayOf(id.toString()))
    }

    suspend fun toggleFavorite(id: Long): Boolean = toggle(id, "is_favorite")
    suspend fun toggleReadLater(id: Long): Boolean = toggle(id, "is_read_later")

    private suspend fun toggle(id: Long, column: String): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val next = db.rawQuery("SELECT $column FROM articles WHERE _id=?", arrayOf(id.toString())).use { cursor ->
                if (!cursor.moveToFirst()) false else cursor.getInt(0) == 0
            }
            db.update("articles", ContentValues().apply { put(column, if (next) 1 else 0) }, "_id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
            next
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateTranslatedTitle(id: Long, title: String) = updateText(id, "translated_title", title)
    suspend fun updateSummary(id: Long, summary: String) = updateText(id, "summary", summary)
    suspend fun updateContent(id: Long, html: String) = updateText(id, "content_html", html)

    private suspend fun updateText(id: Long, column: String, value: String) = withContext(Dispatchers.IO) {
        writableDatabase.update("articles", ContentValues().apply { put(column, value) }, "_id=?", arrayOf(id.toString()))
    }

    private data class ExistingArticle(
        val id: Long,
        val read: Boolean,
        val favorite: Boolean,
        val readLater: Boolean,
        val translatedTitle: String,
        val serverId: Long?,
        val contentHtml: String,
        val summary: String,
        val originalSummary: String,
        val author: String,
    )

    private fun android.database.Cursor.toArticle() = Article(
        id = getLong(0),
        feedId = getLong(1),
        title = getString(2),
        translatedTitle = getString(3),
        url = getString(4),
        publishedAt = getString(5),
        isRead = getInt(6) != 0,
        isFavorite = getInt(7) != 0,
        isReadLater = getInt(8) != 0,
        feedTitle = getString(9),
        author = getString(10),
        summary = getString(11),
        originalSummary = getString(12),
        contentHtml = getString(13),
        serverId = nullableLong(14),
    )

    private fun android.database.Cursor.nullableLong(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun ContentValues.putNullableLong(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }
}

class DirectRssReader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun refresh(feeds: List<Feed>, store: LocalRssStore): RefreshSummary = withContext(Dispatchers.IO) {
        var addedOrUpdated = 0
        val failures = mutableListOf<String>()
        for (feed in feeds) {
            try {
                val response = client.newCall(Request.Builder().url(validateUrl(feed.url)).header("User-Agent", "MrRSS-Android/1.1 (+RSS reader)").build()).execute()
                val xml = response.use {
                    if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                    it.body?.string()?.takeIf(String::isNotBlank) ?: throw IOException("订阅返回内容为空")
                }
                val parsed = parseFeed(feed, xml)
                if (!feed.titleCustom && parsed.title.isNotBlank() && parsed.title != feed.title) {
                    store.upsertFeed(feed.copy(title = parsed.title))
                }
                for (article in parsed.articles) {
                    store.upsertArticle(feed.id, article)
                    addedOrUpdated++
                }
            } catch (error: Exception) {
                failures += "${feed.title}: ${error.message?.takeIf(String::isNotBlank) ?: "无法读取订阅"}"
            }
        }
        RefreshSummary(addedOrUpdated, failures)
    }

    private fun validateUrl(value: String) = value.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
        ?: throw IllegalArgumentException("订阅地址必须是有效的 http:// 或 https:// 链接")

    private data class ParsedFeed(val title: String, val articles: List<Article>)
    private data class PendingItem(
        var title: String = "",
        var url: String = "",
        var guid: String = "",
        var published: String = "",
        var author: String = "",
        var summary: String = "",
        var content: String = "",
    )

    private fun parseFeed(feed: Feed, xml: String): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())
        var feedTitle = ""
        var current: PendingItem? = null
        val parsed = mutableListOf<Article>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.orEmpty().substringAfter(':').lowercase(Locale.ROOT)
                    if (tag == "item" || tag == "entry") {
                        current = PendingItem()
                    } else if (current == null && tag == "title" && feedTitle.isBlank()) {
                        feedTitle = readElementText(parser).trim()
                    } else if (current != null) {
                        val item = current
                        when (tag) {
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href").orEmpty()
                                val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                val text = readElementText(parser).trim()
                                if (href.isNotBlank() && (rel.isBlank() || rel == "alternate")) item.url = href
                                else if (item.url.isBlank() && text.isNotBlank()) item.url = text
                            }
                            "title" -> item.title = readElementText(parser).trim()
                            "guid", "id" -> item.guid = readElementText(parser).trim()
                            "pubdate", "published", "updated", "date" -> item.published = readElementText(parser).trim()
                            "author", "creator", "name" -> if (item.author.isBlank()) item.author = readElementText(parser).trim()
                            "description", "summary", "subtitle" -> if (item.summary.isBlank()) item.summary = readElementText(parser).trim()
                            "encoded", "content" -> item.content = readElementText(parser).trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.orEmpty().substringAfter(':').lowercase(Locale.ROOT)
                    if ((tag == "item" || tag == "entry") && current != null) {
                        val item = current
                        val rawUrl = item.url.ifBlank { item.guid }
                        val resolved = feed.url.toHttpUrlOrNull()?.resolve(rawUrl)?.toString().orEmpty()
                        if (item.title.isNotBlank() && resolved.startsWith("http")) {
                            val content = item.content.ifBlank { item.summary }
                            parsed += Article(
                                id = 0,
                                feedId = feed.id,
                                title = item.title,
                                translatedTitle = "",
                                url = resolved,
                                publishedAt = normalizeDate(item.published),
                                isRead = false,
                                isFavorite = false,
                                isReadLater = false,
                                feedTitle = if (feed.titleCustom) feed.title else feedTitle.ifBlank { feed.title },
                                author = item.author,
                                summary = htmlToText(item.summary),
                                originalSummary = "",
                                contentHtml = content,
                            )
                        }
                        current = null
                    }
                }
            }
            event = parser.next()
        }
        return ParsedFeed(feedTitle, parsed.distinctBy { it.url })
    }

    private fun readElementText(parser: XmlPullParser): String {
        val startingDepth = parser.depth
        val out = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> out.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> if (parser.depth == startingDepth) return out.toString()
                XmlPullParser.END_DOCUMENT -> return out.toString()
            }
        }
    }

    private fun htmlToText(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun normalizeDate(value: String): String {
        if (value.isBlank()) return ""
        runCatching { Instant.parse(value).toString() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant().toString() }.getOrNull()?.let { return it }
        val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)
            }.getOrNull()?.let { return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(it.toInstant().atOffset(ZoneOffset.UTC)) }
        }
        return value
    }
}

data class RefreshSummary(val articleCount: Int, val failures: List<String>)
