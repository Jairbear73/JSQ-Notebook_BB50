package com.jsq.notebook.ui;

import com.jsq.notebook.storage.AttachmentStorageManager;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ListField;
import net.rim.device.api.ui.component.ListFieldCallback;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import java.util.Enumeration;
import java.util.Vector;

/**
 * File and folder browser popup dialog with ListField navigation and OK/Cancel
 * action buttons.
 * Utilizes unsigned JSR-75 FileConnection APIs.
 */
public class FilePickerPopup extends PopupScreen implements ListFieldCallback {

    public static final int MODE_IMAGE_SELECT = 0;
    public static final int MODE_DIRECTORY_SELECT = 1;
    public static final int MODE_JNB_SELECT = 2;
    public static final int MODE_ATTACHMENT_SELECT = 3;
    public static final int MODE_JNBB_SELECT = 4;

    public interface FilePickerListener {
        void onFileSelected(String selectedUrl);

        void onCancelled();
    }

    private static class PickerEntry {
        String label;
        String rawName;
        boolean isUp;
        boolean isDir;

        PickerEntry(String label, String rawName, boolean isUp, boolean isDir) {
            this.label = label;
            this.rawName = rawName;
            this.isUp = isUp;
            this.isDir = isDir;
        }
    }

    private int mode;
    private FilePickerListener listener;
    private String currentDirectoryUrl;
    private LabelField pathLabel;
    private ListField listField;
    private Vector items;
    private boolean handledSelection;

    public FilePickerPopup(int mode, FilePickerListener listener) {
        super(new VerticalFieldManager(VerticalFieldManager.VERTICAL_SCROLL | VerticalFieldManager.VERTICAL_SCROLLBAR));
        this.mode = mode;
        this.listener = listener;
        this.items = new Vector();
        this.handledSelection = false;

        String root = AttachmentStorageManager.getInstance().getStorageRoot();
        this.currentDirectoryUrl = root;

        initUI();
        loadDirectory(this.currentDirectoryUrl);
    }

    private void initUI() {
        String titleText;
        if (mode == MODE_IMAGE_SELECT) {
            titleText = "Select Image to Attach";
        } else if (mode == MODE_JNB_SELECT) {
            titleText = "Select .jnb File to Import";
        } else if (mode == MODE_JNBB_SELECT) {
            titleText = "Select .jnbb Backup to Restore";
        } else if (mode == MODE_ATTACHMENT_SELECT) {
            titleText = "Select File to Attach";
        } else {
            titleText = "Select Destination Folder";
        }
        LabelField title = new LabelField(titleText);
        title.setFont(Font.getDefault().derive(Font.BOLD));
        add(title);
        add(new SeparatorField());

        pathLabel = new LabelField(currentDirectoryUrl);
        pathLabel.setFont(Font.getDefault().derive(Font.ITALIC));
        add(pathLabel);
        add(new SeparatorField());

        listField = new ListField();
        listField.setCallback(this);
        listField.setRowHeight(Font.getDefault().getHeight() + 6);
        add(listField);

        add(new SeparatorField());
        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        String okLabel;
        if (mode == MODE_DIRECTORY_SELECT) {
            okLabel = "Select Folder";
        } else if (mode == MODE_JNB_SELECT) {
            okLabel = "Import";
        } else if (mode == MODE_JNBB_SELECT) {
            okLabel = "Restore";
        } else if (mode == MODE_ATTACHMENT_SELECT) {
            okLabel = "Attach";
        } else {
            okLabel = "OK";
        }
        ButtonField okBtn = new ButtonField(okLabel, ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (mode == MODE_DIRECTORY_SELECT) {
                    handledSelection = true;
                    if (listener != null) {
                        listener.onFileSelected(currentDirectoryUrl);
                    }
                    close();
                } else {
                    handleSelection();
                }
            }
        });
        ButtonField cancelBtn = new ButtonField("Cancel", ButtonField.CONSUME_CLICK);
        cancelBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (listener != null) {
                    listener.onCancelled();
                }
                close();
            }
        });
        buttonBar.add(okBtn);
        buttonBar.add(cancelBtn);
        add(buttonBar);
    }

    private void loadDirectory(String dirUrl) {
        // Capture the currently selected entry name before reloading to preserve
        // selection position
        String previousRawName = null;
        int currentIndex = listField.getSelectedIndex();
        if (currentIndex >= 0 && currentIndex < items.size()) {
            PickerEntry oldEntry = (PickerEntry) items.elementAt(currentIndex);
            previousRawName = oldEntry.rawName;
        }

        currentDirectoryUrl = dirUrl;
        pathLabel.setText(dirUrl);
        items.removeAllElements();

        if (!isRoot(dirUrl)) {
            items.addElement(new PickerEntry(".. [Parent Directory]", "..", true, true));
        }

        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirUrl, Connector.READ);
            if (fc.isDirectory()) {
                Enumeration entries = fc.list("*", true);
                Vector dirs = new Vector();
                Vector files = new Vector();

                while (entries.hasMoreElements()) {
                    String entry = (String) entries.nextElement();
                    if (entry.endsWith("/")) {
                        dirs.addElement(entry);
                    } else {
                        if (mode == MODE_IMAGE_SELECT) {
                            String lower = entry.toLowerCase();
                            if (lower.endsWith(".png") || lower.endsWith(".jpg") ||
                                    lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
                                    lower.endsWith(".bmp")) {
                                files.addElement(entry);
                            }
                        } else if (mode == MODE_JNB_SELECT) {
                            String lower = entry.toLowerCase();
                            if (lower.endsWith(".jnb")) {
                                files.addElement(entry);
                            }
                        } else if (mode == MODE_JNBB_SELECT) {
                            String lower = entry.toLowerCase();
                            if (lower.endsWith(".jnbb")) {
                                files.addElement(entry);
                            }
                        } else {
                            files.addElement(entry);
                        }
                    }
                }

                int dirCount = dirs.size();
                for (int i = 0; i < dirCount; i++) {
                    String dName = (String) dirs.elementAt(i);
                    items.addElement(new PickerEntry("[DIR] " + dName, dName, false, true));
                }

                int fileCount = files.size();
                for (int i = 0; i < fileCount; i++) {
                    String fName = (String) files.elementAt(i);
                    items.addElement(new PickerEntry(fName, fName, false, false));
                }
            }
        } catch (Exception e) {
            Dialog.alert("Cannot read directory: " + e.getMessage());
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (Exception e) {
                }
            }
        }

        listField.setSize(items.size());

        // Try to find and restore the previously selected item by name, otherwise
        // default to 0
        int targetIndex = 0;
        if (previousRawName != null) {
            for (int i = 0; i < items.size(); i++) {
                PickerEntry entry = (PickerEntry) items.elementAt(i);
                if (entry.rawName.equals(previousRawName)) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (items.size() > 0) {
            listField.setSelectedIndex(targetIndex < items.size() ? targetIndex : 0);
        }
    }

    private boolean isRoot(String url) {
        if ("file:///".equals(url) || "file:///SDCard/".equals(url) || "file:///store/".equals(url)) {
            return true;
        }
        return false;
    }

    private void navigateUp() {
        if (isRoot(currentDirectoryUrl)) {
            return;
        }
        String stripped = currentDirectoryUrl;
        if (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        int lastSlash = stripped.lastIndexOf('/');
        if (lastSlash >= 8) { // after file:///
            String parent = stripped.substring(0, lastSlash + 1);
            loadDirectory(parent);
        } else {
            loadDirectory(AttachmentStorageManager.getInstance().getStorageRoot());
        }
    }

    private void handleSelection() {
        int idx = listField.getSelectedIndex();
        if (idx < 0 || idx >= items.size()) {
            if (mode == MODE_IMAGE_SELECT) {
                Dialog.alert("Please highlight an image file to attach.");
            } else if (mode == MODE_JNB_SELECT) {
                Dialog.alert("Please highlight a .jnb file to import.");
            } else if (mode == MODE_JNBB_SELECT) {
                Dialog.alert("Please highlight a .jnbb file to restore.");
            } else if (mode == MODE_ATTACHMENT_SELECT) {
                Dialog.alert("Please highlight a file to attach.");
            }
            return;
        }

        PickerEntry entry = (PickerEntry) items.elementAt(idx);
        if (entry.isUp) {
            navigateUp();
        } else if (entry.isDir) {
            loadDirectory(currentDirectoryUrl + entry.rawName);
        } else {
            if (mode == MODE_IMAGE_SELECT || mode == MODE_ATTACHMENT_SELECT) {
                handledSelection = true;
                if (listener != null) {
                    listener.onFileSelected(currentDirectoryUrl + entry.rawName);
                }
                close();
            } else if (mode == MODE_JNB_SELECT) {
                if (entry.rawName.toLowerCase().endsWith(".jnb")) {
                    handledSelection = true;
                    if (listener != null) {
                        listener.onFileSelected(currentDirectoryUrl + entry.rawName);
                    }
                    close();
                } else {
                    Dialog.alert("Please highlight a .jnb file to import.");
                }
            } else if (mode == MODE_JNBB_SELECT) {
                if (entry.rawName.toLowerCase().endsWith(".jnbb")) {
                    handledSelection = true;
                    if (listener != null) {
                        listener.onFileSelected(currentDirectoryUrl + entry.rawName);
                    }
                    close();
                } else {
                    Dialog.alert("Please highlight a .jnbb file to restore.");
                }
            } else {
                Dialog.alert("Please highlight a folder or click 'Select Folder'");
            }
        }
    }

    protected boolean navigationClick(int status, int time) {
        handleSelection();
        return true;
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        PickerEntry entry = (PickerEntry) items.elementAt(index);
        graphics.drawText(entry.label, 4, y + 2);
    }

    public Object get(ListField listField, int index) {
        if (index >= 0 && index < items.size()) {
            return items.elementAt(index);
        }
        return null;
    }

    public int getPreferredWidth(ListField listField) {
        return Graphics.getScreenWidth();
    }

    public int indexOfList(ListField listField, String prefix, int start) {
        return -1;
    }

    // ==========================================
    // Physical Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        if (mode == MODE_DIRECTORY_SELECT) {
            menu.add(new MenuItem("Select This Folder", 100, 10) {
                public void run() {
                    handledSelection = true;
                    if (listener != null) {
                        listener.onFileSelected(currentDirectoryUrl);
                    }
                    close();
                }
            });
        }

        menu.add(new MenuItem("Open / Select", 110, 15) {
            public void run() {
                handleSelection();
            }
        });

        if (!isRoot(currentDirectoryUrl)) {
            menu.add(new MenuItem("Up One Level", 120, 20) {
                public void run() {
                    navigateUp();
                }
            });
        }

        menu.add(new MenuItem("Cancel", 200, 30) {
            public void run() {
                if (listener != null) {
                    listener.onCancelled();
                }
                close();
            }
        });
    }

    public boolean onClose() {
        if (!handledSelection && listener != null) {
            listener.onCancelled();
        }
        return super.onClose();
    }
}
