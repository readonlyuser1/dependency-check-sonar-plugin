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
 * Produces a compact variant of the Dependency-Check HTML report for storage
 * inside SonarQube. The full report can stay in the CI archive; storing all of
 * it as a measure bloats the database and slows the UI (dependency-heavy
 * projects easily produce reports beyond 10 MB).
 *
 * The summary keeps the report head (styles, scripts), the scan information
 * block and the summary table (which contains both the vulnerable and the
 * complete dependency list, switched by the report's own toggle), and drops
 * the per-dependency details and the suppressed-vulnerabilities section.
 * All markers below are taken from the report template of Dependency-Check;
 * when a marker is missing (layout change, very old scanner), the report is
 * kept as-is — trimming must never lose data silently.
 */
public final class HtmlReportSummarizer {

    /** Start of the per-dependency details section. */
    private static final String DETAILS_MARKER = "<h2 id=\"header-dependencies\">";
    /** Attribution footer that must survive the cut. */
    private static final String FOOTER_MARKER = "This report contains data retrieved from";
    private static final String FOOTER_DIV = "<div>";
    private static final String BODY_OPEN = "<body";
    private static final String BODY_CLOSE = "</body>";
    private static final String TABLE_MARKER = "<table id=\"summaryTable\"";
    private static final String TABLE_END = "</table>";
    private static final String NOT_VULNERABLE_CLASS = "notvulnerable";

    private HtmlReportSummarizer() {
        // utility class
    }

    /**
     * Cuts the per-dependency details and the suppressed section, keeping the
     * head, the scan information and the summary table. Falls back to the
     * unmodified report when the expected markers are absent.
     */
    public static String summarize(String html) {
        int cut = html.indexOf(DETAILS_MARKER);
        if (cut < 0) {
            return html;
        }
        int footerText = html.lastIndexOf(FOOTER_MARKER);
        if (footerText < cut) {
            return html;
        }
        int footerDiv = html.lastIndexOf(FOOTER_DIV, footerText);
        if (footerDiv < cut) {
            return html;
        }
        // The details cut leaves the project content container open; close it
        // before the footer. HTML parsers tolerate imbalance, but being
        // explicit keeps the fragment well-formed.
        return html.substring(0, cut) + "</div>\n" + html.substring(footerDiv)
                + expandScanInformationScript();
    }

    /**
     * The scan information block hides part of its entries behind a
     * "show all" toggle. In the summary variant everything that is left
     * should be visible right away, so the report's own toggle is triggered
     * once the page is loaded — labels and repeated toggling keep working.
     */
    private static String expandScanInformationScript() {
        return "\n<script type=\"text/javascript\">window.addEventListener('load',function(){"
                + "var t=document.getElementById('scanInformationToggle');if(t){t.click();}});</script>\n";
    }

    /**
     * Keeps only the summary table filtered down to vulnerable dependencies.
     * The report head (styles, scripts) and the attribution footer survive;
     * scan information and everything else is dropped.
     */
    public static String summarizeVulnerableOnly(String html) {
        return buildTablesOnly(html, false);
    }

    /**
     * Keeps two expanded tables and nothing else: first the vulnerable
     * dependencies, then all dependencies with every row visible (the
     * original report hides non-vulnerable rows behind a toggle).
     */
    public static String summarizeSplitTables(String html) {
        return buildTablesOnly(html, true);
    }

    private static String buildTablesOnly(String html, boolean includeAllTable) {
        int bodyTag = html.indexOf(BODY_OPEN);
        int bodyEnd = bodyTag < 0 ? -1 : html.indexOf('>', bodyTag);
        int tableStart = html.indexOf(TABLE_MARKER);
        int tableEnd = tableStart < 0 ? -1 : html.indexOf(TABLE_END, tableStart);
        int footerText = html.lastIndexOf(FOOTER_MARKER);
        int footerDiv = footerText < 0 ? -1 : html.lastIndexOf(FOOTER_DIV, footerText);
        int bodyClose = html.lastIndexOf(BODY_CLOSE);
        if (bodyEnd < 0 || tableEnd < 0 || footerDiv < tableEnd || bodyClose < footerDiv) {
            return html;
        }
        String table = html.substring(tableStart, tableEnd + TABLE_END.length());
        StringBuilder out = new StringBuilder(html.length() / 4);
        out.append(html, 0, bodyEnd + 1);
        out.append("\n<h2>Summary</h2>\n");
        out.append("<p><span>Summary of Vulnerable Dependencies</span></p>\n");
        out.append(removeRowsWithClass(table, NOT_VULNERABLE_CLASS)
                .replace(TABLE_MARKER, "<table id=\"summaryTableVulnerable\""));
        if (includeAllTable) {
            out.append("\n<p><span>Summary of All Dependencies</span></p>\n");
            // The original report hides these rows via the CSS class; the
            // class is dropped so the copy is expanded without any toggle.
            out.append(table
                    .replace("class=\"" + NOT_VULNERABLE_CLASS + "\"", "class=\"\"")
                    .replace(TABLE_MARKER, "<table id=\"summaryTableAll\""));
        }
        // The report wires sorting to the original table id only; the copies
        // need their own initialisation. Guarded: no jQuery - no sorting,
        // but the tables still render.
        out.append("\n<script type=\"text/javascript\">window.addEventListener('load',function(){")
                .append("if(window.$&&$.fn&&$.fn.stupidtable){$(\"#summaryTableVulnerable\").stupidtable();")
                .append("$(\"#summaryTableAll\").stupidtable();}});</script>\n");
        out.append(html, footerDiv, bodyClose);
        out.append("</body>\n</html>");
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
     * Injects a banner with a link to the full report (typically a CI
     * artifact) right after the opening body tag. Only http(s) URLs are
     * accepted: the value travels through analysis properties and must not
     * become a script injection into every project's report page.
     */
    public static String injectFullReportLink(String html, @Nullable String url) {
        if (StringUtils.isBlank(url)) {
            return html;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return html;
        }
        String safeUrl = trimmed
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        String banner = "\n<div style=\"padding:8px 12px;margin:8px;border:1px solid #b3b3b3;"
                + "background:#f4f4f4;font-family:sans-serif;\">"
                + "Full report: <a href=\"" + safeUrl + "\" target=\"_blank\" rel=\"noopener\">"
                + safeUrl + "</a></div>\n";
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
}
