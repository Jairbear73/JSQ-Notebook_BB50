package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Root database container stored in BlackBerry internal NVRAM via unsigned
 * RecordStore.
 */
public class NotebookDatabase implements Persistable {

    private int version;
    private long nextId;
    private Vector notebooks;

    public NotebookDatabase() {
        this.version = 1;
        this.nextId = 100;
        this.notebooks = new Vector();
    }

    public synchronized long generateNextId() {
        return ++nextId;
    }

    public synchronized long getNextId() {
        return nextId;
    }

    public synchronized void setNextId(long nextId) {
        this.nextId = nextId;
    }

    public Vector getNotebooks() {
        if (notebooks == null) {
            notebooks = new Vector();
        }
        return notebooks;
    }

    public void addNotebook(Notebook notebook) {
        if (notebook != null) {
            getNotebooks().addElement(notebook);
        }
    }

    public boolean removeNotebook(long notebookId) {
        if (notebooks == null) {
            return false;
        }
        int size = notebooks.size();
        for (int i = 0; i < size; i++) {
            Notebook nb = (Notebook) notebooks.elementAt(i);
            if (nb.getId() == notebookId) {
                notebooks.removeElementAt(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Initializes default sample notebook with a section and intro page.
     */
    public void initSampleData() {
        createDemoNotebook();
    }

    /**
     * Creates and adds a fresh OneNote demo notebook to the database.
     */
    public Notebook createDemoNotebook() {
        long nbId = generateNextId();
        Notebook sampleNb = new Notebook(nbId, "OneNote Showcase");
        sampleNb.setDescription("Interactive OneNote demo & features showcase");

        long secId = generateNextId();
        Section sampleSec = new Section(secId, nbId, "General Notes");
        sampleSec.setDescription("OneNote formatting examples");

        sampleSec.getFieldDefinitions().addElement(
                new CustomFieldDefinition("author", "Author", FieldType.TEXT, false, "JSQ User"));
        sampleSec.getFieldDefinitions().addElement(
                new CustomFieldDefinition("priority", "Priority", FieldType.TEXT, false, "High"));

        long pageId = generateNextId();
        Page samplePage = new Page(pageId, secId, "OneNote Features Demo");
        sampleSec.applyTemplateToPage(samplePage);

        samplePage.setNotes(
                "# OneNote Features Demo\n" +
                "Welcome to the enhanced **JSQ Notebook** for **BlackBerry OS 5.0** with full markdown and rich formatting!\n\n" +
                "## Inline Formatting & Spans\n" +
                "Supports **bold**, *italic*, ***bold & italic***, __underlined__, ~~strikethrough~~, ==highlighted text==, and `inline code`.\n" +
                "Keyboard shortcuts like <kbd>Alt</kbd> + <kbd>Enter</kbd> or <kbd>Space</kbd> are styled cleanly.\n" +
                "You can also use inline links such as [GitHub](https://github.com) or [BlackBerry Developer](https://blackberry.com).\n\n" +
                "<!-- This is an HTML comment: hidden in View Mode, visible in Edit Mode -->\n" +
                "## Interactive Checklist Tasks\n" +
                "[ ] Incomplete task (click in View Mode to toggle)\n" +
                "[x] Completed task\n" +
                "[-] Cancelled or postponed item\n\n" +
                "## Tables with In-Cell Checkboxes & Section Headers\n" +
                "Click table checkboxes directly in View Mode to toggle! Notice that text wraps across multiple lines and row heights adapt dynamically:\n\n" +
                "| Done | Task Item | Priority | Notes |\n" +
                "|---|---|---|---|\n" +
                "| [x] | Multi-line cell word wrapping | High | Cells wrap cleanly without overflowing off screen |\n" +
                "| [ ] | Interactive table checkboxes | Urgent | Click directly with trackpad to toggle state |\n" +
                "| [x] | Proportional column sizing | Medium | Checkbox column stays compact |\n" +
                "|---|---|---|---|\n" +
                "| Sub | Advanced Table Capabilities | Type | Details |\n" +
                "| [ ] | Horizontal rule divider (---) | Feature | Automatically makes the next row a header row |\n" +
                "| [x] | Accurate cursor positioning | System | Switches to edit mode right at the selected row |\n\n" +
                "## Callout Alert Banners\n" +
                "* [!NOTE] This is a blue Note callout alert banner. Text automatically wraps to fit the screen width perfectly.\n" +
                "* [!TIP] Navigate through lines using trackwheel with the subtle grey cursor.\n" +
                "* [!WARNING] Remember to save edits or use the Back button dialog to discard changes.\n" +
                "* [!IMPORTANT] In Dark Mode, all callout and table themes automatically invert for optimal contrast.\n\n" +
                "## Heading Levels Demonstration\n" +
                "### Heading Level 3 (Section)\n" +
                "#### Heading Level 4 (Subsection)\n" +
                "##### Heading Level 5 (Compact Bold)\n" +
                "###### Heading Level 6 (Sub-detail Italic)\n\n" +
                "Setext Heading Style H1\n" +
                "======================\n\n" +
                "Setext Subheading Style H2\n" +
                "--------------------------\n\n" +
                "## Code Block (Word-Wrapped)\n" +
                "```\n" +
                "// Table and checkbox handling on BlackBerry OS 5\n" +
                "TableField tf = new TableField(page, rows, isHeaderFlags, rowLines, cols, callback);\n" +
                "if (tf.handleTableClick()) {\n" +
                "    return true; // Checkbox toggled interactively\n" +
                "}\n" +
                "```\n\n" +
                "## Nested Lists & Tags\n" +
                "• Top-level bullet point\n" +
                "  • Nested sub-item (2 leading spaces)\n" +
                "    • Deeply nested sub-item (4 spaces)\n" +
                "1. First numbered workflow step\n" +
                "2. Second workflow step\n\n" +
                "[!] High Priority action item tag\n" +
                "[?] Question regarding sync schedule\n" +
                "[*] Starred reference item\n\n" +
                "---\n\n" +
                "![JSQ Logo](img_jsq_logo.png)\n" +
                "[JSQ Notebook Documentation](https://github.com)");

        sampleSec.addPage(samplePage);

        long cheatPageId = generateNextId();
        Page cheatPage = new Page(cheatPageId, secId, "Markdown Cheat Sheet");
        sampleSec.applyTemplateToPage(cheatPage);
        cheatPage.setNotes(
                "# Markdown Syntax Reference\n" +
                "Quick reference guide for formatted notes in JSQ Notebook.\n\n" +
                "| Syntax | Output | Category |\n" +
                "|---|---|---|\n" +
                "| `**text**` | Bold text | Inline |\n" +
                "| `*text*` | Italic text | Inline |\n" +
                "| `***text***` | Bold + Italic | Inline |\n" +
                "| `__text__` | Underline | Inline |\n" +
                "| `~~text~~` | Strikethrough | Inline |\n" +
                "| `==text==` | Highlight | Inline |\n" +
                "| `` `code` `` | Inline code | Inline |\n" +
                "| `<kbd>Key</kbd>` | Keyboard key | Inline |\n" +
                "| `[text](url)` | Web link | Inline |\n" +
                "|---|---|---|\n" +
                "| Block Syntax | Description | Example |\n" +
                "| `| a | b |` | Table row | `| [ ] | Task |` |\n" +
                "| `* [!NOTE]` | Alert callout | `* [!TIP] Hint` |\n" +
                "| ```` code ```` | Code block | Monospaced block |\n" +
                "| `[ ]` / `[x]` | Checklists | Interactive tasks |\n" +
                "| `#` to `######` | Headings 1-6 | `# Title` |");
        sampleSec.addPage(cheatPage);
        sampleNb.addSection(sampleSec);
        addNotebook(sampleNb);
        return sampleNb;
    }

    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeInt(version);
        dos.writeLong(nextId);
        int nbCount = notebooks != null ? notebooks.size() : 0;
        dos.writeInt(nbCount);
        for (int i = 0; i < nbCount; i++) {
            Notebook nb = (Notebook) notebooks.elementAt(i);
            nb.serialize(dos);
        }
    }

    public static NotebookDatabase deserialize(DataInputStream dis) throws IOException {
        int ver = dis.readInt();
        NotebookDatabase db = new NotebookDatabase();
        db.version = ver;
        db.nextId = dis.readLong();
        int nbCount = dis.readInt();
        db.notebooks = new Vector();
        for (int i = 0; i < nbCount; i++) {
            db.notebooks.addElement(Notebook.deserialize(dis));
        }
        return db;
    }
}
