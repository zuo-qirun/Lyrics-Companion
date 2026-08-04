package com.zuoqirun.lyricscompanion;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the small Markdown subset used by release notes without a network dependency. */
final class MarkdownRenderer {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern UNORDERED = Pattern.compile("^[-*]\\s+(.+)$");
    private static final Pattern ORDERED = Pattern.compile("^\\d+[.]\\s+(.+)$");
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\((https?://[^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern ITALIC = Pattern.compile("(?<![*])\\*([^*]+)\\*(?![*])");

    private MarkdownRenderer() {}

    static Spanned render(String markdown) {
        String html = toHtml(markdown);
        return Build.VERSION.SDK_INT >= 24
                ? Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                : Html.fromHtml(html);
    }

    static String toHtml(String markdown) {
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n");
        StringBuilder html = new StringBuilder(source.length() + 128);
        boolean unorderedOpen = false;
        boolean orderedOpen = false;
        boolean codeOpen = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (unorderedOpen) { html.append("</ul>"); unorderedOpen = false; }
                if (orderedOpen) { html.append("</ol>"); orderedOpen = false; }
                if (codeOpen) html.append("</tt></pre>");
                else html.append("<pre><tt>");
                codeOpen = !codeOpen;
                continue;
            }
            if (codeOpen) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }
            Matcher heading = HEADING.matcher(trimmed);
            Matcher unordered = UNORDERED.matcher(trimmed);
            Matcher ordered = ORDERED.matcher(trimmed);
            if (heading.matches()) {
                closeLists(html, unorderedOpen, orderedOpen);
                unorderedOpen = false;
                orderedOpen = false;
                int level = Math.min(6, heading.group(1).length());
                html.append("<h").append(level).append(">")
                        .append(inlineHtml(heading.group(2))).append("</h").append(level).append(">");
            } else if (unordered.matches()) {
                if (orderedOpen) { html.append("</ol>"); orderedOpen = false; }
                if (!unorderedOpen) { html.append("<ul>"); unorderedOpen = true; }
                html.append("<li>").append(inlineHtml(unordered.group(1))).append("</li>");
            } else if (ordered.matches()) {
                if (unorderedOpen) { html.append("</ul>"); unorderedOpen = false; }
                if (!orderedOpen) { html.append("<ol>"); orderedOpen = true; }
                html.append("<li>").append(inlineHtml(ordered.group(1))).append("</li>");
            } else {
                if (unorderedOpen) { html.append("</ul>"); unorderedOpen = false; }
                if (orderedOpen) { html.append("</ol>"); orderedOpen = false; }
                if (trimmed.isEmpty()) html.append("<br>");
                else if (trimmed.startsWith(">")) {
                    html.append("<blockquote>").append(inlineHtml(trimmed.substring(1).trim()))
                            .append("</blockquote>");
                } else if (trimmed.matches("[-*_]{3,}")) {
                    html.append("<hr>");
                } else {
                    html.append("<div>").append(inlineHtml(line)).append("</div>");
                }
            }
        }
        if (codeOpen) html.append("</tt></pre>");
        closeLists(html, unorderedOpen, orderedOpen);
        return html.toString();
    }

    private static void closeLists(StringBuilder html, boolean unorderedOpen,
                                   boolean orderedOpen) {
        if (unorderedOpen) html.append("</ul>");
        if (orderedOpen) html.append("</ol>");
    }

    private static String inlineHtml(String value) {
        String html = escapeHtml(value);
        html = replace(LINK, html, "<a href=\"$2\">$1</a>");
        html = replace(CODE, html, "<tt>$1</tt>");
        html = replace(BOLD, html, "<strong>$2</strong>");
        return replace(ITALIC, html, "<i>$1</i>");
    }

    private static String replace(Pattern pattern, String value, String replacement) {
        return pattern.matcher(value).replaceAll(replacement);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
