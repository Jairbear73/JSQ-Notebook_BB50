package com.jsq.notebook.ui;

import net.rim.device.api.system.Characters;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Ui;
import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.SeparatorField;
import net.rim.device.api.ui.container.PopupScreen;
import net.rim.device.api.ui.container.VerticalFieldManager;

/**
 * Modal progress popup screen featuring an operation title, dynamic status label,
 * ProgressBarField, and item counter.
 * All APIs are 100% unsigned BlackBerry OS 5.0 compatible.
 */
public class ProgressPopupScreen extends PopupScreen {

    private LabelField titleLabel;
    private LabelField statusLabel;
    private LabelField countLabel;
    private ProgressBarField progressBar;
    private volatile boolean isRunning;

    public ProgressPopupScreen(String title) {
        super(new VerticalFieldManager());
        this.isRunning = true;

        titleLabel = new LabelField(title != null ? title : "Please Wait...");
        titleLabel.setFont(Font.getDefault().derive(Font.BOLD));
        add(titleLabel);
        add(new SeparatorField());

        statusLabel = new LabelField("Preparing...", Field.USE_ALL_WIDTH);
        statusLabel.setFont(Font.getDefault().derive(Font.ITALIC, Font.getDefault().getHeight() - 2, Ui.UNITS_px));
        add(statusLabel);

        progressBar = new ProgressBarField(0, 100);
        add(progressBar);

        countLabel = new LabelField("", Field.FIELD_RIGHT);
        Font small = Font.getDefault().derive(Font.PLAIN, Font.getDefault().getHeight() - 4, Ui.UNITS_px);
        countLabel.setFont(small);
        add(countLabel);
    }

    /**
     * Dispatches progress updates to the UI thread safely.
     */
    public void updateProgress(final int current, final int total, final String status) {
        UiApplication.getUiApplication().invokeLater(new Runnable() {
            public void run() {
                if (total > 0) {
                    progressBar.setMaxValue(total);
                    progressBar.setValue(current);
                    countLabel.setText(current + " / " + total);
                }
                if (status != null) {
                    statusLabel.setText(status);
                }
            }
        });
    }

    /**
     * Closes the progress popup screen on the UI event thread.
     */
    public void finish() {
        isRunning = false;
        UiApplication.getUiApplication().invokeLater(new Runnable() {
            public void run() {
                close();
            }
        });
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Prevent accidental dismissal via ESC key while an I/O operation is in progress.
     */
    public boolean keyChar(char key, int status, int time) {
        if (key == Characters.ESCAPE && isRunning) {
            return true;
        }
        return super.keyChar(key, status, time);
    }
}

