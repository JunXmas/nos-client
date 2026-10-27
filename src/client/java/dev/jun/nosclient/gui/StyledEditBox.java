package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class StyledEditBox extends EditBox {
    private int anchor;
    private int start;
    private int lastCursor = -1;
    private long blinkAt;
    private Component hint = Component.empty();
    private boolean editable = true;

    public StyledEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
    }

    @Override
    public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        this.anchor = Math.clamp(position, 0, this.getValue().length());
    }

    @Override
    public void setHint(Component value) {
        super.setHint(value);
        this.hint = value;
    }

    @Override
    public void setEditable(boolean value) {
        super.setEditable(value);
        this.editable = value;
    }

    private int measure(String text) {
        return Ui.width(Ui.ui(text));
    }

    private int textX() {
        return this.getX() + (this.isBordered() ? 5 : 0);
    }

    private int available() {
        return Math.max(1, this.getWidth() - (this.isBordered() ? 10 : 1));
    }

    private void revealCursor() {
        String text = this.getValue();
        int cursor = this.getCursorPosition();
        this.start = Math.min(this.start, Math.min(cursor, text.length()));
        if (this.start > 0 && this.start < text.length() && Character.isLowSurrogate(text.charAt(this.start))) {
            this.start--;
        }

        while (this.start < cursor && this.measure(text.substring(this.start, cursor)) > this.available() - 2) {
            this.start = text.offsetByCodePoints(this.start, 1);
        }

        while (this.start > 0) {
            int previous = text.offsetByCodePoints(this.start, -1);
            if (this.measure(text.substring(previous)) > this.available() - 2) {
                break;
            }

            this.start = previous;
        }
    }

    private int clicked(double mouseX) {
        String text = this.getValue();
        double x = mouseX - this.textX();
        int previous = this.start;

        for (int end = this.start; end < text.length(); previous = end) {
            end = text.offsetByCodePoints(end, 1);
            if (x < (this.measure(text.substring(this.start, previous)) + this.measure(text.substring(this.start, end))) / 2.0) {
                return previous;
            }
        }

        return text.length();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean twice) {
        int position = this.clicked(event.x());
        this.moveCursorTo(position, event.hasShiftDown());
        if (twice) {
            String text = this.getValue();
            int left = position;
            int right = position;

            while (left > 0 && !Character.isWhitespace(text.charAt(left - 1))) {
                left--;
            }

            while (right < text.length() && !Character.isWhitespace(text.charAt(right))) {
                right++;
            }

            this.moveCursorTo(left, false);
            this.moveCursorTo(right, true);
        }

        this.blinkAt = System.currentTimeMillis();
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        this.moveCursorTo(this.clicked(event.x()), true);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        if (this.isVisible()) {
            if (this.isBordered()) {
                Glass.panel(
                    g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), -720365280, this.isFocused() ? Ui.alpha(Ui.ACCENT, 0.7F) : 905969663
                );
            }

            this.revealCursor();
            String text = this.getValue();
            int cursor = this.getCursorPosition();
            if (cursor != this.lastCursor) {
                this.blinkAt = System.currentTimeMillis();
                this.lastCursor = cursor;
            }

            int x = this.textX();
            int y = this.getY() + Math.max(0, (this.getHeight() - 8) / 2);
            int end = this.start;

            while (end < text.length()) {
                int next = text.offsetByCodePoints(end, 1);
                if (this.measure(text.substring(this.start, next)) > this.available()) {
                    break;
                }

                end = next;
            }

            g.enableScissor(x, this.getY(), x + this.available() + 1, this.getY() + this.getHeight());

            try {
                if (this.isFocused() && cursor != this.anchor) {
                    int a = Math.clamp(Math.min(cursor, this.anchor), this.start, end);
                    int b = Math.clamp(Math.max(cursor, this.anchor), this.start, end);
                    g.fill(
                        x + this.measure(text.substring(this.start, a)),
                        y - 1,
                        x + this.measure(text.substring(this.start, b)),
                        y + 9,
                        UiOpacity.tint(Ui.alpha(Ui.ACCENT, 0.3F))
                    );
                }

                Ui.text(
                    g, text.isEmpty() ? Ui.ui(this.hint) : Ui.ui(text.substring(this.start, end)), x, y, !text.isEmpty() && this.editable ? Ui.TEXT : Ui.MUTED
                );
                if (this.editable && this.isFocused() && (System.currentTimeMillis() - this.blinkAt) / 500L % 2L == 0L) {
                    int caret = x + this.measure(text.substring(this.start, cursor));
                    g.fill(caret, y, caret + 1, y + 8, UiOpacity.tint(Ui.TEXT));
                }
            } finally {
                g.disableScissor();
            }
        }
    }
}
