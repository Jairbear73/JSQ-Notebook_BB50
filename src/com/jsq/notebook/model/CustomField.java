package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Concrete custom field attached to a Page.
 * Can be inherited from a Section's template or uniquely added to the Page.
 */
public class CustomField implements Persistable {

    private String key;
    private String label;
    private int fieldType;
    private String value;
    private boolean pageSpecific;

    public CustomField() {
        this.key = "";
        this.label = "";
        this.fieldType = FieldType.TEXT;
        this.value = "";
        this.pageSpecific = false;
    }

    public CustomField(String key, String label, int fieldType, String value, boolean pageSpecific) {
        this.key = key != null ? key : "";
        this.label = label != null ? label : "";
        this.fieldType = fieldType;
        this.value = value != null ? value : "";
        this.pageSpecific = pageSpecific;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public int getFieldType() {
        return fieldType;
    }

    public void setFieldType(int fieldType) {
        this.fieldType = fieldType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
    }

    public boolean isPageSpecific() {
        return pageSpecific;
    }

    public void setPageSpecific(boolean pageSpecific) {
        this.pageSpecific = pageSpecific;
    }

    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeUTF(key != null ? key : "");
        dos.writeUTF(label != null ? label : "");
        dos.writeInt(fieldType);
        dos.writeUTF(value != null ? value : "");
        dos.writeBoolean(pageSpecific);
    }

    public static CustomField deserialize(DataInputStream dis) throws IOException {
        String key = dis.readUTF();
        String label = dis.readUTF();
        int fieldType = dis.readInt();
        String value = dis.readUTF();
        boolean pageSpecific = dis.readBoolean();
        return new CustomField(key, label, fieldType, value, pageSpecific);
    }
}
