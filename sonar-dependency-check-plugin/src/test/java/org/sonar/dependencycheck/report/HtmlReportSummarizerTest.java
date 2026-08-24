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
            + "Scan Information (<a href=\"#\" id=\"scanInformationToggle\">show all</a>):\n"
            + "<ul><li class=\"scaninfo hidden\">engine version</li></ul>\n"
            + "<div class=\"\">\n"
            + "<h2 class=\"\">Summary</h2>\n"
            + "<table id=\"summaryTable\"><tr class=\"notvulnerable\"><td>dep</td></tr></table>\n"
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
    void summarizeCutsDetailsAndKeepsSummaryAndFooter() {
        String slim = HtmlReportSummarizer.summarize(REPORT);
        assertTrue(slim.contains("id=\"summaryTable\""));
        assertTrue(slim.contains("Scan Information"));
        assertTrue(slim.contains("This report contains data retrieved from"));
        assertFalse(slim.contains("huge details"));
        assertFalse(slim.contains("Suppressed Vulnerabilities"));
        assertTrue(slim.contains("scanInformationToggle')"), "auto-expand script expected");
        assertTrue(slim.length() < REPORT.length());
    }

    @Test
    void summarizeKeepsReportWhenMarkersAreMissing() {
        String noMarkers = "<html><body><p>some other layout</p></body></html>";
        assertEquals(noMarkers, HtmlReportSummarizer.summarize(noMarkers));
    }

    @Test
    void summarizeKeepsReportWhenFooterPrecedesDetails() {
        String broken = "This report contains data retrieved from <div>x</div>"
                + "<h2 id=\"header-dependencies\">Dependencies</h2>";
        assertEquals(broken, HtmlReportSummarizer.summarize(broken));
    }

    @Test
    void injectsLinkAfterBodyTag() {
        String out = HtmlReportSummarizer.injectFullReportLink(REPORT, "https://ci.example.com/jobs/1/artifacts/report.html");
        int body = out.indexOf("<body");
        int banner = out.indexOf("Full report:");
        int project = out.indexOf("Project:");
        assertTrue(body < banner && banner < project, "banner must sit right after <body>");
        assertTrue(out.contains("href=\"https://ci.example.com/jobs/1/artifacts/report.html\""));
    }

    @Test
    void escapesUrlInBanner() {
        String out = HtmlReportSummarizer.injectFullReportLink(REPORT, "https://ci.example.com/a?b=1&c=<x>\"'");
        assertTrue(out.contains("b=1&amp;c=&lt;x&gt;&quot;&#39;"));
        assertFalse(out.contains("c=<x>"));
    }

    @Test
    void rejectsNonHttpUrls() {
        assertEquals(REPORT, HtmlReportSummarizer.injectFullReportLink(REPORT, "javascript:alert(1)"));
        assertEquals(REPORT, HtmlReportSummarizer.injectFullReportLink(REPORT, "ftp://host/file"));
        assertEquals(REPORT, HtmlReportSummarizer.injectFullReportLink(REPORT, "  "));
        assertEquals(REPORT, HtmlReportSummarizer.injectFullReportLink(REPORT, null));
    }

    @Test
    void acceptsUpperCaseScheme() {
        String out = HtmlReportSummarizer.injectFullReportLink(REPORT, "HTTPS://ci.example.com/r.html");
        assertTrue(out.contains("Full report:"));
    }
}
