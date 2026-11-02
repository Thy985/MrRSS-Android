package com.devxdojo.mrrss

data class Feed(
    val id: Long,
    val title: String,
    val titleCustom: Boolean = false,
    val url: String,
    val category: String,
    val imageUrl: String,
    val serverId: Long? = null,
    val link: String = "",
    val description: String = "",
    val position: Int = 0,
    val scriptPath: String = "",
    val hideFromTimeline: Boolean = false,
    val proxyUrl: String = "",
    val proxyEnabled: Boolean = false,
    val refreshInterval: Int = 0,
    val isImageMode: Boolean = false,
    val type: String = "",
    val xpathItem: String = "",
    val xpathItemTitle: String = "",
    val xpathItemContent: String = "",
    val xpathItemUri: String = "",
    val xpathItemAuthor: String = "",
    val xpathItemTimestamp: String = "",
    val xpathItemTimeFormat: String = "",
    val xpathItemThumbnail: String = "",
    val xpathItemCategories: String = "",
    val xpathItemUid: String = "",
    val articleViewMode: String = "",
    val autoExpandContent: String = "",
    val emailAddress: String = "",
    val emailImapServer: String = "",
    val emailImapPort: Int = 0,
    val emailUsername: String = "",
    val emailFolder: String = "",
)

data class OpmlSubscription(
    val title: String,
    val url: String,
    val category: String,
)

data class Article(
    val id: Long,
    val feedId: Long,
    val title: String,
    val translatedTitle: String,
    val url: String,
    val publishedAt: String,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val isReadLater: Boolean,
    val feedTitle: String,
    val author: String,
    val summary: String,
    val originalSummary: String,
    val contentHtml: String = "",
    val serverId: Long? = null,
)

data class ArticleContent(
    val html: String,
    val feedUrl: String,
)

enum class ArticleFilter(val apiValue: String?, val label: String) {
    ALL(null, "全部"),
    UNREAD("unread", "未读"),
    FAVORITES("favorites", "收藏"),
    READ_LATER("read_later", "稍后读"),
}

enum class AppDestination(val label: String) {
    ARTICLES("文章"),
    FEEDS("订阅"),
    SETTINGS("设置"),
}
