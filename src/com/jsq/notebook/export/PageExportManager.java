package com.jsq.notebook.export;

import com.jsq.notebook.model.CustomField;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Page;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.AttachmentStorageManager;
import com.jsq.notebook.util.DateUtils;
import com.jsq.notebook.util.ProgressListener;
import com.jsq.notebook.util.StringUtils;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Vector;

/**
 * Handles exporting Pages and whole Notebooks to the SD Card using JSR-75 FileConnection.
 *
 * <p>Export layout produced by exportNotebook():
 * <pre>
 * Export/
 *   &lt;NotebookName&gt;/
 *     index.html          (notebook overview with links to all sections/pages)
 *     &lt;SectionName&gt;/
 *       &lt;PageTitle&gt;/
 *         index.html      (rendered page content)
 *         metadata.json   (structured metadata for .NET bridge)
 *         &lt;image.jpg&gt;    (raw image attachments)
 * </pre>
 *
 * <p>All APIs are unsigned JSR-75 FileConnection only.
 */
public class PageExportManager {

    private static PageExportManager instance;

    private PageExportManager() {
    }

    public static synchronized PageExportManager getInstance() {
        if (instance == null) {
            instance = new PageExportManager();
        }
        return instance;
    }

    /**
     * Exports a single page to the default SD Card export folder.
     */
    public String exportPage(Notebook notebook, Section section, Page page) throws IOException {
        String baseDir = AttachmentStorageManager.getInstance().getStorageRoot() +
                         "BlackBerry/documents/JSQNotebook/Export/";
        return exportPageTo(notebook, section, page, baseDir);
    }


    /**
     * Exports an entire Notebook into a caller-supplied base directory on the SD Card.
     *
     * <p>Produces the structure:
     * <pre>
     *   &lt;baseExportUrl&gt;&lt;NotebookName&gt;/
     *     &lt;NotebookName&gt;.jnb   -- plain-text manifest (name + description)
     *     index.html            -- HTML overview linking all sections/pages
     *     &lt;SectionName&gt;/
     *       &lt;PageTitle&gt;/
     *         index.html        -- rendered page content
     *         metadata.json     -- structured JSON for .NET bridge
     *         &lt;image.jpg&gt;      -- raw image attachments
     * </pre>
     *
     * @param notebook      the Notebook to export (must not be null)
     * @param baseExportUrl file:// URL of the parent directory to export into
     * @return the file:// URL of the notebook root folder that was created
     * @throws IOException if any I/O error occurs
     */
    public String exportNotebook(Notebook notebook, String baseExportUrl) throws IOException {
        return exportNotebook(notebook, baseExportUrl, null);
    }

    /**
     * Exports an entire Notebook into a caller-supplied base directory on the SD Card
     * with optional progress reporting.
     *
     * <p>Produces the structure:
     * <pre>
     *   &lt;baseExportUrl&gt;&lt;NotebookName&gt;/
     *     &lt;NotebookName&gt;.jnb   -- plain-text manifest (name + description)
     *     index.html            -- HTML overview linking all sections/pages
     *     &lt;SectionName&gt;/
     *       &lt;PageTitle&gt;/
     *         index.html        -- rendered page content
     *         metadata.json     -- structured JSON for .NET bridge and fast re-import
     *         &lt;image.jpg&gt;      -- raw image attachments
     * </pre>
     *
     * @param notebook      the Notebook to export (must not be null)
     * @param baseExportUrl file:// URL of the parent directory to export into
     * @param listener      optional ProgressListener for real-time progress callbacks
     * @return the file:// URL of the notebook root folder that was created
     * @throws IOException if any I/O error occurs
     */
    public String exportNotebook(Notebook notebook, String baseExportUrl, ProgressListener listener) throws IOException {
        if (notebook == null) {
            throw new IllegalArgumentException("Notebook cannot be null");
        }
        if (baseExportUrl == null || baseExportUrl.length() == 0) {
            throw new IllegalArgumentException("baseExportUrl cannot be empty");
        }
        if (!baseExportUrl.endsWith("/")) {
            baseExportUrl = baseExportUrl + "/";
        }

        Vector sections = notebook.getSections();
        int secCount = sections != null ? sections.size() : 0;
        int totalPages = 0;
        for (int si = 0; si < secCount; si++) {
            Section section = (Section) sections.elementAt(si);
            Vector pages = section.getPages();
            totalPages += pages != null ? pages.size() : 0;
        }

        int totalUnits = totalPages + 2;
        int currentUnit = 0;
        if (listener != null) {
            listener.onProgress(currentUnit, totalUnits, "Initializing export...");
        }

        AttachmentStorageManager storage = AttachmentStorageManager.getInstance();

        // Notebook root folder: <baseExportUrl>/<SafeNotebookName>/
        String safeNbName = StringUtils.sanitizeFileName(notebook.getName());
        String notebookDir = baseExportUrl + safeNbName + "/";
        storage.ensureDirectoryExists(notebookDir);

        // 1. Write .jnb manifest (plain-text, used by importNotebook)
        String jnbContent = buildJnbManifest(notebook);
        writeStringToFile(notebookDir + safeNbName + ".jnb", jnbContent);

        currentUnit++;
        if (listener != null) {
            listener.onProgress(currentUnit, totalUnits, "Writing manifest...");
        }

        int pageIndex = 0;
        int totalSections = 0;

        // 2. Build notebook-level HTML index
        StringBuffer nbIndex = new StringBuffer(2048);
        nbIndex.append("<!DOCTYPE html>\n<html>\n<head>\n");
        nbIndex.append("<meta charset=\"utf-8\" />\n");
        nbIndex.append("<title>").append(StringUtils.htmlEscape(notebook.getName())).append("</title>\n");
        nbIndex.append("<style>\n");
        nbIndex.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;margin:30px;color:#222;background:#fafafa}\n");
        nbIndex.append(".container{max-width:800px;margin:0 auto;background:#fff;padding:30px;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.08)}\n");
        nbIndex.append("h1{color:#1a5276;border-bottom:2px solid #2980b9;padding-bottom:8px}\n");
        nbIndex.append("h2{color:#2c3e50;margin-top:24px}\n");
        nbIndex.append("ul{list-style:none;padding:0}\n");
        nbIndex.append("li a{display:block;padding:6px 10px;border-radius:4px;color:#2980b9;text-decoration:none}\n");
        nbIndex.append("li a:hover{background:#eef2f7}\n");
        nbIndex.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");
        nbIndex.append("<h1>").append(StringUtils.htmlEscape(notebook.getName())).append("</h1>\n");
        if (notebook.getDescription() != null && notebook.getDescription().length() > 0) {
            nbIndex.append("<p>").append(StringUtils.htmlEscape(notebook.getDescription())).append("</p>\n");
        }

        // 3. Iterate sections
        for (int si = 0; si < secCount; si++) {
            Section section = (Section) sections.elementAt(si);
            totalSections++;

            String safeSecName = StringUtils.sanitizeFileName(section.getName());
            String sectionDir = notebookDir + safeSecName + "/";
            storage.ensureDirectoryExists(sectionDir);

            nbIndex.append("<h2>").append(StringUtils.htmlEscape(section.getName())).append("</h2>\n<ul>\n");

            Vector pages = section.getPages();
            int pageCount = pages != null ? pages.size() : 0;
            for (int pi = 0; pi < pageCount; pi++) {
                Page page = (Page) pages.elementAt(pi);
                pageIndex++;

                if (listener != null) {
                    String title = page.getTitle();
                    if (title.length() > 22) {
                        title = title.substring(0, 19) + "...";
                    }
                    listener.onProgress(currentUnit + pageIndex, totalUnits, "Exporting: " + title);
                }

                exportPageTo(notebook, section, page, sectionDir);

                String safePageName = StringUtils.sanitizeFileName(page.getTitle());
                nbIndex.append("  <li><a href=\"")
                       .append(StringUtils.htmlEscape(safeSecName)).append("/")
                       .append(StringUtils.htmlEscape(safePageName)).append("/index.html\">")
                       .append(StringUtils.htmlEscape(page.getTitle())).append("</a></li>\n");
            }

            nbIndex.append("</ul>\n");
        }

        // Footer
        nbIndex.append("<hr />\n<p style=\"font-size:12px;color:#7f8c8d\">");
        nbIndex.append(totalSections).append(" section").append(totalSections == 1 ? "" : "s").append(", ");
        nbIndex.append(pageIndex).append(" page").append(pageIndex == 1 ? "" : "s").append(" exported.");
        nbIndex.append("</p>\n</div>\n</body>\n</html>\n");

        writeStringBufferToFile(notebookDir + "index.html", nbIndex);

        if (listener != null) {
            listener.onProgress(totalUnits, totalUnits, "Export complete!");
        }

        return notebookDir;
    }

    /**
     * Builds the plain-text .jnb manifest content.
     * Format (UTF-8, one field per line):
     * <pre>
     *   JNB:1
     *   name:&lt;notebook name&gt;
     *   description:&lt;notebook description&gt;
     * </pre>
     */
    private String buildJnbManifest(Notebook notebook) {
        StringBuffer sb = new StringBuffer();
        sb.append("JNB:1\n");
        sb.append("name:").append(escapeManifestValue(notebook.getName())).append("\n");
        sb.append("description:").append(escapeManifestValue(notebook.getDescription())).append("\n");
        return sb.toString();
    }

    /**
     * Escapes newlines in manifest field values so each field stays on one line.
     */
    private String escapeManifestValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(value.length());
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Exports a single Page into a dedicated subfolder under baseExportUrl.
     * Generates index.html, metadata.json, and copies raw image attachments.
     */
    public String exportPageTo(Notebook notebook, Section section, Page page, String baseExportUrl) throws IOException {
        if (page == null) {
            throw new IllegalArgumentException("Page cannot be null");
        }

        if (!baseExportUrl.endsWith("/")) {
            baseExportUrl = baseExportUrl + "/";
        }

        String safeFolderName = StringUtils.sanitizeFileName(page.getTitle());
        String pageExportFolder = baseExportUrl + safeFolderName + "/";

        AttachmentStorageManager storage = AttachmentStorageManager.getInstance();
        storage.ensureDirectoryExists(pageExportFolder);

        // 1. Identify all image attachments referenced in notes
        Vector imageNames = extractImagePlaceholders(page.getNotes());

        // 2. Copy image attachments directly into the export folder as raw binaries
        int imageCount = imageNames.size();
        for (int i = 0; i < imageCount; i++) {
            String imgName = (String) imageNames.elementAt(i);
            String srcUrl = storage.getAttachmentDirectoryUrl() + imgName;
            String destUrl = pageExportFolder + imgName;
            try {
                storage.copyFile(srcUrl, destUrl);
            } catch (Exception e) {
                // If attachment file was missing on SD Card, proceed with remaining files
            }
        }

        // 3. Write index.html using StringBuffer directly (avoids heap duplicate String)
        StringBuffer htmlContent = buildHtmlContent(notebook, section, page, imageNames);
        writeStringBufferToFile(pageExportFolder + "index.html", htmlContent);

        // 4. Write metadata.json for .NET bridge and fast re-import consumption
        StringBuffer jsonContent = buildMetadataJson(notebook, section, page, imageNames);
        writeStringBufferToFile(pageExportFolder + "metadata.json", jsonContent);

        return pageExportFolder;
    }

    /**
     * Parses notes to find all occurrences of [Image: filename].
     */
    public static Vector extractImagePlaceholders(String notes) {
        Vector images = new Vector();
        if (notes == null) {
            return images;
        }

        int searchIdx = 0;
        String prefix = "[Image:";
        int prefixLen = prefix.length();

        while (true) {
            int start = notes.indexOf(prefix, searchIdx);
            if (start < 0) {
                break;
            }
            int end = notes.indexOf("]", start);
            if (end < 0) {
                break;
            }

            String filename = notes.substring(start + prefixLen, end).trim();
            if (filename.length() > 0 && !images.contains(filename)) {
                images.addElement(filename);
            }
            searchIdx = end + 1;
        }
        return images;
    }

    /**
     * Constructs rich HTML document with metadata table and rendered markdown notes.
     * Uses StringBuffer to minimize heap churn.
     */
    private StringBuffer buildHtmlContent(Notebook notebook, Section section, Page page, Vector images) {
        StringBuffer sb = new StringBuffer(2048);

        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"utf-8\" />\n");
        sb.append("<title>").append(StringUtils.htmlEscape(page.getTitle())).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 30px; color: #222; background: #fafafa; }\n");
        sb.append(".container { max-width: 800px; margin: 0 auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n");
        sb.append("h1.page-title { margin-top: 0; color: #1a5276; border-bottom: 2px solid #2980b9; padding-bottom: 8px; }\n");
        sb.append(".metadata-box { background: #eef2f7; padding: 15px; border-radius: 6px; margin-bottom: 24px; font-size: 14px; }\n");
        sb.append(".metadata-box table { border-collapse: collapse; width: 100%; }\n");
        sb.append(".metadata-box td { padding: 4px 8px; vertical-align: top; }\n");
        sb.append(".metadata-box td.label { font-weight: bold; width: 140px; color: #4a6572; }\n");
        sb.append(".notes-body { line-height: 1.6; font-size: 16px; }\n");
        sb.append(".notes-body h1 { color: #2c3e50; font-size: 24px; margin-top: 20px; }\n");
        sb.append(".notes-body h2 { color: #34495e; font-size: 20px; margin-top: 16px; }\n");
        sb.append(".notes-body h3 { color: #5d6d7e; font-size: 17px; margin-top: 12px; }\n");
        sb.append(".notes-body h4 { color: #7f8c8d; font-size: 15px; margin-top: 10px; }\n");
        sb.append("table.onenote-table { border-collapse: collapse; width: 100%; margin: 16px 0; font-size: 14px; }\n");
        sb.append("table.onenote-table th, table.onenote-table td { border: 1px solid #d0d7de; padding: 8px 12px; text-align: left; }\n");
        sb.append("table.onenote-table th { background-color: #f6f8fa; font-weight: 600; color: #24292f; }\n");
        sb.append("table.onenote-table tr:nth-child(even) { background-color: #fcfcfc; }\n");
        sb.append("ul.checklist { list-style-type: none; padding-left: 4px; margin: 10px 0; }\n");
        sb.append("ul.checklist li { margin: 6px 0; }\n");
        sb.append("ul.checklist li input[type=\"checkbox\"] { margin-right: 8px; vertical-align: middle; }\n");
        sb.append("ul.checklist li.completed { text-decoration: line-through; color: #8c959f; }\n");
        sb.append("ol.onenote-ol { padding-left: 24px; margin: 10px 0; }\n");
        sb.append("ol.onenote-ol li { margin: 4px 0; }\n");
        sb.append("ul.onenote-ul { padding-left: 20px; margin: 10px 0; }\n");
        sb.append("ul.onenote-ul li { margin: 4px 0; }\n");
        sb.append("blockquote.onenote-callout { border-left: 4px solid #7719aa; background: #faf8fc; margin: 14px 0; padding: 10px 16px; border-radius: 0 4px 4px 0; color: #4a5568; font-style: italic; }\n");
        sb.append("pre.code-block { background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px; padding: 12px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 13px; line-height: 1.45; overflow-x: auto; margin: 14px 0; }\n");
        sb.append("code.inline-code { background: #eff1f3; padding: 2px 5px; border-radius: 4px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 85%; color: #900; }\n");
        sb.append("mark { background-color: #fff3a8; padding: 1px 4px; border-radius: 2px; }\n");
        sb.append("del { color: #8c959f; }\n");
        sb.append("u { text-decoration: underline; }\n");
        sb.append(".badge-tag { display: inline-block; padding: 2px 6px; font-size: 11px; font-weight: bold; border-radius: 3px; margin-right: 6px; }\n");
        sb.append(".badge-important { background: #ffebe9; color: #cf222e; border: 1px solid #ff8182; }\n");
        sb.append(".badge-question { background: #ddf4ff; color: #0969da; border: 1px solid #54aeff; }\n");
        sb.append(".badge-star { background: #fff8c5; color: #9a6700; border: 1px solid #d4a72c; }\n");
        sb.append("hr.divider { border: 0; height: 1px; background: #e1e4e8; margin: 20px 0; }\n");
        sb.append(".image-wrapper { margin: 18px 0; text-align: center; }\n");
        sb.append(".image-wrapper img { max-width: 100%; height: auto; border: 1px solid #ddd; border-radius: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }\n");
        sb.append(".image-caption { font-size: 12px; color: #7f8c8d; margin-top: 4px; }\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");

        // Page Header
        sb.append("<h1 class=\"page-title\">").append(StringUtils.htmlEscape(page.getTitle())).append("</h1>\n");

        // Metadata Table
        sb.append("<div class=\"metadata-box\">\n<table>\n");
        if (notebook != null) {
            sb.append("<tr><td class=\"label\">Notebook:</td><td>").append(StringUtils.htmlEscape(notebook.getName())).append("</td></tr>\n");
        }
        if (section != null) {
            sb.append("<tr><td class=\"label\">Section:</td><td>").append(StringUtils.htmlEscape(section.getName())).append("</td></tr>\n");
        }
        sb.append("<tr><td class=\"label\">Created:</td><td>").append(DateUtils.formatDateTime(page.getCreatedTime())).append("</td></tr>\n");
        sb.append("<tr><td class=\"label\">Modified:</td><td>").append(DateUtils.formatDateTime(page.getModifiedTime())).append("</td></tr>\n");

        // Custom Fields
        Vector customFields = page.getCustomFields();
        int cfCount = customFields != null ? customFields.size() : 0;
        for (int i = 0; i < cfCount; i++) {
            CustomField cf = (CustomField) customFields.elementAt(i);
            sb.append("<tr><td class=\"label\">")
              .append(StringUtils.htmlEscape(cf.getLabel()))
              .append(":</td><td>")
              .append(StringUtils.htmlEscape(cf.getValue()))
              .append("</td></tr>\n");
        }
        sb.append("</table>\n</div>\n");

        // Notes Body
        sb.append("<div class=\"notes-body\">\n");
        renderNotesHtml(sb, page.getNotes());
        sb.append("\n</div>\n");

        sb.append("</div>\n</body>\n</html>\n");
        return sb;
    }

    /**
     * Converts notes markdown formatting, tables, checklists, callouts, and image placeholders to HTML tags.
     */
    private void renderNotesHtml(StringBuffer sb, String notes) {
        if (notes == null || notes.length() == 0) {
            sb.append("<p><em>No content</em></p>");
            return;
        }

        Vector lines = StringUtils.splitLines(notes);
        int lineCount = lines.size();
        boolean inParagraph = false;

        for (int i = 0; i < lineCount; i++) {
            String line = (String) lines.elementAt(i);
            String trimmed = line.trim();

            if (trimmed.length() == 0) {
                if (inParagraph) {
                    sb.append("</p>\n");
                    inParagraph = false;
                }
                continue;
            }

            // Code Block: ```...```
            if (trimmed.startsWith("```")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<pre class=\"code-block\"><code>");
                while (i + 1 < lineCount) {
                    i++;
                    String nextLine = (String) lines.elementAt(i);
                    if (nextLine.trim().startsWith("```")) {
                        break;
                    }
                    sb.append(StringUtils.htmlEscape(nextLine)).append("\n");
                }
                sb.append("</code></pre>\n");
                continue;
            }

            // Table: | col1 | col2 |
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                Vector tableLines = new Vector();
                tableLines.addElement(trimmed);
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (nextLine.startsWith("|") && nextLine.endsWith("|")) {
                        tableLines.addElement(nextLine);
                        i++;
                    } else {
                        break;
                    }
                }
                renderTableToHtml(sb, tableLines);
                continue;
            }

            // Blockquote / Callout: >
            if (trimmed.startsWith(">")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<blockquote class=\"onenote-callout\">\n<p>");
                sb.append(formatInlineMarkup(stripQuote(trimmed)));
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (nextLine.startsWith(">")) {
                        sb.append("<br />\n").append(formatInlineMarkup(stripQuote(nextLine)));
                        i++;
                    } else {
                        break;
                    }
                }
                sb.append("</p>\n</blockquote>\n");
                continue;
            }

            // Checklist Item: [ ] / [x]
            if (isTaskLine(trimmed)) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<ul class=\"checklist\">\n");
                renderTaskLineToHtml(sb, trimmed);
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (isTaskLine(nextLine)) {
                        renderTaskLineToHtml(sb, nextLine);
                        i++;
                    } else {
                        break;
                    }
                }
                sb.append("</ul>\n");
                continue;
            }

            // Numbered List: 1. / 2.
            if (isNumberedLine(trimmed)) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<ol class=\"onenote-ol\">\n");
                int dotIdx = trimmed.indexOf(". ");
                sb.append("  <li>").append(formatInlineMarkup(trimmed.substring(dotIdx + 2))).append("</li>\n");
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (isNumberedLine(nextLine)) {
                        int nextDot = nextLine.indexOf(". ");
                        sb.append("  <li>").append(formatInlineMarkup(nextLine.substring(nextDot + 2))).append("</li>\n");
                        i++;
                    } else {
                        break;
                    }
                }
                sb.append("</ol>\n");
                continue;
            }

            // Bullet List: - / * / •
            if (isBulletLine(trimmed)) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<ul class=\"onenote-ul\">\n");
                sb.append("  <li>").append(formatInlineMarkup(trimmed.substring(1).trim())).append("</li>\n");
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (isBulletLine(nextLine)) {
                        sb.append("  <li>").append(formatInlineMarkup(nextLine.substring(1).trim())).append("</li>\n");
                        i++;
                    } else {
                        break;
                    }
                }
                sb.append("</ul>\n");
                continue;
            }

            // Horizontal Rule: ---, ***, ___
            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<hr class=\"divider\" />\n");
                continue;
            }

            // Headings
            if (trimmed.startsWith("#### ")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<h4>").append(formatInlineMarkup(trimmed.substring(5))).append("</h4>\n");
            } else if (trimmed.startsWith("### ")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<h3>").append(formatInlineMarkup(trimmed.substring(4))).append("</h3>\n");
            } else if (trimmed.startsWith("## ")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<h2>").append(formatInlineMarkup(trimmed.substring(3))).append("</h2>\n");
            } else if (trimmed.startsWith("# ")) {
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                sb.append("<h1>").append(formatInlineMarkup(trimmed.substring(2))).append("</h1>\n");
            } else if (trimmed.startsWith("[Image:") && trimmed.endsWith("]")) {
                // Standalone image block
                if (inParagraph) { sb.append("</p>\n"); inParagraph = false; }
                String imgName = trimmed.substring(7, trimmed.length() - 1).trim();
                sb.append("<div class=\"image-wrapper\">")
                  .append("<img src=\"").append(StringUtils.htmlEscape(imgName)).append("\" alt=\"").append(StringUtils.htmlEscape(imgName)).append("\" />")
                  .append("<div class=\"image-caption\">").append(StringUtils.htmlEscape(imgName)).append("</div>")
                  .append("</div>\n");
            } else {
                if (!inParagraph) {
                    sb.append("<p>");
                    inParagraph = true;
                } else {
                    sb.append("<br />");
                }
                sb.append(formatInlineMarkup(line));
            }
        }

        if (inParagraph) {
            sb.append("</p>\n");
        }
    }

    private void renderTableToHtml(StringBuffer sb, Vector tableLines) {
        sb.append("<table class=\"onenote-table\">\n");
        boolean hasHeader = false;
        boolean inBody = false;

        for (int i = 0; i < tableLines.size(); i++) {
            String row = (String) tableLines.elementAt(i);
            if (isTableDivider(row)) {
                continue;
            }
            Vector cells = parseTableRowCells(row);
            if (!hasHeader) {
                sb.append("<thead>\n  <tr>\n");
                for (int c = 0; c < cells.size(); c++) {
                    sb.append("    <th>").append(formatInlineMarkup((String) cells.elementAt(c))).append("</th>\n");
                }
                sb.append("  </tr>\n</thead>\n<tbody>\n");
                hasHeader = true;
                inBody = true;
            } else {
                sb.append("  <tr>\n");
                for (int c = 0; c < cells.size(); c++) {
                    sb.append("    <td>").append(formatInlineMarkup((String) cells.elementAt(c))).append("</td>\n");
                }
                sb.append("  </tr>\n");
            }
        }
        if (inBody) {
            sb.append("</tbody>\n");
        }
        sb.append("</table>\n");
    }

    private boolean isTableDivider(String row) {
        String trimmed = row.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '|' && c != '-' && c != ':' && c != ' ') {
                return false;
            }
        }
        return true;
    }

    private Vector parseTableRowCells(String row) {
        Vector cells = new Vector();
        int start = row.startsWith("|") ? 1 : 0;
        int end = row.endsWith("|") ? row.length() - 1 : row.length();
        if (start < end) {
            String inner = row.substring(start, end);
            Vector raw = StringUtils.split(inner, '|');
            for (int i = 0; i < raw.size(); i++) {
                cells.addElement(((String) raw.elementAt(i)).trim());
            }
        }
        return cells;
    }

    private boolean isTaskLine(String line) {
        if (line == null) return false;
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ")) {
            s = s.substring(2).trim();
        }
        return s.startsWith("[ ]") || StringUtils.startsWithIgnoreCase(s, "[x]");
    }

    private void renderTaskLineToHtml(StringBuffer sb, String line) {
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ")) {
            s = s.substring(2).trim();
        }
        boolean isChecked = StringUtils.startsWithIgnoreCase(s, "[x]");
        int closeBracket = s.indexOf(']');
        String taskLabel = closeBracket >= 0 ? s.substring(closeBracket + 1).trim() : s;

        sb.append("  <li class=\"task-item").append(isChecked ? " completed" : "").append("\">");
        sb.append("<input type=\"checkbox\"").append(isChecked ? " checked=\"checked\"" : "").append(" disabled=\"disabled\" /> ");
        sb.append("<span>").append(formatInlineMarkup(taskLabel)).append("</span></li>\n");
    }

    private boolean isNumberedLine(String line) {
        if (line == null) return false;
        String s = line.trim();
        int dot = s.indexOf(". ");
        if (dot > 0 && dot <= 5) {
            return StringUtils.isDigits(s.substring(0, dot));
        }
        return false;
    }

    private boolean isBulletLine(String line) {
        if (line == null) return false;
        String s = line.trim();
        return s.startsWith("•") || s.startsWith("-") || (s.startsWith("*") && !s.startsWith("**"));
    }

    private String stripQuote(String line) {
        if (line.startsWith(">>")) {
            return line.substring(2).trim();
        } else if (line.startsWith(">")) {
            return line.substring(1).trim();
        }
        return line;
    }

    /**
     * Converts markdown inline markup to HTML tags:
     * bold (**), italic (*), underline (__), strikethrough (~~), highlight (==),
     * inline code (`), hyperlinks [text](url), OneNote badges ([!], [?], [*]), and inline images.
     */
    private String formatInlineMarkup(String text) {
        String escaped = StringUtils.htmlEscape(text);

        // Replace bold **...**
        escaped = replacePairedDelimiters(escaped, "**", "<strong>", "</strong>");
        // Replace underline __...__
        escaped = replacePairedDelimiters(escaped, "__", "<u>", "</u>");
        // Replace strikethrough ~~...~~
        escaped = replacePairedDelimiters(escaped, "~~", "<del>", "</del>");
        // Replace highlight ==...==
        escaped = replacePairedDelimiters(escaped, "==", "<mark>", "</mark>");
        // Replace inline code `...`
        escaped = replacePairedDelimiters(escaped, "`", "<code class=\"inline-code\">", "</code>");
        // Replace italic *...*
        escaped = replacePairedDelimiters(escaped, "*", "<em>", "</em>");

        // Replace OneNote badges
        escaped = StringUtils.replace(escaped, "[!]", "<span class=\"badge-tag badge-important\">[!] IMPORTANT</span>");
        escaped = StringUtils.replace(escaped, "[?]", "<span class=\"badge-tag badge-question\">[?] QUESTION</span>");
        escaped = StringUtils.replace(escaped, "[*]", "<span class=\"badge-tag badge-star\">[*] STAR</span>");

        // Replace markdown hyperlinks [text](url)
        int linkStart = 0;
        while ((linkStart = escaped.indexOf("[", linkStart)) >= 0) {
            int linkMid = escaped.indexOf("](", linkStart);
            if (linkMid < 0) {
                linkStart++;
                continue;
            }
            int linkEnd = escaped.indexOf(")", linkMid);
            if (linkEnd < 0) {
                linkStart++;
                continue;
            }
            String lText = escaped.substring(linkStart + 1, linkMid);
            String lUrl = escaped.substring(linkMid + 2, linkEnd);
            String replacement = "<a href=\"" + lUrl + "\" target=\"_blank\">" + lText + "</a>";
            escaped = escaped.substring(0, linkStart) + replacement + escaped.substring(linkEnd + 1);
            linkStart = linkStart + replacement.length();
        }

        // Replace inline [Image: filename]
        String prefix = "[Image:";
        int pLen = prefix.length();
        int start = 0;
        while ((start = escaped.indexOf(prefix, start)) >= 0) {
            int end = escaped.indexOf("]", start);
            if (end < 0) {
                break;
            }
            String imgName = escaped.substring(start + pLen, end).trim();
            String replacement = "<img src=\"" + imgName + "\" alt=\"" + imgName + "\" style=\"max-width:100%;\" />";
            escaped = escaped.substring(0, start) + replacement + escaped.substring(end + 1);
            start = start + replacement.length();
        }

        return escaped;
    }

    private String replacePairedDelimiters(String src, String delim, String openTag, String closeTag) {
        StringBuffer sb = new StringBuffer();
        int dLen = delim.length();
        int start = 0;
        boolean open = false;

        while (true) {
            int idx = src.indexOf(delim, start);
            if (idx < 0) {
                sb.append(src.substring(start));
                break;
            }
            sb.append(src.substring(start, idx));
            if (!open) {
                sb.append(openTag);
                open = true;
            } else {
                sb.append(closeTag);
                open = false;
            }
            start = idx + dLen;
        }
        return sb.toString();
    }

    /**
     * Generates structured JSON metadata for the .NET bridge and fast re-import.
     */
    private StringBuffer buildMetadataJson(Notebook notebook, Section section, Page page, Vector images) {
        StringBuffer sb = new StringBuffer(1024);
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"pageId\": ").append(page.getId()).append(",\n");
        sb.append("  \"title\": \"").append(jsonEscape(page.getTitle())).append("\",\n");
        sb.append("  \"notebookId\": ").append(notebook != null ? notebook.getId() : 0).append(",\n");
        sb.append("  \"notebookName\": \"").append(notebook != null ? jsonEscape(notebook.getName()) : "").append("\",\n");
        sb.append("  \"sectionId\": ").append(section != null ? section.getId() : 0).append(",\n");
        sb.append("  \"sectionName\": \"").append(section != null ? jsonEscape(section.getName()) : "").append("\",\n");
        sb.append("  \"createdTime\": ").append(page.getCreatedTime()).append(",\n");
        sb.append("  \"createdIso\": \"").append(DateUtils.formatIso8601(page.getCreatedTime())).append("\",\n");
        sb.append("  \"modifiedTime\": ").append(page.getModifiedTime()).append(",\n");
        sb.append("  \"modifiedIso\": \"").append(DateUtils.formatIso8601(page.getModifiedTime())).append("\",\n");
        sb.append("  \"notes\": \"").append(jsonEscape(page.getNotes())).append("\",\n");

        // Custom Fields
        sb.append("  \"customFields\": [\n");
        Vector cfs = page.getCustomFields();
        int cfCount = cfs != null ? cfs.size() : 0;
        for (int i = 0; i < cfCount; i++) {
            CustomField cf = (CustomField) cfs.elementAt(i);
            sb.append("    { \"key\": \"").append(jsonEscape(cf.getKey())).append("\", ")
              .append("\"label\": \"").append(jsonEscape(cf.getLabel())).append("\", ")
              .append("\"type\": ").append(cf.getFieldType()).append(", ")
              .append("\"value\": \"").append(jsonEscape(cf.getValue())).append("\" }");
            if (i < cfCount - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Attachments
        sb.append("  \"attachments\": [\n");
        int imgCount = images.size();
        for (int i = 0; i < imgCount; i++) {
            String img = (String) images.elementAt(i);
            sb.append("    \"").append(jsonEscape(img)).append("\"");
            if (i < imgCount - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        return sb;
    }

    private String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Writes UTF-8 string content out through FileConnection.
     */
    private void writeStringToFile(String fileUrl, String content) throws IOException {
        FileConnection fc = null;
        OutputStream out = null;
        OutputStreamWriter writer = null;

        try {
            fc = (FileConnection) Connector.open(fileUrl, Connector.READ_WRITE);
            if (fc.exists()) {
                fc.delete();
            }
            fc.create();

            out = fc.openOutputStream();
            writer = new OutputStreamWriter(out, "UTF-8");
            writer.write(content);
            writer.flush();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception e) {}
            }
            if (out != null) {
                try { out.close(); } catch (Exception e) {}
            }
            if (fc != null) {
                try { fc.close(); } catch (Exception e) {}
            }
        }
    }

    /**
     * Writes UTF-8 StringBuffer content in chunks to minimize memory allocations.
     */
    private void writeStringBufferToFile(String fileUrl, StringBuffer sb) throws IOException {
        FileConnection fc = null;
        OutputStream out = null;
        OutputStreamWriter writer = null;

        try {
            fc = (FileConnection) Connector.open(fileUrl, Connector.READ_WRITE);
            if (fc.exists()) {
                fc.delete();
            }
            fc.create();

            out = fc.openOutputStream();
            writer = new OutputStreamWriter(out, "UTF-8");

            int len = sb.length();
            char[] cbuf = new char[2048];
            int offset = 0;
            while (offset < len) {
                int count = Math.min(2048, len - offset);
                sb.getChars(offset, offset + count, cbuf, 0);
                writer.write(cbuf, 0, count);
                offset += count;
            }
            writer.flush();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (Exception e) {}
            }
            if (out != null) {
                try { out.close(); } catch (Exception e) {}
            }
            if (fc != null) {
                try { fc.close(); } catch (Exception e) {}
            }
        }
    }
}
