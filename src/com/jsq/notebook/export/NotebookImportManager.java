package com.jsq.notebook.export;

import com.jsq.notebook.model.CustomField;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Page;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.AttachmentStorageManager;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.ProgressListener;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Reconstructs a Notebook from a .jnb export folder on the SD Card.
 *
 * <p>Expected folder structure (produced by PageExportManager.exportNotebook):
 * <pre>
 *   &lt;notebookDir&gt;/
 *     &lt;NotebookName&gt;.jnb   -- plain-text manifest with name + description
 *     index.html            -- ignored during import
 *     &lt;SectionName&gt;/       -- each subdirectory = one Section
 *       &lt;PageTitle&gt;/       -- each subdirectory inside section = one Page
 *         metadata.json     -- parsed to recover page title and notes content
 *         index.html        -- not used during import (metadata.json is canonical)
 * </pre>
 *
 * <p>All APIs are unsigned JSR-75 FileConnection only (OS 5.0 / CLDC 1.1 compatible).
 */
public class NotebookImportManager {

    private static NotebookImportManager instance;

    private NotebookImportManager() {
    }

    public static synchronized NotebookImportManager getInstance() {
        if (instance == null) {
            instance = new NotebookImportManager();
        }
        return instance;
    }

    /**
     * Imports a Notebook from a .jnb export folder, saves it to device NVRAM, and returns it.
     *
     * <p>The supplied {@code notebookDirUrl} must be the file:// URL of the folder that
     * directly contains the {@code .jnb} manifest file (i.e. the notebook root folder,
     * not its parent).
     *
     * @param notebookDirUrl file:// URL of the notebook root folder (must end with '/')
     * @return the newly created and persisted Notebook
     * @throws IOException if any I/O error occurs or the folder is not a valid .jnb export
     */
    /**
     * Imports a Notebook from a .jnb export folder, saves it to device NVRAM, and returns it.
     */
    public Notebook importNotebook(String notebookDirUrl) throws IOException {
        return importNotebook(notebookDirUrl, null);
    }

    /**
     * Imports a Notebook from a .jnb export folder with optional progress reporting.
     *
     * @param notebookDirUrl file:// URL of the notebook root folder (must end with '/')
     * @param listener optional ProgressListener for real-time progress callbacks
     * @return the newly created and persisted Notebook
     * @throws IOException if any I/O error occurs or the folder is not a valid .jnb export
     */
    public Notebook importNotebook(String notebookDirUrl, ProgressListener listener) throws IOException {
        if (notebookDirUrl == null || notebookDirUrl.length() == 0) {
            throw new IllegalArgumentException("notebookDirUrl cannot be empty");
        }

        // --- 1. Read .jnb manifest ---
        String manifestUrl = null;
        if (notebookDirUrl.toLowerCase().endsWith(".jnb")) {
            manifestUrl = notebookDirUrl;
            int lastSlash = notebookDirUrl.lastIndexOf('/');
            if (lastSlash >= 0) {
                notebookDirUrl = notebookDirUrl.substring(0, lastSlash + 1);
            }
        } else {
            if (!notebookDirUrl.endsWith("/")) {
                notebookDirUrl = notebookDirUrl + "/";
            }
            manifestUrl = findJnbManifest(notebookDirUrl);
        }

        String notebookName;
        String notebookDesc;

        if (manifestUrl != null) {
            String[] nameDesc = parseJnbManifest(manifestUrl);
            notebookName = nameDesc[0];
            notebookDesc = nameDesc[1];
        } else {
            // Fall back to folder name as notebook name
            notebookName = lastPathComponent(notebookDirUrl);
            notebookDesc = "";
        }

        // --- 2. Calculate work units for progress bar ---
        Vector sectionDirs = listSubdirectories(notebookDirUrl);
        int secCount = sectionDirs.size();
        int totalPages = 0;
        for (int si = 0; si < secCount; si++) {
            Vector pageDirs = listSubdirectories((String) sectionDirs.elementAt(si));
            totalPages += pageDirs.size();
        }

        int totalUnits = totalPages + 2;
        int currentUnit = 0;
        if (listener != null) {
            listener.onProgress(currentUnit, totalUnits, "Reading manifest...");
        }

        // --- 3. Create Notebook shell ---
        NotebookStoreManager store = NotebookStoreManager.getInstance();
        long nbId = store.nextId();
        Notebook notebook = new Notebook(nbId, notebookName);
        notebook.setDescription(notebookDesc);

        // --- 4. Discover and import sections and pages ---
        for (int si = 0; si < secCount; si++) {
            String sectionDir = (String) sectionDirs.elementAt(si);
            String sectionName = lastPathComponent(sectionDir);

            long secId = store.nextId();
            Section section = new Section(secId, nbId, sectionName);
            section.getFieldDefinitions().removeAllElements();

            Vector pageDirs = listSubdirectories(sectionDir);
            int pageCount = pageDirs.size();
            for (int pi = 0; pi < pageCount; pi++) {
                String pageDir = (String) pageDirs.elementAt(pi);
                String pageTitle = lastPathComponent(pageDir);
                String notes = "";

                if (listener != null) {
                    listener.onProgress(++currentUnit, totalUnits, "Importing: " + pageTitle);
                }

                // Copy any attachments present in page folder to global Attachments dir
                copyAttachmentsFromPageDir(pageDir);

                // Try to read metadata.json
                String metaUrl = pageDir + "metadata.json";
                long pageId = store.nextId();
                Page page = new Page(pageId, secId, pageTitle);

                if (fileExists(metaUrl)) {
                    String json = readFileAsString(metaUrl);
                    String parsedTitle = parseJsonField(json, "title");
                    if (parsedTitle != null && parsedTitle.length() > 0) {
                        pageTitle = parsedTitle;
                        page.setTitle(pageTitle);
                    }

                    // Fast-path: read raw markdown notes directly from metadata.json if present
                    String parsedNotes = parseJsonField(json, "notes");
                    if (parsedNotes != null && parsedNotes.length() > 0) {
                        notes = parsedNotes;
                    } else {
                        // Fallback: extract notes from index.html
                        parsedNotes = extractNotesFromHtml(pageDir + "index.html");
                        if (parsedNotes != null) {
                            notes = parsedNotes;
                        }
                    }

                    // Parse custom fields from metadata.json
                    parseCustomFieldsFromJson(json, page);
                } else {
                    // Fallback: try reading notes body from index.html
                    String htmlUrl = pageDir + "index.html";
                    if (fileExists(htmlUrl)) {
                        String extracted = extractNotesFromHtml(htmlUrl);
                        if (extracted != null) {
                            notes = extracted;
                        }
                    }
                }

                page.setNotes(notes);
                section.addPage(page);
            }

            notebook.addSection(section);
        }

        // --- 5. Persist notebook to NVRAM ---
        if (listener != null) {
            listener.onProgress(totalUnits - 1, totalUnits, "Saving to database...");
        }

        store.addNotebook(notebook);

        if (listener != null) {
            listener.onProgress(totalUnits, totalUnits, "Import complete!");
        }

        return notebook;
    }

    /**
     * Imports the OneNote demo notebook with progress reporting.
     */
    public Notebook importDemoNotebook(ProgressListener listener) {
        return NotebookStoreManager.getInstance().addDemoNotebook(listener);
    }

    // ==========================================
    // File discovery helpers
    // ==========================================

    /**
     * Finds the first .jnb file directly in the given directory URL.
     * Returns its file:// URL or null if not found.
     */
    private String findJnbManifest(String dirUrl) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirUrl, Connector.READ);
            if (!fc.isDirectory()) {
                return null;
            }
            Enumeration entries = fc.list("*.jnb", true);
            if (entries.hasMoreElements()) {
                String name = (String) entries.nextElement();
                return dirUrl + name;
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            closeQuietly(fc);
        }
        return null;
    }

    /**
     * Lists all immediate subdirectories of a given directory URL.
     * Returns a Vector of file:// URLs (each ending with '/').
     */
    private Vector listSubdirectories(String dirUrl) {
        Vector result = new Vector();
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirUrl, Connector.READ);
            if (!fc.isDirectory()) {
                return result;
            }
            Enumeration entries = fc.list("*", true);
            while (entries.hasMoreElements()) {
                String entry = (String) entries.nextElement();
                if (entry.endsWith("/")) {
                    result.addElement(dirUrl + entry);
                }
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            closeQuietly(fc);
        }
        return result;
    }

    /**
     * Returns true if the file at the given URL exists.
     */
    private boolean fileExists(String url) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(url, Connector.READ);
            return fc.exists() && !fc.isDirectory();
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(fc);
        }
    }

    // ==========================================
    // File reading helpers
    // ==========================================

    /**
     * Reads the entire content of a UTF-8 text file into a String.
     * Uses a 4 KB buffer to minimize heap usage.
     */
    private String readFileAsString(String fileUrl) throws IOException {
        FileConnection fc = null;
        InputStream in = null;
        InputStreamReader reader = null;
        try {
            fc = (FileConnection) Connector.open(fileUrl, Connector.READ);
            if (!fc.exists()) {
                return "";
            }
            in = fc.openInputStream();
            reader = new InputStreamReader(in, "UTF-8");
            StringBuffer sb = new StringBuffer((int) Math.min(fc.fileSize(), 65536L));
            char[] buf = new char[2048];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            return sb.toString();
        } finally {
            if (reader != null) { try { reader.close(); } catch (Exception e) {} }
            if (in != null) { try { in.close(); } catch (Exception e) {} }
            closeQuietly(fc);
        }
    }

    // ==========================================
    // .jnb manifest parsing
    // ==========================================

    /**
     * Parses a .jnb manifest file.
     * Returns a two-element array: [name, description].
     */
    private String[] parseJnbManifest(String manifestUrl) {
        String name = "";
        String description = "";
        try {
            String content = readFileAsString(manifestUrl);
            Vector lines = splitLines(content);
            int count = lines.size();
            for (int i = 0; i < count; i++) {
                String line = (String) lines.elementAt(i);
                if (line.startsWith("name:")) {
                    name = line.substring(5).trim();
                } else if (line.startsWith("description:")) {
                    description = line.substring(12).trim();
                }
            }
        } catch (Exception e) {
            // Return defaults on error
        }
        return new String[] { name, description };
    }

    // ==========================================
    // metadata.json / HTML parsing
    // ==========================================

    /**
     * Extracts the value of a simple JSON string field from raw JSON text.
     * Handles basic escaped quotes only; suitable for the metadata.json files
     * produced by PageExportManager (no nested objects in target fields).
     *
     * @param json  the raw JSON string
     * @param field the field name without quotes
     * @return the field value, or null if not found
     */
    private String parseJsonField(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        int i = quoteStart + 1;
        int len = json.length();
        while (i < len) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case 'n':  sb.append('\n'); i += 2; break;
                    case 'r':  sb.append('\r'); i += 2; break;
                    case 't':  sb.append('\t'); i += 2; break;
                    default:   sb.append(next); i += 2; break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Copies any image or binary media files located in the page export folder
     * back into the application's global Attachments folder on the SD Card.
     */
    private void copyAttachmentsFromPageDir(String pageDir) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(pageDir, Connector.READ);
            if (fc.exists() && fc.isDirectory()) {
                Enumeration entries = fc.list("*", true);
                AttachmentStorageManager storage = AttachmentStorageManager.getInstance();
                String attDir = storage.getAttachmentDirectoryUrl();
                storage.ensureDirectoryExists(attDir);

                while (entries.hasMoreElements()) {
                    String entry = (String) entries.nextElement();
                    if (!entry.endsWith("/")) {
                        String lower = entry.toLowerCase();
                        if (AttachmentStorageManager.isImageFile(lower) || lower.endsWith(".mp3") ||
                                lower.endsWith(".amr") || lower.endsWith(".wav") ||
                                lower.endsWith(".pdf") || lower.endsWith(".doc")) {
                            String srcUrl = pageDir + entry;
                            String destUrl = attDir + entry;
                            try {
                                if (!fileExists(destUrl)) {
                                    storage.copyFile(srcUrl, destUrl);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(fc);
        }
    }

    /**
     * Parses custom fields array from metadata.json and adds them to the page.
     */
    private void parseCustomFieldsFromJson(String json, Page page) {
        if (json == null || page == null) return;
        int cfStart = json.indexOf("\"customFields\"");
        if (cfStart < 0) return;
        int arrayStart = json.indexOf('[', cfStart);
        if (arrayStart < 0) return;
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayEnd < 0) return;

        String cfArray = json.substring(arrayStart + 1, arrayEnd);
        int objStart = 0;
        while ((objStart = cfArray.indexOf('{', objStart)) >= 0) {
            int objEnd = cfArray.indexOf('}', objStart);
            if (objEnd < 0) break;
            String obj = cfArray.substring(objStart + 1, objEnd);
            String key = parseJsonField(obj, "key");
            String label = parseJsonField(obj, "label");
            String val = parseJsonField(obj, "value");
            int type = 0;
            int typeIdx = obj.indexOf("\"type\"");
            if (typeIdx >= 0) {
                int colon = obj.indexOf(':', typeIdx);
                if (colon >= 0) {
                    int comma = obj.indexOf(',', colon);
                    String typeStr = (comma >= 0 ? obj.substring(colon + 1, comma) : obj.substring(colon + 1)).trim();
                    try {
                        type = Integer.parseInt(typeStr);
                    } catch (Exception ignored) {}
                }
            }
            if (key != null && key.length() > 0) {
                CustomField cf = new CustomField(key, label != null ? label : key, type, val != null ? val : "", false);
                page.addCustomField(cf);
            }
            objStart = objEnd + 1;
        }
    }

    /**
     * Extracts the plain-text notes content from an exported page index.html.
     *
     * <p>The exported HTML wraps the notes body in {@code <div class="notes-body">}.
     * This method extracts that div's inner HTML and converts it back to the
     * application's lightweight markdown format (**bold**, *italic*, ## headings,
     * [Image: name]) so that the page can be re-edited inside the app.
     *
     * @param htmlUrl file:// URL of the page's index.html
     * @return reconstructed notes string, or null if not parseable
     */
    private String extractNotesFromHtml(String htmlUrl) {
        if (!fileExists(htmlUrl)) {
            return null;
        }
        String html;
        try {
            html = readFileAsString(htmlUrl);
        } catch (Exception e) {
            return null;
        }

        // Find the notes-body div
        String openTag = "<div class=\"notes-body\">";
        int start = html.indexOf(openTag);
        if (start < 0) {
            return null;
        }
        start += openTag.length();

        // Find matching closing </div>
        int end = html.indexOf("</div>", start);
        if (end < 0) {
            end = html.length();
        }

        String body = html.substring(start, end).trim();
        return htmlToMarkdown(body);
    }

    /**
     * Converts a subset of HTML (as produced by PageExportManager) back to the app's
     * lightweight markdown notation so re-imported pages are editable.
     *
     * Handled conversions:
     * <ul>
     *   <li>{@code <h1>...</h1>} → {@code # ...}</li>
     *   <li>{@code <h2>...</h2>} → {@code ## ...}</li>
     *   <li>{@code <h3>...</h3>} → {@code ### ...}</li>
     *   <li>{@code <strong>...</strong>} → {@code **...**}</li>
     *   <li>{@code <em>...</em>} → {@code *...*}</li>
     *   <li>{@code <p>...</p>} → paragraph text</li>
     *   <li>{@code <br />} → newline</li>
     *   <li>{@code <div class="image-wrapper"><img src="name"...>} → {@code [Image: name]}</li>
     *   <li>HTML entities &amp; &lt; &gt; &quot; &amp;#39; → their characters</li>
     * </ul>
     */
    private String htmlToMarkdown(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }

        StringBuffer out = new StringBuffer(html.length());
        int i = 0;
        int len = html.length();

        boolean inOl = false;
        int olCounter = 1;
        boolean inChecklist = false;
        int headerCols = 0;
        boolean inTableHeader = false;
        int linkTextStart = -1;
        String currentLinkUrl = null;

        while (i < len) {
            char c = html.charAt(i);

            if (c == '<') {
                int tagEnd = html.indexOf('>', i);
                if (tagEnd < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                String tag = html.substring(i + 1, tagEnd).trim();
                String tagLower = tag.toLowerCase();

                if (tagLower.equals("h1")) {
                    out.append("# ");
                } else if (tagLower.equals("/h1")) {
                    out.append("\n");
                } else if (tagLower.equals("h2")) {
                    out.append("## ");
                } else if (tagLower.equals("/h2")) {
                    out.append("\n");
                } else if (tagLower.equals("h3")) {
                    out.append("### ");
                } else if (tagLower.equals("/h3")) {
                    out.append("\n");
                } else if (tagLower.equals("h4")) {
                    out.append("#### ");
                } else if (tagLower.equals("/h4")) {
                    out.append("\n");
                } else if (tagLower.equals("h5")) {
                    out.append("##### ");
                } else if (tagLower.equals("/h5")) {
                    out.append("\n");
                } else if (tagLower.equals("h6")) {
                    out.append("###### ");
                } else if (tagLower.equals("/h6")) {
                    out.append("\n");
                } else if (tagLower.equals("strong") || tagLower.equals("b")) {
                    out.append("**");
                } else if (tagLower.equals("/strong") || tagLower.equals("/b")) {
                    out.append("**");
                } else if (tagLower.equals("em") || tagLower.equals("i")) {
                    out.append("*");
                } else if (tagLower.equals("/em") || tagLower.equals("/i")) {
                    out.append("*");
                } else if (tagLower.equals("u")) {
                    out.append("__");
                } else if (tagLower.equals("/u")) {
                    out.append("__");
                } else if (tagLower.equals("del") || tagLower.equals("s") || tagLower.equals("strike")) {
                    out.append("~~");
                } else if (tagLower.equals("/del") || tagLower.equals("/s") || tagLower.equals("/strike")) {
                    out.append("~~");
                } else if (tagLower.equals("mark")) {
                    out.append("==");
                } else if (tagLower.equals("/mark")) {
                    out.append("==");
                } else if (tagLower.equals("code")) {
                    out.append("`");
                } else if (tagLower.equals("/code")) {
                    out.append("`");
                } else if (tagLower.equals("pre")) {
                    out.append("\n```\n");
                } else if (tagLower.equals("/pre")) {
                    out.append("\n```\n");
                } else if (tagLower.equals("blockquote") || (tagLower.startsWith("blockquote ") && tagLower.indexOf("onenote-callout") >= 0)) {
                    out.append("\n> ");
                } else if (tagLower.equals("/blockquote")) {
                    out.append("\n");
                } else if (tagLower.equals("hr") || tagLower.equals("hr /") || tagLower.equals("hr/")) {
                    out.append("\n---\n");
                } else if (tagLower.startsWith("input ") && tagLower.indexOf("checkbox") >= 0) {
                    if (tagLower.indexOf("checked") >= 0) {
                        out.append("[x] ");
                    } else {
                        out.append("[ ] ");
                    }
                } else if (tagLower.startsWith("ul")) {
                    if (tagLower.indexOf("checklist") >= 0) {
                        inChecklist = true;
                    }
                } else if (tagLower.equals("/ul")) {
                    inChecklist = false;
                } else if (tagLower.startsWith("ol")) {
                    inOl = true;
                    olCounter = 1;
                } else if (tagLower.equals("/ol")) {
                    inOl = false;
                    olCounter = 1;
                } else if (tagLower.startsWith("li")) {
                    if (inChecklist) {
                        // Checkbox input inside li will render [ ] or [x]
                    } else if (inOl) {
                        out.append(olCounter++).append(". ");
                    } else {
                        out.append("- ");
                    }
                } else if (tagLower.equals("/li")) {
                    out.append("\n");
                } else if (tagLower.startsWith("table")) {
                    out.append("\n");
                } else if (tagLower.equals("/table")) {
                    out.append("\n");
                } else if (tagLower.startsWith("thead")) {
                    inTableHeader = true;
                    headerCols = 0;
                } else if (tagLower.equals("/thead")) {
                    inTableHeader = false;
                } else if (tagLower.startsWith("tr")) {
                    out.append("| ");
                } else if (tagLower.equals("/tr")) {
                    out.append("\n");
                    if (headerCols > 0) {
                        out.append("|");
                        for (int h = 0; h < headerCols; h++) {
                            out.append("---|");
                        }
                        out.append("\n");
                        headerCols = 0;
                    }
                } else if (tagLower.startsWith("th")) {
                    // cell start
                } else if (tagLower.equals("/th")) {
                    out.append(" | ");
                    if (inTableHeader) {
                        headerCols++;
                    }
                } else if (tagLower.startsWith("td")) {
                    // cell start
                } else if (tagLower.equals("/td")) {
                    out.append(" | ");
                } else if (tagLower.startsWith("a ")) {
                    currentLinkUrl = extractAttr(tag, "href");
                    linkTextStart = out.length();
                } else if (tagLower.equals("/a")) {
                    if (currentLinkUrl != null && linkTextStart >= 0 && linkTextStart <= out.length()) {
                        String anchorText = out.toString().substring(linkTextStart);
                        out.setLength(linkTextStart);
                        out.append("[").append(anchorText).append("](").append(currentLinkUrl).append(")");
                        currentLinkUrl = null;
                        linkTextStart = -1;
                    }
                } else if (tagLower.startsWith("span ") && tagLower.indexOf("badge-") >= 0) {
                    if (tagLower.indexOf("badge-important") >= 0) {
                        out.append("[!] ");
                    } else if (tagLower.indexOf("badge-question") >= 0) {
                        out.append("[?] ");
                    } else if (tagLower.indexOf("badge-star") >= 0) {
                        out.append("[*] ");
                    }
                } else if (tagLower.equals("p")) {
                    // opening <p>
                } else if (tagLower.equals("/p")) {
                    out.append("\n");
                } else if (tagLower.equals("br /") || tagLower.equals("br/") || tagLower.equals("br")) {
                    out.append("\n");
                } else if (tagLower.startsWith("img ")) {
                    String src = extractAttr(tag, "src");
                    if (src != null && src.length() > 0) {
                        out.append("[Image: ").append(src).append("]");
                    }
                } else if (tagLower.startsWith("div ") && tagLower.indexOf("image-wrapper") >= 0) {
                    // skip wrapper div
                }

                i = tagEnd + 1;
            } else if (c == '&') {
                int semicolon = html.indexOf(';', i);
                if (semicolon >= 0 && semicolon - i <= 8) {
                    String entity = html.substring(i + 1, semicolon).toLowerCase();
                    if (entity.equals("amp")) {
                        out.append('&');
                    } else if (entity.equals("lt")) {
                        out.append('<');
                    } else if (entity.equals("gt")) {
                        out.append('>');
                    } else if (entity.equals("quot")) {
                        out.append('"');
                    } else if (entity.equals("#39")) {
                        out.append('\'');
                    } else if (entity.equals("nbsp")) {
                        out.append(' ');
                    } else {
                        out.append('&').append(html.substring(i + 1, semicolon + 1));
                    }
                    i = semicolon + 1;
                } else {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }

        // Trim trailing whitespace and normalise multiple blank lines to one
        String result = out.toString();
        while (result.indexOf("\n\n\n") >= 0) {
            result = replaceAll(result, "\n\n\n", "\n\n");
        }
        return result.trim();
    }

    /**
     * Extracts a quoted attribute value from an HTML tag string (the content between < and >).
     */
    private String extractAttr(String tag, String attrName) {
        String search = attrName + "=\"";
        int start = tag.toLowerCase().indexOf(search);
        if (start < 0) {
            return null;
        }
        start += search.length();
        int end = tag.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return tag.substring(start, end);
    }

    // ==========================================
    // String utilities (CLDC 1.1 compatible)
    // ==========================================

    /**
     * Returns the last path component of a file:// URL (the folder name).
     * Strips trailing slash before extracting.
     */
    private String lastPathComponent(String url) {
        if (url == null || url.length() == 0) {
            return "Imported Notebook";
        }
        String s = url;
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    /** Splits a string on newlines (\r\n, \n, \r). */
    private Vector splitLines(String source) {
        Vector result = new Vector();
        if (source == null) {
            return result;
        }
        int len = source.length();
        int start = 0;
        int i = 0;
        while (i < len) {
            char c = source.charAt(i);
            if (c == '\r') {
                result.addElement(source.substring(start, i));
                if (i + 1 < len && source.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            } else if (c == '\n') {
                result.addElement(source.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        if (start <= len) {
            result.addElement(source.substring(start));
        }
        return result;
    }

    /** Replaces all occurrences of target with replacement (CLDC 1.1, no regex). */
    private String replaceAll(String src, String target, String replacement) {
        StringBuffer sb = new StringBuffer();
        int tLen = target.length();
        int start = 0;
        int idx;
        while ((idx = src.indexOf(target, start)) >= 0) {
            sb.append(src.substring(start, idx));
            sb.append(replacement);
            start = idx + tLen;
        }
        sb.append(src.substring(start));
        return sb.toString();
    }

    /** Closes a FileConnection silently. */
    private void closeQuietly(FileConnection fc) {
        if (fc != null) {
            try { fc.close(); } catch (Exception e) {}
        }
    }
}
