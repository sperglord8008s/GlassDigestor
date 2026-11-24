// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;

/** Real grid widgets/gestures and state, independent of file acquisition or native engines. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ByteGridInteractionTest {
    private static final String SHA = "12".repeat(32);

    private ByteGrid.Result result(long size, int columns, int rows) {
        int count = columns * rows;
        int[] pixels = new int[count];
        Arrays.fill(pixels, 0xff123456);
        return new ByteGrid.Result(
                new ByteGrid.Layout(size, columns, rows),
                ByteGrid.Mode.ENTROPY,
                SHA,
                new double[count],
                pixels);
    }

    private ByteGridView view(ByteGrid.Result result) {
        ByteGridView view = new ByteGridView(RuntimeEnvironment.getApplication(), result, () -> {});
        view.layout(0, 0, 400, 240);
        return view;
    }

    private void touch(ByteGridView view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private void tap(ByteGridView view, int cell) {
        double scale = view.viewport.scale();
        float x = (float) (view.viewport.left() + (cell % view.viewport.columns + 0.5) * scale);
        float y = (float) (view.viewport.top() + (cell / view.viewport.columns + 0.5) * scale);
        touch(view, MotionEvent.ACTION_DOWN, x, y);
        touch(view, MotionEvent.ACTION_UP, x, y);
    }

    @Test
    public void tapsAndDragSelectOriginalContiguousBytesAcrossRowsAndFinalRemainder() {
        ByteGrid.Result result = result(103, 4, 2);
        ByteGridView view = view(result);
        tap(view, 2);
        tap(view, 5);
        assertEquals(2, view.anchor());
        assertEquals(5, view.end());
        assertEquals(25, view.selection().offset);
        assertEquals(52, view.selection().length);
        // Reverse extension retains the original anchor and includes every intervening block.
        tap(view, 0);
        assertEquals(2, view.anchor());
        assertEquals(0, view.end());
        assertEquals(0, view.selection().offset);
        assertEquals(38, view.selection().length);
        view.clearSelection();
        view.setExtendsSelection(false);
        touch(view, MotionEvent.ACTION_DOWN, 250, 70); // Cell 2, letterboxed 4:2 raster.
        touch(view, MotionEvent.ACTION_MOVE, 350, 170); // Cell 7, across a row boundary.
        touch(view, MotionEvent.ACTION_UP, 350, 170);
        assertEquals(2, view.anchor());
        assertEquals(7, view.end());
        assertEquals(103, view.selection().offset + view.selection().length);
        view.dispose();
    }

    @Test
    public void letterboxUnusedCellsAndKeyboardSteppingPreserveExactSelection() {
        ByteGridView view = view(result(3, 4, 2));
        touch(view, MotionEvent.ACTION_DOWN, 1, 1);
        touch(view, MotionEvent.ACTION_UP, 1, 1);
        assertNull(view.selection());
        tap(view, 1);
        tap(view, 7); // Magenta blocks are not valid endpoints.
        assertEquals(1, view.anchor());
        assertEquals(1, view.end());
        view.onKeyDown(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(1, view.anchor());
        assertEquals(2, view.end());
        assertEquals(1, view.selection().offset);
        assertEquals(2, view.selection().length);
        view.step(1);
        assertEquals(2, view.end());
        view.setExtendsSelection(false);
        view.step(-1);
        assertEquals(1, view.anchor());
        assertEquals(1, view.end());
        view.dispose();
    }

    private void twoPointers(
            ByteGridView view,
            long start,
            long time,
            int action,
            float x1,
            float y1,
            float x2,
            float y2) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = i == 0 ? x1 : x2;
            coordinates[i].y = i == 0 ? y1 : y2;
            coordinates[i].pressure = coordinates[i].size = 1;
        }
        MotionEvent event =
                MotionEvent.obtain(
                        start,
                        time,
                        action,
                        2,
                        properties,
                        coordinates,
                        0,
                        0,
                        1,
                        1,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0);
        view.onTouchEvent(event);
        event.recycle();
    }

    @Test
    public void actualPinchAndTwoFingerPanKeepTheSelectedRange() {
        ByteGridView view = view(result(100000, 100, 100));
        view.setSelection(3000, 3050);
        long start = SystemClock.uptimeMillis();
        touch(view, MotionEvent.ACTION_DOWN, 100, 120);
        twoPointers(
                view,
                start,
                start + 10,
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                100,
                120,
                220,
                120);
        twoPointers(view, start, start + 30, MotionEvent.ACTION_MOVE, 70, 120, 250, 120);
        twoPointers(view, start, start + 50, MotionEvent.ACTION_MOVE, 40, 120, 280, 120);
        twoPointers(
                view,
                start,
                start + 70,
                MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                40,
                120,
                280,
                120);
        touch(view, MotionEvent.ACTION_UP, 40, 120);
        assertTrue("Real ScaleGestureDetector did not zoom", view.viewport.zoom > 1);
        assertEquals(3000, view.anchor());
        assertEquals(3050, view.end());
        view.zoomBy(4);
        double before = view.viewport.centerX;
        touch(view, MotionEvent.ACTION_DOWN, 80, 120);
        twoPointers(
                view,
                start,
                start + 100,
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                80,
                120,
                240,
                120);
        twoPointers(view, start, start + 120, MotionEvent.ACTION_MOVE, 60, 120, 220, 120);
        twoPointers(view, start, start + 140, MotionEvent.ACTION_MOVE, 40, 120, 200, 120);
        assertTrue(
                "Two fingers did not move through the zoomed grid", view.viewport.centerX > before);
        assertEquals(3000, view.anchor());
        assertEquals(3050, view.end());
        view.dispose();
    }

    @Test
    public void retainedViewportAndSelectionRequireExactSourceLayoutAndMode() {
        ByteGrid.Result result = result(4097, 17, 13);
        ByteGridView original = view(result);
        original.setSelection(198, 220);
        original.zoomBy(16);
        original.viewport.pan(-30, -20);
        Bundle state = original.saveInteractionState();
        ByteGridView restored =
                new ByteGridView(RuntimeEnvironment.getApplication(), result, () -> {});
        assertTrue(restored.restoreInteractionState(state)); // Before the first Android layout.
        restored.layout(0, 0, 400, 240);
        assertEquals(original.viewport.zoom, restored.viewport.zoom, 0);
        assertEquals(original.viewport.centerX, restored.viewport.centerX, 1e-12);
        assertEquals(original.viewport.centerY, restored.viewport.centerY, 1e-12);
        assertEquals(198, restored.anchor());
        assertEquals(220, restored.end());
        assertEquals(4097, restored.selection().offset + restored.selection().length);
        Bundle foreign = new Bundle(state);
        foreign.putString("sha256", "34".repeat(32));
        assertFalse(restored.restoreInteractionState(foreign));
        foreign = new Bundle(state);
        foreign.putInt("columns", 18);
        assertFalse(restored.restoreInteractionState(foreign));
        foreign = new Bundle(state);
        foreign.putString("mode", "BYTES");
        assertFalse(restored.restoreInteractionState(foreign));
        foreign = new Bundle(state);
        foreign.putInt("end", result.layout.used);
        assertFalse(restored.restoreInteractionState(foreign));
        foreign = new Bundle(state);
        foreign.putDouble("zoom", Double.NaN);
        assertFalse(restored.restoreInteractionState(foreign));
        assertEquals(198, restored.anchor());
        assertEquals(220, restored.end());
        original.dispose();
        restored.dispose();
    }

    @Test
    public void maximumZoomAndMillionBlockSelectionKeepOnlyTheOriginalRaster() {
        ByteGrid.Result result = result(256L * 1024 * 1024, 2048, 512);
        ByteGridView view = view(result);
        view.setSelection(result.layout.used - 1, 0);
        view.zoomBy(Double.MAX_VALUE);
        assertEquals(ByteGrid.Viewport.MAX_ZOOM, view.viewport.zoom, 0);
        assertEquals(result.layout.size, view.selection().length);
        Bitmap bitmap = ReflectionHelpers.getField(view, "bitmap");
        assertEquals(2048, bitmap.getWidth());
        assertEquals(512, bitmap.getHeight());
        assertTrue(bitmap.getAllocationByteCount() <= 4 * 1024 * 1024);
        Bitmap display = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(display));
        assertEquals(0, view.viewport.cellAt(0.5, 0.5));
        assertSame(bitmap, ReflectionHelpers.getField(view, "bitmap"));
        display.recycle();
        view.dispose();
    }
}
