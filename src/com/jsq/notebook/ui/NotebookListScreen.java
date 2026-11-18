package com.jsq.notebook.ui;

import com.jsq.notebook.export.NotebookBackupManager;
import com.jsq.notebook.export.NotebookImportManager;
import com.jsq.notebook.export.PageExportManager;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.DateUtils;
import com.jsq.notebook.util.ProgressListener;
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
        FilePickerPopup.open(
                FilePickerPopup.MODE_DIRECTORY_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(final String selectedUrl) {
                        final ProgressPopupScreen progress = new ProgressPopupScreen("Exporting Notebook");
                        UiApplication.getUiApplication().pushScreen(progress);

                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    final String exportPath = PageExportManager.getInstance()
                                            .exportNotebook(nb, selectedUrl, new ProgressListener() {
                                                public void onProgress(int current, int total, String status) {
                                                    progress.updateProgress(current, total, status);
                                                }
                                            });

                                    progress.finish();
                                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                                        public void run() {
                                            Dialog.alert("Export complete!\n\n" +
                                                    "Notebook: " + nb.getName() + "\n" +
                                                    "Location: " + exportPath);
                                        }
                                    });
                                } catch (final Exception e) {
                                    progress.finish();
                                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                                        public void run() {
                                            Dialog.alert("Export failed:\n" + e.getMessage());
                                        }
                                    });
                                }
                            }
                        }).start();
                    }

                    public void onCancelled() {
                        // User cancelled — do nothing
                    }
                });
    }

    private void promptImportNotebook() {
        // Open file picker so user can navigate to and click a .jnb file
        FilePickerPopup.open(
                FilePickerPopup.MODE_JNB_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(final String selectedUrl) {
                        if ("demo://jsq_demo_notebook".equals(selectedUrl)) {
                            promptImportDemoNotebook();
                            return;
                        }

                        final ProgressPopupScreen progress = new ProgressPopupScreen("Importing Notebook");
                        UiApplication.getUiApplication().pushScreen(progress);

                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    final Notebook imported = NotebookImportManager.getInstance()
                                            .importNotebook(selectedUrl, new ProgressListener() {
                                                public void onProgress(int current, int total, String status) {
                                                    progress.updateProgress(current, total, status);
                                                }
                                            });

                                    progress.finish();
                                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                                        public void run() {
                                            refreshList();
                                            Dialog.alert("Import complete!\n\n" +
                                                    "Notebook: " + imported.getName() + "\n" +
                                                    imported.getSections().size() + " section(s), " +
                                                    imported.getTotalPageCount() + " page(s) imported.");
                                        }
                                    });
                                } catch (final Exception e) {
                                    progress.finish();
                                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                                        public void run() {
                                            refreshList();
                                            Dialog.alert("Import failed:\n" + e.getMessage());
                                        }
                                    });
                                }
                            }
                        }).start();
                    }

                    public void onCancelled() {
                        // User cancelled — do nothing
                    }
                });
    }

    private void promptImportDemoNotebook() {
        final ProgressPopupScreen progress = new ProgressPopupScreen("Importing Demo Notebook");
        UiApplication.getUiApplication().pushScreen(progress);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final Notebook nb = NotebookImportManager.getInstance()
                            .importDemoNotebook(new ProgressListener() {
                                public void onProgress(int current, int total, String status) {
                                    progress.updateProgress(current, total, status);
                                }
                            });

                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            refreshList();
                            Dialog.alert("Demo notebook imported:\n\"" + nb.getName()
                                    + "\"\n\nContains full demo of all OneNote features.");
                        }
                    });
                } catch (final Exception e) {
                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            Dialog.alert("Failed to import demo notebook:\n" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void promptBackupAllNotebooks() {
        if (notebooks == null || notebooks.size() == 0) {
            Dialog.alert("There are no notebooks to back up.");
            return;
        }

        FilePickerPopup.open(
                FilePickerPopup.MODE_DIRECTORY_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(final String selectedDirUrl) {
                        confirmBackupFileName(selectedDirUrl);
                    }

                    public void onCancelled() {
                    }
                });
    }

    private void confirmBackupFileName(final String baseDirUrl) {
        String defaultFileName = "JSQNotebooks_Backup_" +
                DateUtils.formatCompactDateTime(System.currentTimeMillis()) + ".jnbb";

        final EditField fileField = new EditField("File: ", defaultFileName);

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Start Backup", 100, 10) {
                    public void run() {
                        String name = fileField.getText().trim();
                        close();
                        startBackup(baseDirUrl, name);
                    }
                });
                menu.add(new MenuItem("Cancel", 200, 20) {
                    public void run() {
                        close();
                    }
                });
            }
        };

        LabelField title = new LabelField("Backup All Notebooks");
        title.setFont(Font.getDefault().derive(Font.BOLD));
        popup.add(title);
        popup.add(new SeparatorField());

        int count = notebooks != null ? notebooks.size() : 0;
        LabelField info = new LabelField("Backing up " + count + " notebook" + (count == 1 ? "" : "s") + " to:");
        info.setFont(Font.getDefault().derive(Font.ITALIC));
        popup.add(info);

        LabelField dirLabel = new LabelField(baseDirUrl);
        popup.add(dirLabel);
        popup.add(new SeparatorField());
        popup.add(fileField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("Backup", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                String name = fileField.getText().trim();
                popup.close();
                startBackup(baseDirUrl, name);
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

    private void startBackup(String baseDirUrl, String fileName) {
        if (fileName == null || fileName.length() == 0) {
            fileName = "JSQNotebooks_Backup.jnbb";
        }
        if (!fileName.toLowerCase().endsWith(".jnbb")) {
            fileName = fileName + ".jnbb";
        }

        String dir = baseDirUrl;
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        final String destFileUrl = dir + fileName;
        final String displayFileName = fileName;

        final ProgressPopupScreen progress = new ProgressPopupScreen("Backing Up Notebooks");
        UiApplication.getUiApplication().pushScreen(progress);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final NotebookBackupManager.BackupResult result = NotebookBackupManager.getInstance()
                            .backupAll(destFileUrl, new NotebookBackupManager.ProgressListener() {
                                public void onProgress(int current, int total, String status) {
                                    progress.updateProgress(current, total, status);
                                }
                            });

                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            Dialog.alert("Backup complete!\n\n" +
                                    "Saved: " + displayFileName + "\n" +
                                    result.getNotebookCount() + " notebook(s), " +
                                    result.getSectionCount() + " section(s), " +
                                    result.getPageCount() + " page(s), " +
                                    result.getAttachmentCount() + " attachment(s) backed up.");
                        }
                    });
                } catch (final Exception e) {
                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            Dialog.alert("Backup failed:\n" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void promptRestoreNotebooks() {
        FilePickerPopup.open(
                FilePickerPopup.MODE_JNBB_SELECT,
                new FilePickerPopup.FilePickerListener() {
                    public void onFileSelected(final String selectedFileUrl) {
                        confirmRestoreOptions(selectedFileUrl);
                    }

                    public void onCancelled() {
                    }
                });
    }

    private void confirmRestoreOptions(final String fileUrl) {
        String fileName = fileUrl;
        int lastSlash = fileUrl.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileUrl.substring(lastSlash + 1);
        }

        final boolean replace;
        if (notebooks != null && notebooks.size() > 0) {
            String question = "Restore notebooks from:\n" + fileName + "\n\n" +
                    "Do you want to REPLACE current notebooks, or MERGE into them?\n\n" +
                    "• YES = Replace all existing notebooks\n" +
                    "• NO = Merge into existing notebooks";
            int choice = Dialog.ask(Dialog.D_YES_NO, question, Dialog.NO);
            if (choice != Dialog.YES && choice != Dialog.NO) {
                return;
            }
            replace = (choice == Dialog.YES);
        } else {
            int choice = Dialog.ask(Dialog.D_YES_NO, "Restore notebooks from:\n" + fileName + "?", Dialog.YES);
            if (choice != Dialog.YES) {
                return;
            }
            replace = true;
        }

        final ProgressPopupScreen progress = new ProgressPopupScreen("Restoring Notebooks");
        UiApplication.getUiApplication().pushScreen(progress);

        new Thread(new Runnable() {
            public void run() {
                try {
                    final NotebookBackupManager.RestoreResult result = NotebookBackupManager.getInstance()
                            .restoreAll(fileUrl, replace, new NotebookBackupManager.ProgressListener() {
                                public void onProgress(int current, int total, String status) {
                                    progress.updateProgress(current, total, status);
                                }
                            });

                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            refreshList();
                            Dialog.alert("Restore complete!\n\n" +
                                    result.getNotebookCount() + " notebook(s), " +
                                    result.getSectionCount() + " section(s), " +
                                    result.getPageCount() + " page(s), " +
                                    result.getAttachmentCount() + " attachment(s) restored.");
                        }
                    });
                } catch (final Exception e) {
                    progress.finish();
                    UiApplication.getUiApplication().invokeLater(new Runnable() {
                        public void run() {
                            refreshList();
                            Dialog.alert("Restore failed:\n" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
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
            menu.add(new MenuItem("Backup All", 145, 45) {
                public void run() {
                    promptBackupAllNotebooks();
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

        menu.add(new MenuItem("Import Demo Notebook", 152, 52) {
            public void run() {
                promptImportDemoNotebook();
            }
        });

        menu.add(new MenuItem("Restore All", 155, 55) {
            public void run() {
                promptRestoreNotebooks();
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
