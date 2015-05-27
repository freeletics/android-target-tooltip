package it.sephiroth.android.library.tooltip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.List;

public class TooltipBackgroundDrawable extends Drawable {

    private int mBackgroundColor;

    private List<View> mHighlightViews;

    private Drawable mHighlightDrawable;

    public TooltipBackgroundDrawable(Context context, TooltipManager.Builder builder) {
        mBackgroundColor = context.getResources().getColor(builder.backgroundColorResId);
        mHighlightViews = builder.highlightViews;
        if (builder.highlightDrawableResId > 0) {
            mHighlightDrawable = context.getResources().getDrawable(builder.highlightDrawableResId);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        if (mHighlightViews != null) {
            Rect highlightRect = new Rect();
            Rect vRect = new Rect();
            for(View v: mHighlightViews) {
                v.getGlobalVisibleRect(vRect);
                highlightRect.union(vRect);
            }

            if (mHighlightDrawable != null) {
                mHighlightDrawable.setBounds(highlightRect);
                mHighlightDrawable.draw(canvas);
            }

            canvas.clipRect(highlightRect, Region.Op.DIFFERENCE);
        }

        canvas.drawColor(mBackgroundColor);
        canvas.restore();
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
