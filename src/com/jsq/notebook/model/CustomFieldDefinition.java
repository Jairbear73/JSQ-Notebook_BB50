package com.jsq.notebook.model;

import net.rim.device.api.util.Persistable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Defines a custom field template specification within a Section.
 * Implements Persistable and serialization for unsigned RecordStore NVRAM persistence.
 */
public class CustomFieldDefinition implements Persistable {

    private String key;
    private String label;
    private int fieldType;
    private boolean mandatory;
    private String defaultValue;

    public CustomFieldDefinition() {
        this.key = "";
        this.label = "";
        this.fieldType = FieldType.TEXT;
        this.mandatory = false;
        this.defaultValue = "";
    }

    public CustomFieldDefinition(String key, String label, int fieldType, boolean mandatory, String defaultValue) {
        this.key = key != null ? key : "";
        this.label = label != null ? label : "";
        this.fieldType = fieldType;
        this.mandatory = mandatory;
        this.defaultValue = defaultValue != null ? defaultValue : "";
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public int getFieldType() {
        return fieldType;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeUTF(key != null ? key : "");
        dos.writeUTF(label != null ? label : "");
        dos.writeInt(fieldType);
        dos.writeBoolean(mandatory);
        dos.writeUTF(defaultValue != null ? defaultValue : "");
    }

    public static CustomFieldDefinition deserialize(DataInputStream dis) throws IOException {
        String key = dis.readUTF();
        String label = dis.readUTF();
        int fieldType = dis.readInt();
        boolean mandatory = dis.readBoolean();
        String defaultValue = dis.readUTF();
        return new CustomFieldDefinition(key, label, fieldType, mandatory, defaultValue);
    }
}
