package com.jsq.notebook.ui;

import com.jsq.notebook.model.AppSettings;
import com.jsq.notebook.model.Notebook;
import com.jsq.notebook.storage.NotebookStoreManager;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.FieldChangeListener;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.MenuItem;
import net.rim.device.api.ui.component.ButtonField;
import net.rim.device.api.ui.component.CheckboxField;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;

/**
 * Settings and preferences dialog for JSQ Notebook.
 * Controls Dark Mode support, default opening modes, and UI features.
 */
public class OptionsDialog extends PopupScreen {

    private CheckboxField darkModeField;
    private CheckboxField defaultEditField;
    private CheckboxField showMetadataField;
    private CheckboxField confirmDeleteField;
    private Runnable onSettingsChangedCallback;

    public OptionsDialog() {
        this(null);
    }

    public OptionsDialog(final Runnable onSettingsChangedCallback) {
        super(new VerticalFieldManager(VerticalFieldManager.VERTICAL_SCROLL | VerticalFieldManager.VERTICAL_SCROLLBAR));
        this.onSettingsChangedCallback = onSettingsChangedCallback;

        AppSettings settings = AppSettings.getInstance();

        LabelField title = new LabelField("Options & Settings");
        title.setFont(Font.getDefault().derive(Font.BOLD));
        add(title);
        add(new SeparatorField());

        // Dark Mode Checkbox
        darkModeField = new CheckboxField("Dark Mode (Invert View)", settings.isDarkMode());
        add(darkModeField);

        LabelField darkDesc = new LabelField(
                " Adjusts the colours to be compatible with a dark theme, text becomes white, and colour intensity is inverted.");
        darkDesc.setFont(Font.getDefault().derive(Font.ITALIC, Font.getDefault().getHeight() - 3,
                net.rim.device.api.ui.Ui.UNITS_px));
        add(darkDesc);
        add(new SeparatorField());

        // Other settings
        defaultEditField = new CheckboxField("Open Pages in Edit Mode", settings.isDefaultEditMode());
        add(defaultEditField);

        showMetadataField = new CheckboxField("Show Metadata Box in View", settings.isShowMetadata());
        add(showMetadataField);

        confirmDeleteField = new CheckboxField("Confirm Delete Actions", settings.isConfirmDelete());
        add(confirmDeleteField);
        add(new SeparatorField());

        // Quick demo loader button
        ButtonField loadDemoBtn = new ButtonField("Load OneNote Demo Notebook",
                ButtonField.CONSUME_CLICK | Field.FIELD_HCENTER);
        loadDemoBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                Notebook nb = NotebookStoreManager.getInstance().addDemoNotebook();
                Dialog.alert("Demo notebook added:\n\"" + nb.getName()
                        + "\"\n\nContains full demo of all added OneNote features.");
                if (onSettingsChangedCallback != null) {
                    onSettingsChangedCallback.run();
                }
            }
        });
        add(loadDemoBtn);
        add(new SeparatorField());

        // OK / Cancel Action Buttons
        HorizontalFieldManager buttonBar = new HorizontalFieldManager(Field.FIELD_HCENTER);
        ButtonField saveBtn = new ButtonField("Save", ButtonField.CONSUME_CLICK);
        saveBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                applyAndSave();
                close();
            }
        });

        ButtonField cancelBtn = new ButtonField("Cancel", ButtonField.CONSUME_CLICK);
        cancelBtn.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) {
                close();
            }
        });

        buttonBar.add(saveBtn);
        buttonBar.add(cancelBtn);
        add(buttonBar);
    }

    private void applyAndSave() {
        AppSettings settings = AppSettings.getInstance();
        settings.setDarkMode(darkModeField.getChecked());
        settings.setDefaultEditMode(defaultEditField.getChecked());
        settings.setShowMetadata(showMetadataField.getChecked());
        settings.setConfirmDelete(confirmDeleteField.getChecked());
        settings.save();

        if (onSettingsChangedCallback != null) {
            onSettingsChangedCallback.run();
        }
    }

    protected void makeMenu(Menu menu, int instance) {
        menu.add(new MenuItem("Save Settings", 100, 10) {
            public void run() {
                applyAndSave();
                close();
            }
        });
        menu.add(new MenuItem("Cancel", 200, 20) {
            public void run() {
                close();
            }
        });
    }
}
