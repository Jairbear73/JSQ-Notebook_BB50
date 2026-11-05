package com.jsq.notebook.ui;

import com.jsq.notebook.export.NotebookImportManager;
import com.jsq.notebook.export.PageExportManager;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.DateUtils;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.Ui;
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

import java.util.Vector;

/**
 * Primary startup screen displaying all top-level Notebooks in a classic
 * BlackBerry ListField.
 * Navigation and operations are entirely driven by trackpad selection and
 * BlackBerry menu.
 */
public class NotebookListScreen extends MainScreen implements ListFieldCallback {

    private ListField listField;
    private Vector notebooks;
    private Font boldFont;
    private Font smallFont;
    private int rowHeight;

    public NotebookListScreen() {
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        setTitle("JSQ Notebook");

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
        // Save the currently highlighted index before resizing the list
        int currentIndex = listField.getSelectedIndex();

        notebooks = NotebookStoreManager.getInstance().getNotebooks();
        int count = notebooks != null ? notebooks.size() : 0;
        listField.setSize(count);

        if (count > 0) {
            // Restore the previous index if valid, or clamp to the last item
            if (currentIndex >= 0) {
                listField.setSelectedIndex(currentIndex < count ? currentIndex : count - 1);
            } else {
                listField.setSelectedIndex(0);
            }
        }
    }

    private Notebook getSelectedNotebook() {
        if (notebooks == null || notebooks.size() == 0) {
            return null;
        }
        int idx = listField.getSelectedIndex();
        if (idx >= 0 && idx < notebooks.size()) {
            return (Notebook) notebooks.elementAt(idx);
        }
        return null;
    }

    private void openSelectedNotebook() {
        Notebook nb = getSelectedNotebook();
        if (nb != null) {
            UiApplication.getUiApplication().pushScreen(new SectionListScreen(nb));
        } else {
            promptNewNotebook();
        }
    }

    protected boolean navigationClick(int status, int time) {
        openSelectedNotebook();
        return true;
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (notebooks == null || index < 0 || index >= notebooks.size()) {
            return;
        }

        Notebook nb = (Notebook) notebooks.elementAt(index);

        // Line 1: Notebook Name (Bold)
        graphics.setFont(boldFont);
        graphics.drawText(nb.getName(), 6, y + 2);

        // Line 2: Sections count, pages count, modified date
        int secCount = nb.getSections().size();
        int pageCount = nb.getTotalPageCount();
        String info = secCount + (secCount == 1 ? " section, " : " sections, ") +
                pageCount + (pageCount == 1 ? " page" : " pages") +
                " | " + DateUtils.formatDateTime(nb.getModifiedTime());

        graphics.setFont(smallFont);
        graphics.drawText(info, 6, y + Font.getDefault().getHeight() + 4);
    }

    public Object get(ListField listField, int index) {
        if (notebooks != null && index >= 0 && index < notebooks.size()) {
            return notebooks.elementAt(index);
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
    private void promptNewNotebook() {
        final EditField nameField = new EditField("Name: ", "");
        final EditField descField = new EditField("Description: ", "");

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Create Notebook", 100, 10) {
                    public void run() {
                        if (saveNewNotebook(nameField, descField)) {
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
        popup.add(new LabelField("New Notebook"));
        popup.add(new SeparatorField());
        popup.add(nameField);
        popup.add(descField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveNewNotebook(nameField, descField)) {
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

    private boolean saveNewNotebook(EditField nameField, EditField descField) {
        String name = nameField.getText().trim();
        if (name.length() == 0) {
            name = "Untitled Notebook";
        }
        long id = NotebookStoreManager.getInstance().nextId();
        Notebook nb = new Notebook(id, name);
        nb.setDescription(descField.getText().trim());
        NotebookStoreManager.getInstance().addNotebook(nb);
        refreshList();
        return true;
    }

    private void promptEditNotebook() {
        final Notebook nb = getSelectedNotebook();
        if (nb == null) {
            Dialog.alert("Please highlight a notebook first.");
            return;
        }

        final EditField nameField = new EditField("Name: ", nb.getName());
        final EditField descField = new EditField("Description: ", nb.getDescription());

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Save Changes", 100, 10) {
                    public void run() {
                        if (saveEditedNotebook(nb, nameField, descField)) {
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
        popup.add(new LabelField("Edit Notebook"));
        popup.add(new SeparatorField());
        popup.add(nameField);
        popup.add(descField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveEditedNotebook(nb, nameField, descField)) {
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

    private boolean saveEditedNotebook(Notebook nb, EditField nameField, EditField descField) {
        String name = nameField.getText().trim();
        if (name.length() > 0) {
            nb.setName(name);
        }
        nb.setDescription(descField.getText().trim());
        NotebookStoreManager.getInstance().commit();
        refreshList();
        return true;
    }

    private void promptDeleteNotebook() {
        Notebook nb = getSelectedNotebook();
        if (nb == null) {
            Dialog.alert("Please select a notebook first.");
            return;
        }

        int choice = Dialog.ask(Dialog.D_YES_NO, "Delete notebook '" + nb.getName() + "' and all its sections?");
        if (choice == Dialog.YES) {
            NotebookStoreManager.getInstance().deleteNotebook(nb.getId());
            refreshList();
        }
    }

    private void exportSelectedNotebook() {
        final Notebook nb = getSelectedNotebook();
        if (nb == null) {
            Dialog.alert("Please select a notebook to export.");
            return;
        }

        // Open file picker so user can choose the destination directory
        FilePickerPopup picker = new FilePickerPopup(
                FilePickerPopup.MODE_DIRECTORY_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(String selectedUrl) {
                        // selectedUrl is the chosen directory; export into it
                        try {
                            String exportPath = PageExportManager.getInstance()
                                    .exportNotebook(nb, selectedUrl);
                            Dialog.alert("Export complete!\n\n" +
                                    "Notebook: " + nb.getName() + "\n" +
                                    "Location: " + exportPath);
                        } catch (Exception e) {
                            Dialog.alert("Export failed:\n" + e.getMessage());
                        }
                    }

                    public void onCancelled() {
                        // User cancelled — do nothing
                    }
                });
        UiApplication.getUiApplication().pushScreen(picker);
    }

    private void promptImportNotebook() {
        // Open file picker so user can navigate to and click a .jnb file
        FilePickerPopup picker = new FilePickerPopup(
                FilePickerPopup.MODE_JNB_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(String selectedUrl) {
                        // selectedUrl is the .jnb file (or notebook folder)
                        try {
                            Notebook imported = NotebookImportManager.getInstance()
                                    .importNotebook(selectedUrl);
                            refreshList();
                            Dialog.alert("Import complete!\n\n" +
                                    "Notebook: " + imported.getName() + "\n" +
                                    imported.getSections().size() + " section(s), " +
                                    imported.getTotalPageCount() + " page(s) imported.");
                        } catch (Exception e) {
                            Dialog.alert("Import failed:\n" + e.getMessage());
                        }
                    }

                    public void onCancelled() {
                        // User cancelled — do nothing
                    }
                });
        UiApplication.getUiApplication().pushScreen(picker);
    }

    // ==========================================
    // Physical Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        if (notebooks != null && notebooks.size() > 0) {
            /*
             * menu.add(new MenuItem("Open Notebook", 100, 10) {
             * public void run() {
             * openSelectedNotebook();
             * }
             * });
             */
            menu.add(new MenuItem("Edit Properties", 120, 20) {
                public void run() {
                    promptEditNotebook();
                }
            });
            menu.add(new MenuItem("Delete", 130, 30) {
                public void run() {
                    promptDeleteNotebook();
                }
            });
            menu.add(new MenuItem("Export", 140, 40) {
                public void run() {
                    exportSelectedNotebook();
                }
            });
        }

        menu.add(new MenuItem("New Notebook", 110, 15) {
            public void run() {
                promptNewNotebook();
            }
        });

        menu.add(new MenuItem("Import", 150, 50) {
            public void run() {
                promptImportNotebook();
            }
        });

        menu.add(new MenuItem("Options...", 160, 60) {
            public void run() {
                UiApplication.getUiApplication().pushScreen(new OptionsDialog(new Runnable() {
                    public void run() {
                        refreshList();
                    }
                }));
            }
        });

        menu.add(new MenuItem("About...", 200, 80) {
            public void run() {
                Dialog.alert("JSQ Notebook v0.2\n" +
                        "A rich text notebook application for BlackBerry OS 5.0\n\n" +
                        "(C) Copyright JSQ Systems 2026");
            }
        });

        menu.add(new MenuItem("Close Application", 300, 99) {
            public void run() {
                System.exit(0);
            }
        });
    }
}
