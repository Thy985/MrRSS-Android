package com.devxdojo.mrrss

data class AiArticleRecommendation(
    val articleId: Long,
    val reason: String,
)

data class RecommendedArticle(
    val article: Article,
    val reason: String,
)

data class RecommendationHistoryEntry(
    val articleUrl: String,
    val articleId: Long,
    val articleTitle: String,
    val feedTitle: String,
    val reason: String,
    val recommendedAt: Long,
    val article: Article?,
)

data class ArticlePassage(
    val label: String,
    val text: String,
)

data class ArticleAnswer(
    val text: String,
    val passages: List<ArticlePassage>,
    val basedOnSummaryOnly: Boolean,
)

data class ArticleChatTurn(
    val question: String,
    val answer: ArticleAnswer? = null,
    val error: String? = null,
    val pending: Boolean = false,
)
