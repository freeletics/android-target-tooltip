package it.sephiroth.android.library.tooltip;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;

public class TooltipBackgroundDrawable extends TransitionDrawable {

    public TooltipBackgroundDrawable(int color, View brightView) {
        super(new Drawable[] { new ClippedColorDrawable(Color.TRANSPARENT, brightView),
                               new ClippedColorDrawable(color, brightView) });
    }

    private static class ClippedColorDrawable extends ColorDrawable {

        private View mClipView;

        public ClippedColorDrawable(int color, View clipView) {
            super(color);
            mClipView = clipView;
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (mClipView != null) {
                Rect clipRect = new Rect();
                mClipView.getGlobalVisibleRect(clipRect);
                canvas.clipRect(clipRect, Region.Op.DIFFERENCE);
            }
        }
    }
}
