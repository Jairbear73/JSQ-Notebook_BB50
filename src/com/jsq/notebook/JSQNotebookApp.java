package com.jsq.notebook;

import com.jsq.notebook.ui.NotebookListScreen;
import net.rim.device.api.ui.UiApplication;

/**
 * Main application entry point for JSQ Notebook on BlackBerry OS 5.0.
 * Utilizes exclusively unsigned RIM and CLDC 1.1 / MIDP 2.0 APIs.
 */
public class JSQNotebookApp extends UiApplication {

    public JSQNotebookApp() {
        // Launch primary startup screen
        pushScreen(new NotebookListScreen());
    }

    public static void main(String[] args) {
        JSQNotebookApp app = new JSQNotebookApp();
        app.enterEventDispatcher();
    }
}
