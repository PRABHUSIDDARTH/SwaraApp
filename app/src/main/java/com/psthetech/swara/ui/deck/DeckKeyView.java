package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** An icon key. An ImageView, so existing code that sets icons and tints keeps working. */
public class DeckKeyView extends AppCompatImageView {

    private final DeckKeyPainter painter;

    public DeckKeyView(Context context) { this(context, null); }

    public DeckKeyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        painter = new DeckKeyPainter(this);
        setScaleType(ScaleType.CENTER);
        setClickable(true);
        setFocusable(true);
    }

    public void setPalette(@NonNull DeckPalette palette) { painter.setPalette(palette); }

    public void setPrimary(boolean primary) { painter.setPrimary(primary); }

    public void setLit(boolean lit) { painter.setLit(lit); }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        // Null while the superclass constructor is still running.
        if (painter != null) {
            painter.onPressedChanged(isPressed());
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        painter.draw(canvas);
        int save = canvas.save();
        canvas.translate(0f, painter.contentOffset());
        super.onDraw(canvas);
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
