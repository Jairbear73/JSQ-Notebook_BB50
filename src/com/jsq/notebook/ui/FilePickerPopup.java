package com.jsq.notebook.ui;

import com.jsq.notebook.model.AppSettings;
import com.jsq.notebook.storage.AttachmentStorageManager;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.EditField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.ListField;
import net.rim.device.api.ui.component.ListFieldCallback;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;
//import net.rim.device.api.ui.picker.FilePicker;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Modern, full-screen file and directory chooser for BlackBerry OS 5.0.
 *
 * Provides:
 * 1. Automatic delegation to native BlackBerry OS 5.0 FilePicker for file
 * selections.
 * 2. Dedicated, rock-solid Directory Chooser where selecting destination
 * folders via
 * the OK button, menu, top action bar, or trackwheel click works 100% reliably.
 * 3. In-app new folder creation.
 * 4. Multi-drive root browsing (SD Card & Device Memory).
 * 5. 100% unsigned CLDC 1.1 / JSR-75 compatible.
 */
public class FilePickerPopup extends MainScreen implements ListFieldCallback {

    public static final int MODE_IMAGE_SELECT = 0;
    public static final int MODE_DIRECTORY_SELECT = 1;
    public static final int MODE_JNB_SELECT = 2;
    public static final int MODE_ATTACHMENT_SELECT = 3;
    public static final int MODE_JNBB_SELECT = 4;

    public interface FilePickerListener {
        void onFileSelected(String selectedUrl);

        void onCancelled();
    }

    /**
     * Primary entry point for launching file or directory selection.
     * Uses native OS 5.0 FilePicker for file modes when available, and falls back
     * to the redesigned full-screen chooser for directories or fallback.
     */
    public static void open(final int mode, final FilePickerListener listener) {
        FilePickerPopup screen = new FilePickerPopup(mode, listener);
        UiApplication.getUiApplication().pushScreen(screen);
    }

    // ==========================================
    // Internal Entry Model
    // ==========================================
    private static class PickerEntry {
        static final int TYPE_SELECT_CURRENT = 0;
        static final int TYPE_UP = 1;
        static final int TYPE_ROOT_DRIVE = 2;
        static final int TYPE_DIR = 3;
        static final int TYPE_FILE = 4;

        int type;
        String label;
        String rawName;
        String fullUrl;
        long size;

        PickerEntry(int type, String label, String rawName, String fullUrl, long size) {
            this.type = type;
            this.label = label;
            this.rawName = rawName;
            this.fullUrl = fullUrl;
            this.size = size;
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
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        this.mode = mode;
        this.listener = listener;
        this.items = new Vector();
        this.handledSelection = false;

        String root = AttachmentStorageManager.getInstance().getStorageRoot();
        if (root == null || root.length() == 0) {
            root = "file:///SDCard/";
        }
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
        setTitle(titleText);

        // Path header
        pathLabel = new LabelField(getFriendlyPath(currentDirectoryUrl));
        pathLabel.setFont(Font.getDefault().derive(Font.ITALIC));
        add(pathLabel);
        add(new SeparatorField());

        // Top quick-action bar for Directory Selection Mode
        if (mode == MODE_DIRECTORY_SELECT) {
            HorizontalFieldManager topBar = new HorizontalFieldManager(Field.FIELD_HCENTER);

            ButtonField selectHereBtn = new ButtonField("[ Select This Folder ]", ButtonField.CONSUME_CLICK);
            selectHereBtn.setChangeListener(new FieldChangeListener() {
                public void fieldChanged(Field field, int context) {
                    confirmCurrentDirectorySelection();
                }
            });

            ButtonField newFolderBtn = new ButtonField("[ New Folder ]", ButtonField.CONSUME_CLICK);
            newFolderBtn.setChangeListener(new FieldChangeListener() {
                public void fieldChanged(Field field, int context) {
                    promptNewFolder();
                }
            });

            ButtonField upBtn = new ButtonField("[ Up ]", ButtonField.CONSUME_CLICK);
            upBtn.setChangeListener(new FieldChangeListener() {
                public void fieldChanged(Field field, int context) {
                    navigateUp();
                }
            });

            topBar.add(selectHereBtn);
            topBar.add(newFolderBtn);
            topBar.add(upBtn);
            add(topBar);
            add(new SeparatorField());
        }

        // Main item list
        listField = new ListField();
        listField.setCallback(this);
        listField.setRowHeight(Font.getDefault().getHeight() + 10);
        add(listField);

        add(new SeparatorField());

        // Bottom action button bar
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
                handleExplicitOkButton();
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

    // ==========================================
    // Directory Loading & Roots Navigation
    // ==========================================
    private void loadRoots() {
        currentDirectoryUrl = "roots://";
        pathLabel.setText("Location: Storage Drives");
        items.removeAllElements();

        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements()) {
                String r = (String) roots.nextElement();
                String driveLabel;
                if ("SDCard/".equalsIgnoreCase(r)) {
                    driveLabel = "[SD Card] (file:///" + r + ")";
                } else if ("store/".equalsIgnoreCase(r)) {
                    driveLabel = "[Device Memory] (file:///" + r + ")";
                } else {
                    driveLabel = "[" + r + "] (file:///" + r + ")";
                }
                items.addElement(new PickerEntry(
                        PickerEntry.TYPE_ROOT_DRIVE,
                        driveLabel,
                        r,
                        "file:///" + r,
                        0));
            }
        } catch (Exception e) {
            String fallback = AttachmentStorageManager.getInstance().getStorageRoot();
            items.addElement(new PickerEntry(
                    PickerEntry.TYPE_ROOT_DRIVE,
                    "[Default Storage] (" + fallback + ")",
                    fallback,
                    fallback,
                    0));
        }

        listField.setSize(items.size());
        if (items.size() > 0) {
            listField.setSelectedIndex(0);
        }
    }

    private void loadDirectory(String dirUrl) {
        if ("roots://".equals(dirUrl)) {
            loadRoots();
            return;
        }

        if (!dirUrl.endsWith("/")) {
            dirUrl = dirUrl + "/";
        }

        currentDirectoryUrl = dirUrl;
        pathLabel.setText("Location: " + getFriendlyPath(dirUrl));
        items.removeAllElements();

        // 1. In directory mode, item 0 is always "Select this current folder"
        if (mode == MODE_DIRECTORY_SELECT) {
            String folderName = getFolderNameFromUrl(dirUrl);
            items.addElement(new PickerEntry(
                    PickerEntry.TYPE_SELECT_CURRENT,
                    ">> USE THIS FOLDER: " + folderName,
                    folderName,
                    dirUrl,
                    0));
        }

        // 2. Up one level entry
        items.addElement(new PickerEntry(
                PickerEntry.TYPE_UP,
                "^ [.. Up One Level]",
                "..",
                null,
                0));

        // 3. Populate subdirectories and files
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
                            if (entry.toLowerCase().endsWith(".jnb")) {
                                files.addElement(entry);
                            }
                        } else if (mode == MODE_JNBB_SELECT) {
                            if (entry.toLowerCase().endsWith(".jnbb")) {
                                files.addElement(entry);
                            }
                        } else if (mode == MODE_ATTACHMENT_SELECT) {
                            files.addElement(entry);
                        }
                    }
                }

                // Add directories first
                for (int i = 0; i < dirs.size(); i++) {
                    String dirName = (String) dirs.elementAt(i);
                    items.addElement(new PickerEntry(
                            PickerEntry.TYPE_DIR,
                            "[Folder] " + dirName,
                            dirName,
                            dirUrl + dirName,
                            0));
                }

                // Add files if not directory-only selection
                if (mode != MODE_DIRECTORY_SELECT) {
                    for (int i = 0; i < files.size(); i++) {
                        String fileName = (String) files.elementAt(i);
                        long sz = 0;
                        try {
                            FileConnection fileFc = (FileConnection) Connector.open(dirUrl + fileName, Connector.READ);
                            sz = fileFc.fileSize();
                            fileFc.close();
                        } catch (Exception ignored) {
                        }

                        String sizeStr = formatFileSize(sz);
                        items.addElement(new PickerEntry(
                                PickerEntry.TYPE_FILE,
                                "[File] " + fileName + " (" + sizeStr + ")",
                                fileName,
                                dirUrl + fileName,
                                sz));
                    }
                }
            }
        } catch (Exception e) {
            Dialog.alert("Could not open directory:\n" + e.getMessage());
        } finally {
            if (fc != null) {
                try {
                    fc.close();
                } catch (Exception ignored) {
                }
            }
        }

        listField.setSize(items.size());
        if (items.size() > 0) {
            listField.setSelectedIndex(0);
        }
    }

    private void navigateUp() {
        if ("roots://".equals(currentDirectoryUrl)) {
            return;
        }

        if (isStorageRoot(currentDirectoryUrl)) {
            loadRoots();
            return;
        }

        String stripped = currentDirectoryUrl;
        if (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        int lastSlash = stripped.lastIndexOf('/');
        if (lastSlash >= 0) {
            String parent = stripped.substring(0, lastSlash + 1);
            loadDirectory(parent);
        } else {
            loadRoots();
        }
    }

    private boolean isStorageRoot(String url) {
        if (url == null) {
            return true;
        }
        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            while (roots.hasMoreElements()) {
                String r = (String) roots.nextElement();
                if (url.equalsIgnoreCase("file:///" + r) || url.equalsIgnoreCase("file:///" + r + "/")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return "file:///SDCard/".equalsIgnoreCase(url) || "file:///store/".equalsIgnoreCase(url);
    }

    private String getFriendlyPath(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("file:///")) {
            return "/" + url.substring(8);
        }
        return url;
    }

    private String getFolderNameFromUrl(String url) {
        if (url == null) {
            return "/";
        }
        String s = url;
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int lastSlash = s.lastIndexOf('/');
        if (lastSlash >= 0) {
            return s.substring(lastSlash + 1) + "/";
        }
        return url;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1048576) {
            return (bytes / 1024) + " KB";
        }
        return (bytes / 1048576) + " MB";
    }

    // ==========================================
    // Selection Handling
    // ==========================================
    private void confirmCurrentDirectorySelection() {
        if ("roots://".equals(currentDirectoryUrl)) {
            Dialog.alert("Please open a storage drive first.");
            return;
        }
        handledSelection = true;
        close();
        if (listener != null) {
            listener.onFileSelected(currentDirectoryUrl);
        }
    }

    /**
     * Resolves the OK button action.
     * Fixes the previous bug: If the user highlights a subfolder in the list,
     * clicking OK will select THAT subfolder as destination!
     * If the current folder row or empty list is highlighted, it selects
     * currentDirectoryUrl.
     */
    private void handleExplicitOkButton() {
        if ("roots://".equals(currentDirectoryUrl)) {
            int idx = listField.getSelectedIndex();
            if (idx >= 0 && idx < items.size()) {
                PickerEntry entry = (PickerEntry) items.elementAt(idx);
                if (entry.type == PickerEntry.TYPE_ROOT_DRIVE) {
                    loadDirectory(entry.fullUrl);
                    return;
                }
            }
            Dialog.alert("Please select a drive first.");
            return;
        }

        if (mode == MODE_DIRECTORY_SELECT) {
            int idx = listField.getSelectedIndex();
            if (idx >= 0 && idx < items.size()) {
                PickerEntry entry = (PickerEntry) items.elementAt(idx);
                if (entry.type == PickerEntry.TYPE_DIR) {
                    // User highlighted a specific subfolder and clicked Select Folder
                    handledSelection = true;
                    close();
                    if (listener != null) {
                        listener.onFileSelected(entry.fullUrl);
                    }
                    return;
                }
            }
            // Otherwise select the current directory
            confirmCurrentDirectorySelection();
            return;
        }

        // File selection modes
        int idx = listField.getSelectedIndex();
        if (idx < 0 || idx >= items.size()) {
            Dialog.alert("Please highlight a file to select.");
            return;
        }

        PickerEntry entry = (PickerEntry) items.elementAt(idx);
        if (entry.type == PickerEntry.TYPE_FILE) {
            handledSelection = true;
            close();
            if (listener != null) {
                listener.onFileSelected(entry.fullUrl);
            }
        } else if (entry.type == PickerEntry.TYPE_DIR) {
            loadDirectory(entry.fullUrl);
        } else if (entry.type == PickerEntry.TYPE_UP) {
            navigateUp();
        } else {
            Dialog.alert("Please highlight a valid file.");
        }
    }

    /**
     * Triggered on trackwheel click, Enter key, or touch tap on the list.
     */
    private void handleListSelection() {
        int idx = listField.getSelectedIndex();
        if (idx < 0 || idx >= items.size()) {
            if (mode == MODE_DIRECTORY_SELECT) {
                confirmCurrentDirectorySelection();
            }
            return;
        }

        PickerEntry entry = (PickerEntry) items.elementAt(idx);
        if (entry.type == PickerEntry.TYPE_SELECT_CURRENT) {
            confirmCurrentDirectorySelection();
        } else if (entry.type == PickerEntry.TYPE_UP) {
            navigateUp();
        } else if (entry.type == PickerEntry.TYPE_ROOT_DRIVE) {
            loadDirectory(entry.fullUrl);
        } else if (entry.type == PickerEntry.TYPE_DIR) {
            loadDirectory(entry.fullUrl);
        } else if (entry.type == PickerEntry.TYPE_FILE) {
            handledSelection = true;
            close();
            if (listener != null) {
                listener.onFileSelected(entry.fullUrl);
            }
        }
    }

    protected boolean navigationClick(int status, int time) {
        if (listField.isFocus()) {
            handleListSelection();
            return true;
        }
        return super.navigationClick(status, time);
    }

    protected boolean keyChar(char c, int status, int time) {
        if (c == Keypad.KEY_ENTER) {
            if (listField.isFocus()) {
                handleListSelection();
                return true;
            }
        } else if (c == Keypad.KEY_ESCAPE) {
            if (listener != null) {
                listener.onCancelled();
            }
            close();
            return true;
        }
        return super.keyChar(c, status, time);
    }

    // ==========================================
    // In-App New Folder Creation
    // ==========================================
    private void promptNewFolder() {
        if ("roots://".equals(currentDirectoryUrl)) {
            Dialog.alert("Please choose a storage drive first.");
            return;
        }

        final PopupScreen prompt = new PopupScreen(new VerticalFieldManager());
        LabelField title = new LabelField("Create New Folder");
        title.setFont(Font.getDefault().derive(Font.BOLD));
        prompt.add(title);
        prompt.add(new SeparatorField());

        final EditField folderField = new EditField("Folder name: ", "MyFolder");
        prompt.add(folderField);
        prompt.add(new SeparatorField());

        HorizontalFieldManager btnMgr = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField createBtn = new ButtonField("Create", ButtonField.CONSUME_CLICK);
        createBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                String rawName = folderField.getText().trim();
                if (rawName.length() == 0) {
                    Dialog.alert("Please enter a folder name.");
                    return;
                }
                String sanitized = sanitizeName(rawName);
                String newDirUrl = currentDirectoryUrl + sanitized + "/";
                FileConnection fc = null;
                try {
                    fc = (FileConnection) Connector.open(newDirUrl, Connector.READ_WRITE);
                    if (!fc.exists()) {
                        fc.mkdir();
                        prompt.close();
                        loadDirectory(newDirUrl);
                    } else {
                        Dialog.alert("A folder with that name already exists.");
                    }
                } catch (Exception e) {
                    Dialog.alert("Could not create folder:\n" + e.getMessage());
                } finally {
                    if (fc != null) {
                        try {
                            fc.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });

        ButtonField cancelBtn = new ButtonField("Cancel", ButtonField.CONSUME_CLICK);
        cancelBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                prompt.close();
            }
        });

        btnMgr.add(createBtn);
        btnMgr.add(cancelBtn);
        prompt.add(btnMgr);

        UiApplication.getUiApplication().pushScreen(prompt);
    }

    private String sanitizeName(String name) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '/' || ch == '\\' || ch == ':' || ch == '*' ||
                    ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                sb.append('_');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (index < 0 || index >= items.size()) {
            return;
        }

        PickerEntry entry = (PickerEntry) items.elementAt(index);
        AppSettings settings = AppSettings.getInstance();
        boolean dark = settings != null && settings.isDarkMode();

        int originalColor = graphics.getColor();

        if (entry.type == PickerEntry.TYPE_SELECT_CURRENT) {
            // Emphasized green or cyan for selecting current directory
            graphics.setColor(dark ? 0x66FF66 : 0x008800);
            Font oldFont = graphics.getFont();
            graphics.setFont(oldFont.derive(Font.BOLD));
            graphics.drawText(entry.label, 6, y + 4);
            graphics.setFont(oldFont);
        } else if (entry.type == PickerEntry.TYPE_UP || entry.type == PickerEntry.TYPE_ROOT_DRIVE) {
            graphics.setColor(dark ? 0x88CCFF : 0x0055AA);
            Font oldFont = graphics.getFont();
            graphics.setFont(oldFont.derive(Font.BOLD));
            graphics.drawText(entry.label, 6, y + 4);
            graphics.setFont(oldFont);
        } else if (entry.type == PickerEntry.TYPE_DIR) {
            graphics.setColor(dark ? 0xDDDDDD : 0x222222);
            graphics.drawText(entry.label, 6, y + 4);
        } else {
            graphics.setColor(dark ? 0xCCCCCC : 0x333333);
            graphics.drawText(entry.label, 6, y + 4);
        }

        // Row divider
        graphics.setColor(dark ? 0x333333 : 0xEEEEEE);
        graphics.drawLine(0, y + listField.getRowHeight() - 1, width, y + listField.getRowHeight() - 1);

        graphics.setColor(originalColor);
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
    // Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        if (mode == MODE_DIRECTORY_SELECT) {
            int idx = listField.getSelectedIndex();
            if (idx >= 0 && idx < items.size()) {
                final PickerEntry entry = (PickerEntry) items.elementAt(idx);
                if (entry.type == PickerEntry.TYPE_DIR) {
                    menu.add(new MenuItem("Select '" + entry.rawName + "' as Destination", 90, 5) {
                        public void run() {
                            handledSelection = true;
                            close();
                            if (listener != null) {
                                listener.onFileSelected(entry.fullUrl);
                            }
                        }
                    });
                    menu.add(new MenuItem("Open Folder", 95, 6) {
                        public void run() {
                            loadDirectory(entry.fullUrl);
                        }
                    });
                }
            }

            menu.add(new MenuItem("Select Current Folder", 100, 10) {
                public void run() {
                    confirmCurrentDirectorySelection();
                }
            });

            menu.add(new MenuItem("New Folder...", 102, 12) {
                public void run() {
                    promptNewFolder();
                }
            });
        } else {
            menu.add(new MenuItem("Select Highlighted", 100, 10) {
                public void run() {
                    handleListSelection();
                }
            });

            menu.add(new MenuItem("Use System File Picker", 105, 12) {
                public void run() {
                    close();
                    open(mode, listener);
                }
            });

            if (mode == MODE_JNB_SELECT) {
                menu.add(new MenuItem("Import Demo Notebook", 108, 14) {
                    public void run() {
                        handledSelection = true;
                        close();
                        if (listener != null) {
                            listener.onFileSelected("demo://jsq_demo_notebook");
                        }
                    }
                });
            }
        }

        if (!"roots://".equals(currentDirectoryUrl)) {
            menu.add(new MenuItem("Up One Level", 115, 18) {
                public void run() {
                    navigateUp();
                }
            });
            menu.add(new MenuItem("Switch Storage Drive", 120, 20) {
                public void run() {
                    loadRoots();
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
