/*
 * Dependency-Check Plugin for SonarQube
 * Copyright (C) 2015-2025 dependency-check
 * philipp.dallig@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.dependencycheck.report;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Produces compact variants of the Dependency-Check HTML report for storage
 * inside SonarQube. The full report can stay in the CI archive; storing all of
 * it as a measure bloats the database and slows the UI (dependency-heavy
 * projects easily produce reports beyond 10 MB).
 *
 * Everything here is static markup manipulation, deliberately without any
 * JavaScript: the report page renders the stored value inside an iframe, and
 * the host page's Content-Security-Policy may block the report's inline
 * scripts entirely - toggles simply do not react. Trimmed variants therefore
 * come fully expanded, with the dead controls removed.
 *
 * All markers below are taken from the report template of Dependency-Check;
 * when a marker is missing (layout change, very old scanner), the report is
 * kept as-is - trimming must never lose data silently.
 */
public final class HtmlReportSummarizer {

    /** Start of the per-dependency details section. */
    private static final String DETAILS_MARKER = "<h2 id=\"header-dependencies\">";
    /** Heading right before the summary table. */
    private static final String SUMMARY_H2_MARKER = ">Summary</h2>";
    /** Attribution footer that must survive the cut. */
    private static final String FOOTER_MARKER = "This report contains data retrieved from";
    private static final String FOOTER_DIV = "<div>";
    private static final String BODY_OPEN = "<body";
    private static final String BODY_CLOSE = "</body>";
    private static final String TABLE_MARKER = "<table id=\"summaryTable\"";
    private static final String TABLE_END = "</table>";
    private static final String NOT_VULNERABLE_CLASS = "notvulnerable";
    private static final String SCANINFO_TOGGLE_ID = "id=\"scanInformationToggle\"";

    private HtmlReportSummarizer() {
        // utility class
    }

    /**
     * Scan information (statically expanded) plus the two summary tables:
     * vulnerable dependencies first, then all dependencies, both fully
     * visible. The per-dependency details and the suppressed section are
     * dropped. Falls back to the unmodified report when markers are absent.
     */
    public static String summarize(String html) {
        int summaryEnd = html.indexOf(SUMMARY_H2_MARKER);
        int summaryH2 = summaryEnd < 0 ? -1 : html.lastIndexOf("<h2", summaryEnd);
        String table = extractTable(html);
        int footerDiv = findFooterDiv(html);
        int bodyClose = html.lastIndexOf(BODY_CLOSE);
        if (summaryH2 < 0 || table == null || footerDiv < summaryH2 || bodyClose < footerDiv) {
            return html;
        }
        StringBuilder out = new StringBuilder(html.length() / 8);
        out.append(expandScanInformation(html.substring(0, summaryH2)));
        out.append(buildTablesBlock(table, true));
        // The details cut leaves the project content container open; close it
        // before the footer. HTML parsers tolerate imbalance, but being
        // explicit keeps the fragment well-formed.
        out.append("</div>\n");
        out.append(html, footerDiv, bodyClose);
        out.append("</body>\n</html>");
        return out.toString();
    }

    /**
     * Only the summary table filtered down to vulnerable dependencies.
     */
    public static String summarizeVulnerableOnly(String html) {
        return buildTablesOnly(html, false);
    }

    /**
     * Only the two expanded tables: vulnerable dependencies, then all
     * dependencies.
     */
    public static String summarizeSplitTables(String html) {
        return buildTablesOnly(html, true);
    }

    private static String buildTablesOnly(String html, boolean includeAllTable) {
        int bodyTag = html.indexOf(BODY_OPEN);
        int bodyEnd = bodyTag < 0 ? -1 : html.indexOf('>', bodyTag);
        String table = extractTable(html);
        int footerDiv = findFooterDiv(html);
        int bodyClose = html.lastIndexOf(BODY_CLOSE);
        if (bodyEnd < 0 || table == null || footerDiv < bodyEnd || bodyClose < footerDiv) {
            return html;
        }
        StringBuilder out = new StringBuilder(html.length() / 8);
        out.append(html, 0, bodyEnd + 1);
        out.append(buildTablesBlock(table, includeAllTable));
        out.append(html, footerDiv, bodyClose);
        out.append("</body>\n</html>");
        return out.toString();
    }

    @Nullable
    private static String extractTable(String html) {
        int tableStart = html.indexOf(TABLE_MARKER);
        int tableEnd = tableStart < 0 ? -1 : html.indexOf(TABLE_END, tableStart);
        if (tableEnd < 0) {
            return null;
        }
        return html.substring(tableStart, tableEnd + TABLE_END.length());
    }

    private static int findFooterDiv(String html) {
        int footerText = html.lastIndexOf(FOOTER_MARKER);
        return footerText < 0 ? -1 : html.lastIndexOf(FOOTER_DIV, footerText);
    }

    private static String buildTablesBlock(String table, boolean includeAllTable) {
        // Anchors in the Dependency column point at the per-dependency
        // details, which the trimmed variants drop - a link to nowhere.
        // External links (NVD etc.) are kept.
        String cleanTable = stripLocalAnchors(table);
        StringBuilder out = new StringBuilder(cleanTable.length() * (includeAllTable ? 2 : 1) + 256);
        out.append("\n<h2>Summary</h2>\n");
        out.append("<p><span>Summary of Vulnerable Dependencies</span></p>\n");
        out.append(removeRowsWithClass(cleanTable, NOT_VULNERABLE_CLASS)
                .replace(TABLE_MARKER, "<table id=\"summaryTableVulnerable\""));
        if (includeAllTable) {
            out.append("\n<p><span>Summary of All Dependencies</span></p>\n");
            // The original report hides these rows via the CSS class; the
            // class is dropped so the copy is expanded without any toggle.
            out.append(cleanTable
                    .replace("class=\"" + NOT_VULNERABLE_CLASS + "\"", "class=\"\"")
                    .replace(TABLE_MARKER, "<table id=\"summaryTableAll\""));
        }
        out.append('\n');
        return out.toString();
    }

    /**
     * Statically expands the scan information block: the entries hidden
     * behind the "show all" toggle become visible and the toggle itself is
     * removed - inside SonarQube the report's scripts may be blocked by CSP,
     * leaving the toggle dead.
     */
    static String expandScanInformation(String html) {
        String result = html.replace("class=\"scaninfo hidden\"", "class=\"scaninfo\"");
        int toggleId = result.indexOf(SCANINFO_TOGGLE_ID);
        if (toggleId < 0) {
            return result;
        }
        int anchorStart = result.lastIndexOf("<a ", toggleId);
        int anchorEnd = result.indexOf("</a>", toggleId);
        if (anchorStart < 0 || anchorEnd < 0) {
            return result;
        }
        anchorEnd += "</a>".length();
        // The toggle is rendered as "Scan Information (<a ...>show all</a>):"
        // - the surrounding parentheses go away together with the anchor.
        if (anchorStart > 0 && result.charAt(anchorStart - 1) == '('
                && anchorEnd < result.length() && result.charAt(anchorEnd) == ')') {
            anchorStart--;
            anchorEnd++;
        }
        return result.substring(0, anchorStart) + result.substring(anchorEnd);
    }

    /**
     * Replaces every in-page anchor ({@code <a href="#...">text</a>}) with its
     * plain text. Absolute links are kept untouched.
     */
    static String stripLocalAnchors(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int pos = 0;
        while (true) {
            int a = html.indexOf("<a ", pos);
            if (a < 0) {
                out.append(html, pos, html.length());
                break;
            }
            int openEnd = html.indexOf('>', a);
            int close = html.indexOf("</a>", a);
            if (openEnd < 0 || close < 0 || close < openEnd) {
                out.append(html, pos, html.length());
                break;
            }
            String openTag = html.substring(a, openEnd + 1);
            out.append(html, pos, a);
            if (openTag.contains("href=\"#")) {
                out.append(html, openEnd + 1, close);
            } else {
                out.append(html, a, close + "</a>".length());
            }
            pos = close + "</a>".length();
        }
        return out.toString();
    }

    /**
     * Drops every table row whose opening tag carries the given class token.
     */
    private static String removeRowsWithClass(String table, String classToken) {
        StringBuilder out = new StringBuilder(table.length());
        int pos = 0;
        while (true) {
            int tr = table.indexOf("<tr", pos);
            if (tr < 0) {
                out.append(table, pos, table.length());
                break;
            }
            int openEnd = table.indexOf('>', tr);
            int close = table.indexOf("</tr>", tr);
            if (openEnd < 0 || close < 0) {
                out.append(table, pos, table.length());
                break;
            }
            close += "</tr>".length();
            out.append(table, pos, tr);
            if (!table.substring(tr, openEnd + 1).contains(classToken)) {
                out.append(table, tr, close);
            }
            pos = close;
        }
        return out.toString();
    }

    /**
     * Injects a header right after the opening body tag: the analysed branch
     * and, when configured, a link to the full report (typically a CI
     * artifact). Only http(s) URLs are accepted: the value travels through
     * analysis properties and must not become a script injection into every
     * project's report page.
     */
    public static String injectHeader(String html, @Nullable String url, @Nullable String branch) {
        String safeUrl = null;
        if (StringUtils.isNotBlank(url)) {
            String trimmed = url.trim();
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("https://") || lower.startsWith("http://")) {
                safeUrl = escapeHtml(trimmed);
            }
        }
        String safeBranch = StringUtils.isBlank(branch) ? null : escapeHtml(branch.trim());
        if (safeUrl == null && safeBranch == null) {
            return html;
        }
        StringBuilder banner = new StringBuilder(256);
        banner.append("\n<div style=\"padding:8px 12px;margin:8px;border:1px solid #b3b3b3;"
                + "background:#f4f4f4;font-family:sans-serif;\">");
        if (safeBranch != null) {
            banner.append("Branch: <b>").append(safeBranch).append("</b>");
        }
        if (safeUrl != null) {
            if (safeBranch != null) {
                banner.append(" &nbsp;|&nbsp; ");
            }
            banner.append("Full report: <a href=\"").append(safeUrl)
                    .append("\" target=\"_blank\" rel=\"noopener\">").append(safeUrl).append("</a>");
        }
        banner.append("</div>\n");
        int bodyTag = html.indexOf(BODY_OPEN);
        if (bodyTag >= 0) {
            int bodyEnd = html.indexOf('>', bodyTag);
            if (bodyEnd > 0) {
                return html.substring(0, bodyEnd + 1) + banner + html.substring(bodyEnd + 1);
            }
        }
        int bodyClose = html.lastIndexOf(BODY_CLOSE);
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + banner + html.substring(bodyClose);
        }
        return banner + html;
    }

    /**
     * Kept for source compatibility with earlier fork revisions.
     */
    public static String injectFullReportLink(String html, @Nullable String url) {
        return injectHeader(html, url, null);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
