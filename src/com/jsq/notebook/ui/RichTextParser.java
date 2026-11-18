package com.jsq.notebook.ui;

import com.jsq.notebook.model.AppSettings;
import com.jsq.notebook.model.Page;
import com.jsq.notebook.storage.AttachmentStorageManager;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.StringUtils;
import net.rim.device.api.system.Bitmap;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Manager;
import net.rim.device.api.ui.XYRect;
import net.rim.device.api.ui.component.BitmapField;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.CheckboxField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.SeparatorField;

import java.util.Vector;

/**
 * Parses markdown-style markup, tables, checklists, callouts, and inline media
 * into rich BlackBerry UI fields for fluid, high-performance rendering in View
 * Mode.
 * Fully supports Dark Mode theme reversals for color-sensitive View Mode.
 */
public class RichTextParser {

    private static Font baseFont;
    private static Font h1Font;
    private static Font h2Font;
    private static Font h3Font;
    private static Font h4Font;
    private static Font h5Font;
    private static Font h6Font;
    private static Font boldFont;
    private static Font italicFont;
    private static Font boldItalicFont;
    private static Font monoFont;

    static {
        baseFont = Font.getDefault();
        int baseH = baseFont.getHeight();
        h1Font = baseFont.derive(Font.BOLD, baseH + 6, net.rim.device.api.ui.Ui.UNITS_px);
        h2Font = baseFont.derive(Font.BOLD, baseH + 3, net.rim.device.api.ui.Ui.UNITS_px);
        h3Font = baseFont.derive(Font.BOLD, baseH + 1, net.rim.device.api.ui.Ui.UNITS_px);
        h4Font = baseFont.derive(Font.BOLD, baseH, net.rim.device.api.ui.Ui.UNITS_px);
        h5Font = baseFont.derive(Font.BOLD, Math.max(9, baseH - 1), net.rim.device.api.ui.Ui.UNITS_px);
        h6Font = baseFont.derive(Font.BOLD | Font.ITALIC, Math.max(8, baseH - 2), net.rim.device.api.ui.Ui.UNITS_px);
        boldFont = baseFont.derive(Font.BOLD);
        italicFont = baseFont.derive(Font.ITALIC);
        boldItalicFont = baseFont.derive(Font.BOLD | Font.ITALIC);
        monoFont = baseFont.derive(Font.PLAIN, Math.max(9, baseH - 2), net.rim.device.api.ui.Ui.UNITS_px);
    }

    /**
     * Splits a string of text into wrapped lines that each fit within maxWidth using the given Font.
     * Breaks cleanly on word boundaries (spaces), or splits long words by character if needed.
     */
    public static Vector wrapText(String text, Font font, int maxWidth) {
        Vector lines = new Vector();
        if (text == null || text.length() == 0) {
            lines.addElement("");
            return lines;
        }
        if (maxWidth <= 20) {
            maxWidth = 20;
        }

        if (font.getAdvance(text) <= maxWidth) {
            lines.addElement(text);
            return lines;
        }

        Vector rawWords = StringUtils.split(text, ' ');
        StringBuffer currentLine = new StringBuffer();

        for (int i = 0; i < rawWords.size(); i++) {
            String word = (String) rawWords.elementAt(i);
            if (word.length() == 0) {
                continue;
            }

            if (currentLine.length() == 0) {
                if (font.getAdvance(word) <= maxWidth) {
                    currentLine.append(word);
                } else {
                    int wordLen = word.length();
                    int start = 0;
                    for (int c = 1; c <= wordLen; c++) {
                        String sub = word.substring(start, c);
                        if (font.getAdvance(sub) > maxWidth && c > start + 1) {
                            lines.addElement(word.substring(start, c - 1));
                            start = c - 1;
                        }
                    }
                    if (start < wordLen) {
                        currentLine.append(word.substring(start));
                    }
                }
            } else {
                String candidate = currentLine.toString() + " " + word;
                if (font.getAdvance(candidate) <= maxWidth) {
                    currentLine.append(' ').append(word);
                } else {
                    lines.addElement(currentLine.toString());
                    currentLine = new StringBuffer();
                    if (font.getAdvance(word) <= maxWidth) {
                        currentLine.append(word);
                    } else {
                        int wordLen = word.length();
                        int start = 0;
                        for (int c = 1; c <= wordLen; c++) {
                            String sub = word.substring(start, c);
                            if (font.getAdvance(sub) > maxWidth && c > start + 1) {
                                lines.addElement(word.substring(start, c - 1));
                                start = c - 1;
                            }
                        }
                        if (start < wordLen) {
                            currentLine.append(word.substring(start));
                        }
                    }
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.addElement(currentLine.toString());
        }
        if (lines.size() == 0) {
            lines.addElement("");
        }
        return lines;
    }

    /**
     * Parses notes content with live page task update support and Dark Mode
     * rendering.
     */
    public static void renderToManager(final Page page, String notes, Manager targetManager,
            final Runnable onNotesChangedCallback) {
        if (notes == null || notes.trim().length() == 0) {
            ThemedLabelField empty = new ThemedLabelField("No notes content.", italicFont, LabelField.FOCUSABLE,
                    0x7F8C8D, 0x94A3B8);
            targetManager.add(empty);
            return;
        }

        Vector lines = StringUtils.splitLines(notes);
        int lineCount = lines.size();

        int screenWidth = Graphics.getScreenWidth();
        int maxImageWidth = screenWidth - 30;

        final AttachmentStorageManager storage = AttachmentStorageManager.getInstance();

        for (int i = 0; i < lineCount; i++) {
            String line = (String) lines.elementAt(i);
            String trimmed = line.trim();

            if (trimmed.length() == 0) {
                // Spacer
                ThemedLabelField spacer = new ThemedLabelField(" ", baseFont, LabelField.FOCUSABLE, 0x000000, 0xF1F5F9);
                spacer.srcLine = i;
                targetManager.add(spacer);
                continue;
            }

            int leadingSpaces = 0;
            int lineLen = line.length();
            while (leadingSpaces < lineLen) {
                char c = line.charAt(leadingSpaces);
                if (c == ' ') {
                    leadingSpaces++;
                } else if (c == '\t') {
                    leadingSpaces += 2;
                } else {
                    break;
                }
            }
            int listLevel = (leadingSpaces / 2) + 1;

            // HTML Comments: <!-- ... -->
            if (trimmed.startsWith("<!--")) {
                while (i < lineCount) {
                    String commentLine = ((String) lines.elementAt(i)).trim();
                    if (commentLine.indexOf("-->") >= 0) {
                        break;
                    }
                    i++;
                }
                continue;
            }

            // Setext-style Headings: line followed by === or ---
            if (i + 1 < lineCount && trimmed.length() > 0 && !trimmed.startsWith("|") && !trimmed.startsWith("#") && !isTableSeparatorRow(trimmed)) {
                String nextLineTrimmed = ((String) lines.elementAt(i + 1)).trim();
                if (nextLineTrimmed.startsWith("===") && nextLineTrimmed.length() >= 3) {
                    ThemedLabelField h1 = new ThemedLabelField(cleanMarkup(trimmed), h1Font,
                            LabelField.FOCUSABLE, 0x1A5276, 0x90CDF4);
                    h1.srcLine = i;
                    targetManager.add(h1);
                    targetManager.add(new ThemedSeparatorField());
                    i++;
                    continue;
                } else if (nextLineTrimmed.startsWith("---") && nextLineTrimmed.length() >= 3 && !nextLineTrimmed.endsWith("|")) {
                    ThemedLabelField h2 = new ThemedLabelField(cleanMarkup(trimmed), h2Font,
                            LabelField.FOCUSABLE, 0x2C3E50, 0xCBD5E1);
                    h2.srcLine = i;
                    targetManager.add(h2);
                    i++;
                    continue;
                }
            }

            // 1. Multiline Code Block: ```...```
            if (trimmed.startsWith("```")) {
                Vector codeLines = new Vector();
                int startLine = i;
                while (i + 1 < lineCount) {
                    i++;
                    String nextLine = (String) lines.elementAt(i);
                    if (nextLine.trim().startsWith("```")) {
                        break;
                    }
                    codeLines.addElement(nextLine);
                }
                CodeBlockField cbf = new CodeBlockField(codeLines);
                cbf.srcLine = startLine;
                targetManager.add(cbf);
                continue;
            }

            // 2. Markdown Table: | Col 1 | Col 2 |
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                Vector tableLines = new Vector();
                int startLine = i;
                tableLines.addElement(trimmed);
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if ((nextLine.startsWith("|") && nextLine.endsWith("|")) || isTableSeparatorRow(nextLine)) {
                        tableLines.addElement(nextLine);
                        i++;
                    } else {
                        break;
                    }
                }
                renderTableBlock(page, targetManager, tableLines, startLine, onNotesChangedCallback);
                continue;
            }

            // 3. Horizontal Rule: ---, ***, ___
            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                targetManager.add(new ThemedSeparatorField());
                continue;
            }

            // 4. Headings (with Light / Dark mode intensity reversals)
            if (trimmed.startsWith("###### ")) {
                ThemedLabelField h6 = new ThemedLabelField(cleanMarkup(trimmed.substring(7)), h6Font,
                        LabelField.FOCUSABLE, 0x7F8C8D, 0x94A3B8);
                h6.srcLine = i;
                targetManager.add(h6);
                continue;
            } else if (trimmed.startsWith("##### ")) {
                ThemedLabelField h5 = new ThemedLabelField(cleanMarkup(trimmed.substring(6)), h5Font,
                        LabelField.FOCUSABLE, 0x64748B, 0xCBD5E1);
                h5.srcLine = i;
                targetManager.add(h5);
                continue;
            } else if (trimmed.startsWith("#### ")) {
                ThemedLabelField h4 = new ThemedLabelField(cleanMarkup(trimmed.substring(5)), h4Font,
                        LabelField.FOCUSABLE, 0x5D6D7E, 0x94A3B8);
                h4.srcLine = i;
                targetManager.add(h4);
                continue;
            } else if (trimmed.startsWith("### ")) {
                ThemedLabelField h3 = new ThemedLabelField(cleanMarkup(trimmed.substring(4)), h3Font,
                        LabelField.FOCUSABLE, 0x34495E, 0xE2E8F0);
                h3.srcLine = i;
                targetManager.add(h3);
                continue;
            } else if (trimmed.startsWith("## ")) {
                ThemedLabelField h2 = new ThemedLabelField(cleanMarkup(trimmed.substring(3)), h2Font,
                        LabelField.FOCUSABLE, 0x2C3E50, 0xCBD5E1);
                h2.srcLine = i;
                targetManager.add(h2);
                continue;
            } else if (trimmed.startsWith("# ")) {
                ThemedLabelField h1 = new ThemedLabelField(cleanMarkup(trimmed.substring(2)), h1Font,
                        LabelField.FOCUSABLE, 0x1A5276, 0x90CDF4);
                h1.srcLine = i;
                targetManager.add(h1);
                targetManager.add(new ThemedSeparatorField());
                continue;
            }

            // 5. To-Do Checklist Item: [ ] / [x] / - [ ] / * [ ] / + [ ] / > [ ]
            if (isTaskItem(trimmed)) {
                renderTaskItem(targetManager, page, trimmed, i, onNotesChangedCallback, leadingSpaces);
                continue;
            }

            // 6. Purple Callout: * callout OR Alert Banner (* [!NOTE], > [!NOTE], etc.)
            if (isCalloutItem(trimmed) || isAlertStart(trimmed)) {
                Vector calloutLines = new Vector();
                int startLine = i;
                calloutLines.addElement(stripCalloutPrefix(trimmed));
                boolean currentIsAlert = isAlertStart(trimmed);
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (isAlertStart(nextLine)) {
                        // Start of a new distinct alert banner, don't bundle into this one!
                        break;
                    }
                    if (isCalloutItem(nextLine) || (currentIsAlert && nextLine.startsWith(">"))) {
                        calloutLines.addElement(stripCalloutPrefix(nextLine));
                        i++;
                    } else {
                        break;
                    }
                }
                BlockquoteField bqf = new BlockquoteField(calloutLines);
                bqf.srcLine = startLine;
                targetManager.add(bqf);
                continue;
            }

            // 7. Numbered List: 1. / 2. / 10.
            if (isNumberedListItem(trimmed)) {
                StringBuffer sb = new StringBuffer();
                for (int s = 0; s < listLevel * 2; s++) {
                    sb.append(' ');
                }
                sb.append(trimmed);
                renderStyledLine(targetManager, sb.toString(), i);
                continue;
            }

            // 8. Bullet List: • / - / + / >
            if (isBulletItem(trimmed)) {
                String content = trimmed.substring(1).trim();
                StringBuffer sb = new StringBuffer();
                for (int s = 0; s < listLevel * 2; s++) {
                    sb.append(' ');
                }
                sb.append('\u2022');
                if (content.length() > 0) {
                    sb.append(' ').append(content);
                }
                renderStyledLine(targetManager, sb.toString(), i);
                continue;
            }

            // 9. OneNote Note Tags: [!], [?], [*], [TODO], [IDEA]
            if (isTagLine(trimmed)) {
                renderTagLine(targetManager, trimmed, i);
                continue;
            }

            // 10. Standalone Markdown Hyperlink: [label](url)
            if (trimmed.startsWith("[") && trimmed.endsWith(")") && trimmed.indexOf("](") > 0) {
                int mid = trimmed.indexOf("](");
                final String linkText = trimmed.substring(1, mid).trim();
                final String linkUrl = trimmed.substring(mid + 2, trimmed.length() - 1).trim();
                ButtonField linkBtn = new ButtonField("[Web Link] " + linkText,
                        ButtonField.CONSUME_CLICK | Field.FIELD_HCENTER);
                linkBtn.setChangeListener(new FieldChangeListener() {
                    public void fieldChanged(Field field, int context) {
                        storage.openFileExternally(linkUrl);
                    }
                });
                targetManager.add(linkBtn);
                continue;
            }

            // 11. Standalone Raw URL
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                final String rawUrl = trimmed;
                ButtonField linkBtn = new ButtonField("[Open Webpage] " + rawUrl,
                        ButtonField.CONSUME_CLICK | Field.FIELD_HCENTER);
                linkBtn.setChangeListener(new FieldChangeListener() {
                    public void fieldChanged(Field field, int context) {
                        storage.openFileExternally(rawUrl);
                    }
                });
                targetManager.add(linkBtn);
                continue;
            }

            // 12. Standalone image block: [image: ...] or ![alt](file)
            if (trimmed.startsWith("![") && trimmed.indexOf("](") > 0 && trimmed.endsWith(")")) {
                int mid = trimmed.indexOf("](");
                String imgPath = trimmed.substring(mid + 2, trimmed.length() - 1).trim();
                renderImage(targetManager, storage, imgPath, maxImageWidth);
                continue;
            } else if (trimmed.toLowerCase().startsWith("[image:") && trimmed.endsWith("]")) {
                String imgName = trimmed.substring(7, trimmed.length() - 1).trim();
                renderImage(targetManager, storage, imgName, maxImageWidth);
                continue;
            }

            // 13. Standalone file / attachment block
            if ((trimmed.toLowerCase().startsWith("[attachment:") || trimmed.toLowerCase().startsWith("[file:"))
                    && trimmed.endsWith("]")) {
                int colon = trimmed.indexOf(':');
                String attRef = trimmed.substring(colon + 1, trimmed.length() - 1).trim();
                renderAttachment(targetManager, storage, attRef);
                continue;
            }

            // 14. Mixed inline image placeholder
            if (trimmed.toLowerCase().indexOf("[image:") >= 0) {
                renderMixedLine(targetManager, storage, line, maxImageWidth);
                continue;
            }

            // 15. Regular styled paragraph line
            renderStyledLine(targetManager, line, i);
        }
    }

    // ==========================================
    // Custom Themed Components
    // ==========================================
    public static class ThemedLabelField extends LabelField {
        private int lightColor;
        private int darkColor;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public ThemedLabelField(String text, Font font, int lightColor, int darkColor) {
            super(text, LabelField.FOCUSABLE);
            if (font != null) {
                setFont(font);
            }
            this.lightColor = lightColor;
            this.darkColor = darkColor;
        }

        public ThemedLabelField(String text, Font font, long style, int lightColor, int darkColor) {
            super(text, style | LabelField.FOCUSABLE);
            if (font != null) {
                setFont(font);
            }
            this.lightColor = lightColor;
            this.darkColor = darkColor;
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            invalidate();
        }

        protected void onUnfocus() {
            super.onUnfocus();
            invalidate();
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            g.setColor(dark ? darkColor : lightColor);
            super.paint(g);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int fontH = getFont().getHeight();
                int y = (getHeight() - fontH) / 2;
                if (y < 0) {
                    y = 0;
                }
                g.fillRect(1, y, 2, fontH);
            }
        }
    }

    public static class ThemedCheckboxField extends CheckboxField {
        public ThemedCheckboxField(String label, boolean checked) {
            super(label, checked);
        }

        protected void paint(Graphics g) {
            if (AppSettings.getInstance().isDarkMode()) {
                g.setColor(0xF1F5F9);
            }
            super.paint(g);
        }
    }

    public static class ThemedSeparatorField extends SeparatorField {
        protected void paint(Graphics g) {
            if (AppSettings.getInstance().isDarkMode()) {
                g.setColor(0x334155);
                g.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
            } else {
                super.paint(g);
            }
        }
    }

    // ==========================================
    // Table Rendering
    // ==========================================
    private static void renderTableBlock(final Page page, Manager targetManager, Vector tableLines, int startLine, final Runnable onNotesChangedCallback) {
        if (tableLines == null || tableLines.size() == 0) {
            return;
        }

        Vector allRows = new Vector();
        Vector isHeaderFlags = new Vector();
        Vector rowSourceLines = new Vector();
        int maxCols = 0;

        boolean firstRowSeen = false;
        boolean lastRowWasHeader = false;
        boolean nextRowIsHeader = false;

        for (int k = 0; k < tableLines.size(); k++) {
            String rowStr = (String) tableLines.elementAt(k);
            int currentSourceLine = startLine + k;

            if (isTableSeparatorRow(rowStr)) {
                if (lastRowWasHeader) {
                    lastRowWasHeader = false;
                } else {
                    nextRowIsHeader = true;
                }
                continue;
            }

            Vector cells = parseTableRow(rowStr);
            if (cells.size() > maxCols) {
                maxCols = cells.size();
            }

            boolean isHdr = false;
            if (!firstRowSeen) {
                isHdr = true;
                firstRowSeen = true;
                lastRowWasHeader = true;
            } else if (nextRowIsHeader) {
                isHdr = true;
                nextRowIsHeader = false;
                lastRowWasHeader = true;
            } else {
                isHdr = false;
                lastRowWasHeader = false;
            }

            allRows.addElement(cells);
            isHeaderFlags.addElement(isHdr ? Boolean.TRUE : Boolean.FALSE);
            rowSourceLines.addElement(new Integer(currentSourceLine));
        }

        if (maxCols > 0 && allRows.size() > 0) {
            TableField tf = new TableField(page, allRows, isHeaderFlags, rowSourceLines, maxCols, onNotesChangedCallback);
            tf.srcLine = startLine;
            targetManager.add(tf);
        }
    }

    public static boolean isTableSeparatorRow(String row) {
        if (row == null) {
            return false;
        }
        String trimmed = row.trim();
        if (trimmed.length() == 0) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '|' && c != '-' && c != ':' && c != ' ') {
                return false;
            }
        }
        return true;
    }

    private static Vector parseTableRow(String row) {
        Vector cells = new Vector();
        int start = 0;
        if (row.startsWith("|")) {
            start = 1;
        }
        int end = row.length();
        if (row.endsWith("|")) {
            end = row.length() - 1;
        }
        if (start < end) {
            String inner = row.substring(start, end);
            Vector raw = StringUtils.split(inner, '|');
            for (int i = 0; i < raw.size(); i++) {
                String c = ((String) raw.elementAt(i)).trim();
                if (TableField.isCellCheckbox(c)) {
                    String label = TableField.getCellCheckboxLabel(c);
                    String prefix = TableField.isCellCheckboxChecked(c) ? "[x]" : "[ ]";
                    cells.addElement(prefix + (label.length() > 0 ? " " + cleanMarkup(label) : ""));
                } else {
                    cells.addElement(cleanMarkup(c));
                }
            }
        }
        return cells;
    }

    public static class TableField extends Field {
        private Page page;
        private Runnable onNotesChangedCallback;
        private Vector rows; // Vector of Vectors of Strings (cell text)
        private Vector isHeaderFlags; // Vector of Boolean (true if row is a header)
        private Vector rowSourceLines; // Vector of Integer (source line index in page.getNotes())
        private int colCount;
        private int totalHeight;
        private int[] colWidths;
        private int[] rowHeights;
        private int[] rowY;
        private Vector[][] wrappedCells; // wrappedCells[r][c] = Vector of String lines
        private int focusedRow = 0;
        private int focusedCol = 0;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public TableField(Page page, Vector rows, Vector isHeaderFlags, Vector rowSourceLines, int colCount, Runnable callback) {
            super(Field.FOCUSABLE);
            this.page = page;
            this.rows = rows != null ? rows : new Vector();
            this.isHeaderFlags = isHeaderFlags != null ? isHeaderFlags : new Vector();
            this.rowSourceLines = rowSourceLines != null ? rowSourceLines : new Vector();
            this.colCount = Math.max(1, colCount);
            this.onNotesChangedCallback = callback;
        }

        public TableField(Vector headers, Vector rows, int colCount) {
            this(null, buildAllRows(headers, rows), buildHeaderFlags(headers, rows), null, colCount, null);
        }

        private static Vector buildAllRows(Vector headers, Vector rows) {
            Vector all = new Vector();
            if (headers != null && headers.size() > 0) {
                all.addElement(headers);
            }
            if (rows != null) {
                for (int i = 0; i < rows.size(); i++) {
                    all.addElement(rows.elementAt(i));
                }
            }
            return all;
        }

        private static Vector buildHeaderFlags(Vector headers, Vector rows) {
            Vector flags = new Vector();
            if (headers != null && headers.size() > 0) {
                flags.addElement(Boolean.TRUE);
            }
            if (rows != null) {
                for (int i = 0; i < rows.size(); i++) {
                    flags.addElement(Boolean.FALSE);
                }
            }
            return flags;
        }

        public static boolean isCellCheckbox(String text) {
            if (text == null) return false;
            String s = text.trim();
            return s.startsWith("[ ]") || StringUtils.startsWithIgnoreCase(s, "[x]");
        }

        public static boolean isCellCheckboxChecked(String text) {
            if (text == null) return false;
            String s = text.trim();
            return StringUtils.startsWithIgnoreCase(s, "[x]");
        }

        public static String getCellCheckboxLabel(String text) {
            if (text == null) return "";
            String s = text.trim();
            if (s.startsWith("[ ]") || StringUtils.startsWithIgnoreCase(s, "[x]")) {
                int close = s.indexOf(']');
                return close >= 0 ? s.substring(close + 1).trim() : "";
            }
            return s;
        }

        public boolean isRowHeader(int r) {
            if (isHeaderFlags != null && r >= 0 && r < isHeaderFlags.size()) {
                return ((Boolean) isHeaderFlags.elementAt(r)).booleanValue();
            }
            return (r == 0);
        }

        public int getFocusedSourceLine() {
            if (rowSourceLines != null && focusedRow >= 0 && focusedRow < rowSourceLines.size()) {
                return ((Integer) rowSourceLines.elementAt(focusedRow)).intValue();
            }
            return srcLine >= 0 ? srcLine : 0;
        }

        public boolean handleTableClick() {
            if (focusedRow < 0 || focusedRow >= rows.size()) {
                return false;
            }
            Vector rowCells = (Vector) rows.elementAt(focusedRow);
            // 1. If focused column has a checkbox, toggle it
            if (focusedCol >= 0 && focusedCol < rowCells.size()) {
                String cell = (String) rowCells.elementAt(focusedCol);
                if (isCellCheckbox(cell)) {
                    toggleCheckbox(focusedRow, focusedCol);
                    return true;
                }
            }
            // 2. If row has any checkbox, toggle the first one found
            for (int c = 0; c < rowCells.size(); c++) {
                String cell = (String) rowCells.elementAt(c);
                if (isCellCheckbox(cell)) {
                    focusedCol = c;
                    toggleCheckbox(focusedRow, c);
                    return true;
                }
            }
            return false;
        }

        protected boolean navigationClick(int status, int time) {
            if (handleTableClick()) {
                return true;
            }
            return super.navigationClick(status, time);
        }

        public void toggleCheckbox(int r, int c) {
            if (r < 0 || r >= rows.size()) return;
            Vector rowCells = (Vector) rows.elementAt(r);
            if (c < 0 || c >= rowCells.size()) return;

            String oldCell = (String) rowCells.elementAt(c);
            if (!isCellCheckbox(oldCell)) return;

            boolean nowChecked = !isCellCheckboxChecked(oldCell);
            String label = getCellCheckboxLabel(oldCell);
            String newCell = (nowChecked ? "[x]" : "[ ]") + (label.length() > 0 ? " " + label : "");
            rowCells.setElementAt(newCell, c);

            if (page != null && rowSourceLines != null && r < rowSourceLines.size()) {
                int noteLineIdx = ((Integer) rowSourceLines.elementAt(r)).intValue();
                updateCheckboxInPageNotes(noteLineIdx, c, nowChecked);
            }

            invalidate();
            if (onNotesChangedCallback != null) {
                onNotesChangedCallback.run();
            }
        }

        private void updateCheckboxInPageNotes(int noteLineIdx, int colIdx, boolean checked) {
            if (page == null) return;
            String notes = page.getNotes();
            if (notes == null) return;
            Vector lines = StringUtils.splitLines(notes);
            if (noteLineIdx < 0 || noteLineIdx >= lines.size()) return;

            String oldLine = (String) lines.elementAt(noteLineIdx);
            Vector cells = parseTableRow(oldLine);
            if (colIdx >= 0 && colIdx < cells.size()) {
                String cell = (String) cells.elementAt(colIdx);
                String label = getCellCheckboxLabel(cell);
                String updatedCell = (checked ? "[x]" : "[ ]") + (label.length() > 0 ? " " + label : "");
                cells.setElementAt(updatedCell, colIdx);

                StringBuffer sb = new StringBuffer("|");
                for (int i = 0; i < cells.size(); i++) {
                    sb.append(" ").append(cells.elementAt(i)).append(" |");
                }
                lines.setElementAt(sb.toString(), noteLineIdx);

                StringBuffer newNotes = new StringBuffer();
                for (int j = 0; j < lines.size(); j++) {
                    newNotes.append((String) lines.elementAt(j));
                    if (j < lines.size() - 1) {
                        newNotes.append("\n");
                    }
                }
                page.setNotes(newNotes.toString());
                page.setModifiedTime(System.currentTimeMillis());
                NotebookStoreManager.getInstance().commit();
            }
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            int numRows = rows.size();
            if (numRows > 0) {
                if (direction > 0) {
                    focusedRow = 0;
                } else if (direction < 0) {
                    focusedRow = numRows - 1;
                }
            } else {
                focusedRow = 0;
            }
            invalidate();
        }

        protected void onUnfocus() {
            super.onUnfocus();
            invalidate();
        }

        protected boolean navigationMovement(int dx, int dy, int status, int time) {
            int numRows = rows.size();
            if (dy > 0) {
                if (focusedRow < numRows - 1) {
                    focusedRow++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null && rowY != null && focusedRow < rowY.length && rowHeights != null && focusedRow < rowHeights.length) {
                        scr.ensureRegionVisible(this, 0, rowY[focusedRow], getWidth(), rowHeights[focusedRow]);
                    }
                    return true;
                }
            } else if (dy < 0) {
                if (focusedRow > 0) {
                    focusedRow--;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null && rowY != null && focusedRow < rowY.length && rowHeights != null && focusedRow < rowHeights.length) {
                        scr.ensureRegionVisible(this, 0, rowY[focusedRow], getWidth(), rowHeights[focusedRow]);
                    }
                    return true;
                }
            } else if (dx > 0) {
                if (focusedCol < colCount - 1) {
                    focusedCol++;
                    invalidate();
                    return true;
                }
            } else if (dx < 0) {
                if (focusedCol > 0) {
                    focusedCol--;
                    invalidate();
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            if (rowY != null && focusedRow >= 0 && focusedRow < rowY.length && rowHeights != null && focusedRow < rowHeights.length) {
                rect.set(0, rowY[focusedRow], getWidth(), rowHeights[focusedRow]);
            } else {
                rect.set(0, 0, getWidth(), 20);
            }
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on && rowY != null && focusedRow >= 0 && focusedRow < rowY.length) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int y = rowY[focusedRow] + 4;
                int x = 7;
                if (colWidths != null && focusedCol >= 0 && focusedCol < colWidths.length) {
                    int cellX = 5;
                    for (int c = 0; c < focusedCol; c++) {
                        cellX += colWidths[c];
                    }
                    x = cellX + 2;
                }
                g.fillRect(x, y, 2, baseFont.getHeight());
            }
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return totalHeight;
        }

        protected void layout(int width, int height) {
            int numRows = rows.size();
            rowHeights = new int[numRows];
            rowY = new int[numRows];
            wrappedCells = new Vector[numRows][colCount];

            int tableWidth = width - 10;
            colWidths = new int[colCount];

            int[] colWeights = new int[colCount];
            int totalWeight = 0;
            for (int c = 0; c < colCount; c++) {
                int maxLen = 3;
                boolean onlyCheckboxes = true;
                for (int r = 0; r < numRows; r++) {
                    Vector rowCells = (Vector) rows.elementAt(r);
                    if (c < rowCells.size()) {
                        String cell = (String) rowCells.elementAt(c);
                        if (!isCellCheckbox(cell)) {
                            onlyCheckboxes = false;
                        }
                        if (cell.length() > maxLen) {
                            maxLen = cell.length();
                        }
                    }
                }
                if (onlyCheckboxes) {
                    colWeights[c] = 4;
                } else {
                    colWeights[c] = Math.min(30, Math.max(6, maxLen));
                }
                totalWeight += colWeights[c];
            }
            if (totalWeight <= 0) totalWeight = colCount;

            int allocatedWidth = 0;
            for (int c = 0; c < colCount; c++) {
                if (c == colCount - 1) {
                    colWidths[c] = Math.max(24, tableWidth - allocatedWidth);
                } else {
                    int w = (tableWidth * colWeights[c]) / totalWeight;
                    if (w < 26) w = 26;
                    colWidths[c] = w;
                    allocatedWidth += w;
                }
            }

            int curY = 2;
            for (int r = 0; r < numRows; r++) {
                Vector rowCells = (Vector) rows.elementAt(r);
                boolean isHdr = isRowHeader(r);
                Font font = isHdr ? boldFont : baseFont;
                int fontH = font.getHeight();
                int maxLinesInRow = 1;

                rowY[r] = curY;

                for (int c = 0; c < colCount; c++) {
                    String cellText = c < rowCells.size() ? (String) rowCells.elementAt(c) : "";
                    boolean hasCb = isCellCheckbox(cellText);
                    int availW = colWidths[c] - 8;
                    if (hasCb) {
                        availW -= (fontH + 2);
                    }
                    if (availW < 10) availW = 10;

                    String textToWrap = hasCb ? getCellCheckboxLabel(cellText) : cellText;
                    Vector lines = wrapText(textToWrap, font, availW);
                    wrappedCells[r][c] = lines;
                    if (lines.size() > maxLinesInRow) {
                        maxLinesInRow = lines.size();
                    }
                }

                rowHeights[r] = maxLinesInRow * fontH + 8;
                curY += rowHeights[r];
            }

            totalHeight = curY + 2;
            setExtent(width, totalHeight);
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int xOffset = 5;
            int totalTableWidth = 0;
            for (int c = 0; c < colCount; c++) {
                totalTableWidth += colWidths[c];
            }

            int headerBg = dark ? 0x1E293B : 0xEAEEF3;
            int headerTextColor = dark ? 0xF8FAFC : 0x1F2937;
            int borderColor = dark ? 0x334155 : 0xD0D7DE;
            int rowBgEven = dark ? 0x000000 : 0xFFFFFF;
            int rowBgOdd = dark ? 0x0F172A : 0xF8FAFC;
            int dataTextColor = dark ? 0xF1F5F9 : 0x222222;

            int numRows = rows.size();
            for (int r = 0; r < numRows; r++) {
                int y = rowY[r];
                int h = rowHeights[r];
                boolean isHdr = isRowHeader(r);

                int bg = isHdr ? headerBg : (r % 2 == 0 ? rowBgEven : rowBgOdd);
                g.setColor(bg);
                g.fillRect(xOffset, y, totalTableWidth, h);

                Font font = isHdr ? boldFont : baseFont;
                int fontH = font.getHeight();
                g.setFont(font);

                int cellX = xOffset;
                for (int c = 0; c < colCount; c++) {
                    int cw = colWidths[c];

                    Vector cellLines = (wrappedCells != null && r < wrappedCells.length && c < wrappedCells[r].length)
                            ? wrappedCells[r][c] : null;
                    Vector rawCells = (Vector) rows.elementAt(r);
                    String rawCell = c < rawCells.size() ? (String) rawCells.elementAt(c) : "";
                    boolean hasCb = isCellCheckbox(rawCell);
                    boolean isChecked = isCellCheckboxChecked(rawCell);

                    int textX = cellX + 4;
                    if (hasCb) {
                        int boxSize = fontH - 2;
                        int boxY = y + 4 + (fontH - boxSize) / 2;
                        int boxX = cellX + 4;

                        g.setColor(dark ? 0x94A3B8 : 0x64748B);
                        g.drawRect(boxX, boxY, boxSize, boxSize);
                        g.setColor(dark ? 0x0F172A : 0xFFFFFF);
                        g.fillRect(boxX + 1, boxY + 1, boxSize - 1, boxSize - 1);

                        if (isChecked) {
                            g.setColor(dark ? 0x60A5FA : 0x1D4ED8);
                            g.drawLine(boxX + 2, boxY + (boxSize / 2), boxX + (boxSize / 2) - 1, boxY + boxSize - 3);
                            g.drawLine(boxX + (boxSize / 2) - 1, boxY + boxSize - 3, boxX + boxSize - 2, boxY + 2);
                            g.drawLine(boxX + 2, boxY + (boxSize / 2) - 1, boxX + (boxSize / 2) - 1, boxY + boxSize - 4);
                            g.drawLine(boxX + (boxSize / 2) - 1, boxY + boxSize - 4, boxX + boxSize - 2, boxY + 1);
                        }
                        textX += boxSize + 4;
                    }

                    if (cellLines != null) {
                        g.setColor(isHdr ? headerTextColor : dataTextColor);
                        for (int l = 0; l < cellLines.size(); l++) {
                            String lineStr = (String) cellLines.elementAt(l);
                            g.drawText(lineStr, textX, y + 4 + l * fontH);
                        }
                    }

                    g.setColor(borderColor);
                    g.drawLine(cellX, y, cellX, y + h);

                    cellX += cw;
                }

                g.setColor(borderColor);
                g.drawLine(cellX, y, cellX, y + h);

                g.drawLine(xOffset, y, xOffset + totalTableWidth, y);
                g.drawLine(xOffset, y + h, xOffset + totalTableWidth, y + h);
            }
        }
    }

    // ==========================================
    // Callout & Bullet Item Helpers
    // ==========================================
    private static String stripCalloutPrefix(String str) {
        if (str == null) return "";
        String s = str.trim();
        if (s.startsWith("* ") || s.startsWith("> ")) {
            return s.substring(2).trim();
        } else if (s.startsWith("*") || s.startsWith(">")) {
            return s.substring(1).trim();
        }
        return s;
    }

    private static boolean isAlertStart(String text) {
        if (text == null) return false;
        String s = text.trim();
        if (s.startsWith("* ") || s.startsWith("> ")) {
            s = s.substring(2).trim();
        } else if (s.startsWith("*") || s.startsWith(">")) {
            s = s.substring(1).trim();
        }
        String u = s.toUpperCase();
        return u.startsWith("[!NOTE]") || u.startsWith("[!TIP]") || u.startsWith("[!WARNING]")
                || u.startsWith("[!IMPORTANT]") || u.startsWith("[!CAUTION]");
    }

    private static boolean isCalloutItem(String trimmed) {
        if (trimmed == null || trimmed.length() == 0) {
            return false;
        }
        if (trimmed.equals("***")) {
            return false;
        }
        if (trimmed.startsWith("**")) {
            return false;
        }
        if (isTaskItem(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("* ")) {
            return true;
        }
        if (trimmed.equals("*")) {
            return true;
        }
        if (trimmed.startsWith("*") && !trimmed.endsWith("*")) {
            return true;
        }
        return false;
    }

    private static boolean isBulletItem(String trimmed) {
        if (trimmed == null || trimmed.length() == 0) {
            return false;
        }
        if (trimmed.startsWith("•")) {
            return true;
        }
        if (trimmed.startsWith("- ") || trimmed.equals("-")) {
            return true;
        }
        if (trimmed.startsWith("+ ") || trimmed.equals("+")) {
            return true;
        }
        if (trimmed.startsWith(">")) {
            return true;
        }
        return false;
    }

    public static class BlockquoteField extends Field {
        public static final int ALERT_NONE = 0;
        public static final int ALERT_NOTE = 1;
        public static final int ALERT_TIP = 2;
        public static final int ALERT_WARNING = 3;
        public static final int ALERT_IMPORTANT = 4;
        public static final int ALERT_CAUTION = 5;

        private Vector lines;
        private Vector wrappedLines;
        private int cachedHeight;
        private int focusedLine = 0;
        private int alertType = ALERT_NONE;
        private String alertTitle = null;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public BlockquoteField(Vector lines) {
            super(Field.FOCUSABLE);
            this.lines = lines != null ? lines : new Vector();
            detectAlert();
        }

        private void detectAlert() {
            if (lines.size() == 0) return;
            String first = ((String) lines.elementAt(0)).trim();
            String upper = first.toUpperCase();
            int tagLen = 0;
            if (upper.startsWith("[!NOTE]")) {
                alertType = ALERT_NOTE;
                alertTitle = "NOTE";
                tagLen = 7;
            } else if (upper.startsWith("[!TIP]")) {
                alertType = ALERT_TIP;
                alertTitle = "TIP";
                tagLen = 6;
            } else if (upper.startsWith("[!WARNING]")) {
                alertType = ALERT_WARNING;
                alertTitle = "WARNING";
                tagLen = 10;
            } else if (upper.startsWith("[!IMPORTANT]")) {
                alertType = ALERT_IMPORTANT;
                alertTitle = "IMPORTANT";
                tagLen = 12;
            } else if (upper.startsWith("[!CAUTION]")) {
                alertType = ALERT_CAUTION;
                alertTitle = "CAUTION";
                tagLen = 10;
            }

            if (alertTitle != null) {
                String remainder = first.substring(tagLen).trim();
                if (remainder.length() > 0) {
                    lines.setElementAt(remainder, 0);
                } else {
                    if (lines.size() > 1) {
                        lines.removeElementAt(0);
                    } else {
                        lines.setElementAt("", 0);
                    }
                }
            }
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return cachedHeight;
        }

        protected void layout(int width, int height) {
            int textWidth = width - 10 - 18;
            if (textWidth < 50) textWidth = 50;
            wrappedLines = new Vector();
            for (int i = 0; i < lines.size(); i++) {
                String l = (String) lines.elementAt(i);
                Vector w = wrapText(l, italicFont, textWidth);
                for (int k = 0; k < w.size(); k++) {
                    wrappedLines.addElement(w.elementAt(k));
                }
            }
            if (wrappedLines.size() == 0) {
                wrappedLines.addElement("");
            }
            int fontH = italicFont.getHeight();
            int headerH = (alertTitle != null) ? (boldFont.getHeight() + 4) : 0;
            cachedHeight = headerH + wrappedLines.size() * (fontH + 4) + 12;
            setExtent(width, cachedHeight);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            int count = wrappedLines != null ? wrappedLines.size() : lines.size();
            if (count > 0) {
                if (direction > 0) {
                    focusedLine = 0;
                } else if (direction < 0) {
                    focusedLine = count - 1;
                }
            } else {
                focusedLine = 0;
            }
            invalidate();
        }

        protected void onUnfocus() {
            super.onUnfocus();
            invalidate();
        }

        protected boolean navigationMovement(int dx, int dy, int status, int time) {
            int numLines = wrappedLines != null ? wrappedLines.size() : 1;
            int headerH = (alertTitle != null) ? (boldFont.getHeight() + 4) : 0;
            int lineH = italicFont.getHeight() + 4;
            if (dy > 0) {
                if (focusedLine < numLines - 1) {
                    focusedLine++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        scr.ensureRegionVisible(this, 0, 6 + headerH + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            } else if (dy < 0) {
                if (focusedLine > 0) {
                    focusedLine--;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        scr.ensureRegionVisible(this, 0, 6 + headerH + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            int headerH = (alertTitle != null) ? (boldFont.getHeight() + 4) : 0;
            int lineH = italicFont.getHeight() + 4;
            rect.set(0, 6 + headerH + focusedLine * lineH, getWidth(), lineH);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int headerH = (alertTitle != null) ? (boldFont.getHeight() + 4) : 0;
                int lineH = italicFont.getHeight() + 4;
                int y = 6 + headerH + focusedLine * lineH;
                g.fillRect(11, y, 2, italicFont.getHeight());
            }
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int w = getWidth() - 10;
            int bg, bar, textColor;

            switch (alertType) {
                case ALERT_NOTE:
                    bg = dark ? 0x0F172A : 0xEFF6FF;
                    bar = dark ? 0x60A5FA : 0x2563EB;
                    textColor = dark ? 0xDBEAFE : 0x1E3A8A;
                    break;
                case ALERT_TIP:
                    bg = dark ? 0x052E16 : 0xF0FDF4;
                    bar = dark ? 0x4ADE80 : 0x16A34A;
                    textColor = dark ? 0xDCFCE7 : 0x14532D;
                    break;
                case ALERT_WARNING:
                    bg = dark ? 0x281B04 : 0xFFFBEB;
                    bar = dark ? 0xFBBF24 : 0xD97706;
                    textColor = dark ? 0xFEF3C7 : 0x78350F;
                    break;
                case ALERT_IMPORTANT:
                case ALERT_CAUTION:
                    bg = dark ? 0x2D0606 : 0xFEF2F2;
                    bar = dark ? 0xF87171 : 0xDC2626;
                    textColor = dark ? 0xFEE2E2 : 0x7F1D1D;
                    break;
                default:
                    bg = dark ? 0x180D26 : 0xFAF8FC;
                    bar = dark ? 0xC084FC : 0x7719AA;
                    textColor = dark ? 0xF3E8FF : 0x4A5568;
                    break;
            }

            g.setColor(bg);
            g.fillRect(5, 2, w, cachedHeight - 4);
            g.setColor(bar);
            g.fillRect(5, 2, 4, cachedHeight - 4);

            int y = 6;
            if (alertTitle != null) {
                g.setFont(boldFont);
                g.setColor(bar);
                g.drawText(alertTitle, 16, y);
                y += boldFont.getHeight() + 4;
            }

            g.setFont(italicFont);
            g.setColor(textColor);
            int fontH = italicFont.getHeight();
            if (wrappedLines != null) {
                for (int i = 0; i < wrappedLines.size(); i++) {
                    String l = (String) wrappedLines.elementAt(i);
                    g.drawText(l, 16, y);
                    y += fontH + 4;
                }
            }
        }
    }

    // ==========================================
    // Code Block Rendering
    // ==========================================
    public static class CodeBlockField extends Field {
        private Vector codeLines;
        private Vector wrappedLines;
        private int cachedHeight;
        private int focusedLine = 0;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public CodeBlockField(Vector codeLines) {
            super(Field.FOCUSABLE);
            this.codeLines = codeLines != null ? codeLines : new Vector();
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return cachedHeight;
        }

        protected void layout(int width, int height) {
            int textWidth = width - 10 - 14;
            wrappedLines = new Vector();
            for (int i = 0; i < codeLines.size(); i++) {
                String l = (String) codeLines.elementAt(i);
                Vector w = wrapText(l, monoFont, textWidth);
                for (int k = 0; k < w.size(); k++) {
                    wrappedLines.addElement(w.elementAt(k));
                }
            }
            if (wrappedLines.size() == 0) {
                wrappedLines.addElement("");
            }
            int fontH = monoFont.getHeight();
            cachedHeight = wrappedLines.size() * (fontH + 3) + 12;
            setExtent(width, cachedHeight);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            int count = wrappedLines != null ? wrappedLines.size() : codeLines.size();
            if (count > 0) {
                if (direction > 0) {
                    focusedLine = 0;
                } else if (direction < 0) {
                    focusedLine = count - 1;
                }
            } else {
                focusedLine = 0;
            }
            invalidate();
        }

        protected void onUnfocus() {
            super.onUnfocus();
            invalidate();
        }

        protected boolean navigationMovement(int dx, int dy, int status, int time) {
            int numLines = wrappedLines != null ? wrappedLines.size() : 1;
            if (dy > 0) {
                if (focusedLine < numLines - 1) {
                    focusedLine++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int lineH = monoFont.getHeight() + 3;
                        scr.ensureRegionVisible(this, 0, 6 + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            } else if (dy < 0) {
                if (focusedLine > 0) {
                    focusedLine--;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int lineH = monoFont.getHeight() + 3;
                        scr.ensureRegionVisible(this, 0, 6 + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            int lineH = monoFont.getHeight() + 3;
            rect.set(0, 6 + focusedLine * lineH, getWidth(), lineH);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int lineH = monoFont.getHeight() + 3;
                int y = 6 + focusedLine * lineH;
                g.fillRect(8, y, 2, monoFont.getHeight());
            }
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int w = getWidth() - 10;
            int bg = dark ? 0x0F172A : 0xF4F6F8;
            int border = dark ? 0x334155 : 0xD1D5DB;
            int textColor = dark ? 0x38BDF8 : 0x1F2937;

            g.setColor(bg);
            g.fillRect(5, 2, w, cachedHeight - 4);
            g.setColor(border);
            g.drawRect(5, 2, w, cachedHeight - 4);

            g.setFont(monoFont);
            g.setColor(textColor);
            int y = 6;
            int fontH = monoFont.getHeight();
            if (wrappedLines != null) {
                for (int i = 0; i < wrappedLines.size(); i++) {
                    String l = (String) wrappedLines.elementAt(i);
                    g.drawText(l, 12, y);
                    y += fontH + 3;
                }
            }
        }
    }

    // ==========================================
    // Checklist / Task Item Rendering
    // ==========================================
    private static boolean isTaskItem(String line) {
        if (line == null) {
            return false;
        }
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ") || s.startsWith("> ")
                || s.startsWith("• ")) {
            s = s.substring(2).trim();
        }
        return s.startsWith("[ ]") || StringUtils.startsWithIgnoreCase(s, "[x]") || s.startsWith("[-]") || s.startsWith("[~]");
    }

    private static void renderTaskItem(Manager targetManager, final Page page, String line, final int lineIndex,
            final Runnable onNotesChangedCallback, int leadingSpaces) {
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ") || s.startsWith("> ")
                || s.startsWith("• ")) {
            s = s.substring(2).trim();
        }
        final boolean isCancelled = s.startsWith("[-]") || s.startsWith("[~]");
        final boolean isChecked = StringUtils.startsWithIgnoreCase(s, "[x]") || isCancelled;
        int closeBracket = s.indexOf(']');
        String taskLabel = closeBracket >= 0 ? s.substring(closeBracket + 1).trim() : s;
        if (isCancelled && taskLabel.length() > 0) {
            taskLabel = "[CANCELLED] " + taskLabel;
        }

        int level = (leadingSpaces / 2) + 1;
        StringBuffer indent = new StringBuffer();
        for (int ind = 0; ind < (level - 1) * 2; ind++) {
            indent.append(' ');
        }
        indent.append(' ');

        final ThemedCheckboxField cb = new ThemedCheckboxField(indent.toString() + taskLabel, isChecked);
        if (page != null) {
            cb.setChangeListener(new FieldChangeListener() {
                public void fieldChanged(Field field, int context) {
                    boolean newChecked = cb.getChecked();
                    updateTaskInPageNotes(page, lineIndex, newChecked);
                    if (onNotesChangedCallback != null) {
                        onNotesChangedCallback.run();
                    }
                }
            });
        }
        targetManager.add(cb);
    }

    private static void updateTaskInPageNotes(Page page, int lineIdx, boolean checked) {
        if (page == null) {
            return;
        }
        Vector lines = StringUtils.splitLines(page.getNotes());
        if (lineIdx >= 0 && lineIdx < lines.size()) {
            String l = (String) lines.elementAt(lineIdx);
            String updated;
            if (checked) {
                if (l.indexOf("[ ]") >= 0) {
                    updated = StringUtils.replace(l, "[ ]", "[x]");
                } else {
                    updated = l;
                }
            } else {
                if (l.indexOf("[x]") >= 0) {
                    updated = StringUtils.replace(l, "[x]", "[ ]");
                } else if (l.indexOf("[X]") >= 0) {
                    updated = StringUtils.replace(l, "[X]", "[ ]");
                } else if (l.indexOf("[-]") >= 0) {
                    updated = StringUtils.replace(l, "[-]", "[ ]");
                } else if (l.indexOf("[~]") >= 0) {
                    updated = StringUtils.replace(l, "[~]", "[ ]");
                } else {
                    updated = l;
                }
            }
            lines.setElementAt(updated, lineIdx);
            StringBuffer sb = new StringBuffer();
            for (int j = 0; j < lines.size(); j++) {
                sb.append((String) lines.elementAt(j));
                if (j < lines.size() - 1) {
                    sb.append("\n");
                }
            }
            page.setNotes(sb.toString());
            page.setModifiedTime(System.currentTimeMillis());
            NotebookStoreManager.getInstance().commit();
        }
    }

    // ==========================================
    // Numbered List / Tags Detection
    // ==========================================
    private static boolean isNumberedListItem(String line) {
        if (line == null) {
            return false;
        }
        String s = line.trim();
        int dot = s.indexOf(". ");
        if (dot > 0 && dot <= 5) {
            return StringUtils.isDigits(s.substring(0, dot));
        }
        return false;
    }

    private static boolean isTagLine(String line) {
        if (line == null) {
            return false;
        }
        String s = line.trim().toLowerCase();
        return s.startsWith("[!]") || s.startsWith("[!]:") ||
                s.startsWith("[?]") || s.startsWith("[?]:") ||
                s.startsWith("[*]") || s.startsWith("[*]:") ||
                s.startsWith("[todo]") || s.startsWith("[idea]") || s.startsWith("[note]");
    }

    private static void renderTagLine(Manager targetManager, String line, int srcLineIdx) {
        String s = line.trim();
        String lower = s.toLowerCase();
        int closeBracket = s.indexOf(']');
        String tag = s.substring(0, closeBracket + 1);
        String rest = s.substring(closeBracket + 1).trim();
        if (rest.startsWith(":")) {
            rest = rest.substring(1).trim();
        }

        String displayTag;
        int lightColor;
        int darkColor;

        if (lower.startsWith("[!]")) {
            displayTag = "[!] IMPORTANT: ";
            lightColor = 0xB91C1C;
            darkColor = 0xF87171;
        } else if (lower.startsWith("[?]")) {
            displayTag = "[?] QUESTION: ";
            lightColor = 0x1D4ED8;
            darkColor = 0x60A5FA;
        } else if (lower.startsWith("[*]")) {
            displayTag = "[*] STAR: ";
            lightColor = 0xB45309;
            darkColor = 0xFBBF24;
        } else if (lower.startsWith("[idea]")) {
            displayTag = "[IDEA]: ";
            lightColor = 0x7E22CE;
            darkColor = 0xC084FC;
        } else {
            displayTag = tag + " ";
            lightColor = 0x1A5276;
            darkColor = 0x90CDF4;
        }

        ThemedLabelField tagLbl = new ThemedLabelField(displayTag + rest, boldFont, LabelField.FOCUSABLE, lightColor,
                darkColor);
        tagLbl.srcLine = srcLineIdx;
        targetManager.add(tagLbl);
    }

    // ==========================================
    // Image and Attachment Rendering
    // ==========================================
    private static void renderImage(Manager targetManager, final AttachmentStorageManager storage, final String imgName,
            int maxWidth) {
        Bitmap bmp = storage.loadAttachmentBitmap(imgName, maxWidth);
        if (bmp != null) {
            final String resolvedUrl = storage.resolveAttachmentUrl(imgName);
            BitmapField bf = new BitmapField(bmp, BitmapField.FOCUSABLE | Field.FIELD_HCENTER) {
                protected boolean navigationClick(int status, int time) {
                    String target = (resolvedUrl != null) ? resolvedUrl : imgName;
                    storage.openFileExternally(target);
                    return true;
                }
            };
            targetManager.add(bf);
            String displayCaption = imgName;
            if (displayCaption.startsWith("img_")) {
                int secondUnderscore = displayCaption.indexOf('_', 4);
                if (secondUnderscore > 0 && secondUnderscore + 1 < displayCaption.length()) {
                    displayCaption = displayCaption.substring(secondUnderscore + 1);
                }
            } else if (displayCaption.startsWith("file:///")) {
                int lastSlash = displayCaption.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash + 1 < displayCaption.length()) {
                    displayCaption = displayCaption.substring(lastSlash + 1);
                }
            }
            ThemedLabelField caption = new ThemedLabelField(displayCaption + " (Click image to open)", italicFont,
                    Field.FIELD_HCENTER | LabelField.FOCUSABLE, 0x7F8C8D, 0x94A3B8);
            targetManager.add(caption);
        } else {
            ThemedLabelField notFound = new ThemedLabelField("[Image Missing: " + imgName + "]", italicFont,
                    Field.FIELD_HCENTER | LabelField.FOCUSABLE, 0x7F8C8D, 0x94A3B8);
            targetManager.add(notFound);
        }
    }

    private static void renderAttachment(Manager targetManager, final AttachmentStorageManager storage,
            final String attRef) {
        final String resolvedUrl = storage.resolveAttachmentUrl(attRef);
        String displayName = attRef;
        if (displayName.startsWith("att_") || displayName.startsWith("img_")) {
            int secondUnderscore = displayName.indexOf('_', 4);
            if (secondUnderscore > 0 && secondUnderscore + 1 < displayName.length()) {
                displayName = displayName.substring(secondUnderscore + 1);
            }
        } else if (displayName.startsWith("file:///")) {
            int lastSlash = displayName.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash + 1 < displayName.length()) {
                displayName = displayName.substring(lastSlash + 1);
            }
        }

        String typeLabel = getAttachmentTypeLabel(attRef);
        ButtonField openBtn = new ButtonField("[" + typeLabel + "] " + displayName + "  [Open]",
                ButtonField.CONSUME_CLICK | Field.FIELD_HCENTER);
        openBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                String target = (resolvedUrl != null) ? resolvedUrl : attRef;
                storage.openFileExternally(target);
            }
        });
        targetManager.add(openBtn);
    }

    public static String getAttachmentTypeLabel(String name) {
        if (name == null) {
            return "Attachment";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp3") || lower.endsWith(".amr") || lower.endsWith(".wav") ||
                lower.endsWith(".aac") || lower.endsWith(".m4a") || lower.endsWith(".wma") ||
                lower.endsWith(".mid") || lower.endsWith(".midi")) {
            return "Audio";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") ||
                lower.endsWith(".rtf")) {
            return "Document";
        } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) {
            return "Spreadsheet";
        } else if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "Presentation";
        } else if (lower.endsWith(".pdf")) {
            return "PDF";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".avi") ||
                lower.endsWith(".wmv")) {
            return "Video";
        } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".tar") ||
                lower.endsWith(".gz")) {
            return "Archive";
        }
        return "Attachment";
    }

    private static void renderMixedLine(Manager targetManager, AttachmentStorageManager storage, String line,
            int maxWidth) {
        String lower = line.toLowerCase();
        String prefix = "[image:";
        int pLen = prefix.length();
        int start = 0;

        while (true) {
            int imgIdx = lower.indexOf(prefix, start);
            if (imgIdx < 0) {
                String remaining = line.substring(start).trim();
                if (remaining.length() > 0) {
                    renderStyledLine(targetManager, remaining);
                }
                break;
            }

            if (imgIdx > start) {
                String textBefore = line.substring(start, imgIdx).trim();
                if (textBefore.length() > 0) {
                    renderStyledLine(targetManager, textBefore);
                }
            }

            int endIdx = line.indexOf("]", imgIdx);
            if (endIdx < 0) {
                renderStyledLine(targetManager, line.substring(imgIdx));
                break;
            }

            String imgName = line.substring(imgIdx + pLen, endIdx).trim();
            renderImage(targetManager, storage, imgName, maxWidth);
            start = endIdx + 1;
        }
    }

    // ==========================================
    // Inline Styled Paragraph Rendering
    // ==========================================
    private static void renderStyledLine(Manager targetManager, String line) {
        renderStyledLine(targetManager, line, -1);
    }

    private static void renderStyledLine(Manager targetManager, String line, int srcLineIdx) {
        if (line == null || line.length() == 0) {
            ThemedLabelField spacer = new ThemedLabelField(" ", baseFont, LabelField.FOCUSABLE, 0x000000, 0xF1F5F9);
            spacer.srcLine = srcLineIdx;
            targetManager.add(spacer);
            return;
        }

        // Fast path: no markup characters at all
        boolean hasMarkup = line.indexOf('*') >= 0 || line.indexOf('_') >= 0 || line.indexOf('~') >= 0
                || line.indexOf('=') >= 0 || line.indexOf('`') >= 0 || line.indexOf('<') >= 0
                || line.indexOf('[') >= 0 || line.indexOf("http://") >= 0 || line.indexOf("https://") >= 0;
        if (!hasMarkup) {
            ThemedLabelField lbl = new ThemedLabelField(line, baseFont, LabelField.FOCUSABLE, 0x000000, 0xF1F5F9);
            lbl.srcLine = srcLineIdx;
            targetManager.add(lbl);
            return;
        }

        Vector segments = parseInlineSpans(line);
        int segCount = segments.size();

        if (segCount == 0) {
            ThemedLabelField spacer = new ThemedLabelField(" ", baseFont, LabelField.FOCUSABLE, 0x000000, 0xF1F5F9);
            spacer.srcLine = srcLineIdx;
            targetManager.add(spacer);
            return;
        }

        // Single plain segment — no complex wrapper needed
        if (segCount == 1) {
            SpanSegment seg = (SpanSegment) segments.elementAt(0);
            if (!seg.isUnderline && !seg.isStrike && !seg.isHighlight && !seg.isCode && !seg.isLink && !seg.isKbd) {
                ThemedLabelField lbl = new ThemedLabelField(seg.text, getFontForSegment(seg), LabelField.FOCUSABLE,
                        0x000000, 0xF1F5F9);
                lbl.srcLine = srcLineIdx;
                targetManager.add(lbl);
                return;
            }
        }

        // Multiple segments: render via custom word-wrapping paragraph field
        RichParagraphField rpf = new RichParagraphField(segments);
        rpf.srcLine = srcLineIdx;
        targetManager.add(rpf);
    }

    public static class SpanSegment {
        public String text;
        public boolean isBold;
        public boolean isItalic;
        public boolean isUnderline;
        public boolean isStrike;
        public boolean isHighlight;
        public boolean isCode;
        public boolean isLink;
        public boolean isKbd;
        public String linkUrl;

        public SpanSegment(String text, boolean bold, boolean italic, boolean underline,
                boolean strike, boolean highlight, boolean code) {
            this(text, bold, italic, underline, strike, highlight, code, false, false, null);
        }

        public SpanSegment(String text, boolean bold, boolean italic, boolean underline,
                boolean strike, boolean highlight, boolean code, boolean isLink) {
            this(text, bold, italic, underline, strike, highlight, code, isLink, false, null);
        }

        public SpanSegment(String text, boolean bold, boolean italic, boolean underline,
                boolean strike, boolean highlight, boolean code, boolean isLink, boolean isKbd, String linkUrl) {
            this.text = text;
            this.isBold = bold;
            this.isItalic = italic;
            this.isUnderline = underline;
            this.isStrike = strike;
            this.isHighlight = highlight;
            this.isCode = code;
            this.isLink = isLink;
            this.isKbd = isKbd;
            this.linkUrl = linkUrl;
        }
    }

    private static Font getFontForSegment(SpanSegment seg) {
        if (seg.isKbd || seg.isCode) {
            return monoFont;
        } else if (seg.isBold && seg.isItalic) {
            return boldItalicFont;
        } else if (seg.isBold) {
            return boldFont;
        } else if (seg.isItalic) {
            return italicFont;
        } else {
            return baseFont;
        }
    }

    public static class RichParagraphField extends Field {
        private int cachedHeight;
        private Vector lineLayouts; // Vector of Vectors containing Word objects
        private Vector words;
        private int focusedLine = 0;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public RichParagraphField(Vector segments) {
            super(Field.FOCUSABLE);
            buildWords(segments);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            if (lineLayouts != null && lineLayouts.size() > 0) {
                if (direction > 0) {
                    focusedLine = 0;
                } else if (direction < 0) {
                    focusedLine = lineLayouts.size() - 1;
                }
            } else {
                focusedLine = 0;
            }
            invalidate();
        }

        protected void onUnfocus() {
            super.onUnfocus();
            invalidate();
        }

        protected boolean navigationMovement(int dx, int dy, int status, int time) {
            int numLines = lineLayouts != null ? lineLayouts.size() : 1;
            if (dy > 0) {
                if (focusedLine < numLines - 1) {
                    focusedLine++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int lineH = baseFont.getHeight() + 4;
                        scr.ensureRegionVisible(this, 0, 2 + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            } else if (dy < 0) {
                if (focusedLine > 0) {
                    focusedLine--;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int lineH = baseFont.getHeight() + 4;
                        scr.ensureRegionVisible(this, 0, 2 + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            int lineH = baseFont.getHeight() + 4;
            rect.set(0, 2 + focusedLine * lineH, getWidth(), lineH);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int lineH = baseFont.getHeight() + 4;
                int y = 2 + focusedLine * lineH;
                g.fillRect(1, y, 2, baseFont.getHeight());
            }
        }

        private void buildWords(Vector segments) {
            words = new Vector();
            Word currentWord = new Word();

            for (int s = 0; s < segments.size(); s++) {
                SpanSegment seg = (SpanSegment) segments.elementAt(s);
                String text = seg.text;
                Font f = getFontForSegment(seg);

                int len = text.length();
                int i = 0;
                while (i < len) {
                    int nextSpace = text.indexOf(' ', i);
                    String chunk;
                    if (nextSpace >= 0) {
                        chunk = text.substring(i, nextSpace + 1);
                        i = nextSpace + 1;
                    } else {
                        chunk = text.substring(i);
                        i = len;
                    }

                    currentWord.addFragment(chunk, f, seg.isUnderline, seg.isStrike, seg.isHighlight, seg.isCode, seg.isLink);

                    if (chunk.endsWith(" ")) {
                        words.addElement(currentWord);
                        currentWord = new Word();
                    }
                }
            }
            if (currentWord.fragments.size() > 0) {
                words.addElement(currentWord);
            }
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return cachedHeight;
        }

        protected void layout(int width, int height) {
            lineLayouts = new Vector();
            Vector currentLineWords = new Vector();
            int currentLineWidth = 0;

            for (int w = 0; w < words.size(); w++) {
                Word word = (Word) words.elementAt(w);

                if (currentLineWidth + word.totalWidth > width && currentLineWidth > 0) {
                    lineLayouts.addElement(currentLineWords);
                    currentLineWords = new Vector();
                    currentLineWidth = 0;
                }

                currentLineWords.addElement(word);
                currentLineWidth += word.totalWidth;
            }

            if (currentLineWords.size() > 0) {
                lineLayouts.addElement(currentLineWords);
            }

            cachedHeight = lineLayouts.size() * (baseFont.getHeight() + 4);
            setExtent(width, cachedHeight);
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int defaultTextColor = dark ? 0xF1F5F9 : 0x000000;
            int codeTextColor = dark ? 0xFB7185 : 0x990000;
            int highlightTextColor = dark ? 0xFEF08A : 0x000000;
            int linkTextColor = dark ? 0x60A5FA : 0x1D4ED8;

            int y = 2;
            int numLines = lineLayouts.size();
            for (int l = 0; l < numLines; l++) {
                Vector lineWords = (Vector) lineLayouts.elementAt(l);
                int x = 0;
                int numWords = lineWords.size();
                for (int w = 0; w < numWords; w++) {
                    Word word = (Word) lineWords.elementAt(w);
                    int numFrags = word.fragments.size();
                    for (int f = 0; f < numFrags; f++) {
                        Fragment frag = (Fragment) word.fragments.elementAt(f);
                        int fontH = frag.font.getHeight();

                        // Highlight background
                        if (frag.isHighlight) {
                            g.setColor(dark ? 0x785B00 : 0xFFF3A8);
                            g.fillRect(x, y - 1, frag.width, fontH + 2);
                        } else if (frag.isCode) {
                            g.setColor(dark ? 0x27272A : 0xECEFF1);
                            g.fillRect(x, y - 1, frag.width, fontH + 2);
                        }

                        if (frag.isLink) {
                            g.setColor(linkTextColor);
                        } else if (frag.isHighlight) {
                            g.setColor(highlightTextColor);
                        } else if (frag.isCode) {
                            g.setColor(codeTextColor);
                        } else {
                            g.setColor(defaultTextColor);
                        }
                        g.setFont(frag.font);
                        g.drawText(frag.text, x, y);

                        // Underline or link underline
                        if (frag.isUnderline || frag.isLink) {
                            g.setColor(frag.isLink ? linkTextColor : (dark ? 0xF1F5F9 : 0x000000));
                            g.drawLine(x, y + fontH - 1, x + frag.width, y + fontH - 1);
                        }

                        // Strikethrough
                        if (frag.isStrike) {
                            g.setColor(dark ? 0x94A3B8 : 0x777777);
                            g.drawLine(x, y + (fontH / 2), x + frag.width, y + (fontH / 2));
                        }

                        x += frag.width;
                    }
                }
                y += baseFont.getHeight() + 4;
            }
        }
    }

    private static class Word {
        public Vector fragments = new Vector();
        public int totalWidth = 0;

        public void addFragment(String text, Font font, boolean isUnderline, boolean isStrike,
                boolean isHighlight, boolean isCode, boolean isLink) {
            int maxW = Graphics.getScreenWidth() - 20;
            if (maxW <= 0) maxW = 200;
            int w = font.getAdvance(text);
            if (w <= maxW || text.length() <= 3) {
                fragments.addElement(new Fragment(text, font, w, isUnderline, isStrike, isHighlight, isCode, isLink));
                totalWidth += w;
            } else {
                int start = 0;
                for (int c = 1; c <= text.length(); c++) {
                    String sub = text.substring(start, c);
                    if (font.getAdvance(sub) > maxW && c > start + 1) {
                        String part = text.substring(start, c - 1);
                        int pw = font.getAdvance(part);
                        fragments.addElement(new Fragment(part, font, pw, isUnderline, isStrike, isHighlight, isCode, isLink));
                        totalWidth += pw;
                        start = c - 1;
                    }
                }
                if (start < text.length()) {
                    String part = text.substring(start);
                    int pw = font.getAdvance(part);
                    fragments.addElement(new Fragment(part, font, pw, isUnderline, isStrike, isHighlight, isCode, isLink));
                    totalWidth += pw;
                }
            }
        }
    }

    private static class Fragment {
        public String text;
        public Font font;
        public int width;
        public boolean isUnderline;
        public boolean isStrike;
        public boolean isHighlight;
        public boolean isCode;
        public boolean isLink;

        public Fragment(String text, Font font, int width, boolean isUnderline, boolean isStrike,
                boolean isHighlight, boolean isCode, boolean isLink) {
            this.text = text;
            this.font = font;
            this.width = width;
            this.isUnderline = isUnderline;
            this.isStrike = isStrike;
            this.isHighlight = isHighlight;
            this.isCode = isCode;
            this.isLink = isLink;
        }
    }

    /**
     * Parses a line for inline spans: bold (*** or **), italic (*), underline (__),
     * strikethrough (~~), highlight (==), inline code (`), kbd (<kbd>), and links ([text](url)).
     */
    public static Vector parseInlineSpans(String line) {
        Vector segments = new Vector();
        int len = line.length();
        int i = 0;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean highlight = false;
        boolean code = false;
        StringBuffer current = new StringBuffer();

        while (i < len) {
            // *** Bold + Italic
            if (i + 2 < len && line.charAt(i) == '*' && line.charAt(i + 1) == '*' && line.charAt(i + 2) == '*') {
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                bold = !bold;
                italic = !italic;
                i += 3;
            } else if (i + 1 < len && line.charAt(i) == '*' && line.charAt(i + 1) == '*') {
                // ** Bold
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                bold = !bold;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '_' && line.charAt(i + 1) == '_') {
                // __ Underline
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                underline = !underline;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '~' && line.charAt(i + 1) == '~') {
                // ~~ Strikethrough
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                strike = !strike;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '=' && line.charAt(i + 1) == '=') {
                // == Highlight
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                highlight = !highlight;
                i += 2;
            } else if (i + 4 < len && line.substring(i, i + 5).equalsIgnoreCase("<kbd>")) {
                // <kbd> keyboard key
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                code = true;
                highlight = true;
                i += 5;
            } else if (i + 5 < len && line.substring(i, i + 6).equalsIgnoreCase("</kbd>")) {
                // </kbd>
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                code = false;
                highlight = false;
                i += 6;
            } else if (line.charAt(i) == '`') {
                // ` Inline Code
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                code = !code;
                i += 1;
            } else if (line.charAt(i) == '[' && line.indexOf("](", i) > i && line.indexOf(')', line.indexOf("](", i)) > 0) {
                // Inline link [text](url)
                int mid = line.indexOf("](", i);
                int close = line.indexOf(')', mid);
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                String linkText = line.substring(i + 1, mid);
                segments.addElement(new SpanSegment(linkText, bold, italic, true, strike, highlight, code, true));
                i = close + 1;
            } else if (line.charAt(i) == '*') {
                // * Italic
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
                    current = new StringBuffer();
                }
                italic = !italic;
                i += 1;
            } else {
                current.append(line.charAt(i));
                i++;
            }
        }

        if (current.length() > 0) {
            segments.addElement(new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code, false));
        }

        return segments;
    }

    /**
     * Strips formatting markers for plain headings where entire text is styled.
     */
    public static String cleanMarkup(String text) {
        if (text == null) {
            return "";
        }
        String res = StringUtils.replace(text, "***", "");
        res = StringUtils.replace(res, "**", "");
        res = StringUtils.replace(res, "*", "");
        res = StringUtils.replace(res, "__", "");
        res = StringUtils.replace(res, "~~", "");
        res = StringUtils.replace(res, "==", "");
        res = StringUtils.replace(res, "`", "");
        res = StringUtils.replace(res, "<kbd>", "");
        res = StringUtils.replace(res, "</kbd>", "");
        res = StringUtils.replace(res, "<KBD>", "");
        res = StringUtils.replace(res, "</KBD>", "");
        return res;
    }
}