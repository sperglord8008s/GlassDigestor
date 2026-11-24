// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;

/** A ScrollView with a persistent, hit-tested thumb for long generated columns and details. */
class DraggableScrollView extends ScrollView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumb = new RectF();
    private int pointer = MotionEvent.INVALID_POINTER_ID;
    private float grabOffset;
    private boolean dragging;

    DraggableScrollView(Context context) {
        this(context, null);
    }

    DraggableScrollView(Context context, AttributeSet attributes) {
        super(context, attributes);
        // The ordinary ScrollView bar is an indicator; this class supplies the draggable bar.
        setVerticalScrollBarEnabled(false);
        setWillNotDraw(false);
    }

    private int dp(int value) {
        return WorkbenchUi.dp(getContext(), value);
    }

    private int range() {
        if (getChildCount() == 0) return 0;
        View child = getChildAt(0);
        int content = child.getHeight();
        ViewGroup.LayoutParams layout = child.getLayoutParams();
        if (layout instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layout;
            content += margins.topMargin + margins.bottomMargin;
        }
        return Math.max(0, content - (getHeight() - getPaddingTop() - getPaddingBottom()));
    }

    private boolean geometry() {
        int range = range();
        float top = getPaddingTop() + dp(3);
        float bottom = getHeight() - getPaddingBottom() - dp(3);
        float track = bottom - top;
        if (range <= 0 || track <= 0 || getWidth() <= 0) {
            thumb.setEmpty();
            return false;
        }
        float viewport = getHeight() - getPaddingTop() - getPaddingBottom();
        float height = Math.min(track, Math.max(dp(36), track * viewport / (viewport + range)));
        float fraction = Math.max(0f, Math.min(1f, (float) getScrollY() / range));
        float y = top + (track - height) * fraction;
        float edge = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? dp(3) : getWidth() - dp(7);
        thumb.set(edge, y, edge + dp(4), y + height);
        return true;
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        if (!geometry()) return;
        int saved = canvas.save();
        // Foreground drawing shares the scrolled content coordinates; keep the bar in the viewport.
        canvas.translate(getScrollX(), getScrollY());
        paint.setColor(getContext().getColor(R.color.workbench_divider));
        float x = thumb.centerX();
        canvas.drawRect(
                x - dp(1),
                getPaddingTop() + dp(3),
                x + dp(1),
                getHeight() - getPaddingBottom() - dp(3),
                paint);
        paint.setColor(
                getContext()
                        .getColor(dragging ? R.color.workbench_text : R.color.workbench_secondary));
        canvas.drawRoundRect(thumb, dp(2), dp(2), paint);
        canvas.restoreToCount(saved);
    }

    private boolean begin(MotionEvent event) {
        if (!isEnabled() || !geometry()) return false;
        float x = event.getX(), y = event.getY();
        boolean edge =
                getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                        ? x >= 0 && x <= dp(24)
                        : x >= getWidth() - dp(24) && x <= getWidth();
        // Text selection and ordinary content scrolling outside the thumb keep their normal path.
        if (!edge || y < thumb.top - dp(4) || y > thumb.bottom + dp(4)) return false;
        pointer = event.getPointerId(0);
        grabOffset = y - thumb.top;
        dragging = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && begin(event)) return true;
        if (dragging) return true;
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !dragging) begin(event);
        if (!dragging) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Let ScrollView abort an in-flight fling and establish its ordinary touch state.
                super.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(pointer);
                if (index < 0) {
                    finish(event);
                    return true;
                }
                if (geometry()) {
                    float travel =
                            getHeight()
                                    - getPaddingTop()
                                    - getPaddingBottom()
                                    - dp(6)
                                    - thumb.height();
                    if (travel > 0) {
                        float fraction =
                                (event.getY(index) - grabOffset - getPaddingTop() - dp(3)) / travel;
                        scrollTo(
                                getScrollX(),
                                Math.round(Math.max(0f, Math.min(1f, fraction)) * range()));
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int removed = event.getActionIndex();
                if (event.getPointerId(removed) == pointer) {
                    int next = removed == 0 ? 1 : 0;
                    pointer = event.getPointerId(next);
                    geometry();
                    grabOffset = event.getY(next) - thumb.top;
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                finish(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finish(event);
                return true;
            default:
                return true;
        }
    }

    private void finish(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
        dragging = false;
        pointer = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        dragging = false;
        pointer = MotionEvent.INVALID_POINTER_ID;
        super.onDetachedFromWindow();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        int range = range();
        if (range > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.setRangeInfo(
                    AccessibilityNodeInfo.RangeInfo.obtain(
                            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                            0,
                            range,
                            getScrollY()));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                && arguments != null
                && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)
                && range() > 0) {
            float position =
                    arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
            if (!Float.isFinite(position)) return false;
            scrollTo(getScrollX(), Math.round(Math.max(0, Math.min(range(), position))));
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
