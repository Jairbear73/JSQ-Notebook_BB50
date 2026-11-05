package com.jsq.notebook.ui;

import com.jsq.notebook.export.PageExportManager;
import com.jsq.notebook.model.AppSettings;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.model.Page;
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
 * Screen displaying the Pages within a chosen Section using a native BlackBerry
 * ListField.
 * Trackpad and menu driven with zero buttons on the screen.
 */
public class PageListScreen extends MainScreen implements ListFieldCallback {

    private Notebook notebook;
    private Section section;
    private ListField listField;
    private Vector pages;
    private Font boldFont;
    private Font smallFont;
    private int rowHeight;

    public PageListScreen(Notebook notebook, Section section) {
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        this.notebook = notebook;
        this.section = section;

        setTitle(notebook.getName() + " > " + section.getName());

        Font defaultFont = Font.getDefault();
        boldFont = defaultFont.derive(Font.BOLD);
        smallFont = defaultFont.derive(Font.PLAIN, defaultFont.getHeight() - 2, Ui.UNITS_px);
        rowHeight = defaultFont.getHeight() + 8;

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
        pages = section.getPages();
        int count = pages != null ? pages.size() : 0;
        listField.setSize(count);
        if (count > 0) {
            if (currentIndex >= 0) {
                listField.setSelectedIndex(currentIndex < count ? currentIndex : count - 1);
            } else {
                listField.setSelectedIndex(0);
            }
        }
    }

    private Page getSelectedPage() {
        if (pages == null || pages.size() == 0) {
            return null;
        }
        int idx = listField.getSelectedIndex();
        if (idx >= 0 && idx < pages.size()) {
            return (Page) pages.elementAt(idx);
        }
        return null;
    }

    private void openSelectedPage(boolean editMode) {
        Page page = getSelectedPage();
        if (page != null) {
            UiApplication.getUiApplication().pushScreen(new PageDetailScreen(notebook, section, page, editMode));
        } else {
            promptNewPage();
        }
    }

    protected boolean navigationClick(int status, int time) {
        openSelectedPage(AppSettings.getInstance().isDefaultEditMode());
        return true;
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (pages == null || index < 0 || index >= pages.size()) {
            return;
        }

        Page page = (Page) pages.elementAt(index);

        String dateStr = DateUtils.formatDate(page.getModifiedTime());
        int dateWidth = smallFont.getAdvance(dateStr);
        int rightMargin = 8;
        int dateX = width - dateWidth - rightMargin;
        int leftMargin = 6;
        int maxTitleWidth = dateX - leftMargin - 6;

        String title = truncateToWidth(page.getTitle(), boldFont, maxTitleWidth);

        int textY = y + (rowHeight - boldFont.getHeight()) / 2;
        int dateY = y + (rowHeight - smallFont.getHeight()) / 2;

        graphics.setFont(boldFont);
        graphics.drawText(title, leftMargin, textY);

        graphics.setFont(smallFont);
        graphics.drawText(dateStr, dateX, dateY);
    }

    private String truncateToWidth(String text, Font font, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.getAdvance(text) <= maxWidth) {
            return text;
        }
        int len = text.length();
        for (int i = len - 1; i >= 1; i--) {
            String sub = text.substring(0, i) + "...";
            if (font.getAdvance(sub) <= maxWidth) {
                return sub;
            }
        }
        return text;
    }

    public Object get(ListField listField, int index) {
        if (pages != null && index >= 0 && index < pages.size()) {
            return pages.elementAt(index);
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
    // Actions & Dialogs
    // ==========================================
    private void promptNewPage() {
        final EditField titleField = new EditField("Title: ", "");

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Create Page", 100, 10) {
                    public void run() {
                        if (saveNewPage(titleField)) {
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
        popup.add(new LabelField("New Page"));
        popup.add(new SeparatorField());
        popup.add(titleField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveNewPage(titleField)) {
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

    private boolean saveNewPage(EditField titleField) {
        String title = titleField.getText().trim();
        if (title.length() == 0) {
            title = "Untitled Page";
        }

        long pageId = NotebookStoreManager.getInstance().nextId();
        Page newPage = new Page(pageId, section.getId(), title);
        section.applyTemplateToPage(newPage);
        section.addPage(newPage);
        NotebookStoreManager.getInstance().commit();

        UiApplication.getUiApplication().pushScreen(new PageDetailScreen(notebook, section, newPage, true));
        return true;
    }

    private void exportSelectedPage() {
        final Page page = getSelectedPage();
        if (page == null) {
            Dialog.alert("Please select a page first.");
            return;
        }

        int choice = Dialog.ask(Dialog.D_YES_NO,
                "Export to default SD Card folder?\n(Select 'No' to choose custom directory)");
        if (choice == Dialog.YES) {
            try {
                String dest = PageExportManager.getInstance().exportPage(notebook, section, page);
                Dialog.alert("Page exported successfully to:\n" + dest);
            } catch (Exception e) {
                Dialog.alert("Export failed: " + e.getMessage());
            }
        } else {
            FilePickerPopup picker = new FilePickerPopup(FilePickerPopup.MODE_DIRECTORY_SELECT,
                    new FilePickerPopup.FilePickerListener() {
                        public void onFileSelected(String selectedUrl) {
                            try {
                                String dest = PageExportManager.getInstance().exportPageTo(notebook, section, page,
                                        selectedUrl);
                                Dialog.alert("Page exported successfully to:\n" + dest);
                            } catch (Exception e) {
                                Dialog.alert("Export failed: " + e.getMessage());
                            }
                        }

                        public void onCancelled() {
                        }
                    });
            UiApplication.getUiApplication().pushScreen(picker);
        }
    }

    private void promptDeletePage() {
        Page page = getSelectedPage();
        if (page == null) {
            Dialog.alert("Please select a page first.");
            return;
        }

        if (AppSettings.getInstance().isConfirmDelete()) {
            int choice = Dialog.ask(Dialog.D_YES_NO, "Delete page '" + page.getTitle() + "'?");
            if (choice != Dialog.YES) {
                return;
            }
        }
        section.removePage(page.getId());
        NotebookStoreManager.getInstance().commit();
        refreshList();
    }

    // ==========================================
    // Physical Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        if (pages != null && pages.size() > 0) {
            /*
             * menu.add(new MenuItem("Open Page", 100, 10) {
             * public void run() {
             * openSelectedPage(false);
             * }
             * });
             */
            menu.add(new MenuItem("Edit Page", 110, 15) {
                public void run() {
                    openSelectedPage(true);
                }
            });
            menu.add(new MenuItem("Export Page", 130, 30) {
                public void run() {
                    exportSelectedPage();
                }
            });
            menu.add(new MenuItem("Delete Page", 120, 20) {
                public void run() {
                    promptDeletePage();
                }
            });
        }

        menu.add(new MenuItem("New Page", 105, 12) {
            public void run() {
                promptNewPage();
            }
        });

        menu.add(new MenuItem("Section Template", 200, 40) {
            public void run() {
                UiApplication.getUiApplication().pushScreen(new SectionTemplateScreen(section));
            }
        });

        menu.add(new MenuItem("Options...", 250, 50) {
            public void run() {
                UiApplication.getUiApplication().pushScreen(new OptionsDialog());
            }
        });

        /*
         * menu.add(new MenuItem("Back to Sections", 300, 90) {
         * public void run() {
         * close();
         * }
         * });
         */
    }
}
