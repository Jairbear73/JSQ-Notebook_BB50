package com.jsq.notebook.ui;

import com.jsq.notebook.model.AppSettings;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Ui;

/**
 * High-performance, lightweight, and 100% unsigned custom ProgressBarField.
 * Renders smooth progress with percentage text and full Dark Mode support.
 */
public class ProgressBarField extends Field {

    private int value;
    private int maxValue;
    private int barHeight;

    public ProgressBarField() {
        this(0, 100);
    }

    public ProgressBarField(int value, int maxValue) {
        super(Field.NON_FOCUSABLE);
        this.value = value;
        this.maxValue = maxValue > 0 ? maxValue : 100;
        this.barHeight = 18;
    }

    public synchronized void setValue(int value) {
        if (value < 0) {
            value = 0;
        }
        if (value > maxValue) {
            value = maxValue;
        }
        this.value = value;
        invalidate();
    }

    public synchronized int getValue() {
        return value;
    }

    public synchronized void setMaxValue(int maxValue) {
        this.maxValue = maxValue > 0 ? maxValue : 100;
        if (value > this.maxValue) {
            value = this.maxValue;
        }
        invalidate();
    }

    public synchronized int getMaxValue() {
        return maxValue;
    }

    public int getPreferredHeight() {
        return barHeight;
    }

    public int getPreferredWidth() {
        return Graphics.getScreenWidth() - 60;
    }

    protected void layout(int width, int height) {
        int w = Math.min(width, getPreferredWidth());
        setExtent(w, barHeight);
    }

    protected void paint(Graphics graphics) {
        int w = getWidth();
        int h = getHeight();
        boolean dark = AppSettings.getInstance().isDarkMode();

        // Background and border colors
        int bgColor = dark ? 0x2A2A2A : 0xE0E4E8;
        int borderColor = dark ? 0x555555 : 0xB0B8C0;
        int fillColor = dark ? 0x2980B9 : 0x1A5276;

        // Container background
        graphics.setColor(bgColor);
        graphics.fillRoundRect(0, 0, w, h, 6, 6);

        // Progress fill
        int fillWidth = 0;
        if (maxValue > 0 && value > 0) {
            fillWidth = (int) (((long) w * value) / maxValue);
            if (fillWidth > w) {
                fillWidth = w;
            }
        }

        if (fillWidth > 0) {
            graphics.setColor(fillColor);
            graphics.fillRoundRect(0, 0, fillWidth, h, 6, 6);
        }

        // Outline border
        graphics.setColor(borderColor);
        graphics.drawRoundRect(0, 0, w, h, 6, 6);

        // Percentage text overlay
        int percent = maxValue > 0 ? (int) (((long) value * 100) / maxValue) : 0;
        String text = percent + "%";
        Font font = Font.getDefault();
        Font smallFont = font.derive(Font.BOLD, font.getHeight() - 4, Ui.UNITS_px);
        graphics.setFont(smallFont);

        int textW = smallFont.getAdvance(text);
        int textH = smallFont.getHeight();
        int textX = (w - textW) / 2;
        int textY = (h - textH) / 2;

        int textColor;
        if (fillWidth > textX + (textW / 2)) {
            textColor = Color.WHITE;
        } else {
            textColor = dark ? 0xCCCCCC : 0x333333;
        }
        graphics.setColor(textColor);
        graphics.drawText(text, textX, textY);
    }
}

