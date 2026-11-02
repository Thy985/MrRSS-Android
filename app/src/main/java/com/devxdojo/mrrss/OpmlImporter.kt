package com.devxdojo.mrrss

import android.util.Xml
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

object OpmlImporter {
    private const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val MAX_SUBSCRIPTIONS = 10_000
    private const val MAX_FOLDER_DEPTH = 30

    data class Result(
        val subscriptions: List<OpmlSubscription>,
        val invalidCount: Int,
        val duplicateCount: Int,
    )

    fun parse(input: InputStream): Result {
        val bytes = input.use { readLimited(it) }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(ByteArrayInputStream(bytes), null)

        val folders = mutableListOf<Pair<Int, String>>()
        val subscriptions = mutableListOf<OpmlSubscription>()
        val seenUrls = mutableSetOf<String>()
        var invalidCount = 0
        var duplicateCount = 0
        var sawOpmlRoot = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.orEmpty()
                    if (!sawOpmlRoot) {
                        if (!tag.equals("opml", ignoreCase = true)) throw IOException("所选文件不是有效的 OPML 文件")
                        sawOpmlRoot = true
                    }
                    if (tag.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.attribute("xmlUrl", "xmlurl", "xmlURL").trim()
                        if (xmlUrl.isNotBlank()) {
                            val parsedUrl = xmlUrl.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
                            if (parsedUrl == null) {
                                invalidCount++
                            } else {
                                if (subscriptions.size >= MAX_SUBSCRIPTIONS) throw IOException("OPML 文件中的订阅数量超过 $MAX_SUBSCRIPTIONS 个")
                                val canonicalUrl = parsedUrl.toString()
                                val key = canonicalUrl.lowercase(Locale.ROOT)
                                if (seenUrls.add(key)) {
                                    val title = parser.attribute("title").ifBlank { parser.attribute("text") }.ifBlank { parsedUrl.host }
                                    val folderCategory = folders.joinToString(" / ") { it.second }
                                    val category = parser.attribute("category").trim().ifBlank { folderCategory }
                                    subscriptions += OpmlSubscription(title = title.trim(), url = canonicalUrl, category = category)
                                } else {
                                    duplicateCount++
                                }
                            }
                        } else {
                            val folderName = parser.attribute("title").ifBlank { parser.attribute("text") }.trim()
                            if (folderName.isNotBlank()) {
                                if (folders.size >= MAX_FOLDER_DEPTH) throw IOException("OPML 分类嵌套层级过深")
                                folders += parser.depth to folderName
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.orEmpty().equals("outline", ignoreCase = true)) {
                    while (folders.lastOrNull()?.first == parser.depth) folders.removeAt(folders.lastIndex)
                }
            }
            event = parser.next()
        }
        if (!sawOpmlRoot) throw IOException("OPML 文件为空或格式不正确")
        return Result(subscriptions, invalidCount, duplicateCount)
    }

    private fun XmlPullParser.attribute(vararg names: String): String {
        for (index in 0 until attributeCount) {
            if (names.any { it.equals(getAttributeName(index), ignoreCase = true) }) {
                return getAttributeValue(index).orEmpty()
            }
        }
        return ""
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_FILE_BYTES) throw IOException("OPML 文件不能超过 5 MB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
