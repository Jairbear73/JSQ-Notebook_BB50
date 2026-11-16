package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Section tier within a Notebook.
 * Defines the custom field template inherited by member Pages.
 * Implements Persistable and serialization for unsigned RecordStore NVRAM persistence.
 */
public class Section implements Persistable {

    private long id;
    private long notebookId;
    private String name;
    private String description;
    private long createdTime;
    private long modifiedTime;
    private Vector fieldDefinitions;
    private Vector pages;

    public Section() {
        this.id = 0;
        this.notebookId = 0;
        this.name = "";
        this.description = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.fieldDefinitions = new Vector();
        this.pages = new Vector();
        initDefaultTemplate();
    }

    public Section(long id, long notebookId, String name) {
        this.id = id;
        this.notebookId = notebookId;
        this.name = name != null ? name : "";
        this.description = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.fieldDefinitions = new Vector();
        this.pages = new Vector();
        initDefaultTemplate();
    }

    /**
     * Initializes default template: Mandatory Title field and maximum real-estate Notes field.
     */
    public void initDefaultTemplate() {
        fieldDefinitions.removeAllElements();
        fieldDefinitions.addElement(new CustomFieldDefinition("title", "Title", FieldType.TEXT, true, ""));
        fieldDefinitions.addElement(new CustomFieldDefinition("notes", "Notes", FieldType.LONG_TEXT, false, ""));
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(long notebookId) {
        this.notebookId = notebookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
        this.modifiedTime = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
        this.modifiedTime = System.currentTimeMillis();
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public Vector getFieldDefinitions() {
        if (fieldDefinitions == null) {
            fieldDefinitions = new Vector();
            initDefaultTemplate();
        }
        return fieldDefinitions;
    }

    public Vector getPages() {
        if (pages == null) {
            pages = new Vector();
        }
        return pages;
    }

    public void addPage(Page page) {
        if (page != null) {
            getPages().addElement(page);
            this.modifiedTime = System.currentTimeMillis();
        }
    }

    public boolean removePage(long pageId) {
        if (pages == null) {
            return false;
        }
        int size = pages.size();
        for (int i = 0; i < size; i++) {
            Page p = (Page) pages.elementAt(i);
            if (p.getId() == pageId) {
                pages.removeElementAt(i);
                this.modifiedTime = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /**
     * Instantiates template fields onto a new or existing Page.
     * Preserves existing values if already defined on the page.
     */
    public void applyTemplateToPage(Page page) {
        if (page == null) {
            return;
        }
        Vector defs = getFieldDefinitions();
        int count = defs.size();
        for (int i = 0; i < count; i++) {
            CustomFieldDefinition def = (CustomFieldDefinition) defs.elementAt(i);
            String key = def.getKey();
            if ("title".equals(key) || "notes".equals(key)) {
                // Handled natively by page title and notes
                continue;
            }

            CustomField existing = page.getCustomField(key);
            if (existing == null) {
                CustomField field = new CustomField(
                    def.getKey(),
                    def.getLabel(),
                    def.getFieldType(),
                    def.getDefaultValue(),
                    false
                );
                page.addCustomField(field);
            } else {
                // Update label and type from definition if not page-specific
                if (!existing.isPageSpecific()) {
                    existing.setLabel(def.getLabel());
                    existing.setFieldType(def.getFieldType());
                }
            }
        }
    }

    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(notebookId);
        dos.writeUTF(name != null ? name : "");
        dos.writeUTF(description != null ? description : "");
        dos.writeLong(createdTime);
        dos.writeLong(modifiedTime);

        int defCount = fieldDefinitions != null ? fieldDefinitions.size() : 0;
        dos.writeInt(defCount);
        for (int i = 0; i < defCount; i++) {
            CustomFieldDefinition def = (CustomFieldDefinition) fieldDefinitions.elementAt(i);
            def.serialize(dos);
        }

        int pageCount = pages != null ? pages.size() : 0;
        dos.writeInt(pageCount);
        for (int i = 0; i < pageCount; i++) {
            Page p = (Page) pages.elementAt(i);
            p.serialize(dos);
        }
    }

    public static Section deserialize(DataInputStream dis) throws IOException {
        long id = dis.readLong();
        long notebookId = dis.readLong();
        String name = dis.readUTF();
        Section sec = new Section(id, notebookId, name);
        sec.description = dis.readUTF();
        sec.createdTime = dis.readLong();
        sec.modifiedTime = dis.readLong();

        int defCount = dis.readInt();
        sec.fieldDefinitions = new Vector();
        for (int i = 0; i < defCount; i++) {
            sec.fieldDefinitions.addElement(CustomFieldDefinition.deserialize(dis));
        }

        int pageCount = dis.readInt();
        sec.pages = new Vector();
        for (int i = 0; i < pageCount; i++) {
            sec.pages.addElement(Page.deserialize(dis));
        }
        return sec;
    }
}
