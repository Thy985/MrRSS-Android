package com.devxdojo.mrrss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fetches a public article page and extracts its main readable content on-device. */
class FullTextReader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(articleUrl: String): String = withContext(Dispatchers.IO) {
        val url = articleUrl.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?: throw IOException("原文地址不是有效的 HTTP(S) 链接")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MrRSS-Android/1.1 (+RSS reader)")
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("原站返回 HTTP ${response.code}")
            val body = response.body ?: throw IOException("原站没有返回页面内容")
            val contentType = body.contentType()
            if (contentType != null && contentType.type != "text" && contentType.subtype !in setOf("html", "xhtml+xml")) {
                throw IOException("原站返回的不是 HTML 网页")
            }
            if (body.contentLength() > MAX_PAGE_BYTES) throw IOException("原网页过大，已停止抓取")
            val bytes = body.byteStream().use(::readBounded)
            val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            ReadabilityExtractor.extract(String(bytes, charset), response.request.url.toString())
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_PAGE_BYTES) throw IOException("原网页过大，已停止抓取")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_PAGE_BYTES = 8 * 1024 * 1024
    }
}

/** A small, on-device Readability-style extractor; it does not execute page scripts. */
internal object ReadabilityExtractor {
    private val boilerplateSelector = listOf(
        "script", "style", "noscript", "template", "svg", "canvas", "iframe", "object", "embed",
        "form", "button", "input", "select", "textarea", "nav", "footer", "aside",
        "[aria-hidden=true]", ".advert", ".advertisement", ".ads", ".sidebar", ".comments",
        ".comment", ".related", ".recommend", ".social", ".share", ".newsletter", ".cookie",
    ).joinToString(",")
    private val negativePattern = Regex("comment|sidebar|related|recommend|advert|promo|social|share|footer|header|nav|cookie|menu", RegexOption.IGNORE_CASE)
    private val positivePattern = Regex("article|content|entry|main|post|story|正文|文章", RegexOption.IGNORE_CASE)

    fun extract(html: String, baseUri: String): String {
        val document = Jsoup.parse(html, baseUri)
        document.select(boilerplateSelector).remove()
        val body = document.body() ?: throw IOException("无法解析原站网页")
        val candidates = body.select("article, main, [role=main], section, div")
            .asSequence()
            .filter { it.text().length >= MIN_CANDIDATE_TEXT }
            .map { it to score(it) }
            .toList()
        val best = candidates.maxByOrNull { it.second }?.first ?: body
        val extracted = best.clone()
        sanitize(extracted)
        val text = extracted.text().trim()
        if (text.length < MIN_EXTRACTED_TEXT) {
            throw IOException("没有找到足够的正文；原站可能需要登录、执行 JavaScript 或阻止抓取。可改用下方“在浏览器打开原文”。")
        }
        return extracted.html().trim().ifBlank { throw IOException("没有找到可显示的文章正文") }
    }

    private fun score(element: Element): Double {
        val text = element.text()
        val paragraphs = element.select("p").filter { it.text().length >= 25 }
        val paragraphText = paragraphs.sumOf { it.text().length }
        val linkText = element.select("a").sumOf { it.text().length }
        val density = linkText.toDouble() / text.length.coerceAtLeast(1)
        val identity = "${element.id()} ${element.className()}".lowercase(Locale.ROOT)
        val semanticBonus = when (element.normalName()) {
            "article", "main" -> 900.0
            else -> 0.0
        }
        val positiveBonus = if (positivePattern.containsMatchIn(identity)) 500.0 else 0.0
        val negativePenalty = if (negativePattern.containsMatchIn(identity)) 1_500.0 else 0.0
        return paragraphText + text.length * 0.12 + paragraphs.size * 110 + semanticBonus + positiveBonus - text.length * density * 1.6 - negativePenalty
    }

    private fun sanitize(root: Element) {
        root.select("a").forEach { anchor ->
            val href = anchor.absUrl("href").toHttpUrlOrNull()
            if (href == null || (href.scheme != "http" && href.scheme != "https")) {
                anchor.unwrap()
            } else {
                anchor.attr("href", href.toString())
            }
        }
        root.select("img").forEach { image ->
            val alt = image.attr("alt").trim()
            if (alt.isBlank()) image.remove() else image.replaceWith(TextNode(alt))
        }
        root.select("*").forEach { element ->
            val href = if (element.normalName() == "a") element.attr("href") else null
            element.clearAttributes()
            if (!href.isNullOrBlank()) element.attr("href", href)
        }
    }

    private const val MIN_CANDIDATE_TEXT = 80
    private const val MIN_EXTRACTED_TEXT = 120
}
