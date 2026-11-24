// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;

/** Bounded raster with transform-only zoom, two-finger pan and contiguous byte selection. */
final class ByteGridView extends View {
    private final ByteGrid.Result result;
    private final Bitmap bitmap;
    private final Paint paint = new Paint();
    private final RectF bounds = new RectF();
    final ByteGrid.Viewport viewport;
    private final Runnable changed;
    private final ScaleGestureDetector scaling;
    private final int touchSlop;
    private int anchor = -1, end = -1;
    private boolean extend = true, multiplePointers, dragging;
    private float downX, downY, lastFocusX, lastFocusY;
    private boolean lastFocus;

    ByteGridView(Context context, ByteGrid.Result result, Runnable changed) {
        super(context);
        this.result = result;
        this.changed = changed;
        viewport = new ByteGrid.Viewport(result.layout.width, result.layout.height);
        bitmap =
                Bitmap.createBitmap(
                        result.pixels,
                        result.layout.width,
                        result.layout.height,
                        Bitmap.Config.ARGB_8888);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaling =
                new ScaleGestureDetector(
                        context,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                viewport.zoomBy(
                                        detector.getScaleFactor(),
                                        detector.getFocusX(),
                                        detector.getFocusY());
                                updated();
                                return true;
                            }
                        });
        scaling.setQuickScaleEnabled(false);
        setContentDescription(
                result.layout.width
                        + " by "
                        + result.layout.height
                        + " "
                        + result.mode.legend
                        + ". Tap or drag to select consecutive blocks. "
                        + "Two fingers move or pinch to zoom. New starts another range.");
        setFocusable(true);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewport.resize(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap.isRecycled()) return;
        double scale = viewport.scale();
        bounds.set(
                (float) viewport.left(),
                (float) viewport.top(),
                (float) (viewport.left() + result.layout.width * scale),
                (float) (viewport.top() + result.layout.height * scale));
        paint.setFilterBitmap(false);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, null, bounds, paint);
        if (anchor < 0) return;
        int first = Math.min(anchor, end), last = Math.max(anchor, end);
        int columns = result.layout.width;
        int firstRow = first / columns, lastRow = last / columns;
        // At most three rectangles, even when all one million cells are selected.
        if (firstRow == lastRow) {
            selectionRect(canvas, first % columns, firstRow, last % columns + 1, firstRow + 1);
        } else {
            selectionRect(canvas, first % columns, firstRow, columns, firstRow + 1);
            if (lastRow > firstRow + 1) selectionRect(canvas, 0, firstRow + 1, columns, lastRow);
            selectionRect(canvas, 0, lastRow, last % columns + 1, lastRow + 1);
        }
        cellOutline(canvas, anchor, 0xffffffff);
        cellOutline(canvas, end, 0xffff6c6c);
    }

    private void selectionRect(Canvas canvas, int left, int top, int right, int bottom) {
        float scale = (float) viewport.scale();
        float x = (float) viewport.left(), y = (float) viewport.top();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x4431d7ff);
        canvas.drawRect(
                x + left * scale, y + top * scale, x + right * scale, y + bottom * scale, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xff5fe5ff);
        canvas.drawRect(
                x + left * scale, y + top * scale, x + right * scale, y + bottom * scale, paint);
    }

    private void cellOutline(Canvas canvas, int cell, int color) {
        float scale = (float) viewport.scale();
        float x = (float) viewport.left() + cell % result.layout.width * scale;
        float y = (float) viewport.top() + cell / result.layout.width * scale;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawRect(x, y, x + scale, y + scale, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                multiplePointers = false;
                dragging = false;
                lastFocus = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiplePointers = true;
                lastFocus = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    multiplePointers = true;
                    float x = 0, y = 0;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        x += event.getX(i);
                        y += event.getY(i);
                    }
                    x /= event.getPointerCount();
                    y /= event.getPointerCount();
                    if (lastFocus) viewport.pan(x - lastFocusX, y - lastFocusY);
                    lastFocusX = x;
                    lastFocusY = y;
                    lastFocus = true;
                    updated();
                } else if (!multiplePointers) {
                    if (!dragging
                            && Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) {
                        dragging = true;
                        choose(viewport.cellAt(downX, downY), !extend);
                    }
                    if (dragging) choose(viewport.cellAt(event.getX(), event.getY()), false);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                lastFocus = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!multiplePointers) {
                    choose(viewport.cellAt(event.getX(), event.getY()), !dragging && !extend);
                    performClick();
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        scaling.onTouchEvent(event);
        return true;
    }

    private void choose(int index, boolean fresh) {
        if (index < 0 || index >= result.layout.used) return;
        if (fresh || anchor < 0) anchor = index;
        if (end == index && !fresh) return;
        end = index;
        updated();
    }

    void select(int index) {
        result.layout.selection(index, index);
        choose(index, true);
    }

    void setSelection(int anchor, int end) {
        result.layout.selection(anchor, end);
        this.anchor = anchor;
        this.end = end;
        updated();
    }

    void clearSelection() {
        anchor = end = -1;
        updated();
    }

    void step(int direction) {
        if (end < 0) return;
        choose(Math.max(0, Math.min(result.layout.used - 1, end + direction)), !extend);
        viewport.centerOn(end);
        updated();
    }

    int anchor() {
        return anchor;
    }

    int end() {
        return end;
    }

    boolean extendsSelection() {
        return extend;
    }

    void setExtendsSelection(boolean extend) {
        this.extend = extend;
    }

    FileRange selection() {
        return anchor < 0 ? null : result.layout.selection(anchor, end);
    }

    void zoomBy(double factor) {
        viewport.zoomBy(factor, getWidth() / 2.0, getHeight() / 2.0);
        if (end >= 0) viewport.centerOn(end);
        updated();
    }

    void fit() {
        viewport.fit();
        updated();
    }

    Bundle saveInteractionState() {
        Bundle state = new Bundle();
        state.putString("sha256", result.sha256);
        state.putLong("size", result.layout.size);
        state.putInt("columns", result.layout.width);
        state.putInt("rows", result.layout.height);
        state.putString("mode", result.mode.name());
        state.putDouble("zoom", viewport.zoom);
        state.putDouble("centerX", viewport.centerX);
        state.putDouble("centerY", viewport.centerY);
        state.putInt("anchor", anchor);
        state.putInt("end", end);
        state.putBoolean("extend", extend);
        return state;
    }

    boolean restoreInteractionState(Bundle state) {
        if (state == null
                || !result.sha256.equals(state.getString("sha256"))
                || result.layout.size != state.getLong("size", -1)
                || result.layout.width != state.getInt("columns")
                || result.layout.height != state.getInt("rows")
                || !result.mode.name().equals(state.getString("mode"))) return false;
        int anchor = state.getInt("anchor", -1), end = state.getInt("end", -1);
        if ((anchor != -1 || end != -1)
                && (anchor < 0
                        || end < 0
                        || anchor >= result.layout.used
                        || end >= result.layout.used)) return false;
        if (!viewport.restore(
                state.getDouble("zoom", Double.NaN),
                state.getDouble("centerX", Double.NaN),
                state.getDouble("centerY", Double.NaN))) return false;
        this.anchor = anchor;
        this.end = end;
        extend = state.getBoolean("extend", true);
        updated();
        return true;
    }

    void dispose() {
        if (!bitmap.isRecycled()) bitmap.recycle();
    }

    private void updated() {
        invalidate();
        changed.run();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (end < 0 && result.layout.used > 0) select(0);
            else step(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
