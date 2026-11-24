// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Explicit local copy or external-browser handoff. Never submits text in the background. */
final class TextActions {
    static final int MAX_CLIPBOARD_UNITS = 128 * 1024;
    static final int MAX_URL_UNITS = 8192;

    enum Engine {
        DUCKDUCKGO("DuckDuckGo", false),
        GOOGLE("Google", false),
        BING("Bing", false),
        BRAVE("Brave Search", false),
        GOOGLE_TRANSLATE("Google Translate → English", true),
        BING_TRANSLATE("Bing Translator → English", true),
        DEEPL("DeepL → English", true);

        final String label;
        final boolean translation;

        Engine(String label, boolean translation) {
            this.label = label;
            this.translation = translation;
        }

        Uri uri(String text) {
            if (text.isEmpty() || text.length() > MAX_URL_UNITS)
                throw new IllegalArgumentException(
                        "Select a shorter span for the browser; no text was sent or truncated.");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isHighSurrogate(c)
                        && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) i++;
                else if (Character.isSurrogate(c))
                    throw new IllegalArgumentException(
                            "This text has an unpaired UTF-16 surrogate. Copy/export its exact"
                                    + " value instead; no text was sent.");
            }
            Uri result;
            switch (this) {
                case DUCKDUCKGO:
                    result = query("https://duckduckgo.com/", "q", text);
                    break;
                case GOOGLE:
                    result = query("https://www.google.com/search", "q", text);
                    break;
                case BING:
                    result = query("https://www.bing.com/search", "q", text);
                    break;
                case BRAVE:
                    result = query("https://search.brave.com/search", "q", text);
                    break;
                case GOOGLE_TRANSLATE:
                    result =
                            Uri.parse("https://translate.google.com/")
                                    .buildUpon()
                                    .appendQueryParameter("sl", "auto")
                                    .appendQueryParameter("tl", "en")
                                    .appendQueryParameter("text", text)
                                    .appendQueryParameter("op", "translate")
                                    .build();
                    break;
                case BING_TRANSLATE:
                    result =
                            Uri.parse("https://www.bing.com/translator")
                                    .buildUpon()
                                    .appendQueryParameter("from", "auto-detect")
                                    .appendQueryParameter("to", "en")
                                    .appendQueryParameter("text", text)
                                    .build();
                    break;
                default:
                    result =
                            Uri.parse("https://www.deepl.com/translator")
                                    .buildUpon()
                                    .encodedFragment("auto/en/" + Uri.encode(text))
                                    .build();
            }
            if (result.toString().length() > MAX_URL_UNITS)
                throw new IllegalArgumentException(
                        "The encoded browser URL is too long. Copy the text or select a shorter"
                                + " span; no text was sent or truncated.");
            return result;
        }

        private static Uri query(String base, String key, String text) {
            return Uri.parse(base).buildUpon().appendQueryParameter(key, text).build();
        }
    }

    private final Activity activity;

    TextActions(Activity activity) {
        this.activity = activity;
    }

    void copy(String text) {
        if (activity.isDestroyed()) return;
        if (text.length() > MAX_CLIPBOARD_UNITS) {
            notice(
                    "String exceeds the clipboard transfer limit. Export its complete decoded value"
                            + " or select a shorter span; nothing was truncated.");
            return;
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            ClipData clip = ClipData.newPlainText("Captured text", text);
            if (Build.VERSION.SDK_INT >= 33) {
                PersistableBundle flags = new PersistableBundle();
                flags.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                clip.getDescription().setExtras(flags);
            }
            clipboard.setPrimaryClip(clip);
            notice("Copied exact text (" + text.length() + " UTF-16 units)");
        } catch (RuntimeException unavailable) {
            notice(
                    "Clipboard could not accept the text. Use the complete text export or select a"
                            + " shorter span.");
        }
    }

    void choose(String text, String scope, boolean translation) {
        if (activity.isDestroyed()) return;
        String preference = translation ? "translation-engine" : "search-engine";
        var preferences =
                activity.getSharedPreferences("workbench-text-actions", Context.MODE_PRIVATE);
        String previous = preferences.getString(preference, "");
        List<Engine> engines = new ArrayList<>();
        for (Engine engine : Engine.values())
            if (engine.translation == translation) engines.add(engine);
        engines.sort(
                (a, b) -> Boolean.compare(!a.name().equals(previous), !b.name().equals(previous)));
        String[] names = new String[engines.size()];
        for (int i = 0; i < names.length; i++) names[i] = engines.get(i).label;
        TextView explanation =
                WorkbenchUi.prose(
                        activity,
                        scope
                                + " · "
                                + text.length()
                                + " UTF-16 units\n"
                                + WorkbenchUi.preview(text, 180)
                                + "\n\n"
                                + "Choosing an engine opens a browser and gives that external"
                                + " service this text. Nothing is sent until you choose. Only the"
                                + " engine preference is saved.");
        int padding = WorkbenchUi.dp(activity, 12);
        explanation.setPadding(padding, 0, padding, 0);
        new AlertDialog.Builder(activity)
                .setTitle(
                        translation
                                ? "Translate to English · choose engine"
                                : "Search text · choose engine")
                .setView(explanation)
                .setItems(
                        names,
                        (dialog, index) -> {
                            Engine engine = engines.get(index);
                            try {
                                Uri uri = engine.uri(text);
                                if (activity.isDestroyed()) return;
                                Intent view =
                                        new Intent(Intent.ACTION_VIEW, uri)
                                                .addCategory(Intent.CATEGORY_BROWSABLE);
                                activity.startActivity(
                                        Intent.createChooser(
                                                view, engine.label + " · choose browser"));
                                preferences.edit().putString(preference, engine.name()).apply();
                            } catch (IllegalArgumentException failure) {
                                notice(failure.getMessage());
                            } catch (RuntimeException unavailable) {
                                notice(
                                        "No browser accepted this action. Copy the text to use"
                                                + " another application.");
                            }
                        })
                .setNeutralButton("Copy instead", (dialog, which) -> copy(text))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void notice(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    /** Standard Copy remains; additional actions clearly operate on displayed selected text. */
    static void selectionMenu(TextView view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return;
            context = base;
        }
        if (!(context instanceof Activity)) return;
        TextActions actions = new TextActions((Activity) context);
        view.setCustomSelectionActionModeCallback(
                new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        menu.add(0, R.id.workbench_search_selection, 10, "Search selection");
                        menu.add(
                                0,
                                R.id.workbench_translate_selection,
                                11,
                                "Translate selection → English");
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        boolean translate = item.getItemId() == R.id.workbench_translate_selection;
                        if (!translate && item.getItemId() != R.id.workbench_search_selection)
                            return false;
                        int start = view.getSelectionStart(), end = view.getSelectionEnd();
                        if (start >= 0 && end >= 0 && start != end) {
                            String selected =
                                    view.getText()
                                            .subSequence(Math.min(start, end), Math.max(start, end))
                                            .toString();
                            mode.finish();
                            actions.choose(selected, "Selected display text", translate);
                        }
                        return true;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {}
                });
    }
}
