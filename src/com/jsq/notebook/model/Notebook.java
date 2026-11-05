package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Top-tier Notebook entity containing multiple Sections.
 * Implements Persistable and serialization for unsigned RecordStore NVRAM persistence.
 */
public class Notebook implements Persistable {

    private long id;
    private String name;
    private String description;
    private long createdTime;
    private long modifiedTime;
    private Vector sections;

    public Notebook() {
        this.id = 0;
        this.name = "";
        this.description = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.sections = new Vector();
    }

    public Notebook(long id, String name) {
        this.id = id;
        this.name = name != null ? name : "";
        this.description = "";
        long now = System.currentTimeMillis();
        this.createdTime = now;
        this.modifiedTime = now;
        this.sections = new Vector();
    }

    public long getId() {
        return id;
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

    public long getModifiedTime() {
        return modifiedTime;
    }

    public Vector getSections() {
        if (sections == null) {
            sections = new Vector();
        }
        return sections;
    }

    public void addSection(Section section) {
        if (section != null) {
            getSections().addElement(section);
            this.modifiedTime = System.currentTimeMillis();
        }
    }

    public boolean removeSection(long sectionId) {
        if (sections == null) {
            return false;
        }
        int size = sections.size();
        for (int i = 0; i < size; i++) {
            Section s = (Section) sections.elementAt(i);
            if (s.getId() == sectionId) {
                sections.removeElementAt(i);
                this.modifiedTime = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public int getTotalPageCount() {
        if (sections == null) {
            return 0;
        }
        int total = 0;
        int size = sections.size();
        for (int i = 0; i < size; i++) {
            Section s = (Section) sections.elementAt(i);
            total += s.getPages().size();
        }
        return total;
    }

    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeUTF(name != null ? name : "");
        dos.writeUTF(description != null ? description : "");
        dos.writeLong(createdTime);
        dos.writeLong(modifiedTime);

        int secCount = sections != null ? sections.size() : 0;
        dos.writeInt(secCount);
        for (int i = 0; i < secCount; i++) {
            Section s = (Section) sections.elementAt(i);
            s.serialize(dos);
        }
    }

    public static Notebook deserialize(DataInputStream dis) throws IOException {
        long id = dis.readLong();
        String name = dis.readUTF();
        Notebook nb = new Notebook(id, name);
        nb.description = dis.readUTF();
        nb.createdTime = dis.readLong();
        nb.modifiedTime = dis.readLong();

        int secCount = dis.readInt();
        nb.sections = new Vector();
        for (int i = 0; i < secCount; i++) {
            nb.sections.addElement(Section.deserialize(dis));
        }
        return nb;
    }
}
