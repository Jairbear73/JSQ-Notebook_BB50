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
    private static Font boldFont;
    private static Font italicFont;
    private static Font monoFont;

    static {
        baseFont = Font.getDefault();
        int baseH = baseFont.getHeight();
        h1Font = baseFont.derive(Font.BOLD, baseH + 6, net.rim.device.api.ui.Ui.UNITS_px);
        h2Font = baseFont.derive(Font.BOLD, baseH + 3, net.rim.device.api.ui.Ui.UNITS_px);
        h3Font = baseFont.derive(Font.BOLD, baseH + 1, net.rim.device.api.ui.Ui.UNITS_px);
        h4Font = baseFont.derive(Font.BOLD, baseH, net.rim.device.api.ui.Ui.UNITS_px);
        boldFont = baseFont.derive(Font.BOLD);
        italicFont = baseFont.derive(Font.ITALIC);
        monoFont = baseFont.derive(Font.PLAIN, Math.max(9, baseH - 2), net.rim.device.api.ui.Ui.UNITS_px);
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
                    if (nextLine.startsWith("|") && nextLine.endsWith("|")) {
                        tableLines.addElement(nextLine);
                        i++;
                    } else {
                        break;
                    }
                }
                renderTableBlock(targetManager, tableLines, startLine);
                continue;
            }

            // 3. Horizontal Rule: ---, ***, ___
            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                targetManager.add(new ThemedSeparatorField());
                continue;
            }

            // 4. Headings (with Light / Dark mode intensity reversals)
            if (trimmed.startsWith("#### ")) {
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

            // 6. Purple Callout: * callout
            if (isCalloutItem(trimmed)) {
                Vector calloutLines = new Vector();
                int startLine = i;
                calloutLines.addElement(stripStarPrefix(trimmed));
                while (i + 1 < lineCount) {
                    String nextLine = ((String) lines.elementAt(i + 1)).trim();
                    if (isCalloutItem(nextLine)) {
                        calloutLines.addElement(stripStarPrefix(nextLine));
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

            // 12. Standalone image block
            if (trimmed.toLowerCase().startsWith("[image:") && trimmed.endsWith("]")) {
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
    private static void renderTableBlock(Manager targetManager, Vector tableLines, int srcLineIdx) {
        if (tableLines == null || tableLines.size() == 0) {
            return;
        }

        Vector headers = null;
        Vector dataRows = new Vector();
        int maxCols = 0;
        boolean headerProcessed = false;

        for (int i = 0; i < tableLines.size(); i++) {
            String rowStr = (String) tableLines.elementAt(i);
            if (isTableSeparatorRow(rowStr)) {
                continue;
            }
            Vector cells = parseTableRow(rowStr);
            if (cells.size() > maxCols) {
                maxCols = cells.size();
            }

            if (!headerProcessed) {
                headers = cells;
                headerProcessed = true;
            } else {
                dataRows.addElement(cells);
            }
        }

        if (maxCols > 0) {
            TableField tf = new TableField(headers, dataRows, maxCols);
            tf.srcLine = srcLineIdx;
            targetManager.add(tf);
        }
    }

    private static boolean isTableSeparatorRow(String row) {
        String trimmed = row.trim();
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
                cells.addElement(cleanMarkup(((String) raw.elementAt(i)).trim()));
            }
        }
        return cells;
    }

    public static class TableField extends Field {
        private Vector headers;
        private Vector rows;
        private int colCount;
        private int totalHeight;
        private int[] colWidths;
        private int rowHeight;
        private int focusedRow = 0;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public TableField(Vector headers, Vector rows, int colCount) {
            super(Field.FOCUSABLE);
            this.headers = headers;
            this.rows = rows;
            this.colCount = Math.max(1, colCount);
            this.rowHeight = baseFont.getHeight() + 8;
        }

        private int getTotalRows() {
            return (headers != null && headers.size() > 0 ? 1 : 0) + (rows != null ? rows.size() : 0);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            int numRows = getTotalRows();
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
            int numRows = getTotalRows();
            if (dy > 0) {
                if (focusedRow < numRows - 1) {
                    focusedRow++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int rowY = 2 + focusedRow * rowHeight;
                        scr.ensureRegionVisible(this, 0, rowY, getWidth(), rowHeight);
                    }
                    return true;
                }
            } else if (dy < 0) {
                if (focusedRow > 0) {
                    focusedRow--;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int rowY = 2 + focusedRow * rowHeight;
                        scr.ensureRegionVisible(this, 0, rowY, getWidth(), rowHeight);
                    }
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            rect.set(0, 2 + focusedRow * rowHeight, getWidth(), rowHeight);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int y = 2 + focusedRow * rowHeight;
                g.fillRect(7, y + 4, 2, baseFont.getHeight());
            }
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return totalHeight;
        }

        protected void layout(int width, int height) {
            int tableWidth = width - 10;
            colWidths = new int[colCount];
            int eachCol = tableWidth / colCount;
            for (int c = 0; c < colCount; c++) {
                colWidths[c] = eachCol;
            }
            int numRows = (headers != null && headers.size() > 0 ? 1 : 0) + (rows != null ? rows.size() : 0);
            totalHeight = numRows * rowHeight + 4;
            setExtent(width, totalHeight);
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int xOffset = 5;
            int curY = 2;
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

            // Header row
            if (headers != null && headers.size() > 0) {
                g.setColor(headerBg);
                g.fillRect(xOffset, curY, totalTableWidth, rowHeight);
                g.setFont(boldFont);

                int cellX = xOffset;
                for (int c = 0; c < colCount; c++) {
                    String hText = c < headers.size() ? (String) headers.elementAt(c) : "";
                    g.setColor(headerTextColor);
                    g.drawText(truncateText(hText, colWidths[c] - 8, boldFont), cellX + 4, curY + 4);
                    g.setColor(borderColor);
                    g.drawLine(cellX, curY, cellX, curY + rowHeight);
                    cellX += colWidths[c];
                }
                g.setColor(borderColor);
                g.drawLine(cellX, curY, cellX, curY + rowHeight);
                g.drawLine(xOffset, curY, xOffset + totalTableWidth, curY);
                g.drawLine(xOffset, curY + rowHeight, xOffset + totalTableWidth, curY + rowHeight);
                curY += rowHeight;
            }

            // Data rows
            if (rows != null) {
                int numDataRows = rows.size();
                for (int r = 0; r < numDataRows; r++) {
                    Vector rowCells = (Vector) rows.elementAt(r);
                    int bg = (r % 2 == 0) ? rowBgEven : rowBgOdd;
                    g.setColor(bg);
                    g.fillRect(xOffset, curY, totalTableWidth, rowHeight);

                    g.setFont(baseFont);

                    int cellX = xOffset;
                    for (int c = 0; c < colCount; c++) {
                        String cellText = c < rowCells.size() ? (String) rowCells.elementAt(c) : "";
                        g.setColor(dataTextColor);
                        g.drawText(truncateText(cellText, colWidths[c] - 8, baseFont), cellX + 4, curY + 4);
                        g.setColor(borderColor);
                        g.drawLine(cellX, curY, cellX, curY + rowHeight);
                        cellX += colWidths[c];
                    }
                    g.setColor(borderColor);
                    g.drawLine(cellX, curY, cellX, curY + rowHeight);
                    g.drawLine(xOffset, curY + rowHeight, xOffset + totalTableWidth, curY + rowHeight);
                    curY += rowHeight;
                }
            }
        }

        private String truncateText(String text, int maxWidth, Font font) {
            if (text == null) {
                return "";
            }
            if (font.getAdvance(text) <= maxWidth) {
                return text;
            }
            int len = text.length();
            for (int i = len - 1; i >= 1; i--) {
                String sub = text.substring(0, i) + "..";
                if (font.getAdvance(sub) <= maxWidth) {
                    return sub;
                }
            }
            return text;
        }
    }

    // ==========================================
    // Callout & Bullet Item Helpers
    // ==========================================
    private static String stripStarPrefix(String str) {
        if (str.startsWith("*")) {
            return str.substring(1).trim();
        }
        return str;
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
        private Vector lines;
        private int cachedHeight;
        private int focusedLine = 0;
        /**
         * Source line index in the notes string this field was rendered from (-1 if
         * unknown).
         */
        public int srcLine = -1;

        public BlockquoteField(Vector lines) {
            super(Field.FOCUSABLE);
            this.lines = lines != null ? lines : new Vector();
        }

        public int getPreferredWidth() {
            return Graphics.getScreenWidth();
        }

        public int getPreferredHeight() {
            return cachedHeight;
        }

        protected void layout(int width, int height) {
            cachedHeight = Math.max(lines.size(), 1) * (italicFont.getHeight() + 4) + 8;
            setExtent(width, cachedHeight);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            if (lines != null && lines.size() > 0) {
                if (direction > 0) {
                    focusedLine = 0;
                } else if (direction < 0) {
                    focusedLine = lines.size() - 1;
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
            int numLines = lines != null ? lines.size() : 1;
            if (dy > 0) {
                if (focusedLine < numLines - 1) {
                    focusedLine++;
                    invalidate();
                    net.rim.device.api.ui.Screen scr = getScreen();
                    if (scr != null) {
                        int lineH = italicFont.getHeight() + 4;
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
                        int lineH = italicFont.getHeight() + 4;
                        scr.ensureRegionVisible(this, 0, 6 + focusedLine * lineH, getWidth(), lineH);
                    }
                    return true;
                }
            }
            return super.navigationMovement(dx, dy, status, time);
        }

        public void getFocusRect(XYRect rect) {
            int lineH = italicFont.getHeight() + 4;
            rect.set(0, 6 + focusedLine * lineH, getWidth(), lineH);
        }

        protected void drawFocus(Graphics g, boolean on) {
            if (on) {
                boolean dark = AppSettings.getInstance().isDarkMode();
                g.setColor(dark ? 0x9999AA : 0x888888);
                int lineH = italicFont.getHeight() + 4;
                int y = 6 + focusedLine * lineH;
                g.fillRect(11, y, 2, italicFont.getHeight());
            }
        }

        protected void paint(Graphics g) {
            boolean dark = AppSettings.getInstance().isDarkMode();
            int w = getWidth() - 10;
            int bg = dark ? 0x180D26 : 0xFAF8FC;
            int bar = dark ? 0xC084FC : 0x7719AA;
            int textColor = dark ? 0xF3E8FF : 0x4A5568;

            g.setColor(bg);
            g.fillRect(5, 2, w, cachedHeight - 4);
            g.setColor(bar);
            g.fillRect(5, 2, 4, cachedHeight - 4);

            g.setFont(italicFont);
            g.setColor(textColor);
            int y = 6;
            for (int i = 0; i < lines.size(); i++) {
                String l = (String) lines.elementAt(i);
                g.drawText(l, 16, y);
                y += italicFont.getHeight() + 4;
            }
        }
    }

    // ==========================================
    // Code Block Rendering
    // ==========================================
    public static class CodeBlockField extends Field {
        private Vector codeLines;
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
            cachedHeight = Math.max(codeLines.size(), 1) * (monoFont.getHeight() + 3) + 10;
            setExtent(width, cachedHeight);
        }

        protected void onFocus(int direction) {
            super.onFocus(direction);
            if (codeLines != null && codeLines.size() > 0) {
                if (direction > 0) {
                    focusedLine = 0;
                } else if (direction < 0) {
                    focusedLine = codeLines.size() - 1;
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
            int numLines = codeLines != null ? codeLines.size() : 1;
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
            for (int i = 0; i < codeLines.size(); i++) {
                String l = (String) codeLines.elementAt(i);
                g.drawText(l, 12, y);
                y += monoFont.getHeight() + 3;
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
        return s.startsWith("[ ]") || StringUtils.startsWithIgnoreCase(s, "[x]");
    }

    private static void renderTaskItem(Manager targetManager, final Page page, String line, final int lineIndex,
            final Runnable onNotesChangedCallback, int leadingSpaces) {
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ") || s.startsWith("> ")
                || s.startsWith("• ")) {
            s = s.substring(2).trim();
        }
        final boolean isChecked = StringUtils.startsWithIgnoreCase(s, "[x]");
        int closeBracket = s.indexOf(']');
        String taskLabel = closeBracket >= 0 ? s.substring(closeBracket + 1).trim() : s;

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
        if (line.indexOf('*') < 0 && line.indexOf('_') < 0 && line.indexOf('~') < 0
                && line.indexOf('=') < 0 && line.indexOf('`') < 0) {
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
            if (!seg.isUnderline && !seg.isStrike && !seg.isHighlight && !seg.isCode) {
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

        public SpanSegment(String text, boolean bold, boolean italic, boolean underline,
                boolean strike, boolean highlight, boolean code) {
            this.text = text;
            this.isBold = bold;
            this.isItalic = italic;
            this.isUnderline = underline;
            this.isStrike = strike;
            this.isHighlight = highlight;
            this.isCode = code;
        }
    }

    private static Font getFontForSegment(SpanSegment seg) {
        if (seg.isCode) {
            return monoFont;
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

                    currentWord.addFragment(chunk, f, seg.isUnderline, seg.isStrike, seg.isHighlight, seg.isCode);

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

                        if (frag.isHighlight) {
                            g.setColor(highlightTextColor);
                        } else if (frag.isCode) {
                            g.setColor(codeTextColor);
                        } else {
                            g.setColor(defaultTextColor);
                        }
                        g.setFont(frag.font);
                        g.drawText(frag.text, x, y);

                        // Underline
                        if (frag.isUnderline) {
                            g.setColor(dark ? 0xF1F5F9 : 0x000000);
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
                boolean isHighlight, boolean isCode) {
            int w = font.getAdvance(text);
            fragments.addElement(new Fragment(text, font, w, isUnderline, isStrike, isHighlight, isCode));
            totalWidth += w;
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

        public Fragment(String text, Font font, int width, boolean isUnderline, boolean isStrike,
                boolean isHighlight, boolean isCode) {
            this.text = text;
            this.font = font;
            this.width = width;
            this.isUnderline = isUnderline;
            this.isStrike = isStrike;
            this.isHighlight = isHighlight;
            this.isCode = isCode;
        }
    }

    /**
     * Parses a line for inline spans: bold (**), italic (*), underline (__),
     * strikethrough (~~), highlight (==), and inline code (`).
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
            // ** Bold
            if (i + 1 < len && line.charAt(i) == '*' && line.charAt(i + 1) == '*') {
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
                    current = new StringBuffer();
                }
                bold = !bold;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '_' && line.charAt(i + 1) == '_') {
                // __ Underline
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
                    current = new StringBuffer();
                }
                underline = !underline;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '~' && line.charAt(i + 1) == '~') {
                // ~~ Strikethrough
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
                    current = new StringBuffer();
                }
                strike = !strike;
                i += 2;
            } else if (i + 1 < len && line.charAt(i) == '=' && line.charAt(i + 1) == '=') {
                // == Highlight
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
                    current = new StringBuffer();
                }
                highlight = !highlight;
                i += 2;
            } else if (line.charAt(i) == '`') {
                // ` Inline Code
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
                    current = new StringBuffer();
                }
                code = !code;
                i += 1;
            } else if (line.charAt(i) == '*') {
                // * Italic
                if (current.length() > 0) {
                    segments.addElement(
                            new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
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
            segments.addElement(new SpanSegment(current.toString(), bold, italic, underline, strike, highlight, code));
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
        String res = StringUtils.replace(text, "**", "");
        res = StringUtils.replace(res, "*", "");
        res = StringUtils.replace(res, "__", "");
        res = StringUtils.replace(res, "~~", "");
        res = StringUtils.replace(res, "==", "");
        res = StringUtils.replace(res, "`", "");
        return res;
    }
}