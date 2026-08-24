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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmlReportSummarizerTest {

    private static final String REPORT = "<html><head><script>var x=1;</script></head>"
            + "<body class=\"report\">\n"
            + "<h2 class=\"\">Project:&nbsp;</h2>\n"
            + "Scan Information (<a href=\"#\" title=\"Click to toggle display\" id=\"scanInformationToggle\">show all</a>):\n"
            + "<ul><li class=\"scaninfo\">version</li><li class=\"scaninfo hidden\">engine version</li></ul>\n"
            + "<div class=\"\">\n"
            + "<h2 class=\"\">Summary</h2>\n"
            + "<p><span id=\"tablecaption-plain\">Summary of Vulnerable Dependencies </span>"
            + "<a href=\"#\" id=\"vulnerabilityDisplayToggle\">(click to show all)</a></p>\n"
            + "<table id=\"summaryTable\" class=\"lined\">"
            + "<thead><tr style=\"text-align:left\"><th>Dependency</th></tr></thead>"
            + "<tr class=\" vulnerable\"><td><a href=\"#l1_abc\">bad-dep</a> "
            + "<a href=\"https://nvd.nist.gov/x\">CVE-1</a></td></tr>"
            + "<tr class=\"notvulnerable\"><td><a href=\"#l2_def\">good-dep</a></td></tr></table>\n"
            + "<h2 id=\"header-dependencies\">Dependencies (vulnerable)</h2>\n"
            + "<h3>huge details</h3><p>lots of text lots of text lots of text lots of text"
            + " lots of text lots of text lots of text lots of text lots of text lots of text"
            + " lots of text lots of text lots of text lots of text lots of text lots of text"
            + " lots of text lots of text lots of text lots of text lots of text lots of text</p>\n"
            + "<h2>Suppressed Vulnerabilities</h2><p>more text</p>\n"
            + "</div>\n"
            + "<div>\n<br/><br/>\nThis report contains data retrieved from the "
            + "<a href=\"https://nvd.nist.gov\">National Vulnerability Database</a>.\n</div>\n"
            + "</body></html>";

    @Test
    void summarizeKeepsScanInfoAndBothTablesStaticallyExpanded() {
        String slim = HtmlReportSummarizer.summarize(REPORT);
        assertTrue(slim.contains("Scan Information"));
        assertFalse(slim.contains("scanInformationToggle"), "toggle must be removed");
        assertFalse(slim.contains("scaninfo hidden"), "hidden scan info entries must be visible");
        assertTrue(slim.contains("id=\"summaryTableVulnerable\""));
        assertTrue(slim.contains("id=\"summaryTableAll\""));
        assertTrue(slim.contains("Summary of All Dependencies"));
        assertFalse(slim.contains("class=\"notvulnerable\""), "all-table rows must be visible");
        assertFalse(slim.contains("huge details"));
        assertFalse(slim.contains("Suppressed Vulnerabilities"));
        assertFalse(slim.contains("vulnerabilityDisplayToggle"), "dead table toggle must not survive");
        assertTrue(slim.contains("This report contains data retrieved from"));
        assertTrue(slim.length() < REPORT.length());
    }

    @Test
    void summarizeStripsDependencyAnchorsButKeepsExternalLinks() {
        String slim = HtmlReportSummarizer.summarize(REPORT);
        assertFalse(slim.contains("href=\"#l1_abc\""), "local anchors lead nowhere in trimmed reports");
        assertTrue(slim.contains("bad-dep"));
        assertTrue(slim.contains("href=\"https://nvd.nist.gov/x\""), "external links must be kept");
    }

    @Test
    void summarizeKeepsReportWhenMarkersAreMissing() {
        String noMarkers = "<html><body><p>some other layout</p></body></html>";
        assertEquals(noMarkers, HtmlReportSummarizer.summarize(noMarkers));
    }

    @Test
    void vulnerableOnlyKeepsOnlyVulnerableRows() {
        String slim = HtmlReportSummarizer.summarizeVulnerableOnly(REPORT);
        assertTrue(slim.contains("bad-dep"));
        assertFalse(slim.contains("good-dep"));
        assertFalse(slim.contains("Scan Information"));
        assertFalse(slim.contains("huge details"));
        assertTrue(slim.contains("id=\"summaryTableVulnerable\""));
        assertFalse(slim.contains("id=\"summaryTableAll\""));
        assertTrue(slim.contains("<th>Dependency</th>"), "header row must survive");
        assertFalse(slim.contains("href=\"#l1_abc\""));
        assertTrue(slim.contains("This report contains data retrieved from"));
        assertTrue(slim.endsWith("</body>\n</html>"));
    }

    @Test
    void splitTablesKeepsBothTablesExpanded() {
        String slim = HtmlReportSummarizer.summarizeSplitTables(REPORT);
        assertTrue(slim.contains("id=\"summaryTableVulnerable\""));
        assertTrue(slim.contains("id=\"summaryTableAll\""));
        assertTrue(slim.contains("Summary of All Dependencies"));
        assertFalse(slim.contains("class=\"notvulnerable\""));
        int allTable = slim.indexOf("id=\"summaryTableAll\"");
        assertTrue(slim.indexOf("good-dep", allTable) > 0, "all-table keeps non-vulnerable rows");
        assertFalse(slim.contains("Scan Information"));
        assertFalse(slim.contains("huge details"));
    }

    @Test
    void tablesOnlyModesKeepReportWhenMarkersAreMissing() {
        String noMarkers = "<html><body><p>some other layout</p></body></html>";
        assertEquals(noMarkers, HtmlReportSummarizer.summarizeVulnerableOnly(noMarkers));
        assertEquals(noMarkers, HtmlReportSummarizer.summarizeSplitTables(noMarkers));
    }

    @Test
    void expandScanInformationRemovesToggleAndParentheses() {
        String expanded = HtmlReportSummarizer.expandScanInformation(REPORT);
        assertTrue(expanded.contains("Scan Information :")
                || expanded.contains("Scan Information:")
                || expanded.contains("Scan Information \n:"),
                "parentheses around the toggle must go away, got: "
                        + expanded.substring(expanded.indexOf("Scan Information"),
                                expanded.indexOf("Scan Information") + 40));
        assertFalse(expanded.contains(">show all</a>"), "the toggle anchor itself must be gone");
        assertFalse(expanded.contains("scanInformationToggle"));
    }

    @Test
    void injectsHeaderWithBranchAndLink() {
        String out = HtmlReportSummarizer.injectHeader(REPORT, "https://ci.example.com/jobs/1/artifacts/report.html", "master");
        int body = out.indexOf("<body");
        int banner = out.indexOf("Branch: <b>master</b>");
        int project = out.indexOf("Project:");
        assertTrue(body < banner && banner < project, "banner must sit right after <body>");
        assertTrue(out.contains("href=\"https://ci.example.com/jobs/1/artifacts/report.html\""));
    }

    @Test
    void injectsBranchOnlyWhenNoUrl() {
        String out = HtmlReportSummarizer.injectHeader(REPORT, "", "feature/x");
        assertTrue(out.contains("Branch: <b>feature/x</b>"));
        assertFalse(out.contains("Full report:"));
    }

    @Test
    void escapesUrlAndBranchInHeader() {
        String out = HtmlReportSummarizer.injectHeader(REPORT, "https://ci.example.com/a?b=1&c=<x>\"'", "br<script>");
        assertTrue(out.contains("b=1&amp;c=&lt;x&gt;&quot;&#39;"));
        assertTrue(out.contains("br&lt;script&gt;"));
        assertFalse(out.contains("c=<x>"));
    }

    @Test
    void rejectsNonHttpUrls() {
        assertEquals(REPORT, HtmlReportSummarizer.injectHeader(REPORT, "javascript:alert(1)", null));
        assertEquals(REPORT, HtmlReportSummarizer.injectHeader(REPORT, "ftp://host/file", ""));
        assertEquals(REPORT, HtmlReportSummarizer.injectHeader(REPORT, "  ", null));
        assertEquals(REPORT, HtmlReportSummarizer.injectHeader(REPORT, null, null));
    }

    @Test
    void acceptsUpperCaseScheme() {
        String out = HtmlReportSummarizer.injectHeader(REPORT, "HTTPS://ci.example.com/r.html", null);
        assertTrue(out.contains("Full report:"));
    }

    @Test
    void stripLocalAnchorsHandlesMixedContent() {
        String in = "<td><a href=\"#x\">one</a> and <a href=\"https://e.com\">two</a></td><a href=\"#y\">three</a>";
        assertEquals("<td>one and <a href=\"https://e.com\">two</a></td>three",
                HtmlReportSummarizer.stripLocalAnchors(in));
    }
}
