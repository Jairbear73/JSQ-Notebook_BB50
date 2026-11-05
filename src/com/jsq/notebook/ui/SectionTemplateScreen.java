package com.jsq.notebook.ui;

import com.jsq.notebook.model.CustomFieldDefinition;
import com.jsq.notebook.model.FieldType;
import com.jsq.notebook.model.Section;
import com.jsq.notebook.storage.NotebookStoreManager;
import com.jsq.notebook.util.StringUtils;
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
import net.rim.device.api.ui.component.ObjectChoiceField;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;

import java.util.Vector;

/**
 * Screen for configuring a Section's custom field data template using a clean ListField.
 * Trackpad and menu driven with zero buttons.
 */
public class SectionTemplateScreen extends MainScreen implements ListFieldCallback {

    private Section section;
    private ListField listField;
    private Vector defs;
    private Font boldFont;
    private Font smallFont;
    private int rowHeight;

    public SectionTemplateScreen(Section section) {
        super(MainScreen.VERTICAL_SCROLL | MainScreen.VERTICAL_SCROLLBAR);
        this.section = section;

        setTitle("Template: " + section.getName());

        Font defaultFont = Font.getDefault();
        boldFont = defaultFont.derive(Font.BOLD);
        smallFont = defaultFont.derive(Font.PLAIN, defaultFont.getHeight() - 2, Ui.UNITS_px);
        rowHeight = defaultFont.getHeight() * 2 + 8;

        listField = new ListField();
        listField.setCallback(this);
        listField.setRowHeight(rowHeight);
        add(listField);

        refreshFieldsList();
    }

    private void refreshFieldsList() {
        defs = section.getFieldDefinitions();
        int count = defs != null ? defs.size() : 0;
        listField.setSize(count);
        if (count > 0 && listField.getSelectedIndex() < 0) {
            listField.setSelectedIndex(0);
        }
    }

    private CustomFieldDefinition getSelectedDefinition() {
        if (defs == null || defs.size() == 0) {
            return null;
        }
        int idx = listField.getSelectedIndex();
        if (idx >= 0 && idx < defs.size()) {
            return (CustomFieldDefinition) defs.elementAt(idx);
        }
        return null;
    }

    // ==========================================
    // ListFieldCallback Implementation
    // ==========================================
    public void drawListRow(ListField listField, Graphics graphics, int index, int y, int width) {
        if (defs == null || index < 0 || index >= defs.size()) {
            return;
        }

        CustomFieldDefinition def = (CustomFieldDefinition) defs.elementAt(index);

        // Line 1: Field Label & Mandatory tag
        graphics.setFont(boldFont);
        String title = def.getLabel() + (def.isMandatory() ? " [Mandatory]" : "");
        graphics.drawText(title, 6, y + 2);

        // Line 2: Type & Default value
        String typeStr = "Type: " + FieldType.getTypeName(def.getFieldType());
        if (def.getDefaultValue().length() > 0) {
            typeStr += " | Default: " + def.getDefaultValue();
        }
        graphics.setFont(smallFont);
        graphics.drawText(typeStr, 6, y + Font.getDefault().getHeight() + 4);
    }

    public Object get(ListField listField, int index) {
        if (defs != null && index >= 0 && index < defs.size()) {
            return defs.elementAt(index);
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
    private void promptNewField() {
        final EditField labelField = new EditField("Label: ", "");
        final ObjectChoiceField typeChoice = new ObjectChoiceField("Type: ", FieldType.getAllTypeNames(), 0);
        final EditField defaultValueField = new EditField("Default: ", "");

        final PopupScreen popup = new PopupScreen(new VerticalFieldManager()) {
            protected void makeMenu(Menu menu, int instance) {
                menu.add(new MenuItem("Save Field", 100, 10) {
                    public void run() {
                        if (saveNewField(labelField, typeChoice, defaultValueField)) {
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
        popup.add(new LabelField("Add New Custom Field"));
        popup.add(new SeparatorField());
        popup.add(labelField);
        popup.add(typeChoice);
        popup.add(defaultValueField);
        popup.add(new SeparatorField());

        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField okBtn = new ButtonField("OK", ButtonField.CONSUME_CLICK);
        okBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                if (saveNewField(labelField, typeChoice, defaultValueField)) {
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

    private boolean saveNewField(EditField labelField, ObjectChoiceField typeChoice, EditField defaultValueField) {
        String label = labelField.getText().trim();
        if (label.length() == 0) {
            Dialog.alert("Please provide a field label.");
            return false;
        }

        String key = StringUtils.sanitizeFileName(label.toLowerCase());
        int type = typeChoice.getSelectedIndex();
        String defVal = defaultValueField.getText();

        // Check duplicate
        Vector currentDefs = section.getFieldDefinitions();
        int sz = currentDefs.size();
        for (int i = 0; i < sz; i++) {
            CustomFieldDefinition d = (CustomFieldDefinition) currentDefs.elementAt(i);
            if (d.getKey().equals(key)) {
                Dialog.alert("A field with this identifier already exists.");
                return false;
            }
        }

        CustomFieldDefinition newDef = new CustomFieldDefinition(key, label, type, false, defVal);
        section.getFieldDefinitions().addElement(newDef);
        NotebookStoreManager.getInstance().commit();

        refreshFieldsList();
        return true;
    }

    private void removeSelectedField() {
        CustomFieldDefinition def = getSelectedDefinition();
        if (def == null) {
            Dialog.alert("Please select a field first.");
            return;
        }

        if ("title".equals(def.getKey()) || "notes".equals(def.getKey())) {
            Dialog.alert("The 'Title' and 'Notes' fields are built-in and cannot be removed.");
            return;
        }

        int choice = Dialog.ask(Dialog.D_YES_NO, "Remove field '" + def.getLabel() + "' from template?");
        if (choice == Dialog.YES) {
            section.getFieldDefinitions().removeElement(def);
            NotebookStoreManager.getInstance().commit();
            refreshFieldsList();
        }
    }

    // ==========================================
    // Physical Menu Integration
    // ==========================================
    protected void makeMenu(Menu menu, int instance) {
        menu.add(new MenuItem("Add Field", 100, 10) {
            public void run() {
                promptNewField();
            }
        });

        if (defs != null && defs.size() > 0) {
            menu.add(new MenuItem("Remove Field", 110, 20) {
                public void run() {
                    removeSelectedField();
                }
            });
        }

        menu.add(new MenuItem("Back to Section", 300, 90) {
            public void run() {
                close();
            }
        });
    }
}
