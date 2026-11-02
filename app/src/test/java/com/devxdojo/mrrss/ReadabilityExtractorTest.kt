package com.devxdojo.mrrss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReadabilityExtractorTest {
    @Test
    fun extractsArticleAndRemovesNavigationAndUnsafeLinks() {
        val articleText = "这是一段用于验证阅读器正文提取的文章内容。".repeat(20)
        val html = """
            <html><head><title>测试文章</title></head><body>
              <nav><a href="/archive">导航栏目链接</a></nav>
              <aside class="advertisement">广告内容不应进入正文</aside>
              <article class="post-content">
                <h1>文章标题</h1>
                <p>$articleText</p>
                <p><a href="javascript:alert(1)">危险链接文字</a></p>
                <p><a href="/related">相关阅读入口</a></p>
              </article>
              <footer>页脚版权信息</footer>
            </body></html>
        """.trimIndent()

        val extracted = ReadabilityExtractor.extract(html, "https://example.com/news/story")

        assertTrue(extracted.contains("文章标题"))
        assertTrue(extracted.contains(articleText))
        assertFalse(extracted.contains("广告内容"))
        assertFalse(extracted.contains("页脚版权"))
        assertFalse(extracted.contains("javascript:"))
        assertTrue(extracted.contains("https://example.com/related"))
        assertFalse(extracted.contains("/archive"))
    }

    @Test(expected = IOException::class)
    fun rejectsPagesWithoutEnoughReadableText() {
        ReadabilityExtractor.extract(
            "<html><body><nav>菜单</nav><p>只有一点点内容</p></body></html>",
            "https://example.com/",
        )
    }
}
