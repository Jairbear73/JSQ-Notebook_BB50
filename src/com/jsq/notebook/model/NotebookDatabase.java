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
        return createDemoNotebook(null);
    }

    /**
     * Creates and adds a fresh OneNote demo notebook with progress reporting.
     */
    public Notebook createDemoNotebook(com.jsq.notebook.util.ProgressListener listener) {
        if (listener != null) {
            listener.onProgress(0, 5, "Initializing OneNote Showcase...");
        }

        long nbId = generateNextId();
        Notebook sampleNb = new Notebook(nbId, "OneNote Showcase");
        sampleNb.setDescription("Interactive OneNote demo & features showcase");

        if (listener != null) {
            listener.onProgress(1, 5, "Creating General Notes section...");
        }

        long secId = generateNextId();
        Section sampleSec = new Section(secId, nbId, "General Notes");
        sampleSec.setDescription("OneNote formatting examples");

        sampleSec.getFieldDefinitions().addElement(
                new CustomFieldDefinition("author", "Author", FieldType.TEXT, false, "JSQ User"));
        sampleSec.getFieldDefinitions().addElement(
                new CustomFieldDefinition("priority", "Priority", FieldType.TEXT, false, "High"));

        if (listener != null) {
            listener.onProgress(2, 5, "Building: OneNote Features Demo...");
        }

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

        if (listener != null) {
            listener.onProgress(3, 5, "Building: Markdown Cheat Sheet...");
        }

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

        if (listener != null) {
            listener.onProgress(4, 5, "Saving to database...");
        }

        addNotebook(sampleNb);

        if (listener != null) {
            listener.onProgress(5, 5, "Demo notebook ready!");
        }

        return sampleNb;
    }

    // -------------------------------------------------------------------------
    // Debug / Benchmark Notebook
    // -------------------------------------------------------------------------

    /** Creates and adds the debug notebook (no progress). */
    public Notebook createDebugNotebook() {
        return createDebugNotebook(null);
    }

    /**
     * Creates a two-section debug notebook:
     *   Section 1 "Test" – exhaustive feature coverage
     *   Section 2 "Benchmark" – heavy-load stress pages
     */
    public Notebook createDebugNotebook(com.jsq.notebook.util.ProgressListener listener) {
        final int TOTAL = 18; // progress steps
        int step = 0;

        if (listener != null) listener.onProgress(step++, TOTAL, "Creating Debug Notebook...");

        long nbId = generateNextId();
        Notebook nb = new Notebook(nbId, "DEBUG Notebook");
        nb.setDescription("Feature tests and stress benchmarks for JSQ Notebook");

        // ---- Section 1: Test ------------------------------------------------
        if (listener != null) listener.onProgress(step++, TOTAL, "Building Test section...");
        long sec1Id = generateNextId();
        Section testSec = new Section(sec1Id, nbId, "Test");
        testSec.setDescription("Exhaustive feature coverage tests");

        testSec.addPage(buildTestPage_InlineFormatting(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_BlockElements(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_Tables(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_Checklists(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_NestedLists(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_FormattingCombinations(sec1Id, listener, step++, TOTAL));
        testSec.addPage(buildTestPage_EdgeCases(sec1Id, listener, step++, TOTAL));
        nb.addSection(testSec);

        // ---- Section 2: Benchmark -------------------------------------------
        if (listener != null) listener.onProgress(step++, TOTAL, "Building Benchmark section...");
        long sec2Id = generateNextId();
        Section benchSec = new Section(sec2Id, nbId, "Benchmark");
        benchSec.setDescription("Heavy-load stress tests pushing rendering limits");

        benchSec.addPage(buildBenchPage_FormatEveryLetter(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_MaxChecklists(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_MaxTable(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_MaxNestedLists(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_MaxMixedContent(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_MaxCallouts(sec2Id, listener, step++, TOTAL));
        benchSec.addPage(buildBenchPage_OverLimit(sec2Id, listener, step++, TOTAL));
        nb.addSection(benchSec);

        if (listener != null) listener.onProgress(step++, TOTAL, "Saving Debug Notebook...");
        addNotebook(nb);
        if (listener != null) listener.onProgress(TOTAL, TOTAL, "Debug notebook ready!");
        return nb;
    }

    // -- Test section pages ---------------------------------------------------

    private Page buildTestPage_InlineFormatting(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Inline Formatting...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Inline Formatting");
        p.setNotes(
            "# Inline Formatting Test\n" +
            "Every inline span type rendered individually and then combined.\n\n" +
            "## Basic Spans\n" +
            "**Bold text** – should be heavier weight.\n" +
            "*Italic text* – should be slanted.\n" +
            "***Bold and italic*** – both effects together.\n" +
            "__Underlined text__ – line below baseline.\n" +
            "~~Strikethrough~~ – line through centre.\n" +
            "==Highlighted text== – background highlight colour.\n" +
            "`inline code` – monospace font.\n" +
            "<kbd>Alt</kbd> + <kbd>Enter</kbd> – keyboard key styling.\n\n" +
            "## Combined Spans on One Line\n" +
            "**Bold** then *italic* then ***bold+italic*** then __under__ then ~~strike~~ " +
            "then ==hi== then `code` then <kbd>Key</kbd> all on one line.\n\n" +
            "## Nested Span Layering\n" +
            "***__Bold italic underline__***\n" +
            "~~***Strikethrough bold italic***~~\n" +
            "==**Highlighted bold**==\n" +
            "**==Bold highlighted**==\n" +
            "==~~Highlighted strikethrough~~==\n\n" +
            "## Link Formatting\n" +
            "[Plain link](https://example.com)\n" +
            "[**Bold link**](https://example.com)\n" +
            "[*Italic link*](https://example.com)\n" +
            "[==Highlighted link==](https://example.com)\n\n" +
            "## Edge: Empty & Whitespace Spans\n" +
            "** ** – bold with space only.\n" +
            "*  * – italic with spaces.\n" +
            "`` – empty code span.\n" +
            "__ __ – underline with space.\n\n" +
            "## Span at Start/End of Line\n" +
            "**Leading bold** normal tail.\n" +
            "Normal lead **trailing bold**\n" +
            "*Leading italic* normal tail.\n" +
            "Normal lead *trailing italic*\n" +
            "==Leading highlight== normal tail.\n" +
            "Normal lead ==trailing highlight==");
        return p;
    }

    private Page buildTestPage_BlockElements(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Block Elements...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Block Elements");
        p.setNotes(
            "# Block Elements Test\n\n" +
            "## Headings H1-H6\n" +
            "# H1 Heading\n" +
            "## H2 Heading\n" +
            "### H3 Heading\n" +
            "#### H4 Heading\n" +
            "##### H5 Heading\n" +
            "###### H6 Heading\n\n" +
            "## Setext Headings\n" +
            "Setext H1 Style\n" +
            "===============\n\n" +
            "Setext H2 Style\n" +
            "---------------\n\n" +
            "## Separators\n" +
            "Above separator:\n" +
            "---\n" +
            "Below separator.\n\n" +
            "## Code Blocks\n" +
            "```\n" +
            "public class Test {\n" +
            "    // Monospaced code block\n" +
            "    int x = 42;\n" +
            "    String s = \"hello\";\n" +
            "}\n" +
            "```\n\n" +
            "Inline then block:\n" +
            "`inline` versus:\n" +
            "```\n" +
            "block code line 1\n" +
            "block code line 2 with longer text to test wrapping\n" +
            "```\n\n" +
            "## Callout Alert Banners\n" +
            "* [!NOTE] Note callout – informational blue banner.\n" +
            "* [!TIP] Tip callout – helpful green banner.\n" +
            "* [!WARNING] Warning callout – orange caution banner.\n" +
            "* [!IMPORTANT] Important callout – red critical banner.\n" +
            "* [!CAUTION] Caution callout – yellow high-risk banner.\n\n" +
            "## Tag Lines\n" +
            "[!] High priority action item.\n" +
            "[?] Open question or query.\n" +
            "[*] Starred or bookmarked item.\n\n" +
            "## Blockquotes / Callout Text Wrapping\n" +
            "* [!NOTE] This callout contains a very long line of text that must wrap " +
            "gracefully across multiple lines inside the callout banner box without " +
            "overflowing the screen edge or clipping any characters at all.\n\n" +
            "## Horizontal Rules Variants\n" +
            "---\n" +
            "***\n" +
            "___");
        return p;
    }

    private Page buildTestPage_Tables(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Tables...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Tables");
        p.setNotes(
            "# Table Tests\n\n" +
            "## Basic 3-Column Table\n" +
            "| Name | Value | Type |\n" +
            "|---|---|---|\n" +
            "| Alpha | 1 | Integer |\n" +
            "| Beta | 3.14 | Float |\n" +
            "| Gamma | hello | String |\n\n" +
            "## Table with Inline Formatting in Cells\n" +
            "| Format | Example | Result |\n" +
            "|---|---|---|\n" +
            "| **Bold** | `**text**` | Bold cell content |\n" +
            "| *Italic* | `*text*` | Italic cell content |\n" +
            "| ~~Strike~~ | `~~text~~` | Strikethrough cell |\n" +
            "| ==Hi== | `==text==` | Highlighted cell |\n" +
            "| `code` | backtick | Monospace cell |\n\n" +
            "## Table with Checkboxes (Interactive)\n" +
            "| Done | Task | Priority |\n" +
            "|---|---|---|\n" +
            "| [x] | Completed item | High |\n" +
            "| [ ] | Pending item | Medium |\n" +
            "| [-] | Cancelled item | Low |\n" +
            "| [x] | Another done | High |\n" +
            "| [ ] | Another pending | Medium |\n\n" +
            "## Table with Long Cell Content (Wrapping)\n" +
            "| Short | Very long cell content that must wrap across multiple lines | Short |\n" +
            "|---|---|---|\n" +
            "| A | This cell has quite a lot of text in it, testing that the row " +
            "height expands dynamically and all text remains readable | B |\n" +
            "| C | Normal | D |\n\n" +
            "## Table with Section Header Rows\n" +
            "| Item | Status | Notes |\n" +
            "|---|---|---|\n" +
            "| --- Section: Alpha --- | | |\n" +
            "| Item A1 | Done | First |\n" +
            "| Item A2 | Pending | Second |\n" +
            "| --- Section: Beta --- | | |\n" +
            "| Item B1 | Done | Third |\n\n" +
            "## Single-Column Table\n" +
            "| Only Column |\n" +
            "|---|\n" +
            "| Row 1 |\n" +
            "| Row 2 |\n" +
            "| Row 3 |\n\n" +
            "## Two Tables Back to Back\n" +
            "| A | B |\n" +
            "|---|---|\n" +
            "| 1 | 2 |\n\n" +
            "| C | D |\n" +
            "|---|---|\n" +
            "| 3 | 4 |");
        return p;
    }

    private Page buildTestPage_Checklists(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Checklists...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Checklists");
        p.setNotes(
            "# Checklist Tests\n\n" +
            "## All Three States\n" +
            "[ ] Unchecked – open box, clickable in View Mode.\n" +
            "[x] Checked – ticked box, clickable in View Mode.\n" +
            "[-] Cancelled – dash in box, clickable in View Mode.\n\n" +
            "## Mixed Sequence\n" +
            "[x] Task 1 – done\n" +
            "[ ] Task 2 – pending\n" +
            "[x] Task 3 – done\n" +
            "[-] Task 4 – cancelled\n" +
            "[ ] Task 5 – pending\n" +
            "[-] Task 6 – cancelled\n" +
            "[x] Task 7 – done\n" +
            "[ ] Task 8 – pending\n\n" +
            "## Checklists with Inline Formatting\n" +
            "[x] **Bold completed task** – bold text in a ticked item.\n" +
            "[ ] *Italic pending task* – italic text in an open item.\n" +
            "[-] ~~Strikethrough cancelled~~ – strike in a cancelled item.\n" +
            "[x] ==Highlighted done task== – highlighted in ticked item.\n" +
            "[ ] `code task` – code style in pending item.\n\n" +
            "## Checklists Interleaved with Other Content\n" +
            "Normal paragraph before checklist.\n" +
            "[ ] First task\n" +
            "[x] Second task\n" +
            "Normal paragraph between checklists.\n" +
            "[ ] Third task\n" +
            "## Heading Between Tasks\n" +
            "[x] Task after heading\n" +
            "---\n" +
            "[ ] Task after separator\n\n" +
            "## Long-Text Checklist Items (Wrapping)\n" +
            "[ ] This is a very long checklist item that contains enough text to " +
            "require the line to wrap onto multiple lines while still appearing " +
            "as a single interactive checkbox entry.\n" +
            "[x] Another long completed item with enough content to also wrap " +
            "across two or more display lines testing row height expansion.\n");
        return p;
    }

    private Page buildTestPage_NestedLists(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Nested Lists...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Nested Lists");
        p.setNotes(
            "# Nested List Tests\n\n" +
            "## Bullet Nesting (3 levels)\n" +
            "\u2022 Level 1 bullet A\n" +
            "  \u2022 Level 2 bullet A1\n" +
            "    \u2022 Level 3 bullet A1a\n" +
            "    \u2022 Level 3 bullet A1b\n" +
            "  \u2022 Level 2 bullet A2\n" +
            "\u2022 Level 1 bullet B\n" +
            "  \u2022 Level 2 bullet B1\n" +
            "\u2022 Level 1 bullet C\n\n" +
            "## Dash Bullets\n" +
            "- Dash bullet 1\n" +
            "  - Dash nested 1a\n" +
            "  - Dash nested 1b\n" +
            "- Dash bullet 2\n\n" +
            "## Asterisk Bullets\n" +
            "* Asterisk bullet 1\n" +
            "  * Asterisk nested 1a\n" +
            "* Asterisk bullet 2\n\n" +
            "## Numbered Lists\n" +
            "1. First item\n" +
            "2. Second item\n" +
            "3. Third item\n" +
            "4. Fourth item with a longer description that wraps to demonstrate " +
            "that the indent is maintained on continuation lines\n" +
            "5. Fifth item\n\n" +
            "## Mixed Bullets and Numbers\n" +
            "\u2022 Bullet before numbered\n" +
            "1. Numbered after bullet\n" +
            "2. Second numbered\n" +
            "\u2022 Bullet again\n\n" +
            "## Bullets with Inline Formatting\n" +
            "\u2022 **Bold bullet** item\n" +
            "\u2022 *Italic bullet* item\n" +
            "\u2022 ~~Strikethrough~~ bullet item\n" +
            "\u2022 ==Highlighted== bullet item\n" +
            "\u2022 `code` in bullet item\n" +
            "  \u2022 **Nested bold** sub-bullet\n" +
            "  \u2022 *Nested italic* sub-bullet\n\n" +
            "## Long Bullet Items (Wrapping)\n" +
            "\u2022 This bullet item is intentionally long enough that it must wrap " +
            "to a second line, testing that the continuation is indented correctly " +
            "to align with the first character after the bullet marker.\n" +
            "  \u2022 This nested bullet is also long and must wrap correctly with " +
            "deeper indentation than the parent bullet level above it.\n");
        return p;
    }

    private Page buildTestPage_FormattingCombinations(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Format Combinations...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Format Combinations");
        p.setNotes(
            "# Formatting Combinations Test\n" +
            "Every meaningful combination of formatting types rendering together.\n\n" +
            "## Inline in Headings\n" +
            "# **Bold H1 Heading**\n" +
            "## *Italic H2 Heading*\n" +
            "### ***Bold Italic H3***\n" +
            "#### ==Highlighted H4==\n" +
            "##### ~~Strikethrough H5~~\n\n" +
            "## Inline in Table Cells with Formatting\n" +
            "| **Bold Header** | *Italic Header* | ~~Strike Header~~ |\n" +
            "|---|---|---|\n" +
            "| **bold** cell | *italic* cell | ~~strike~~ cell |\n" +
            "| ==hi== cell | `code` cell | ***bold italic*** |\n" +
            "| [x] check | [ ] check | [-] cancel |\n\n" +
            "## Callout with Inline Formatting\n" +
            "* [!NOTE] This callout has **bold**, *italic*, and ==highlighted== " +
            "text all within a single callout banner line.\n" +
            "* [!WARNING] **Critical:** `configFile` is ~~deprecated~~ – use " +
            "***settingsFile*** instead.\n\n" +
            "## Code Block Adjacent to Formatted Text\n" +
            "**Before the code block** – bold paragraph.\n" +
            "```\n" +
            "code block content\n" +
            "second line of code\n" +
            "```\n" +
            "*After the code block* – italic paragraph.\n\n" +
            "## Checklists in Tables\n" +
            "| Status | Item | Detail |\n" +
            "|---|---|---|\n" +
            "| [x] | **Done item** | ==Highlighted== detail |\n" +
            "| [ ] | *Pending item* | ~~Old~~ detail |\n\n" +
            "## Separators Between Format Blocks\n" +
            "**Bold block before separator.**\n" +
            "---\n" +
            "*Italic block after separator.*\n" +
            "---\n" +
            "==Highlighted block after second separator.==\n\n" +
            "## Tag Lines with Formatting\n" +
            "[!] **Important:** this is a high-priority item.\n" +
            "[?] *Question:* what is the expected ==output==?\n" +
            "[*] Starred: `checkResult()` returns `true`.\n");
        return p;
    }

    private Page buildTestPage_EdgeCases(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Test: Edge Cases...");
        long id = generateNextId();
        Page p = new Page(id, secId, "Edge Cases");
        p.setNotes(
            "# Edge Case Tests\n\n" +
            "## Empty Lines and Whitespace\n" +
            "Line before blank.\n" +
            "\n" +
            "Line after one blank.\n" +
            "\n" +
            "\n" +
            "Line after two blanks.\n\n" +
            "## Special Characters\n" +
            "Ampersand: Tom & Jerry\n" +
            "Less-than: a < b\n" +
            "Greater-than: b > a\n" +
            "Quotes: \"double\" and 'single'\n" +
            "Backslash: C:\\path\\to\\file\n" +
            "Slash: /usr/local/bin\n" +
            "Percent: 100% complete\n" +
            "Hash literal: #not-a-heading (inline)\n" +
            "Asterisk literal: 2 * 3 = 6 (inline)\n\n" +
            "## Unicode and Symbols\n" +
            "Bullet symbol: \u2022 \u25e6 \u25aa\n" +
            "Arrows: \u2192 \u2190 \u2194 \u21d2\n" +
            "Math: \u00b1 \u00d7 \u00f7 \u2264 \u2265 \u2260\n" +
            "Currency: \u00a3 \u20ac \u00a5\n" +
            "Accented: \u00e9\u00e8\u00ea\u00e0\u00e2\u00fc\u00f6\u00e4\n\n" +
            "## Unclosed Format Markers\n" +
            "**Unclosed bold no closing\n" +
            "*Unclosed italic no closing\n" +
            "==Unclosed highlight no closing\n" +
            "~~Unclosed strike no closing\n\n" +
            "## Consecutive Format Markers\n" +
            "****Double bold markers****\n" +
            "**Bold****Bold** back to back\n" +
            "***A***B***C*** alternating\n\n" +
            "## Very Long Single Word (No Spaces)\n" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n\n" +
            "## Mixed Direction: RTL-like long run\n" +
            "Normal text then aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa then normal.\n\n" +
            "## Formatting Across Line Boundary Test\n" +
            "**This is bold text that happens to be\n" +
            "split across a raw newline in source.**\n\n" +
            "## Minimal Content Pages\n" +
            ".\n\n" +
            "## Image Reference\n" +
            "![Alt text for missing image](nonexistent_image.png)\n" +
            "Above should degrade gracefully with no crash.\n");
        return p;
    }

    // -- Benchmark section pages ----------------------------------------------

    /**
     * Alternates **bold** and *italic* on every individual letter/word to
     * maximise format-span count per rendered line, up to ~65000 chars.
     */
    private Page buildBenchPage_FormatEveryLetter(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Format Every Letter...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Format Per Letter");
        // Target ~60000 chars. Each unit "**X** *Y* " is 11 chars.
        // 60000 / 11 ~ 5454 pairs. Use alphabet cycling.
        StringBuffer sb = new StringBuffer(62000);
        sb.append("# Format Per Letter Benchmark\n");
        sb.append("Every character wrapped in alternating bold/italic spans.\n");
        sb.append("Tests maximum inline span parsing and render field count.\n\n");
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int col = 0;
        int idx = 0;
        while (sb.length() < 59000) {
            char boldCh = alpha.charAt(idx % 26);
            char italCh = alpha.charAt((idx + 13) % 26);
            sb.append("**").append(boldCh).append("** *").append(italCh).append("* ");
            idx++;
            col++;
            if (col >= 8) {
                sb.append('\n');
                col = 0;
            }
        }
        sb.append("\n\n*End of format-per-letter benchmark.*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * Maximum number of checklist items within the AutoTextField limit.
     */
    private Page buildBenchPage_MaxChecklists(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Max Checklists...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Max Checklists");
        // Each line "[ ] Item NNN\n" is ~14 chars. 65000 / 14 ~ 4642 items.
        StringBuffer sb = new StringBuffer(65000);
        sb.append("# Max Checklist Benchmark\n");
        sb.append("Maximum number of checkbox items the render engine can handle.\n\n");
        int item = 1;
        String[] states = {"[ ] ", "[x] ", "[-] "};
        while (sb.length() < 63000) {
            sb.append(states[item % 3]);
            sb.append("Checklist item ");
            sb.append(item);
            sb.append(" – pending/done/cancelled cycling test entry\n");
            item++;
        }
        sb.append("\n*Total items: ");
        sb.append(item - 1);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * A very wide and tall table filling the character budget.
     */
    private Page buildBenchPage_MaxTable(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Max Table...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Max Table");
        int cols = 5;
        StringBuffer sb = new StringBuffer(64000);
        sb.append("# Max Table Benchmark\n");
        sb.append("Wide table with max rows testing table layout performance.\n\n");
        sb.append("| Row | Col A | Col B | Col C | Status |\n");
        sb.append("|---|---|---|---|---|\n");
        int row = 1;
        while (sb.length() < 62000) {
            sb.append("| ");
            sb.append(row);
            sb.append(" | Cell-");
            sb.append(row);
            sb.append("A | **Bold**-");
            sb.append(row);
            sb.append(" | *Ital*-");
            sb.append(row);
            sb.append(" | ");
            sb.append(row % 2 == 0 ? "[x]" : "[ ]");
            sb.append(" |\n");
            row++;
        }
        sb.append("\n*Total rows: ");
        sb.append(row - 1);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * Deeply nested bullet lists with 4 levels, filling the char budget.
     */
    private Page buildBenchPage_MaxNestedLists(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Max Nested Lists...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Max Nested Lists");
        StringBuffer sb = new StringBuffer(64000);
        sb.append("# Max Nested List Benchmark\n");
        sb.append("4-level deep bullet nesting repeated to fill the character budget.\n\n");
        int group = 1;
        while (sb.length() < 61000) {
            sb.append("\u2022 Group ").append(group).append(" top-level item\n");
            sb.append("  \u2022 Sub-item ").append(group).append(".1 second level\n");
            sb.append("    \u2022 Sub-sub ").append(group).append(".1.1 third level\n");
            sb.append("      \u2022 Deep ").append(group).append(".1.1.1 fourth level\n");
            sb.append("    \u2022 Sub-sub ").append(group).append(".1.2 third level\n");
            sb.append("  \u2022 Sub-item ").append(group).append(".2 second level\n");
            group++;
        }
        sb.append("\n*Total groups: ");
        sb.append(group - 1);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * Mixed content of every element type interleaved, filling the budget.
     */
    private Page buildBenchPage_MaxMixedContent(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Max Mixed Content...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Max Mixed Content");
        StringBuffer sb = new StringBuffer(64000);
        sb.append("# Max Mixed Content Benchmark\n");
        sb.append("Every element type interleaved: headings, bullets, tables, ");
        sb.append("checklists, callouts, code, separators, tags – cycling.\n\n");
        int cycle = 1;
        while (sb.length() < 60000) {
            sb.append("## Cycle ").append(cycle).append("\n");
            sb.append("**Bold** paragraph with *italic* and ==highlight== and `code`.\n");
            sb.append("[ ] Checklist item ").append(cycle).append("a\n");
            sb.append("[x] Checklist item ").append(cycle).append("b done\n");
            sb.append("\u2022 Bullet ").append(cycle).append(" top\n");
            sb.append("  \u2022 Bullet ").append(cycle).append(" nested\n");
            sb.append("| A").append(cycle).append(" | B").append(cycle).append(" |\n");
            sb.append("|---|---|\n");
            sb.append("| ").append(cycle).append(" | **v**").append(cycle).append(" |\n");
            sb.append("* [!NOTE] Callout note for cycle ").append(cycle).append(".\n");
            sb.append("[!] Priority tag cycle ").append(cycle).append("\n");
            sb.append("---\n");
            cycle++;
        }
        sb.append("\n*Total cycles: ");
        sb.append(cycle - 1);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * Maximum number of callout banners filling the character budget.
     */
    private Page buildBenchPage_MaxCallouts(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Max Callouts...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Max Callouts");
        StringBuffer sb = new StringBuffer(64000);
        sb.append("# Max Callout Banner Benchmark\n");
        sb.append("Maximum callout banners testing banner layout and colour rendering.\n\n");
        String[] types = {"NOTE", "TIP", "WARNING", "IMPORTANT", "CAUTION"};
        int i = 0;
        while (sb.length() < 62000) {
            String t = types[i % 5];
            sb.append("* [!").append(t).append("] Callout ").append(i + 1);
            sb.append(" of type ").append(t);
            sb.append(" – **bold**, *italic*, ==highlight== inline formatting test.\n");
            i++;
        }
        sb.append("\n*Total callouts: ");
        sb.append(i);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
    }

    /**
     * Intentionally exceeds the nominal AutoTextField limit to test graceful
     * handling of oversized content. Uses raw repeated text; no complex formatting
     * to minimise parsing time on a device that may be under memory pressure.
     */
    private Page buildBenchPage_OverLimit(long secId,
            com.jsq.notebook.util.ProgressListener l, int step, int total) {
        if (l != null) l.onProgress(step, total, "Bench: Over Limit...");
        long id = generateNextId();
        Page p = new Page(id, secId, "BENCH: Over Limit (>65KB)");
        // Build ~90000 chars – well over the 65535 AutoTextField limit.
        StringBuffer sb = new StringBuffer(92000);
        sb.append("# Over-Limit Stress Benchmark\n");
        sb.append("This page intentionally exceeds the AutoTextField 65535-char limit.\n");
        sb.append("Tests graceful degradation: edit truncation, view scrolling,\n");
        sb.append("memory pressure on serialize/deserialize, and no crashes.\n\n");
        sb.append("## Dense Repeating Content (90k chars total)\n");
        // Repeating numbered lines, plain text, no expensive inline parsing.
        int line = 1;
        while (sb.length() < 89000) {
            sb.append("Line ");
            sb.append(line);
            sb.append(": The quick brown fox jumps over the lazy dog. ");
            sb.append("Pack my box with five dozen liquor jugs. ");
            sb.append("How vexingly quick daft zebras jump!\n");
            line++;
        }
        sb.append("\n## End Marker\n");
        sb.append("*If you can read this, the view rendered beyond the 65k boundary.*\n");
        sb.append("*Total lines: ");
        sb.append(line - 1);
        sb.append("*");
        p.setNotes(sb.toString());
        return p;
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
