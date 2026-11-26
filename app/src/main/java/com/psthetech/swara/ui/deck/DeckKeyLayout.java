package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A key that holds child views (Karaoke and Vibe keep an icon plus a hidden label). */
public class DeckKeyLayout extends LinearLayout {

    private final DeckKeyPainter painter;

    public DeckKeyLayout(Context context) { this(context, null); }

    public DeckKeyLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        painter = new DeckKeyPainter(this);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
    }

    public void setPalette(@NonNull DeckPalette palette) { painter.setPalette(palette); }

    public void setLit(boolean lit) { painter.setLit(lit); }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (painter != null) {
            painter.onPressedChanged(isPressed());
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        painter.draw(canvas);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        int save = canvas.save();
        canvas.translate(0f, painter.contentOffset());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onDetachedFromWindow() {
        painter.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Button";
    }
}
