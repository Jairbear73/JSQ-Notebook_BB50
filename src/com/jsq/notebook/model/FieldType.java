package com.jsq.notebook.model;

/**
 * Supported custom field data types in JSQ Notebook.
 */
public final class FieldType {

    public static final int TEXT = 0;
    public static final int LONG_TEXT = 1;
    public static final int DATE = 2;
    public static final int NUMBER = 3;
    public static final int CHECKBOX = 4;

    private static final String[] NAMES = {
        "Text",
        "Long Text",
        "Date",
        "Number",
        "Checkbox"
    };

    private FieldType() {
    }

    public static String getTypeName(int type) {
        if (type >= 0 && type < NAMES.length) {
            return NAMES[type];
        }
        return "Unknown";
    }

    public static String[] getAllTypeNames() {
        String[] copy = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            copy[i] = NAMES[i];
        }
        return copy;
    }
}
