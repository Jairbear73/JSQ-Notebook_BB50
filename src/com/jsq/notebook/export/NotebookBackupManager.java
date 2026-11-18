package com.jsq.notebook.export;

import com.jsq.notebook.model.CustomField;
import com.jsq.notebook.model.CustomFieldDefinition;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Page;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.AttachmentStorageManager;
import com.jsq.notebook.storage.NotebookStoreManager;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Handles backing up and restoring all Notebooks, Sections, Custom Fields, Pages,
 * and binary attachments to and from a single .jnbb file.
 *
 * <p>All operations are 100% unsigned JSR-75 FileConnection only, and engineered
 * for maximum performance and minimum heap churn on BlackBerry OS 5.0 / CLDC 1.1.
 */
public class NotebookBackupManager {

    /** Magic identifier 'JNBB' = 0x4A4E4242 */
    public static final int MAGIC_JNBB = 0x4A4E4242;
    public static final int FORMAT_VERSION = 1;
    private static final int BUFFER_SIZE = 4096;

    private static NotebookBackupManager instance;

    public interface ProgressListener extends com.jsq.notebook.util.ProgressListener {
    }

    public static class BackupResult {
        private int notebookCount;
        private int sectionCount;
        private int pageCount;
        private int attachmentCount;

        public BackupResult(int notebookCount, int sectionCount, int pageCount, int attachmentCount) {
            this.notebookCount = notebookCount;
            this.sectionCount = sectionCount;
            this.pageCount = pageCount;
            this.attachmentCount = attachmentCount;
        }

        public int getNotebookCount() { return notebookCount; }
        public int getSectionCount() { return sectionCount; }
        public int getPageCount() { return pageCount; }
        public int getAttachmentCount() { return attachmentCount; }
    }

    public static class RestoreResult {
        private int notebookCount;
        private int sectionCount;
        private int pageCount;
        private int attachmentCount;

        public RestoreResult(int notebookCount, int sectionCount, int pageCount, int attachmentCount) {
            this.notebookCount = notebookCount;
            this.sectionCount = sectionCount;
            this.pageCount = pageCount;
            this.attachmentCount = attachmentCount;
        }

        public int getNotebookCount() { return notebookCount; }
        public int getSectionCount() { return sectionCount; }
        public int getPageCount() { return pageCount; }
        public int getAttachmentCount() { return attachmentCount; }
    }

    private NotebookBackupManager() {
    }

    public static synchronized NotebookBackupManager getInstance() {
        if (instance == null) {
            instance = new NotebookBackupManager();
        }
        return instance;
    }

    /**
     * Backs up all notebooks, sections, custom fields, pages, and attachment files
     * into a single .jnbb file at the specified file:// URL.
     *
     * @param destFileUrl destination file:// URL (must end with .jnbb)
     * @param listener optional ProgressListener for real-time progress updates
     * @return BackupResult summary
     * @throws IOException if any I/O error occurs
     */
    public BackupResult backupAll(String destFileUrl, ProgressListener listener) throws IOException {
        if (destFileUrl == null || destFileUrl.length() == 0) {
            throw new IllegalArgumentException("Destination file URL cannot be empty");
        }

        Vector notebooks = NotebookStoreManager.getInstance().getNotebooks();
        int nbCount = notebooks != null ? notebooks.size() : 0;

        // Count total sections and pages for granular progress tracking
        int totalSections = 0;
        int totalPages = 0;
        for (int i = 0; i < nbCount; i++) {
            Notebook nb = (Notebook) notebooks.elementAt(i);
            Vector secs = nb.getSections();
            int sc = secs != null ? secs.size() : 0;
            totalSections += sc;
            for (int s = 0; s < sc; s++) {
                Section sec = (Section) secs.elementAt(s);
                totalPages += sec.getPages() != null ? sec.getPages().size() : 0;
            }
        }

        // Collect all attachment files
        Vector attachments = findAllAttachments(notebooks);
        int attachCount = attachments.size();

        // Total progress units = 1 (header) + totalPages + attachCount + 1 (finalize)
        int totalUnits = 1 + totalPages + attachCount + 1;
        int currentUnit = 0;

        if (listener != null) {
            listener.onProgress(currentUnit, totalUnits, "Initializing backup...");
        }

        FileConnection fc = null;
        OutputStream os = null;
        DataOutputStream dos = null;

        try {
            fc = (FileConnection) Connector.open(destFileUrl, Connector.READ_WRITE);
            if (fc.exists()) {
                fc.delete();
            }
            fc.create();

            os = fc.openOutputStream();
            dos = new DataOutputStream(os);

            // 1. Header
            dos.writeInt(MAGIC_JNBB);
            dos.writeInt(FORMAT_VERSION);
            dos.writeLong(System.currentTimeMillis());
            writeSafeString(dos, "JSQ Notebook Full Backup");

            long currentNextId = NotebookStoreManager.getInstance().nextId();
            dos.writeLong(currentNextId);

            currentUnit++;
            if (listener != null) {
                listener.onProgress(currentUnit, totalUnits, "Writing notebooks...");
            }

            // 2. Notebooks, Sections, Field Definitions, Pages, and Custom Fields
            dos.writeInt(nbCount);
            for (int ni = 0; ni < nbCount; ni++) {
                Notebook nb = (Notebook) notebooks.elementAt(ni);
                dos.writeLong(nb.getId());
                writeSafeString(dos, nb.getName());
                writeSafeString(dos, nb.getDescription());
                dos.writeLong(nb.getCreatedTime());
                dos.writeLong(nb.getModifiedTime());

                Vector sections = nb.getSections();
                int secCount = sections != null ? sections.size() : 0;
                dos.writeInt(secCount);

                for (int si = 0; si < secCount; si++) {
                    Section sec = (Section) sections.elementAt(si);
                    dos.writeLong(sec.getId());
                    dos.writeLong(sec.getNotebookId());
                    writeSafeString(dos, sec.getName());
                    writeSafeString(dos, sec.getDescription());
                    dos.writeLong(sec.getCreatedTime());
                    dos.writeLong(sec.getModifiedTime());

                    // Custom Field Definitions
                    Vector defs = sec.getFieldDefinitions();
                    int defCount = defs != null ? defs.size() : 0;
                    dos.writeInt(defCount);
                    for (int di = 0; di < defCount; di++) {
                        CustomFieldDefinition def = (CustomFieldDefinition) defs.elementAt(di);
                        writeSafeString(dos, def.getKey());
                        writeSafeString(dos, def.getLabel());
                        dos.writeInt(def.getFieldType());
                        dos.writeBoolean(def.isMandatory());
                        writeSafeString(dos, def.getDefaultValue());
                    }

                    // Pages
                    Vector pages = sec.getPages();
                    int pageCount = pages != null ? pages.size() : 0;
                    dos.writeInt(pageCount);

                    for (int pi = 0; pi < pageCount; pi++) {
                        Page page = (Page) pages.elementAt(pi);
                        dos.writeLong(page.getId());
                        dos.writeLong(page.getSectionId());
                        writeSafeString(dos, page.getTitle());
                        writeSafeString(dos, page.getNotes());
                        dos.writeLong(page.getCreatedTime());
                        dos.writeLong(page.getModifiedTime());

                        // Custom Fields
                        Vector cfs = page.getCustomFields();
                        int cfCount = cfs != null ? cfs.size() : 0;
                        dos.writeInt(cfCount);
                        for (int ci = 0; ci < cfCount; ci++) {
                            CustomField cf = (CustomField) cfs.elementAt(ci);
                            writeSafeString(dos, cf.getKey());
                            writeSafeString(dos, cf.getLabel());
                            dos.writeInt(cf.getFieldType());
                            writeSafeString(dos, cf.getValue());
                            dos.writeBoolean(cf.isPageSpecific());
                        }

                        currentUnit++;
                        if (listener != null) {
                            String pageTitle = page.getTitle();
                            if (pageTitle.length() > 22) {
                                pageTitle = pageTitle.substring(0, 19) + "...";
                            }
                            listener.onProgress(currentUnit, totalUnits, "Saving: " + pageTitle);
                        }
                    }
                }
            }

            // 3. Attachments
            dos.writeInt(attachCount);
            byte[] buffer = new byte[BUFFER_SIZE];
            String attDir = AttachmentStorageManager.getInstance().getAttachmentDirectoryUrl();

            for (int ai = 0; ai < attachCount; ai++) {
                String attName = (String) attachments.elementAt(ai);
                writeSafeString(dos, attName);

                String attUrl = attDir + attName;
                FileConnection attFc = null;
                InputStream attIn = null;
                long writtenLen = 0;

                try {
                    attFc = (FileConnection) Connector.open(attUrl, Connector.READ);
                    if (attFc.exists() && !attFc.isDirectory()) {
                        long fSize = attFc.fileSize();
                        dos.writeLong(fSize);
                        attIn = attFc.openInputStream();
                        long remaining = fSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = attIn.read(buffer, 0, toRead);
                            if (read == -1) {
                                break;
                            }
                            dos.write(buffer, 0, read);
                            remaining -= read;
                        }
                        writtenLen = fSize;
                    } else {
                        dos.writeLong(0);
                    }
                } catch (Exception e) {
                    dos.writeLong(0);
                } finally {
                    if (attIn != null) {
                        try { attIn.close(); } catch (Exception ignored) {}
                    }
                    if (attFc != null) {
                        try { attFc.close(); } catch (Exception ignored) {}
                    }
                }

                currentUnit++;
                if (listener != null) {
                    listener.onProgress(currentUnit, totalUnits, "Saving: " + attName);
                }
            }

            dos.flush();
            currentUnit++;
            if (listener != null) {
                listener.onProgress(totalUnits, totalUnits, "Backup complete!");
            }

            return new BackupResult(nbCount, totalSections, totalPages, attachCount);

        } finally {
            if (dos != null) {
                try { dos.close(); } catch (Exception ignored) {}
            }
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
            if (fc != null) {
                try { fc.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Restores all notebooks and attachment files from a .jnbb backup file.
     *
     * @param srcFileUrl file:// URL of the .jnbb file
     * @param replaceExisting if true, replaces current database; if false, merges with fresh IDs
     * @param listener optional ProgressListener
     * @return RestoreResult summary
     * @throws IOException if any I/O error occurs or format is invalid
     */
    public RestoreResult restoreAll(String srcFileUrl, boolean replaceExisting, ProgressListener listener) throws IOException {
        if (srcFileUrl == null || srcFileUrl.length() == 0) {
            throw new IllegalArgumentException("Source file URL cannot be empty");
        }

        FileConnection fc = null;
        InputStream is = null;
        DataInputStream dis = null;

        try {
            fc = (FileConnection) Connector.open(srcFileUrl, Connector.READ);
            if (!fc.exists() || fc.isDirectory()) {
                throw new IOException("Backup file does not exist: " + srcFileUrl);
            }

            is = fc.openInputStream();
            dis = new DataInputStream(is);

            if (listener != null) {
                listener.onProgress(0, 100, "Reading backup file...");
            }

            // 1. Validate Header
            int magic = dis.readInt();
            if (magic != MAGIC_JNBB) {
                throw new IOException("Invalid backup file: not a JSQ Notebook backup (.jnbb)");
            }

            int version = dis.readInt();
            if (version > FORMAT_VERSION) {
                throw new IOException("Unsupported backup format version: " + version);
            }

            dis.readLong(); // backupTimestamp
            readSafeString(dis); // manifest
            long savedNextId = dis.readLong();

            // 2. Read Notebooks
            int nbCount = dis.readInt();
            Vector restoredNotebooks = new Vector();
            int totalSections = 0;
            int totalPages = 0;

            for (int ni = 0; ni < nbCount; ni++) {
                long nbId = dis.readLong();
                String nbName = readSafeString(dis);
                Notebook nb = new Notebook(nbId, nbName);
                nb.setDescription(readSafeString(dis));
                nb.setCreatedTime(dis.readLong());
                nb.setModifiedTime(dis.readLong());

                int secCount = dis.readInt();
                totalSections += secCount;

                for (int si = 0; si < secCount; si++) {
                    long secId = dis.readLong();
                    long secNbId = dis.readLong();
                    String secName = readSafeString(dis);
                    Section sec = new Section(secId, secNbId, secName);
                    sec.setDescription(readSafeString(dis));
                    sec.setCreatedTime(dis.readLong());
                    sec.setModifiedTime(dis.readLong());

                    // Clear default template
                    sec.getFieldDefinitions().removeAllElements();

                    // Field definitions
                    int defCount = dis.readInt();
                    for (int di = 0; di < defCount; di++) {
                        String defKey = readSafeString(dis);
                        String defLabel = readSafeString(dis);
                        int defType = dis.readInt();
                        boolean defMandatory = dis.readBoolean();
                        String defDefault = readSafeString(dis);
                        CustomFieldDefinition def = new CustomFieldDefinition(
                                defKey, defLabel, defType, defMandatory, defDefault);
                        sec.getFieldDefinitions().addElement(def);
                    }

                    // Pages
                    int pageCount = dis.readInt();
                    totalPages += pageCount;

                    for (int pi = 0; pi < pageCount; pi++) {
                        long pageId = dis.readLong();
                        long pageSecId = dis.readLong();
                        String pageTitle = readSafeString(dis);
                        Page page = new Page(pageId, pageSecId, pageTitle);
                        page.setNotes(readSafeString(dis));
                        page.setCreatedTime(dis.readLong());
                        page.setModifiedTime(dis.readLong());

                        // Custom fields
                        int cfCount = dis.readInt();
                        for (int ci = 0; ci < cfCount; ci++) {
                            String cfKey = readSafeString(dis);
                            String cfLabel = readSafeString(dis);
                            int cfType = dis.readInt();
                            String cfValue = readSafeString(dis);
                            boolean cfPageSpecific = dis.readBoolean();
                            CustomField cf = new CustomField(
                                    cfKey, cfLabel, cfType, cfValue, cfPageSpecific);
                            page.addCustomField(cf);
                        }

                        sec.addPage(page);

                        if (listener != null) {
                            String title = page.getTitle();
                            if (title.length() > 22) {
                                title = title.substring(0, 19) + "...";
                            }
                            listener.onProgress(totalPages, totalPages + 10, "Restoring: " + title);
                        }
                    }

                    nb.addSection(sec);
                }

                restoredNotebooks.addElement(nb);
            }

            // 3. Read Attachments
            int attachCount = dis.readInt();
            byte[] buffer = new byte[BUFFER_SIZE];
            AttachmentStorageManager storage = AttachmentStorageManager.getInstance();
            String attDir = storage.getAttachmentDirectoryUrl();
            storage.ensureDirectoryExists(attDir);

            int totalUnits = totalPages + attachCount + 2;
            int currentUnit = totalPages;

            for (int ai = 0; ai < attachCount; ai++) {
                String attName = readSafeString(dis);
                long fileSize = dis.readLong();

                if (fileSize > 0) {
                    String destUrl = attDir + attName;
                    FileConnection destFc = null;
                    OutputStream destOut = null;
                    try {
                        destFc = (FileConnection) Connector.open(destUrl, Connector.READ_WRITE);
                        if (destFc.exists()) {
                            destFc.delete();
                        }
                        destFc.create();
                        destOut = destFc.openOutputStream();

                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = dis.read(buffer, 0, toRead);
                            if (read == -1) {
                                break;
                            }
                            destOut.write(buffer, 0, read);
                            remaining -= read;
                        }
                        destOut.flush();
                    } finally {
                        if (destOut != null) {
                            try { destOut.close(); } catch (Exception ignored) {}
                        }
                        if (destFc != null) {
                            try { destFc.close(); } catch (Exception ignored) {}
                        }
                    }
                }

                currentUnit++;
                if (listener != null) {
                    listener.onProgress(currentUnit, totalUnits, "Restoring: " + attName);
                }
            }

            // 4. Commit to Database
            if (listener != null) {
                listener.onProgress(totalUnits - 1, totalUnits, "Saving to database...");
            }

            NotebookStoreManager store = NotebookStoreManager.getInstance();
            if (replaceExisting) {
                store.replaceAllNotebooks(restoredNotebooks, savedNextId);
            } else {
                // Merge: re-assign unique IDs to prevent collisions with existing records
                int count = restoredNotebooks.size();
                for (int i = 0; i < count; i++) {
                    Notebook nb = (Notebook) restoredNotebooks.elementAt(i);
                    long newNbId = store.nextId();
                    nb.setId(newNbId);

                    Vector sections = nb.getSections();
                    int secCount = sections != null ? sections.size() : 0;
                    for (int si = 0; si < secCount; si++) {
                        Section sec = (Section) sections.elementAt(si);
                        long newSecId = store.nextId();
                        sec.setId(newSecId);
                        sec.setNotebookId(newNbId);

                        Vector pages = sec.getPages();
                        int pCount = pages != null ? pages.size() : 0;
                        for (int pi = 0; pi < pCount; pi++) {
                            Page p = (Page) pages.elementAt(pi);
                            long newPageId = store.nextId();
                            p.setId(newPageId);
                            p.setSectionId(newSecId);
                        }
                    }
                    store.addNotebook(nb);
                }
            }

            if (listener != null) {
                listener.onProgress(totalUnits, totalUnits, "Restore complete!");
            }

            return new RestoreResult(nbCount, totalSections, totalPages, attachCount);

        } finally {
            if (dis != null) {
                try { dis.close(); } catch (Exception ignored) {}
            }
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (fc != null) {
                try { fc.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Discovers all binary attachments referenced by any page in any notebook,
     * as well as all files currently in the device attachments folder.
     */
    private Vector findAllAttachments(Vector notebooks) {
        Vector list = new Vector();

        // 1. Scan notes in all pages
        if (notebooks != null) {
            int nbCount = notebooks.size();
            for (int ni = 0; ni < nbCount; ni++) {
                Notebook nb = (Notebook) notebooks.elementAt(ni);
                Vector sections = nb.getSections();
                int secCount = sections != null ? sections.size() : 0;
                for (int si = 0; si < secCount; si++) {
                    Section sec = (Section) sections.elementAt(si);
                    Vector pages = sec.getPages();
                    int pageCount = pages != null ? pages.size() : 0;
                    for (int pi = 0; pi < pageCount; pi++) {
                        Page page = (Page) pages.elementAt(pi);
                        Vector imgs = PageExportManager.extractImagePlaceholders(page.getNotes());
                        int imgCount = imgs != null ? imgs.size() : 0;
                        for (int ii = 0; ii < imgCount; ii++) {
                            String img = (String) imgs.elementAt(ii);
                            if (img != null && img.length() > 0 && !list.contains(img)) {
                                list.addElement(img);
                            }
                        }
                    }
                }
            }
        }

        // 2. Scan attachments directory on SD Card
        String attDir = AttachmentStorageManager.getInstance().getAttachmentDirectoryUrl();
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(attDir, Connector.READ);
            if (fc.exists() && fc.isDirectory()) {
                Enumeration entries = fc.list("*", true);
                while (entries.hasMoreElements()) {
                    String name = (String) entries.nextElement();
                    if (!name.endsWith("/") && !list.contains(name)) {
                        list.addElement(name);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (fc != null) {
                try { fc.close(); } catch (Exception ignored) {}
            }
        }

        return list;
    }

    /**
     * Writes UTF-8 encoded string safely using 32-bit length prefix,
     * supporting strings of arbitrary length without the 64 KB limit of writeUTF().
     */
    private static void writeSafeString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            dos.writeInt(-1);
            return;
        }
        byte[] bytes = str.getBytes("UTF-8");
        dos.writeInt(bytes.length);
        if (bytes.length > 0) {
            dos.write(bytes);
        }
    }

    /**
     * Reads UTF-8 encoded string written by writeSafeString.
     */
    private static String readSafeString(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        if (len < 0) {
            return "";
        }
        if (len == 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}
