package com.jsq.notebook.storage;

import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.NotebookDatabase;

import javax.microedition.rms.RecordStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Vector;

/**
 * Manages device internal NVRAM persistence using standard unsigned J2ME RecordStore APIs.
 * Eliminates RIM Runtime API signature requirements while guaranteeing non-volatile flash storage.
 */
public class NotebookStoreManager {

    private static final String RECORD_STORE_NAME = "jsq_notebook_db";
    private static NotebookStoreManager instance;
    private NotebookDatabase database;

    private NotebookStoreManager() {
        initStore();
    }

    public static synchronized NotebookStoreManager getInstance() {
        if (instance == null) {
            instance = new NotebookStoreManager();
        }
        return instance;
    }

    /**
     * Initializes connection to NVRAM RecordStore and loads or creates the database.
     */
    private void initStore() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                database = NotebookDatabase.deserialize(dis);
            }
        } catch (Exception e) {
            database = null;
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                }
            }
        }

        if (database == null) {
            database = new NotebookDatabase();
            database.initSampleData();
            commit();
        }
    }

    /**
     * Persists all current database state into device NVRAM.
     */
    public synchronized void commit() {
        if (database == null) {
            return;
        }

        RecordStore rs = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            database.serialize(dos);
            dos.flush();
            byte[] data = baos.toByteArray();

            rs = RecordStore.openRecordStore(RECORD_STORE_NAME, true);
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
            // Error writing to NVRAM record store
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                }
            }
        }
    }

    public synchronized Vector getNotebooks() {
        return database.getNotebooks();
    }

    public synchronized NotebookDatabase getDatabase() {
        return database;
    }

    public synchronized void replaceAllNotebooks(Vector newNotebooks, long newNextId) {
        if (database == null) {
            database = new NotebookDatabase();
        }
        Vector current = database.getNotebooks();
        current.removeAllElements();
        if (newNotebooks != null) {
            int count = newNotebooks.size();
            for (int i = 0; i < count; i++) {
                current.addElement(newNotebooks.elementAt(i));
            }
        }
        if (newNextId > database.getNextId()) {
            database.setNextId(newNextId);
        }
        commit();
    }

    public synchronized void addNotebook(Notebook notebook) {
        database.addNotebook(notebook);
        commit();
    }

    public synchronized boolean deleteNotebook(long notebookId) {
        boolean removed = database.removeNotebook(notebookId);
        if (removed) {
            commit();
        }
        return removed;
    }

    public synchronized long nextId() {
        return database.generateNextId();
    }

    public synchronized Notebook addDemoNotebook() {
        return addDemoNotebook(null);
    }

    public synchronized Notebook addDemoNotebook(com.jsq.notebook.util.ProgressListener listener) {
        Notebook nb = database.createDemoNotebook(listener);
        commit();
        return nb;
    }
}
