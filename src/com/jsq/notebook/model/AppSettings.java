package com.jsq.notebook.model;

import javax.microedition.rms.RecordStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Manages user preferences and application settings persisted in NVRAM.
 * Includes Dark Mode support, default screen modes, and UI options.
 */
public class AppSettings {

    private static final String RECORD_STORE_NAME = "jsq_settings";
    private static AppSettings instance;

    private boolean darkMode = false;
    private boolean defaultEditMode = false;
    private boolean showMetadata = true;
    private boolean confirmDelete = true;

    private AppSettings() {
        load();
    }

    public static synchronized AppSettings getInstance() {
        if (instance == null) {
            instance = new AppSettings();
        }
        return instance;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public boolean isDefaultEditMode() {
        return defaultEditMode;
    }

    public void setDefaultEditMode(boolean defaultEditMode) {
        this.defaultEditMode = defaultEditMode;
    }

    public boolean isShowMetadata() {
        return showMetadata;
    }

    public void setShowMetadata(boolean showMetadata) {
        this.showMetadata = showMetadata;
    }

    public boolean isConfirmDelete() {
        return confirmDelete;
    }

    public void setConfirmDelete(boolean confirmDelete) {
        this.confirmDelete = confirmDelete;
    }

    public synchronized void load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                dis.readInt(); // format version
                darkMode = dis.readBoolean();
                defaultEditMode = dis.readBoolean();
                showMetadata = dis.readBoolean();
                confirmDelete = dis.readBoolean();
            }
        } catch (Exception e) {
            // Default settings applied on error
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                }
            }
        }
    }

    public synchronized void save() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(1); // settings version
            dos.writeBoolean(darkMode);
            dos.writeBoolean(defaultEditMode);
            dos.writeBoolean(showMetadata);
            dos.writeBoolean(confirmDelete);
            dos.flush();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                }
            }
        }
    }
}
