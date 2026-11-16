package com.jsq.notebook.util;

/**
 * Generic callback interface for reporting progress of long-running operations.
 * 100% unsigned CLDC 1.1 compatible.
 */
public interface ProgressListener {
    /**
     * Reports progress update.
     *
     * @param current current completed unit of work
     * @param total total units of work
     * @param status descriptive text of the current task
     */
    void onProgress(int current, int total, String status);
}

