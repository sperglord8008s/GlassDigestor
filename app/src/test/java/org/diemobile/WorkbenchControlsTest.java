// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Real menu actions, content coordinates and native adapter-position fast-scroll gestures. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class WorkbenchControlsTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private long clock = 1000;

    @Before
    public void create() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        activity.getSharedPreferences("workbench-presentation", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @After
    public void destroy() {
        controller.pause().stop().destroy();
    }

    private void layout(View view, int width, int height) {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private void touch(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1000, clock += 20, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private void choose(LinearLayout controls, int action) {
        controls.findViewWithTag("explanation-options").performClick();
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertEquals(2, popup.getMenu().size());
        assertTrue(popup.getMenu().performIdentifierAction(action, 0));
        popup.dismiss();
    }

    @Test
    public void compactMenuAndIndividualOverridesKeepFactsAndToolGroupsVisible() {
        ExpandableHelp help = new ExpandableHelp(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls = help.controls(activity);
        root.addView(controls);
        TextView fact = WorkbenchUi.fact(activity, "Offset", "0x1234", true);
        root.addView(fact);
        help.paragraph(
                activity,
                root,
                "meaning",
                "Offset meaning",
                "Exact physical byte offset.",
                true,
                false);
        Button definition = (Button) root.getChildAt(2);
        View explanation = root.findViewWithTag("explanation:meaning");
        help.paragraph(activity, root, "tools", "Tools", "Available tools", false, true);
        View tools = root.findViewWithTag("explanation:tools");
        activity.setContentView(root);
        layout(root, 360, 640);
        assertEquals(1, controls.getChildCount());
        assertEquals(WorkbenchUi.dp(activity, 40), controls.getChildAt(0).getWidth());
        assertTrue(definition.getWidth() < root.getWidth());
        assertEquals(View.GONE, explanation.getVisibility());

        choose(controls, ExpandableHelp.EXPAND_ALL);
        assertEquals(View.VISIBLE, explanation.getVisibility());
        definition.performClick();
        assertEquals(View.GONE, explanation.getVisibility());
        assertEquals(View.VISIBLE, fact.getVisibility());
        assertEquals(View.VISIBLE, tools.getVisibility());
        assertEquals("Offset: 0x1234", fact.getText().toString());

        // Bind fresh views to the persisted individual choice, then override it globally.
        ExpandableHelp restored = new ExpandableHelp(activity);
        LinearLayout next = new LinearLayout(activity);
        next.setOrientation(LinearLayout.VERTICAL);
        restored.paragraph(
                activity,
                next,
                "meaning",
                "Offset meaning",
                "Exact physical byte offset.",
                true,
                false);
        View restoredBody = next.findViewWithTag("explanation:meaning");
        assertEquals(View.GONE, restoredBody.getVisibility());
        restored.setAll(true);
        assertEquals(View.VISIBLE, restoredBody.getVisibility());
        help.refresh();
        choose(controls, ExpandableHelp.COLLAPSE_ALL);
        assertEquals(View.GONE, explanation.getVisibility());
        assertEquals(View.VISIBLE, tools.getVisibility());
    }

    @Test
    public void scrollThumbDragsToActualEndsWithoutTakingContentTapsOrSelection() {
        DraggableScrollView scroll = WorkbenchUi.scroll(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        AtomicInteger tapped = new AtomicInteger();
        for (int i = 0; i < 100; i++) {
            TextView row = WorkbenchUi.prose(activity, "Original row " + i);
            row.setOnTouchListener(
                    (view, event) -> {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP)
                            tapped.incrementAndGet();
                        return false;
                    });
            rows.addView(row, new LinearLayout.LayoutParams(-1, 48));
        }
        scroll.addView(rows);
        activity.setContentView(scroll);
        layout(scroll, 360, 240);
        int range = rows.getHeight() - scroll.getHeight();
        assertTrue(range > 4000);

        touch(scroll, MotionEvent.ACTION_DOWN, 355, 18);
        touch(scroll, MotionEvent.ACTION_MOVE, 355, 239);
        touch(scroll, MotionEvent.ACTION_UP, 355, 239);
        assertEquals(range, scroll.getScrollY());
        touch(scroll, MotionEvent.ACTION_DOWN, 355, 220);
        touch(scroll, MotionEvent.ACTION_MOVE, 355, 0);
        touch(scroll, MotionEvent.ACTION_UP, 355, 0);
        assertEquals(0, scroll.getScrollY());

        touch(scroll, MotionEvent.ACTION_DOWN, 30, 20);
        touch(scroll, MotionEvent.ACTION_UP, 30, 20);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, tapped.get());
        TextView first = (TextView) rows.getChildAt(0);
        assertTrue(first.isTextSelectable());
        android.text.Selection.setSelection((android.text.Spannable) first.getText(), 0, 8);
        assertEquals(
                "Original",
                first.getText()
                        .subSequence(first.getSelectionStart(), first.getSelectionEnd())
                        .toString());
        assertEquals(0, scroll.getScrollY());
    }

    @Test
    public void rtlThumbAndAccessiblePositionChangeRealContentPosition() {
        DraggableScrollView scroll = WorkbenchUi.scroll(activity);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView text = WorkbenchUi.prose(activity, "Original content");
        text.setMinHeight(3000);
        scroll.addView(text, new android.widget.FrameLayout.LayoutParams(-1, 3000));
        activity.setContentView(scroll);
        layout(scroll, 360, 240);
        touch(scroll, MotionEvent.ACTION_DOWN, 4, 18);
        touch(scroll, MotionEvent.ACTION_MOVE, 4, 239);
        touch(scroll, MotionEvent.ACTION_CANCEL, 4, 239);
        assertEquals(2760, scroll.getScrollY());

        Bundle target = new Bundle();
        target.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 1234);
        assertTrue(
                scroll.performAccessibilityAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),
                        target));
        assertEquals(1234, scroll.getScrollY());
    }

    @Test
    public void nativeListAndGridThumbsMoveAdapterPositionAndRetainSelectableRecords() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) rows.add("Record " + i);
        for (AbsListView list :
                new AbsListView[] {new ListView(activity), new GridView(activity)}) {
            if (list instanceof GridView) ((GridView) list).setNumColumns(2);
            WorkbenchUi.fastScroll(list);
            list.setAdapter(WorkbenchUi.adapter(activity, rows, value -> value));
            activity.setContentView(list);
            layout(list, 360, 240);
            assertEquals(0, list.getFirstVisiblePosition());
            Object scroller = ReflectionHelpers.getField(list, "mFastScroll");
            ImageView thumb = ReflectionHelpers.getField(scroller, "mThumbImage");
            assertTrue(thumb.getWidth() > 0);
            assertTrue(thumb.getHeight() > 0);
            float x = thumb.getX() + thumb.getWidth() / 2f;
            float y = thumb.getY() + thumb.getHeight() / 2f;
            touch(list, MotionEvent.ACTION_DOWN, x, y);
            touch(list, MotionEvent.ACTION_MOVE, x, 239);
            touch(list, MotionEvent.ACTION_UP, x, 239);
            layout(list, 360, 240);
            assertTrue(
                    "Native thumb did not move adapter position",
                    list.getFirstVisiblePosition() > 900);
            assertEquals(1000, list.getAdapter().getCount());
            assertEquals("Record 999", list.getAdapter().getItem(999));
            AtomicInteger selected = new AtomicInteger(-1);
            list.setOnItemClickListener((parent, view, position, id) -> selected.set(position));
            View first = list.getChildAt(0);
            int position = list.getFirstVisiblePosition();
            list.performItemClick(first, position, list.getAdapter().getItemId(position));
            assertEquals(position, selected.get());
        }
    }
}
