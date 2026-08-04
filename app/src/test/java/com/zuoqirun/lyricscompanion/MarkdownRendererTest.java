package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarkdownRendererTest {
    @Test public void commonReleaseNoteMarkdownIsConvertedToHtml() {
        String html = MarkdownRenderer.toHtml("# 更新日志\n\n## 1.2.0\n"
                + "- 支持 **粗体**、`代码` 和 [项目主页](https://example.com)\n");

        assertTrue(html.contains("<h1>更新日志</h1>"));
        assertTrue(html.contains("<h2>1.2.0</h2>"));
        assertTrue(html.contains("<ul><li>支持 <strong>粗体</strong>"));
        assertTrue(html.contains("<tt>代码</tt>"));
        assertTrue(html.contains("<a href=\"https://example.com\">项目主页</a>"));
    }
}
