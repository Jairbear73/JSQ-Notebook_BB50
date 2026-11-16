package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Individual Page within a Section.
 * Implements Persistable and serialization for unsigned RecordStore NVRAM persistence.
 */
public class Page implements Persistable {

    private long id;
    private long sectionId;
    private String title;
    private String notes;
    private long createdTime;
    private long modifiedTime;
    private Vector customFields;

    public Page() {
        this.id = 0;
        this.sectionId = 0;
        this.title = "";
        this.notes = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.customFields = new Vector();
    }

    public Page(long id, long sectionId, String title) {
        this.id = id;
        this.sectionId = sectionId;
        this.title = title != null ? title : "";
        this.notes = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.customFields = new Vector();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSectionId() {
        return sectionId;
    }

    public void setSectionId(long sectionId) {
        this.sectionId = sectionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
        this.modifiedTime = System.currentTimeMillis();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
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

    public Vector getCustomFields() {
        if (customFields == null) {
            customFields = new Vector();
        }
        return customFields;
    }

    public CustomField getCustomField(String key) {
        if (key == null || customFields == null) {
            return null;
        }
        int size = customFields.size();
        for (int i = 0; i < size; i++) {
            CustomField cf = (CustomField) customFields.elementAt(i);
            if (key.equals(cf.getKey())) {
                return cf;
            }
        }
        return null;
    }

    public void addCustomField(CustomField field) {
        if (field == null) {
            return;
        }
        if (customFields == null) {
            customFields = new Vector();
        }
        CustomField existing = getCustomField(field.getKey());
        if (existing != null) {
            existing.setValue(field.getValue());
            existing.setLabel(field.getLabel());
            existing.setFieldType(field.getFieldType());
            existing.setPageSpecific(field.isPageSpecific());
        } else {
            customFields.addElement(field);
        }
        this.modifiedTime = System.currentTimeMillis();
    }


    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(sectionId);
        dos.writeUTF(title != null ? title : "");
        dos.writeUTF(notes != null ? notes : "");
        dos.writeLong(createdTime);
        dos.writeLong(modifiedTime);
        int cfCount = customFields != null ? customFields.size() : 0;
        dos.writeInt(cfCount);
        for (int i = 0; i < cfCount; i++) {
            CustomField cf = (CustomField) customFields.elementAt(i);
            cf.serialize(dos);
        }
    }

    public static Page deserialize(DataInputStream dis) throws IOException {
        long id = dis.readLong();
        long sectionId = dis.readLong();
        String title = dis.readUTF();
        Page page = new Page(id, sectionId, title);
        page.notes = dis.readUTF();
        page.createdTime = dis.readLong();
        page.modifiedTime = dis.readLong();
        int cfCount = dis.readInt();
        for (int i = 0; i < cfCount; i++) {
            page.addCustomField(CustomField.deserialize(dis));
        }
        return page;
    }
}
