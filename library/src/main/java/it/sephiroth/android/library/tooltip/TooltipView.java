package it.sephiroth.android.library.tooltip;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static it.sephiroth.android.library.tooltip.TooltipManager.ClosePolicy;
import static it.sephiroth.android.library.tooltip.TooltipManager.DBG;

class TooltipView extends ViewGroup implements Tooltip {

    private static final String TAG = "ToolTipLayout";
    private final long showDelay;

    private boolean mAttached;
    private boolean mInitialized;
    private boolean mActivated;

    private final int toolTipId;
    private final Rect viewRect;
    private final Rect drawRect;
    private final Rect tempRect;

    private final long showDuration;
    private final ClosePolicy closePolicy;
    private final View targetView;
    private final Point point;
    private final int textResId;
    private final int textStyleResId;
    private final int topRule;
    private final int maxWidth;
    private final boolean hideArrow;
    private int padding;
    private final long activateDelay;
    private final boolean restrict;
    private final long animationDuration;
    private final TooltipManager.onTooltipClosingCallback closeCallback;
    private final int inAnimation;
    private final int outAnimation;
    private final int backgroundColorResId;
    private final boolean centerHorizontally;

    private CharSequence text;
    TooltipManager.Gravity gravity;

    private View mView;
    private TextView mTextView;
    private final TooltipTextDrawable mDrawable;
    private TransitionDrawable mBackgroundTransitionDrawable;

    public TooltipView(Context context, TooltipManager.Builder builder) {
        super(context);

        TypedArray theme = context.getTheme().obtainStyledAttributes(null, R.styleable.TooltipLayout, builder.defStyleAttr, builder.defStyleRes);
        this.padding = theme.getDimensionPixelSize(R.styleable.TooltipLayout_ttlm_padding, 30);
        theme.recycle();

        TypedValue value = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(R.attr.ttlm_defaultTextStyle, value, true);
        if (found) {
            textStyleResId = value.resourceId;
        } else {
            textStyleResId = R.style.ToolTipTextDefaultStyle;
        }

        this.toolTipId = builder.id;
        this.text = builder.text;
        this.gravity = builder.gravity;
        this.textResId = builder.textResId;
        this.maxWidth = builder.maxWidth;
        this.topRule = builder.actionbarSize;
        this.closePolicy = builder.closePolicy;
        this.showDuration = builder.showDuration;
        this.showDelay = builder.showDelay;
        this.hideArrow = builder.hideArrow;
        this.activateDelay = builder.activateDelay;
        this.targetView = builder.view;
        this.restrict = builder.restrictToScreenEdges;
        this.animationDuration = builder.animationDuration;
        this.closeCallback = builder.closeCallback;
        this.inAnimation = builder.inAnimation;
        this.outAnimation = builder.outAnimation;
        this.backgroundColorResId = builder.backgroundColorResId;
        this.centerHorizontally = builder.centerHorizontally;

        mBackgroundTransitionDrawable = new TransitionDrawable(new Drawable[] {
                new ColorDrawable(Color.TRANSPARENT),
                new TooltipBackgroundDrawable(context, builder)});

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            //noinspection deprecation
            setBackgroundDrawable(mBackgroundTransitionDrawable);
        } else {
            setBackground(mBackgroundTransitionDrawable);
        }

        if (null != builder.point) {
            this.point = new Point(builder.point);
            this.point.y += topRule;
        } else {
            this.point = null;
        }

        this.viewRect = new Rect();
        this.drawRect = new Rect();
        this.tempRect = new Rect();

        if (!builder.isCustomView) {
            this.mDrawable = new TooltipTextDrawable(context, builder);
        } else {
            this.mDrawable = null;
        }

        setVisibility(INVISIBLE);
    }

    int getTooltipId() {
        return toolTipId;
    }

    @Override
    public void show() {
        if (DBG) Log.i(TAG, "show");
        if (!isAttached()) {
            if (DBG) Log.e(TAG, "not attached!");
            return;
        }

        postDelayed(new Runnable() {
            @Override
            public void run() {
                animateIn();
            }
        }, showDelay);
    }

    @Override
    public void hide(boolean remove) {
        if (DBG) Log.i(TAG, "hide");
        if (!isAttached()) return;
        animateOut(remove);
    }

    Animator mAnimation;
    boolean mShowing;

    protected void animateIn() {
        if (mShowing) return;

        if (null != mAnimation) {
            mAnimation.cancel();
        }

        if (DBG) Log.i(TAG, "animateIn");

        mShowing = true;

        if (animationDuration > 0 && inAnimation > 0) {

            mAnimation = AnimatorInflater.loadAnimator(getContext(), inAnimation);
            mAnimation.setTarget(mView);
            mAnimation.setDuration(animationDuration);

            mAnimation.addListener(new Animator.AnimatorListener() {
                private boolean cancelled;

                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(VISIBLE);
                    cancelled = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (DBG) Log.i(TAG, "animateIn::onAnimationEnd, cancelled: " + cancelled);
                    if (null != tooltipListener && !cancelled) {
                        tooltipListener.onShowCompleted(TooltipView.this);
                        postActivate(activateDelay);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            mAnimation.start();

            if (mBackgroundTransitionDrawable != null) {
                mBackgroundTransitionDrawable.startTransition((int) animationDuration);
            }
        } else {
            setVisibility(VISIBLE);
            if (backgroundColorResId > 0) {
                setBackgroundColor(getContext().getResources().getColor(backgroundColorResId));
            }
            tooltipListener.onShowCompleted(TooltipView.this);
            if (!mActivated) {
                postActivate(activateDelay);
            }
        }

        if (showDuration > 0) {
            getHandler().removeCallbacks(hideRunnable);
            getHandler().postDelayed(hideRunnable, showDuration);
        }
    }

    Runnable activateRunnable = new Runnable() {
        @Override
        public void run() {
            if (DBG) Log.v(TAG, "activated..");
            mActivated = true;
        }
    };

    Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            onClose(false, false);
        }
    };

    boolean isShowing() {
        return mShowing;
    }

    void postActivate(long ms) {
        if (DBG) Log.i(TAG, "postActivate: " + ms);
        if (ms > 0) {
            if (isAttached()) {
                postDelayed(activateRunnable, ms);
            }
        } else {
            mActivated = true;
        }
    }

    void removeFromParent() {
        if (DBG) Log.i(TAG, "removeFromParent: " + toolTipId);
        ViewParent parent = getParent();
        if (null != parent) {
            if (null != getHandler()) {
                getHandler().removeCallbacks(hideRunnable);
            }
            ((ViewGroup) parent).removeView(TooltipView.this);

            if (null != mAnimation && mAnimation.isStarted()) {
                mAnimation.cancel();
            }
        }
    }

    protected void animateOut(final boolean remove) {
        if (!isAttached() || !mShowing) return;
        if (DBG) Log.i(TAG, "animateOut");

        if (null != mAnimation) {
            mAnimation.cancel();
        }

        mShowing = false;

        if (animationDuration > 0 && outAnimation != 0) {

            mAnimation = AnimatorInflater.loadAnimator(getContext(), outAnimation);
            mAnimation.setTarget(mView);
            mAnimation.addListener(new Animator.AnimatorListener() {
                private boolean cancelled;

                @Override
                public void onAnimationStart(Animator animation) {
                    cancelled = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (DBG) Log.i(TAG, "animateOut::onAnimationEnd, cancelled: " + cancelled);
                    if (cancelled) return;

                    if (remove) {
                        fireOnHideCompleted();
                    }
                    setVisibility(INVISIBLE);
                    mAnimation = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            mAnimation.start();

            if (mBackgroundTransitionDrawable != null) {
                mBackgroundTransitionDrawable.reverseTransition((int) animationDuration);
            }
        } else {
            setVisibility(INVISIBLE);
            setBackgroundColor(Color.TRANSPARENT);
            if (remove) {
                fireOnHideCompleted();
            }
        }
    }

    private void fireOnHideCompleted() {
        if (null != tooltipListener) {
            tooltipListener.onHideCompleted(TooltipView.this);
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        if (DBG) Log.i(TAG, "onLayout, changed: " + changed + ", " + l + ", " + t + ", " + r + ", " + b);

        //  The layout has actually already been performed and the positions
        //  cached.  Apply the cached values to the children.
        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.layout(child.getLeft(), child.getTop(), child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        if (changed) {

            List<TooltipManager.Gravity> gravities = new ArrayList<TooltipManager.Gravity>(
                    Arrays.asList(
                            TooltipManager.Gravity.LEFT,
                            TooltipManager.Gravity.RIGHT,
                            TooltipManager.Gravity.TOP,
                            TooltipManager.Gravity.BOTTOM,
                            TooltipManager.Gravity.CENTER
                    )
            );

            gravities.remove(gravity);
            gravities.add(0, gravity);
            calculatePositions(gravities);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (DBG) Log.i(TAG, "onMeasure");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int myWidth = -1;
        int myHeight = -1;

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // Record our dimensions if they are known;
        if (widthMode != MeasureSpec.UNSPECIFIED) {
            myWidth = widthSize;
        }

        if (heightMode != MeasureSpec.UNSPECIFIED) {
            myHeight = heightSize;
        }

        if (DBG) {
            Log.v(TAG, "myWidth: " + myWidth);
            Log.v(TAG, "myHeight: " + myHeight);
        }

        final int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(myWidth, MeasureSpec.AT_MOST);
                int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(myHeight, MeasureSpec.AT_MOST);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }

        setMeasuredDimension(myWidth, myHeight);
    }

    @Override
    protected void onAttachedToWindow() {
        if (DBG) Log.i(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        mAttached = true;

        initializeView();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DBG) Log.i(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        mAttached = false;
    }

    private void initializeView() {
        if (!isAttached() || mInitialized) return;
        mInitialized = true;

        if (DBG) Log.i(TAG, "initializeView");

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        mView = LayoutInflater.from(getContext()).inflate(textResId, this, false);

        if (null != mDrawable) {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                //noinspection deprecation
                mView.setBackgroundDrawable(mDrawable);
            } else {
                mView.setBackground(mDrawable);
            }

            if (hideArrow) {
                mView.setPadding(padding / 2, padding / 2, padding / 2, padding / 2);
            } else {
                mView.setPadding(padding, padding, padding, padding);
            }
        }

        mTextView = (TextView) mView.findViewById(android.R.id.text1);
        if (mTextView != null) {
            mTextView.setTextAppearance(getContext(), textStyleResId);
            mTextView.setText(Html.fromHtml((String) this.text));
            if (maxWidth > -1) {
                mTextView.setMaxWidth(maxWidth);
            }
        }

        this.addView(mView, params);
    }

    private void calculatePositions(List<TooltipManager.Gravity> gravities) {
        if (!isAttached()) return;

        // failed to display the tooltip due to
        // something wrong with its dimensions or
        // the target position..
        if (gravities.size() < 1) {
            if (null != tooltipListener) {
                tooltipListener.onShowFailed(this);
            }
            setVisibility(GONE);
            return;
        }

        TooltipManager.Gravity gravity = gravities.get(0);

        if (DBG) Log.i(TAG, "calculatePositions: " + gravity + ", gravities: " + gravities.size());

        gravities.remove(0);

        Rect screenRect = new Rect();
        Window window = ((Activity) getContext()).getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(screenRect);

        int statusbarHeight = screenRect.top;

        if (DBG) {
            Log.d(TAG, "screenRect: " + screenRect + ", topRule: " + topRule + ", statusBar: " + statusbarHeight);
        }

        screenRect.top += topRule;

        // get the global visible rect for the target targetView
        if (null != targetView) {
            targetView.getGlobalVisibleRect(viewRect);
        } else {
            viewRect.set(point.x, point.y + statusbarHeight, point.x, point.y + statusbarHeight);
        }

        int width = mView.getMeasuredWidth();
        int height = mView.getMeasuredHeight();

        // get the destination point
        Point point = new Point();

        //@formatter:off
        if (gravity == TooltipManager.Gravity.BOTTOM) {
            drawRect.set(viewRect.centerX() - width / 2,
                    viewRect.bottom,
                    viewRect.centerX() + width / 2,
                    viewRect.bottom + height);

            point.x = viewRect.centerX();
            point.y = viewRect.bottom;

            if (restrict && !screenRect.contains(drawRect)) {
                if (drawRect.right > screenRect.right) {
                    drawRect.offset(screenRect.right - drawRect.right, 0);
                } else if (drawRect.left < screenRect.left) {
                    drawRect.offset(-drawRect.left, 0);
                }
                if (drawRect.bottom > screenRect.bottom) {
                    // this means there's no enough space!
                    calculatePositions(gravities);
                    return;
                } else if (drawRect.top < screenRect.top) {
                    drawRect.offset(0, screenRect.top - drawRect.top);
                }
            }
        } else if (gravity == TooltipManager.Gravity.TOP) {
            drawRect.set(viewRect.centerX() - width / 2,
                    viewRect.top - height,
                    viewRect.centerX() + width / 2,
                    viewRect.top);

            point.x = viewRect.centerX();
            point.y = viewRect.top;

            if (restrict && !screenRect.contains(drawRect)) {
                if (drawRect.right > screenRect.right) {
                    drawRect.offset(screenRect.right - drawRect.right, 0);
                } else if (drawRect.left < screenRect.left) {
                    drawRect.offset(-drawRect.left, 0);
                }
                if (drawRect.top < screenRect.top) {
                    // this means there's no enough space!
                    calculatePositions(gravities);
                    return;
                } else if (drawRect.bottom > screenRect.bottom) {
                    drawRect.offset(0, screenRect.bottom - drawRect.bottom);
                }
            }
        } else if (gravity == TooltipManager.Gravity.RIGHT) {
            drawRect.set(viewRect.right,
                    viewRect.centerY() - height / 2,
                    viewRect.right + width,
                    viewRect.centerY() + height / 2);

            point.x = viewRect.right;
            point.y = viewRect.centerY();

            if (restrict && !screenRect.contains(drawRect)) {
                if (drawRect.bottom > screenRect.bottom) {
                    drawRect.offset(0, screenRect.bottom - drawRect.bottom);
                } else if (drawRect.top < screenRect.top) {
                    drawRect.offset(0, screenRect.top - drawRect.top);
                }
                if (drawRect.right > screenRect.right) {
                    // this means there's no enough space!
                    calculatePositions(gravities);
                    return;
                } else if (drawRect.left < screenRect.left) {
                    drawRect.offset(screenRect.left - drawRect.left, 0);
                }
            }
        } else if (gravity == TooltipManager.Gravity.LEFT) {
            drawRect.set(viewRect.left - width,
                    viewRect.centerY() - height / 2,
                    viewRect.left,
                    viewRect.centerY() + height / 2);

            point.x = viewRect.left;
            point.y = viewRect.centerY();

            if (restrict && !screenRect.contains(drawRect)) {
                if (drawRect.bottom > screenRect.bottom) {
                    drawRect.offset(0, screenRect.bottom - drawRect.bottom);
                } else if (drawRect.top < screenRect.top) {
                    drawRect.offset(0, screenRect.top - drawRect.top);
                }
                if (drawRect.left < screenRect.left) {
                    // this means there's no enough space!
                    this.gravity = TooltipManager.Gravity.RIGHT;
                    calculatePositions(gravities);
                    return;
                } else if (drawRect.right > screenRect.right) {
                    drawRect.offset(screenRect.right - drawRect.right, 0);
                }
            }
        } else if (this.gravity == TooltipManager.Gravity.CENTER) {
            drawRect.set(viewRect.centerX() - width / 2,
                    viewRect.centerY() - height / 2,
                    viewRect.centerX() - width / 2,
                    viewRect.centerY() + height / 2);

            point.x = viewRect.centerX();
            point.y = viewRect.centerY();

            if (restrict && !screenRect.contains(drawRect)) {
                if (drawRect.bottom > screenRect.bottom) {
                    drawRect.offset(0, screenRect.bottom - drawRect.bottom);
                } else if (drawRect.top < screenRect.top) {
                    drawRect.offset(0, screenRect.top - drawRect.top);
                }
                if (drawRect.right > screenRect.right) {
                    drawRect.offset(screenRect.right - drawRect.right, 0);
                } else if (drawRect.left < screenRect.left) {
                    drawRect.offset(screenRect.left - drawRect.left, 0);
                }
            }
        }
        //@formatter:on

        if (centerHorizontally) {
            drawRect.offset((screenRect.right - drawRect.right - drawRect.left) / 2, 0);
        }

        // translate the textview
        mView.setTranslationX(drawRect.left);
        mView.setTranslationY(drawRect.top);

        if (null != mDrawable) {
            // get the global rect for the textview
            mView.getGlobalVisibleRect(tempRect);

            point.x -= tempRect.left;
            point.y -= tempRect.top;

            if (!hideArrow) {
                if (gravity == TooltipManager.Gravity.LEFT || gravity == TooltipManager.Gravity.RIGHT) {
                    point.y -= padding / 2;
                } else if (gravity == TooltipManager.Gravity.TOP || gravity == TooltipManager.Gravity.BOTTOM) {
                    point.x -= padding / 2;
                }
            }

            mDrawable.setAnchor(gravity, hideArrow ? 0 : padding / 2);

            if (!this.hideArrow) {
                mDrawable.setDestinationPoint(point);
            }
        }
    }

    @Override
    public void setOffsetX(int x) {
        setTranslationX(x - viewRect.left);
    }

    @Override
    public void setOffsetY(int y) {
        setTranslationY(viewRect.top);
    }

    @Override
    public void offsetTo(final int x, final int y) {
        setTranslationX(x - viewRect.left);
        setTranslationY(y - viewRect.top);
    }

    @Override
    public boolean isAttached() {
        return mAttached;
    }

    void setText(final CharSequence text) {
        if (DBG) Log.i(TAG, "setText: " + text);
        this.text = text;
        if (null != mTextView) {
            mTextView.setText(Html.fromHtml((String) text));
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (mAnimation != null && mAnimation.isStarted()) return true;
        if (!mAttached || !mShowing || !isShown()) return false;

        if (DBG) Log.i(TAG, "onTouchEvent: " + event.getAction() + ", active: " + mActivated);

        final int action = event.getActionMasked();

        if (closePolicy == ClosePolicy.TouchOutside
                || closePolicy == ClosePolicy.TouchInside
                || closePolicy == ClosePolicy.TouchInsideExclusive
                || closePolicy == ClosePolicy.TouchOutsideExclusive
                ) {

            if (!mActivated) {
                if (DBG) Log.w(TAG, "not yet activated..., " + action);
                return true;
            }

            if (action == MotionEvent.ACTION_DOWN) {

                final boolean containsTouch = drawRect.contains((int) event.getX(), (int) event.getY());

                if (closePolicy == ClosePolicy.TouchInside || closePolicy == ClosePolicy.TouchInsideExclusive) {
                    if (containsTouch) {
                        onClose(true, true);
                        return true;
                    }
                    return closePolicy == ClosePolicy.TouchInsideExclusive;
                } else {
                    onClose(true, containsTouch);
                    return closePolicy == ClosePolicy.TouchOutsideExclusive || containsTouch;
                }
            }
        }

        return false;
    }

    private void onClose(boolean fromUser, boolean containsTouch) {
        if (DBG) Log.i(TAG, "onClose. fromUser: " + fromUser + ", containsTouch: " + containsTouch);

        if (null == getHandler()) return;
        if (!isAttached()) return;

        getHandler().removeCallbacks(hideRunnable);

        if (null != closeListener) {
            closeListener.onClose(this);
        }

        if (null != closeCallback) {
            closeCallback.onClosing(toolTipId, fromUser, containsTouch);
        }
    }

    private OnCloseListener closeListener;
    private OnToolTipListener tooltipListener;

    void setOnCloseListener(OnCloseListener listener) {
        this.closeListener = listener;
    }

    void setOnToolTipListener(OnToolTipListener listener) {
        this.tooltipListener = listener;
    }

    static interface OnCloseListener {
        void onClose(TooltipView layout);
    }

    static interface OnToolTipListener {
        void onHideCompleted(TooltipView layout);

        void onShowCompleted(TooltipView layout);

        void onShowFailed(TooltipView layout);
    }
}
