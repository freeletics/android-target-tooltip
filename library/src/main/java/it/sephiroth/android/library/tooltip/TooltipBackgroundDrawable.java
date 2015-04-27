package it.sephiroth.android.library.tooltip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.View;

public class TooltipBackgroundDrawable extends Drawable {

    private int mBackgroundColor;

    private View mHighlightedView;

    private Drawable mHighlightDrawable;

    public TooltipBackgroundDrawable(Context context, TooltipManager.Builder builder) {
        mBackgroundColor = context.getResources().getColor(builder.backgroundColorResId);
        mHighlightedView = builder.highlightView;
        if (builder.highlightDrawableResId > 0) {
            mHighlightDrawable = context.getResources().getDrawable(builder.highlightDrawableResId);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mHighlightedView != null) {
            Rect highlightRect = new Rect();
            mHighlightedView.getGlobalVisibleRect(highlightRect);

            if (mHighlightDrawable != null) {
                mHighlightDrawable.setBounds(highlightRect);
                mHighlightDrawable.draw(canvas);
            }

            canvas.clipRect(highlightRect, Region.Op.DIFFERENCE);
        }

        canvas.drawColor(mBackgroundColor);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
