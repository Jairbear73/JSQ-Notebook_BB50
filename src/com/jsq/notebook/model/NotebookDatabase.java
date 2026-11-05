package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Root database container stored in BlackBerry internal NVRAM via unsigned RecordStore.
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
            new CustomFieldDefinition("author", "Author", FieldType.TEXT, false, "JSQ User")
        );
        sampleSec.getFieldDefinitions().addElement(
            new CustomFieldDefinition("priority", "Priority", FieldType.TEXT, false, "High")
        );

        long pageId = generateNextId();
        Page samplePage = new Page(pageId, secId, "OneNote Features Demo");
        sampleSec.applyTemplateToPage(samplePage);

        samplePage.setNotes(
            "# OneNote Features Demo\n" +
            "Welcome to the enhanced **JSQ Notebook** with full **OneNote Page** compatibility for **BlackBerry OS 5.0**!\n\n" +
            "## Inline Text Formatting\n" +
            "Supports **bold**, *italic*, __underlined__, ~~strikethrough~~, ==highlighted text==, and `inline code` spans.\n\n" +
            "### Daily Tasks (Interactive in View Mode)\n" +
            "[ ] Review OneNote page styling\n" +
            "[x] Implement markdown tables\n" +
            "[x] Add callouts & blockquotes\n" +
            "[ ] Toggle Dark Mode in Options menu\n\n" +
            "### Feature Support Matrix\n" +
            "| Feature | Syntax | Status |\n" +
            "|---|---|---|\n" +
            "| Checklists | [ ] and [x] | Interactive |\n" +
            "| Tables | Pipe Matrix | Formatted |\n" +
            "| Callouts | > Quote | Active |\n" +
            "| Code Blocks | ``` Code ``` | Monospaced |\n" +
            "| Dark Mode | View Invert | Supported |\n\n" +
            "> OneNote Tip: In View Mode, click checkboxes directly with the trackpad to toggle tasks without entering Edit Mode!\n\n" +
            "### Sample Code Block\n" +
            "```\n" +
            "// BlackBerry OS 5 Dark Mode\n" +
            "if (AppSettings.getInstance().isDarkMode()) {\n" +
            "    g.setColor(0x000000); // Black background\n" +
            "}\n" +
            "```\n\n" +
            "### Workflow Steps\n" +
            "1. Organize notes into Notebooks and Sections\n" +
            "2. Format content with rich OneNote elements\n" +
            "3. Export to SD Card for desktop sync\n\n" +
            "---\n\n" +
            "### OneNote Note Tags\n" +
            "[!] High Priority action item\n" +
            "[?] Question regarding sync schedule\n" +
            "[*] Starred reference item\n\n" +
            "[JSQ Notebook Documentation](https://github.com)"
        );

        sampleSec.addPage(samplePage);
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
