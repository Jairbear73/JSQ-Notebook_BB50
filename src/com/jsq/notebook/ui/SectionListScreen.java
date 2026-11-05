package com.jsq.notebook.ui;

import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.DateUtils;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.Ui;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
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

import java.util.Vector;

/**
 * Screen displaying the Sections within a chosen Notebook using a native
 * BlackBerry ListField.
 * Purely trackpad and menu driven with zero buttons.
 */
public class SectionListScreen extends MainScreen implements ListFieldCallback {

    private Notebook notebook;
    private ListField listField;
    private Vector sections;
    private Font boldFont;
    private Font smallFont;
    private int rowHeight;

    public SectionListScreen(Notebook notebook) {
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        this.notebook = notebook;

        setTitle(notebook.getName());

        Font defaultFont = Font.getDefault();
        boldFont = defaultFont.derive(Font.BOLD);
        smallFont = defaultFont.derive(Font.PLAIN, defaultFont.getHeight() - 2, Ui.UNITS_px);
        rowHeight = defaultFont.getHeight() * 2 + 8;

        listField = new ListField();
        listField.setCallback(this);
        listField.setRowHeight(rowHeight);
        add(listField);
        refreshList();
    }

    public void onExposed() {
        super.onExposed();
        refreshList();
    }

    private void refreshList() {
        int currentIndex = listField.getSelectedIndex();
        sections = notebook.getSections();
        int count = sections != null ? sections.size() : 0;
        listField.setSize(count);
        if (count > 0) {
            if (currentIndex >= 0) {
                listField.setSelectedIndex(currentIndex < count ? currentIndex : count - 1);
            } else {
                listField.setSelectedIndex(0);
            }
        }
    }

    private Section getSelectedSection() {
        if (sections == null || sections.size() == 0) {
            return null;
        }
        int idx = listField.getSelectedIndex();
        if (idx >= 0 && idx < sections.size()) {
            return (Section) sections.elementAt(idx);
        }
        return null;
    }

    private void openSelectedSection() {
        Section sec = getSelectedSection();
        if (sec != null) {
            UiApplication.getUiApplication().pushScreen(new PageListScreen(notebook, sec));
        } else {
            promptNewSection();
        }
    }

    protected boolean navigationClick(int status, int time) {
        openSelectedSection();
        return true;
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (sections == null || index < 0 || index >= sections.size()) {
            return;
        }

        Section sec = (Section) sections.elementAt(index);

        // Line 1: Section Name (Bold)
        graphics.setFont(boldFont);
        graphics.drawText(sec.getName(), 6, y + 2);

        // Line 2: Page count and modified timestamp
        int pageCount = sec.getPages().size();
        String info = pageCount + (pageCount == 1 ? " page" : " pages") +
                " | Updated: " + DateUtils.formatDateTime(sec.getModifiedTime());

        graphics.setFont(smallFont);
        graphics.drawText(info, 6, y + Font.getDefault().getHeight() + 4);
    }

    public Object get(ListField listField, int index) {
        if (sections != null && index >= 0 && index < sections.size()) {
            return sections.elementAt(index);
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
    // Dialog Prompts
    // ==========================================
    private void promptNewSection() {
        final EditField nameField = new EditField("Name: ", "");
        final EditField descField = new EditField("Description: ", "");

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Create Section", 100, 10) {
                    public void run() {
                        if (saveNewSection(nameField, descField)) {
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
        popup.add(new LabelField("New Section"));
        popup.add(new SeparatorField());
        popup.add(nameField);
        popup.add(descField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveNewSection(nameField, descField)) {
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

    private boolean saveNewSection(EditField nameField, EditField descField) {
        String name = nameField.getText().trim();
        if (name.length() == 0) {
            name = "Untitled Section";
        }
        long secId = NotebookStoreManager.getInstance().nextId();
        Section newSec = new Section(secId, notebook.getId(), name);
        newSec.setDescription(descField.getText().trim());

        notebook.addSection(newSec);
        NotebookStoreManager.getInstance().commit();

        refreshList();
        return true;
    }

    private void promptEditSection() {
        final Section sec = getSelectedSection();
        if (sec == null) {
            Dialog.alert("Please select a section first.");
            return;
        }

        final EditField nameField = new EditField("Name: ", sec.getName());
        final EditField descField = new EditField("Description: ", sec.getDescription());

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Save Changes", 100, 10) {
                    public void run() {
                        if (saveEditedSection(sec, nameField, descField)) {
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
        popup.add(new LabelField("Edit Section"));
        popup.add(new SeparatorField());
        popup.add(nameField);
        popup.add(descField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveEditedSection(sec, nameField, descField)) {
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

    private boolean saveEditedSection(Section sec, EditField nameField, EditField descField) {
        String name = nameField.getText().trim();
        if (name.length() > 0) {
            sec.setName(name);
        }
        sec.setDescription(descField.getText().trim());
        NotebookStoreManager.getInstance().commit();
        refreshList();
        return true;
    }

    private void promptDeleteSection() {
        Section sec = getSelectedSection();
        if (sec == null) {
            Dialog.alert("Please select a section first.");
            return;
        }

        int choice = Dialog.ask(Dialog.D_YES_NO, "Delete section '" + sec.getName() + "' and all its pages?");
        if (choice == Dialog.YES) {
            notebook.removeSection(sec.getId());
            NotebookStoreManager.getInstance().commit();
            refreshList();
        }
    }

    // ==========================================
    // Physical Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        if (sections != null && sections.size() > 0) {
            menu.add(new MenuItem("Open Section", 100, 10) {
                public void run() {
                    openSelectedSection();
                }
            });
            menu.add(new MenuItem("Customize Template", 110, 15) {
                public void run() {
                    Section sec = getSelectedSection();
                    if (sec != null) {
                        UiApplication.getUiApplication().pushScreen(new SectionTemplateScreen(sec));
                    }
                }
            });
            menu.add(new MenuItem("Edit Section", 120, 20) {
                public void run() {
                    promptEditSection();
                }
            });
            menu.add(new MenuItem("Delete Section", 130, 30) {
                public void run() {
                    promptDeleteSection();
                }
            });
        }

        menu.add(new MenuItem("New Section", 105, 12) {
            public void run() {
                promptNewSection();
            }
        });

        menu.add(new MenuItem("Back to Notebooks", 300, 90) {
            public void run() {
                close();
            }
        });
    }
}
