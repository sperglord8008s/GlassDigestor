// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Selection;
import android.text.Spannable;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

/** Captured text, clipboard and browser intents; no external service is contacted. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
public final class TextActionsWorkbenchTest {
    private ActivityController<Activity> controller;
    private Activity activity;

    @Before
    public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        activity.getSharedPreferences("workbench-text-actions", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @After
    public void cleanup() {
        controller.pause().stop().destroy();
    }

    @Test
    public void everyEngineKeepsUntrustedTextInsideItsFixedHttpsDestination() {
        String text = "é 研究😀 + & ? = / # % <script> javascript:fixture\nsecond line";
        String[] hosts = {
            "duckduckgo.com",
            "www.google.com",
            "www.bing.com",
            "search.brave.com",
            "translate.google.com",
            "www.bing.com",
            "www.deepl.com"
        };
        for (TextActions.Engine engine : TextActions.Engine.values()) {
            Uri uri = engine.uri(text);
            assertEquals("https", uri.getScheme());
            assertEquals(hosts[engine.ordinal()], uri.getHost());
            assertFalse(uri.toString().contains("\n"));
            if (engine == TextActions.Engine.DEEPL) {
                assertEquals("auto/en/" + Uri.encode(text), uri.getEncodedFragment());
            } else assertEquals(text, uri.getQueryParameter(engine.translation ? "text" : "q"));
            if (engine == TextActions.Engine.GOOGLE_TRANSLATE)
                assertEquals("en", uri.getQueryParameter("tl"));
            if (engine == TextActions.Engine.BING_TRANSLATE)
                assertEquals("en", uri.getQueryParameter("to"));
        }
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void clipboardPreservesRawUtf16AndRefusesAnOversizedTransferWithoutReplacingIt() {
        String raw = "before\u202Eafter\0\uD800 end";
        TextActions actions = new TextActions(activity);
        actions.copy(raw);
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals(raw, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertTrue(WorkbenchUi.visible(raw).contains("\\u202E"));
        assertTrue(WorkbenchUi.visible(raw).contains("\\uD800"));
        assertEquals("😀", WorkbenchUi.visible("😀"));
        assertEquals("\\uDB40\\uDC01", WorkbenchUi.visible("\uDB40\uDC01"));
        actions.copy("x".repeat(TextActions.MAX_CLIPBOARD_UNITS + 1));
        assertEquals(raw, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertTrue(
                ShadowToast.getTextOfLatestToast().toString().contains("clipboard transfer limit"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    @Config(sdk = 35)
    public void modernClipboardDoesNotPreviewPotentiallySensitiveFileText() {
        new TextActions(activity).copy("private fixture text");
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertTrue(
                clipboard
                        .getPrimaryClip()
                        .getDescription()
                        .getExtras()
                        .getBoolean(ClipDescription.EXTRA_IS_SENSITIVE));
    }

    @Test
    public void cancellingOrInvalidExternalTextDoesNotLaunchOrPersistTheText() {
        TextActions actions = new TextActions(activity);
        actions.choose("private fixture query", "Captured string", true);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.cancel();
        assertTrue(activity.getSharedPreferences("workbench-text-actions", 0).getAll().isEmpty());
        actions.choose("\uD800", "Captured string", false);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null, 0, 0);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("unpaired UTF-16"));
        for (String invalid : new String[] {"", "x".repeat(8193), "研究".repeat(2000)}) {
            try {
                TextActions.Engine.GOOGLE.uri(invalid);
                fail("Unsupported URL text was silently accepted/truncated");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void selectionActionsRetainSystemCopyAndSendOnlyTheSelectedDisplayedSpan() {
        TextView text = WorkbenchUi.prose(activity, "prefix 研究 suffix");
        Selection.setSelection((Spannable) text.getText(), 7, 9);
        Menu menu = new PopupMenu(activity, text).getMenu();
        ActionMode.Callback callback = text.getCustomSelectionActionModeCallback();
        TestMode mode = new TestMode(menu);
        assertTrue(callback.onCreateActionMode(mode, menu));
        assertFalse(callback.onActionItemClicked(mode, menu.add(0, android.R.id.copy, 0, "Copy")));
        assertTrue(
                callback.onActionItemClicked(
                        mode, menu.findItem(R.id.workbench_translate_selection)));
        assertTrue(mode.finished);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null, 0, 0);
        Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity();
        Intent view = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("研究", view.getData().getQueryParameter("text"));
        assertEquals("en", view.getData().getQueryParameter("tl"));
    }

    private final class TestMode extends ActionMode {
        private final Menu menu;
        boolean finished;

        TestMode(Menu menu) {
            this.menu = menu;
        }

        @Override
        public void setTitle(CharSequence title) {}

        @Override
        public void setTitle(int title) {}

        @Override
        public void setSubtitle(CharSequence title) {}

        @Override
        public void setSubtitle(int title) {}

        @Override
        public void setCustomView(View view) {}

        @Override
        public void invalidate() {}

        @Override
        public void finish() {
            finished = true;
        }

        @Override
        public Menu getMenu() {
            return menu;
        }

        @Override
        public CharSequence getTitle() {
            return "";
        }

        @Override
        public CharSequence getSubtitle() {
            return "";
        }

        @Override
        public View getCustomView() {
            return null;
        }

        @Override
        public MenuInflater getMenuInflater() {
            return new MenuInflater(activity);
        }
    }
}
