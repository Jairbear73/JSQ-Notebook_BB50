package com.jsq.notebook.ui;

import com.jsq.notebook.export.PageExportManager;
import com.jsq.notebook.model.AppSettings;
import com.jsq.notebook.model.CustomField;
import com.jsq.notebook.model.FieldType;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Page;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.AttachmentStorageManager;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.DateUtils;
import com.jsq.notebook.util.StringUtils;
import net.rim.device.api.system.KeypadListener;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Manager;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.EditField;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;
import net.rim.device.api.ui.component.AutoTextEditField;

import java.io.IOException;
import java.util.Vector;

/**
 * Dual-Mode screen for viewing rich rendered notes and high-speed editing.
 * View Mode: Renders rich formatting and inline images directly.
 * Edit Mode: Text placeholders for images, zero keystroke latency, formatting
 * menu & image attachment.
 */
public class PageDetailScreen extends MainScreen {

    private Notebook notebook;
    private Section section;
    private Page page;
    private boolean isEditMode;

    // View mode containers
    private VerticalFieldManager viewContentManager;

    // Edit mode components
    private VerticalFieldManager editContentManager;
    private AutoTextEditField titleEditField;
    private AutoTextEditField notesEditField;
    private Vector customEditFields; // Vector of EditField

    /** Source line index (in page notes) that had focus when last in view mode. */
    private int viewFocusedSourceLine = 0;

    public PageDetailScreen(Notebook notebook, Section section, Page page, boolean startInEditMode) {
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        this.notebook = notebook;
        this.section = section;
        this.page = page;
        this.isEditMode = startInEditMode;

        // Ensure page has all inherited section template fields
        section.applyTemplateToPage(page);

        customEditFields = new Vector();
        switchMode(this.isEditMode);
    }

    public void switchMode(boolean editMode) {
        // If we are currently leaving Edit Mode, capture the cursor's source line first
        if (this.isEditMode && !editMode && notesEditField != null) {
            String text = notesEditField.getText();
            int cursor = notesEditField.getCursorPosition();
            int lineIdx = 0;
            for (int i = 0; i < cursor && i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lineIdx++;
                }
            }
            viewFocusedSourceLine = lineIdx;
        }

        this.isEditMode = editMode;
        deleteAll();
        Manager mgr = getMainManager();
        if (mgr != null) {
            mgr.setVerticalScroll(0);
        }

        if (isEditMode) {
            renderEditMode();
        } else {
            renderViewMode();
        }
        clearDirty();
    }

    protected void paintBackground(Graphics g) {
        if (!isEditMode && AppSettings.getInstance().isDarkMode()) {
            int old = g.getColor();
            g.setColor(0x000000);
            g.fillRect(0, 0, Math.max(getWidth(), Graphics.getScreenWidth()),
                    Math.max(getHeight(), Graphics.getScreenHeight()));
            g.setColor(old);
        } else {
            super.paintBackground(g);
        }
    }

    // ==========================================
    // VIEW MODE
    // ==========================================
    private void renderViewMode() {
        // setTitle("Page: " + page.getTitle());

        viewContentManager = new VerticalFieldManager(Manager.USE_ALL_WIDTH) {
            protected void paintBackground(Graphics g) {
                if (AppSettings.getInstance().isDarkMode()) {
                    int old = g.getColor();
                    g.setColor(0x000000);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(old);
                } else {
                    super.paintBackground(g);
                }
            }
        };

        // Page title (Heading 1 style, reversed intensity in Dark Mode)
        RichTextParser.ThemedLabelField titleLabel = new RichTextParser.ThemedLabelField(
                page.getTitle(),
                Font.getDefault().derive(Font.BOLD, Font.getDefault().getHeight() + 2,
                        net.rim.device.api.ui.Ui.UNITS_px),
                LabelField.FOCUSABLE,
                0x1A5276, // Dark blue in light mode
                0x90CDF4 // Light sky blue in dark mode
        );
        viewContentManager.add(titleLabel);

        // Timestamps (if enabled in settings)
        if (AppSettings.getInstance().isShowMetadata()) {
            RichTextParser.ThemedLabelField metaLabel = new RichTextParser.ThemedLabelField(
                    "Updated: " + DateUtils.formatDateTime(page.getModifiedTime()),
                    Font.getDefault().derive(Font.ITALIC),
                    LabelField.FOCUSABLE,
                    0x7F8C8D, // Muted dark slate in light mode
                    0xA0AEC0 // Muted light slate in dark mode
            );
            viewContentManager.add(metaLabel);
            viewContentManager.add(new RichTextParser.ThemedSeparatorField());
        }

        // Custom Fields summary
        Vector cfs = page.getCustomFields();
        int cfCount = cfs != null ? cfs.size() : 0;
        if (cfCount > 0) {
            VerticalFieldManager metaBox = new VerticalFieldManager();
            for (int i = 0; i < cfCount; i++) {
                CustomField cf = (CustomField) cfs.elementAt(i);
                String val = cf.getValue();
                if (val.length() == 0) {
                    val = "(empty)";
                }
                RichTextParser.ThemedLabelField cfLabel = new RichTextParser.ThemedLabelField(
                        cf.getLabel() + ": " + val,
                        Font.getDefault(),
                        LabelField.FOCUSABLE,
                        0x333333, // Dark in light mode
                        0xE2E8F0 // Light off-white in dark mode
                );
                metaBox.add(cfLabel);
            }
            viewContentManager.add(metaBox);
            viewContentManager.add(new RichTextParser.ThemedSeparatorField());
        }

        // Render rich notes and inline images with live interactive tasks
        RichTextParser.renderToManager(page, page.getNotes(), viewContentManager, new Runnable() {
            public void run() {
                saveEdits();
            }
        });

        // Add manager to screen FIRST so fields have a valid screen reference
        add(viewContentManager);

        // Restore focus to the corresponding source line field in view mode
        if (viewFocusedSourceLine > 0) {
            int fieldCount = viewContentManager.getFieldCount();
            for (int i = 0; i < fieldCount; i++) {
                Field f = viewContentManager.getField(i);
                int fLine = getSrcLineOfFocusedField(f);
                if (fLine == viewFocusedSourceLine) {
                    f.setFocus();
                    break;
                }
            }
        }
    }

    // ==========================================
    // EDIT MODE
    // ==========================================
    private void renderEditMode() {
        // setTitle("Editing: " + page.getTitle());

        editContentManager = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
        customEditFields.removeAllElements();

        // Title edit field
        titleEditField = new AutoTextEditField("Title: ", page.getTitle(), 100, EditField.FILTER_DEFAULT);
        editContentManager.add(titleEditField);
        editContentManager.add(new SeparatorField());

        // Custom fields
        Vector cfs = page.getCustomFields();
        int cfCount = cfs != null ? cfs.size() : 0;
        for (int i = 0; i < cfCount; i++) {
            CustomField cf = (CustomField) cfs.elementAt(i);
            AutoTextEditField ef = new AutoTextEditField(cf.getLabel() + ": ", cf.getValue(), 200,
                    EditField.NO_COMPLEX_INPUT);
            customEditFields.addElement(ef);
            editContentManager.add(ef);
        }

        if (cfCount > 0) {
            editContentManager.add(new SeparatorField());
        }

        // Full screen Notes editor
        // LabelField notesHeader = new LabelField("Notes Content (Maximum Screen
        // Area):");
        // notesHeader.setFont(Font.getDefault().derive(Font.BOLD));
        // editContentManager.add(notesHeader);

        notesEditField = new AutoTextEditField("", page.getNotes(), 100000, EditField.FILTER_DEFAULT);
        editContentManager.add(notesEditField);

        // Add manager to screen FIRST so the focus chain is valid
        add(editContentManager);

        // Restore cursor to the notes line that was focused in view mode (deferred to
        // ensure layout is ready)
        if (viewFocusedSourceLine > 0 && page.getNotes() != null && page.getNotes().length() > 0) {
            final int targetLine = viewFocusedSourceLine;
            UiApplication.getUiApplication().invokeLater(new Runnable() {
                public void run() {
                    if (notesEditField != null && page.getNotes() != null) {
                        Vector notesLines = StringUtils.splitLines(page.getNotes());
                        int offset = 0;
                        for (int i = 0; i < targetLine && i < notesLines.size(); i++) {
                            offset += ((String) notesLines.elementAt(i)).length() + 1; // +1 for newline
                        }
                        if (offset > page.getNotes().length()) {
                            offset = page.getNotes().length();
                        }
                        notesEditField.setCursorPosition(offset);
                        notesEditField.setFocus();
                    }
                }
            });
        }
    }

    /**
     * Commits all field values from edit controls to the Page model and persistent
     * store.
     */
    private void saveEdits() {
        if (!isEditMode) {
            return;
        }

        if (titleEditField != null) {
            String newTitle = titleEditField.getText().trim();
            if (newTitle.length() > 0) {
                page.setTitle(newTitle);
            }
        }

        if (notesEditField != null) {
            page.setNotes(notesEditField.getText());
        }

        Vector cfs = page.getCustomFields();
        int count = Math.min(cfs.size(), customEditFields.size());
        for (int i = 0; i < count; i++) {
            CustomField cf = (CustomField) cfs.elementAt(i);
            EditField ef = (EditField) customEditFields.elementAt(i);
            cf.setValue(ef.getText());
        }

        page.setModifiedTime(System.currentTimeMillis());
        NotebookStoreManager.getInstance().commit();
        clearDirty();
    }

    private void clearDirty() {
        setDirty(false);
        if (titleEditField != null) {
            titleEditField.setDirty(false);
        }
        if (notesEditField != null) {
            notesEditField.setDirty(false);
        }
        if (customEditFields != null) {
            for (int i = 0; i < customEditFields.size(); i++) {
                Field f = (Field) customEditFields.elementAt(i);
                if (f != null) {
                    f.setDirty(false);
                }
            }
        }
    }

    // ==========================================
    // TEXT FORMATTING & IMAGE ATTACHMENT
    // ==========================================
    private String getLineBeforeCursor() {
        if (notesEditField == null) {
            return "";
        }
        String fullText = notesEditField.getText();
        if (fullText == null || fullText.length() == 0) {
            return "";
        }
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > fullText.length()) {
            cursor = fullText.length();
        }
        int lineStart = fullText.lastIndexOf('\n', cursor - 1);
        if (lineStart < 0) {
            lineStart = 0;
        } else {
            lineStart = lineStart + 1;
        }
        return fullText.substring(lineStart, cursor);
    }

    private boolean isBoldActive() {
        String line = getLineBeforeCursor();
        int count = 0;
        int idx = 0;
        while ((idx = line.indexOf("**", idx)) >= 0) {
            count++;
            idx += 2;
        }
        return (count % 2 == 1);
    }

    private boolean isItalicActive() {
        String line = getLineBeforeCursor();
        String withoutBold = StringUtils.replace(line, "**", "");
        int count = 0;
        int idx = 0;
        while ((idx = withoutBold.indexOf('*', idx)) >= 0) {
            count++;
            idx += 1;
        }
        return (count % 2 == 1);
    }

    private void insertAtCursor(String str) {
        if (notesEditField == null || str == null) {
            return;
        }
        String fullText = notesEditField.getText();
        if (fullText == null) {
            fullText = "";
        }
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > fullText.length()) {
            cursor = fullText.length();
        }

        StringBuffer sb = new StringBuffer();
        sb.append(fullText.substring(0, cursor));
        sb.append(str);
        sb.append(fullText.substring(cursor));

        notesEditField.setText(sb.toString());
        notesEditField.setCursorPosition(cursor + str.length());
    }

    private void toggleBold() {
        if (!isEditMode || notesEditField == null) {
            return;
        }

        String fullText = notesEditField.getText();
        if (fullText == null) {
            fullText = "";
        }
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > fullText.length()) {
            cursor = fullText.length();
        }

        boolean active = isBoldActive();
        if (active) {
            // Bold is currently ON.
            // If the user immediately toggles it off without typing anything (cursor right
            // after "**"), undo it
            if (cursor >= 2 && fullText.substring(cursor - 2, cursor).equals("**")) {
                StringBuffer sb = new StringBuffer();
                sb.append(fullText.substring(0, cursor - 2));
                sb.append(fullText.substring(cursor));
                notesEditField.setText(sb.toString());
                notesEditField.setCursorPosition(cursor - 2);
                return;
            }
            insertAtCursor("**");
        } else {
            // Bold is currently OFF. Turn it ON from this point forward.
            insertAtCursor("**");
        }
    }

    private void toggleItalic() {
        if (!isEditMode || notesEditField == null) {
            return;
        }

        String fullText = notesEditField.getText();
        if (fullText == null) {
            fullText = "";
        }
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > fullText.length()) {
            cursor = fullText.length();
        }

        boolean active = isItalicActive();
        if (active) {
            // Italic is currently ON.
            // If immediately toggled off without typing anything, undo it
            if (cursor >= 1 && fullText.charAt(cursor - 1) == '*') {
                boolean precededBySecondStar = (cursor >= 2 && fullText.charAt(cursor - 2) == '*');
                if (!precededBySecondStar) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(fullText.substring(0, cursor - 1));
                    sb.append(fullText.substring(cursor));
                    notesEditField.setText(sb.toString());
                    notesEditField.setCursorPosition(cursor - 1);
                    return;
                }
            }
            insertAtCursor("*");
        } else {
            // Italic is currently OFF. Turn it ON from this point forward.
            insertAtCursor("*");
        }
    }

    private void toggleHeading(String prefix) {
        if (!isEditMode || notesEditField == null) {
            return;
        }
        String fullText = notesEditField.getText();
        if (fullText == null) {
            fullText = "";
        }
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0) {
            cursor = 0;
        }
        if (cursor > fullText.length()) {
            cursor = fullText.length();
        }

        int lineStart = fullText.lastIndexOf('\n', cursor - 1);
        if (lineStart < 0) {
            lineStart = 0;
        } else {
            lineStart = lineStart + 1;
        }

        String afterStart = fullText.substring(lineStart);
        if (afterStart.startsWith(prefix)) {
            // Remove heading prefix
            StringBuffer sb = new StringBuffer();
            sb.append(fullText.substring(0, lineStart));
            sb.append(afterStart.substring(prefix.length()));
            notesEditField.setText(sb.toString());
            int newCursor = Math.max(lineStart, cursor - prefix.length());
            notesEditField.setCursorPosition(newCursor);
        } else {
            int existingPrefixLen = 0;
            if (afterStart.startsWith("### "))
                existingPrefixLen = 4;
            else if (afterStart.startsWith("## "))
                existingPrefixLen = 3;
            else if (afterStart.startsWith("# "))
                existingPrefixLen = 2;

            StringBuffer sb = new StringBuffer();
            sb.append(fullText.substring(0, lineStart));
            sb.append(prefix);
            sb.append(afterStart.substring(existingPrefixLen));
            notesEditField.setText(sb.toString());
            int newCursor = cursor + prefix.length() - existingPrefixLen;
            if (newCursor < lineStart + prefix.length()) {
                newCursor = lineStart + prefix.length();
            }
            notesEditField.setCursorPosition(newCursor);
        }
    }

    private void promptAttachFile() {
        if (!isEditMode) {
            Dialog.alert("Please switch to Edit Mode to attach a file.");
            return;
        }

        FilePickerPopup.open(FilePickerPopup.MODE_ATTACHMENT_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(final String selectedUrl) {
                        int lastSlash = selectedUrl.lastIndexOf('/');
                        String displayName = lastSlash >= 0 ? selectedUrl.substring(lastSlash + 1) : selectedUrl;

                        String[] options = new String[] { "Copy to Notebook", "Link to Original", "Cancel" };
                        int choice = Dialog.ask(
                                "Attach File:\n" + displayName + "\n\nHow would you like to attach this file?", options,
                                0);

                        if (choice == 0) {
                            // 1. Copy into Notebook attachments directory
                            try {
                                String savedFileName = AttachmentStorageManager.getInstance()
                                        .importAttachmentFromUrl(selectedUrl);
                                if (AttachmentStorageManager.isImageFile(savedFileName)) {
                                    insertPlaceholder("[Image: " + savedFileName + "]");
                                } else {
                                    insertPlaceholder("[Attachment: " + savedFileName + "]");
                                }
                                Dialog.alert("Attached & copied:\n" + savedFileName);
                            } catch (IOException ioe) {
                                Dialog.alert("Failed to copy attachment: " + ioe.getMessage());
                            }
                        } else if (choice == 1) {
                            // 2. Link directly to original location
                            if (AttachmentStorageManager.isImageFile(selectedUrl)) {
                                insertPlaceholder("[Image: " + selectedUrl + "]");
                            } else {
                                insertPlaceholder("[Attachment: " + selectedUrl + "]");
                            }
                            Dialog.alert("Linked to:\n" + displayName);
                        }
                    }

                    public void onCancelled() {
                    }
                });
    }

    private void insertPlaceholder(String tag) {
        if (notesEditField == null) {
            return;
        }

        String fullTag = "\n" + tag + "\n";
        int cursor = notesEditField.getCursorPosition();
        String text = notesEditField.getText();
        if (cursor < 0)
            cursor = text.length();

        StringBuffer sb = new StringBuffer();
        sb.append(text.substring(0, cursor));
        sb.append(fullTag);
        sb.append(text.substring(cursor));

        notesEditField.setText(sb.toString());
        notesEditField.setCursorPosition(cursor + fullTag.length());
    }

    /**
     * Returns true if the given field is an interactive element that should
     * receive clicks in view mode (buttons, checkboxes, focusable bitmaps).
     * Plain LabelFields and other non-interactive fields return false.
     */
    private boolean isInteractiveViewField(Field f) {
        if (f == null) {
            return false;
        }
        return (f instanceof net.rim.device.api.ui.component.ButtonField)
                || (f instanceof net.rim.device.api.ui.component.CheckboxField)
                || (f instanceof net.rim.device.api.ui.component.BitmapField);
    }

    protected boolean navigationClick(int status, int time) {
        if (!isEditMode) {
            // Walk up the focus hierarchy: the deepest focused field tells us
            // whether the user clicked on an interactive element or plain text.
            Field focused = null;
            Manager mgr = getMainManager();
            while (mgr != null) {
                int idx = mgr.getFieldWithFocusIndex();
                if (idx < 0)
                    break;
                Field child = mgr.getField(idx);
                if (child instanceof Manager) {
                    mgr = (Manager) child;
                } else {
                    focused = child;
                    break;
                }
            }

            if (focused instanceof RichTextParser.TableField) {
                RichTextParser.TableField tf = (RichTextParser.TableField) focused;
                if (tf.handleTableClick()) {
                    return true;
                }
                viewFocusedSourceLine = tf.getFocusedSourceLine();
                switchMode(true);
                return true;
            }

            if (isInteractiveViewField(focused)) {
                // Fire the interactive element (button, checkbox, image)
                return super.navigationClick(status, time);
            }
            // Plain text area clicked — record view focus line then enter edit mode
            viewFocusedSourceLine = getSrcLineOfFocusedField(focused);
            switchMode(true);
            return true;
        }
        return super.navigationClick(status, time);
    }

    /**
     * Extracts the source line index from a focused view-mode field.
     * Returns 0 if the field type is unknown or untagged.
     */
    private int getSrcLineOfFocusedField(Field f) {
        if (f instanceof RichTextParser.ThemedLabelField) {
            int sl = ((RichTextParser.ThemedLabelField) f).srcLine;
            return sl >= 0 ? sl : 0;
        }
        if (f instanceof RichTextParser.TableField) {
            int sl = ((RichTextParser.TableField) f).getFocusedSourceLine();
            return sl >= 0 ? sl : 0;
        }
        if (f instanceof RichTextParser.BlockquoteField) {
            int sl = ((RichTextParser.BlockquoteField) f).srcLine;
            return sl >= 0 ? sl : 0;
        }
        if (f instanceof RichTextParser.CodeBlockField) {
            int sl = ((RichTextParser.CodeBlockField) f).srcLine;
            return sl >= 0 ? sl : 0;
        }
        if (f instanceof RichTextParser.RichParagraphField) {
            int sl = ((RichTextParser.RichParagraphField) f).srcLine;
            return sl >= 0 ? sl : 0;
        }
        return 0;
    }

    private void promptAddPageSpecificField() {
        final EditField labelField = new EditField("Field Name: ", "");
        final ObjectChoiceField typeChoice = new ObjectChoiceField("Type: ", FieldType.getAllTypeNames(), 0);
        final EditField valField = new EditField("Initial Value: ", "");

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Add Field", 100, 10) {
                    public void run() {
                        if (savePageSpecificField(labelField, typeChoice, valField)) {
                            close();
                        }
                    }
                });
                menu.add(new MenuItem("Cancel", 200, 20) {
                    public void run() {
                        close();
                    }
                });
            }
        };
        popup.add(new LabelField("Add Field to this Page"));
        popup.add(new SeparatorField());
        popup.add(labelField);
        popup.add(typeChoice);
        popup.add(valField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (savePageSpecificField(labelField, typeChoice, valField)) {
                    popup.close();
                }
            }
        });
        ButtonField cancelBtn = new ButtonField("Cancel", ButtonField.CONSUME_CLICK);
        cancelBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                popup.close();
            }
        });
        buttonBar.add(okBtn);
        buttonBar.add(cancelBtn);
        popup.add(buttonBar);

        UiApplication.getUiApplication().pushScreen(popup);
    }

    private boolean savePageSpecificField(EditField labelField, ObjectChoiceField typeChoice, EditField valField) {
        String label = labelField.getText().trim();
        if (label.length() == 0) {
            Dialog.alert("Please enter a field name.");
            return false;
        }

        saveEdits();
        String key = "page_" + System.currentTimeMillis();
        int type = typeChoice.getSelectedIndex();
        String val = valField.getText();

        CustomField cf = new CustomField(key, label, type, val, true);
        page.addCustomField(cf);
        NotebookStoreManager.getInstance().commit();

        switchMode(true);
        return true;
    }

    private void performExport() {
        saveEdits();
        int choice = Dialog.ask(Dialog.D_YES_NO,
                "Export to default SD Card folder?\n(Select 'No' to choose custom directory)");
        if (choice == Dialog.YES) {
            try {
                String destFolder = PageExportManager.getInstance().exportPage(notebook, section, page);
                Dialog.alert("Page exported successfully to:\n" + destFolder);
            } catch (Exception e) {
                Dialog.alert("Export error: " + e.getMessage());
            }
        } else {
            FilePickerPopup.open(FilePickerPopup.MODE_DIRECTORY_SELECT,
                    new FilePickerPopup.FilePickerListener() {
                        public void onFileSelected(String selectedUrl) {
                            try {
                                String destFolder = PageExportManager.getInstance().exportPageTo(notebook, section,
                                        page, selectedUrl);
                                Dialog.alert("Page exported successfully to:\n" + destFolder);
                            } catch (Exception e) {
                                Dialog.alert("Export error: " + e.getMessage());
                            }
                        }

                        public void onCancelled() {
                        }
                    });
        }
    }

    // ==========================================
    // PHYSICAL MENU INTEGRATION
    // ==========================================

    /**
     * Emulates a submenu for formatting options using a ListField-based
     * PopupScreen.
     * This is the correct OS 5 approach — the SubMenu class only exists in OS 6+.
     * The popup renders like a native menu list: each row is a selectable action,
     * trackball click or BB Menu selects and executes it, Escape dismisses.
     */
    private boolean isDelimiterActive(String delim) {
        String line = getLineBeforeCursor();
        int count = 0;
        int idx = 0;
        int dLen = delim.length();
        while ((idx = line.indexOf(delim, idx)) >= 0) {
            count++;
            idx += dLen;
        }
        return (count % 2 == 1);
    }

    private void toggleDelimiter(String delim) {
        if (!isEditMode || notesEditField == null) {
            return;
        }
        String fullText = notesEditField.getText();
        if (fullText == null)
            fullText = "";
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0)
            cursor = 0;
        if (cursor > fullText.length())
            cursor = fullText.length();

        int dLen = delim.length();
        boolean active = isDelimiterActive(delim);
        if (active) {
            if (cursor >= dLen && fullText.substring(cursor - dLen, cursor).equals(delim)) {
                StringBuffer sb = new StringBuffer();
                sb.append(fullText.substring(0, cursor - dLen));
                sb.append(fullText.substring(cursor));
                notesEditField.setText(sb.toString());
                notesEditField.setCursorPosition(cursor - dLen);
                return;
            }
            insertAtCursor(delim);
        } else {
            insertAtCursor(delim);
        }
    }

    private void toggleLinePrefix(String prefix) {
        if (!isEditMode || notesEditField == null) {
            return;
        }
        String fullText = notesEditField.getText();
        if (fullText == null)
            fullText = "";
        int cursor = notesEditField.getCursorPosition();
        if (cursor < 0)
            cursor = 0;
        if (cursor > fullText.length())
            cursor = fullText.length();

        int lineStart = fullText.lastIndexOf('\n', cursor - 1);
        if (lineStart < 0) {
            lineStart = 0;
        } else {
            lineStart = lineStart + 1;
        }

        String afterStart = fullText.substring(lineStart);
        if (afterStart.startsWith(prefix)) {
            StringBuffer sb = new StringBuffer();
            sb.append(fullText.substring(0, lineStart));
            sb.append(afterStart.substring(prefix.length()));
            notesEditField.setText(sb.toString());
            int newCursor = Math.max(lineStart, cursor - prefix.length());
            notesEditField.setCursorPosition(newCursor);
        } else {
            int existingPrefixLen = 0;
            if (afterStart.startsWith("#### "))
                existingPrefixLen = 5;
            else if (afterStart.startsWith("### "))
                existingPrefixLen = 4;
            else if (afterStart.startsWith("## "))
                existingPrefixLen = 3;
            else if (afterStart.startsWith("# "))
                existingPrefixLen = 2;
            else if (afterStart.startsWith("- [ ] "))
                existingPrefixLen = 6;
            else if (afterStart.startsWith("- [x] "))
                existingPrefixLen = 6;
            else if (afterStart.startsWith("[ ] "))
                existingPrefixLen = 4;
            else if (afterStart.startsWith("[x] "))
                existingPrefixLen = 4;
            else if (afterStart.startsWith("[!]: "))
                existingPrefixLen = 5;
            else if (afterStart.startsWith("[?]: "))
                existingPrefixLen = 5;
            else if (afterStart.startsWith("[*]: "))
                existingPrefixLen = 5;
            else if (afterStart.startsWith("- "))
                existingPrefixLen = 2;
            else if (afterStart.startsWith("* "))
                existingPrefixLen = 2;
            else if (afterStart.startsWith("> "))
                existingPrefixLen = 2;

            StringBuffer sb = new StringBuffer();
            sb.append(fullText.substring(0, lineStart));
            sb.append(prefix);
            sb.append(afterStart.substring(existingPrefixLen));
            notesEditField.setText(sb.toString());
            int newCursor = cursor + prefix.length() - existingPrefixLen;
            if (newCursor < lineStart + prefix.length()) {
                newCursor = lineStart + prefix.length();
            }
            notesEditField.setCursorPosition(newCursor);
        }
    }

    private void promptFormatMenu() {
        if (!isEditMode || notesEditField == null) {
            return;
        }

        final boolean boldOn = isDelimiterActive("**");
        final boolean italicOn = isItalicActive();
        final boolean underlineOn = isDelimiterActive("__");
        final boolean strikeOn = isDelimiterActive("~~");
        final boolean highlightOn = isDelimiterActive("==");
        final boolean codeOn = isDelimiterActive("`");

        // Build labels array with complete OneNote formatting options
        final String[] labels = {
                boldOn ? "Bold: Turn OFF (**)" : "Bold: Turn ON (**)",
                italicOn ? "Italic: Turn OFF (*)" : "Italic: Turn ON (*)",
                underlineOn ? "Underline: Turn OFF (__)" : "Underline: Turn ON (__)",
                strikeOn ? "Strikethrough: Turn OFF (~~)" : "Strikethrough: Turn ON (~~)",
                highlightOn ? "Highlight: Turn OFF (==)" : "Highlight: Turn ON (==)",
                codeOn ? "Inline Code: Turn OFF (`)" : "Inline Code: Turn ON (`)",
                "To-Do Checkbox  ([ ] ...)",
                "Completed Task  ([x] ...)",
                "Bullet List  (- ...)",
                "Numbered List  (1. ...)",
                "Heading 1  (# ...)",
                "Heading 2  (## ...)",
                "Heading 3  (### ...)",
                "Heading 4  (#### ...)",
                "Insert Table Template",
                "Callout / Quote  (* ...)",
                "Insert Code Block (```)",
                "Horizontal Divider (---)",
                "Tag: Important  ([!]: ...)",
                "Tag: Question  ([?]: ...)",
                "Tag: Star  ([*]: ...)"
        };

        final net.rim.device.api.ui.component.ListField list = new net.rim.device.api.ui.component.ListField(
                labels.length);

        list.setCallback(new net.rim.device.api.ui.component.ListFieldCallback() {
            public void drawListRow(net.rim.device.api.ui.component.ListField lf,
                    net.rim.device.api.ui.Graphics g,
                    int index, int y, int width) {
                if (index >= 0 && index < labels.length) {
                    g.drawText(labels[index], 6, y + 2);
                }
            }

            public Object get(net.rim.device.api.ui.component.ListField lf, int index) {
                return index >= 0 && index < labels.length ? labels[index] : null;
            }

            public int getPreferredWidth(net.rim.device.api.ui.component.ListField lf) {
                return net.rim.device.api.ui.Graphics.getScreenWidth();
            }

            public int indexOfList(net.rim.device.api.ui.component.ListField lf,
                    String prefix, int start) {
                return -1;
            }
        });
        list.setRowHeight(Font.getDefault().getHeight() + 6);
        list.setSelectedIndex(0);

        final PopupScreen popup = new PopupScreen(
                new VerticalFieldManager(VerticalFieldManager.VERTICAL_SCROLL |
                        VerticalFieldManager.VERTICAL_SCROLLBAR)) {
            protected void makeMenu(Menu menu, int inst) {
                menu.add(new MenuItem("Select", 100, 10) {
                    public void run() {
                        applyFormatChoice(list.getSelectedIndex());
                        close();
                    }
                });
                menu.add(new MenuItem("Cancel", 200, 20) {
                    public void run() {
                        close();
                    }
                });
            }

            protected boolean navigationClick(int status, int time) {
                applyFormatChoice(list.getSelectedIndex());
                close();
                return true;
            }
        };

        LabelField title = new LabelField("Format");
        title.setFont(Font.getDefault().derive(Font.BOLD));
        popup.add(title);
        popup.add(new SeparatorField());
        popup.add(list);

        UiApplication.getUiApplication().pushScreen(popup);
    }

    /**
     * Executes the formatting action identified by its index in the Format popup
     * list.
     */
    private void applyFormatChoice(int index) {
        switch (index) {
            case 0:
                toggleBold();
                break;
            case 1:
                toggleItalic();
                break;
            case 2:
                toggleDelimiter("__");
                break;
            case 3:
                toggleDelimiter("~~");
                break;
            case 4:
                toggleDelimiter("==");
                break;
            case 5:
                toggleDelimiter("`");
                break;
            case 6:
                toggleLinePrefix("[ ] ");
                break;
            case 7:
                toggleLinePrefix("[x] ");
                break;
            case 8:
                toggleLinePrefix("- ");
                break;
            case 9:
                toggleLinePrefix("1. ");
                break;
            case 10:
                toggleHeading("# ");
                break;
            case 11:
                toggleHeading("## ");
                break;
            case 12:
                toggleHeading("### ");
                break;
            case 13:
                toggleHeading("#### ");
                break;
            case 14:
                insertPlaceholder("| Header 1 | Header 2 |\n|---|---|\n| Item 1 | Item 2 |");
                break;
            case 15:
                toggleLinePrefix("* ");
                break;
            case 16:
                insertPlaceholder("```\n// Code snippet\n```");
                break;
            case 17:
                insertPlaceholder("---");
                break;
            case 18:
                toggleLinePrefix("[!]: ");
                break;
            case 19:
                toggleLinePrefix("[?]: ");
                break;
            case 20:
                toggleLinePrefix("[*]: ");
                break;
            default:
                break;
        }
    }

    /**
     * makeMenu:
     * instance == Menu.INSTANCE_CONTEXT (trackball quick-press) -> Save only
     * instance == 0 (Menu button) -> full menu
     */
    protected void makeMenu(Menu menu, int instance) {
        // Quick/context menu: Save only while editing
        if (instance == Menu.INSTANCE_CONTEXT) {
            if (isEditMode) {
                menu.add(new MenuItem("Save", 100, 10) {
                    public void run() {
                        saveEdits();
                    }
                });
                menu.add(new MenuItem("Save & View", 105, 15) {
                    public void run() {
                        saveEdits();
                        switchMode(false);
                    }
                });
            }
            return;
        }

        // Full menu
        if (isEditMode) {
            menu.add(new MenuItem("Save & View", 105, 15) {
                public void run() {
                    saveEdits();
                    switchMode(false);
                }
            });
            menu.add(new MenuItem("Save", 100, 10) {
                public void run() {
                    saveEdits();
                }
            });
            menu.add(new MenuItem("Insert Attachment", 200, 30) {
                public void run() {
                    promptAttachFile();
                }
            });
            menu.add(new MenuItem("Format...", 110, 20) {
                public void run() {
                    promptFormatMenu();
                }
            });
            menu.add(new MenuItem("Customize Page", 300, 40) {
                public void run() {
                    promptAddPageSpecificField();
                }
            });
            menu.add(new MenuItem("Cancel Changes", 450, 85) {
                public void run() {
                    switchMode(false);
                }
            });
            menu.add(new MenuItem("Options...", 400, 80) {
                public void run() {
                    UiApplication.getUiApplication().pushScreen(new OptionsDialog(new Runnable() {
                        public void run() {
                            if (!isEditMode) {
                                switchMode(false);
                            }
                        }
                    }));
                }
            });
        } else {
            menu.add(new MenuItem("Edit", 100, 10) {
                public void run() {
                    switchMode(true);
                }
            });
            menu.add(new MenuItem("Export Page", 110, 20) {
                public void run() {
                    performExport();
                }
            });
            menu.add(new MenuItem("Options...", 120, 25) {
                public void run() {
                    UiApplication.getUiApplication().pushScreen(new OptionsDialog(new Runnable() {
                        public void run() {
                            if (!isEditMode) {
                                switchMode(false);
                            }
                        }
                    }));
                }
            });
        }

        /*
         * menu.add(new MenuItem("Back to Pages", 500, 90) {
         * public void run() {
         * close();
         * }
         * });
         */
    }

    // ==========================================
    // VIEW MODE SCROLLING & NAVIGATION
    // ==========================================

    private Manager getScrollableManager() {
        Manager main = getMainManager();
        if (main != null && main.getVirtualHeight() > main.getVisibleHeight()) {
            return main;
        }
        if (viewContentManager != null
                && viewContentManager.getVirtualHeight() > viewContentManager.getVisibleHeight()) {
            return viewContentManager;
        }
        return main;
    }

    /**
     * Smoothly scrolls the view by the given number of pixel rows.
     * Returns true if any scrolling occurred.
     */
    private boolean smoothScroll(int pixelDelta) {
        Manager mgr = getScrollableManager();
        if (mgr == null) {
            return false;
        }
        int virtualH = mgr.getVirtualHeight();
        if (viewContentManager != null && viewContentManager.getHeight() > virtualH) {
            virtualH = viewContentManager.getHeight();
        }
        int visibleH = mgr.getVisibleHeight();
        int maxScroll = virtualH - visibleH;
        if (maxScroll <= 0) {
            return false;
        }
        int currentScroll = mgr.getVerticalScroll();
        int newScroll = currentScroll + pixelDelta;
        if (newScroll < 0)
            newScroll = 0;
        if (newScroll > maxScroll)
            newScroll = maxScroll;
        if (newScroll == currentScroll) {
            return false;
        }
        mgr.setVerticalScroll(newScroll);
        return true;
    }

    protected boolean navigationMovement(int dx, int dy, int status, int time) {
        // In both modes let the platform move focus naturally — this gives the
        // text-selection cursor feel in view mode and proper caret movement in edit
        // mode.
        // The platform will scroll the MainScreen as focus moves, which is the
        // standard BB behaviour users expect.
        return super.navigationMovement(dx, dy, status, time);
    }

    protected boolean trackwheelRoll(int amount, int status, int time) {
        if (!isEditMode && amount != 0) {
            // Trackwheel (older BB devices): each notch = one line
            int lineH = Font.getDefault().getHeight() + 2;
            if (smoothScroll(amount * lineH)) {
                return true;
            }
        }
        return super.trackwheelRoll(amount, status, time);
    }

    protected boolean keyChar(char key, int status, int time) {
        if (!isEditMode) {
            Manager mgr = getScrollableManager();
            if (mgr != null) {
                int virtualH = mgr.getVirtualHeight();
                if (viewContentManager != null && viewContentManager.getHeight() > virtualH) {
                    virtualH = viewContentManager.getHeight();
                }
                int visibleH = mgr.getVisibleHeight();
                int maxScroll = virtualH - visibleH;
                if (maxScroll > 0) {
                    int currentScroll = mgr.getVerticalScroll();
                    if (key == ' ') {
                        // Space = scroll down, Shift+Space = scroll up
                        boolean shift = (status & KeypadListener.STATUS_SHIFT) != 0;
                        int pageStep = visibleH - (Font.getDefault().getHeight() * 2);
                        if (pageStep < 30) {
                            pageStep = 60;
                        }
                        int newScroll = shift ? (currentScroll - pageStep) : (currentScroll + pageStep);
                        if (newScroll < 0)
                            newScroll = 0;
                        if (newScroll > maxScroll)
                            newScroll = maxScroll;
                        mgr.setVerticalScroll(newScroll);
                        return true;
                    } else if (key == 't' || key == 'T') {
                        mgr.setVerticalScroll(0);
                        return true;
                    } else if (key == 'b' || key == 'B') {
                        mgr.setVerticalScroll(maxScroll);
                        return true;
                    }
                }
            }
        }
        return super.keyChar(key, status, time);
    }

    public void onExposed() {
        super.onExposed();
        // Only refresh if NOT currently in edit mode
        if (!isEditMode) {
            switchMode(false);
        }
    }

    public boolean isDirty() {
        if (isEditMode) {
            if (titleEditField != null) {
                String curTitle = titleEditField.getText().trim();
                String origTitle = page.getTitle() != null ? page.getTitle().trim() : "";
                if (!curTitle.equals(origTitle)) {
                    return true;
                }
            }
            if (notesEditField != null) {
                String curNotes = notesEditField.getText();
                String origNotes = page.getNotes();
                if (curNotes == null) {
                    curNotes = "";
                }
                if (origNotes == null) {
                    origNotes = "";
                }
                if (!curNotes.equals(origNotes)) {
                    return true;
                }
            }
            if (customEditFields != null) {
                Vector cfs = page.getCustomFields();
                int count = Math.min(cfs.size(), customEditFields.size());
                for (int i = 0; i < count; i++) {
                    CustomField cf = (CustomField) cfs.elementAt(i);
                    EditField ef = (EditField) customEditFields.elementAt(i);
                    String curVal = ef.getText();
                    String origVal = cf.getValue();
                    if (curVal == null) {
                        curVal = "";
                    }
                    if (origVal == null) {
                        origVal = "";
                    }
                    if (!curVal.equals(origVal)) {
                        return true;
                    }
                }
            }
            return super.isDirty();
        }
        return super.isDirty();
    }

    public boolean onClose() {
        if (isEditMode) {
            if (isDirty()) {
                int choice = Dialog.ask(Dialog.D_SAVE);
                if (choice == Dialog.SAVE) {
                    saveEdits();
                    switchMode(false);
                    return false;
                } else if (choice == Dialog.DISCARD) {
                    clearDirty();
                    switchMode(false);
                    return false;
                } else {
                    // Dialog.CANCEL or dialog dismissed - remain in edit mode
                    return false;
                }
            } else {
                // No unsaved changes - switch directly to view mode
                clearDirty();
                switchMode(false);
                return false;
            }
        }
        return super.onClose();
    }
}
