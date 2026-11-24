// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Shared dense hierarchy: section, record identity, labelled facts, then explanatory prose. */
final class WorkbenchUi {
    private static final Map<AbsListView, Boolean> fastScrollBindings = new WeakHashMap<>();

    private WorkbenchUi() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, CharSequence value, int size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                context.getColor(
                        size <= 12 ? R.color.workbench_secondary : R.color.workbench_text));
        if (size >= 16) view.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        TextActions.selectionMenu(view);
        return view;
    }

    static TextView prose(Context context, String value) {
        TextView view = text(context, visible(value), 14);
        view.setLineSpacing(dp(context, 2), 1.08f);
        view.setTextIsSelectable(true);
        return view;
    }

    static TextView section(Context context, String title) {
        TextView view = text(context, inline(title), 14);
        view.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        view.setTextColor(context.getColor(R.color.workbench_accent));
        view.setPadding(0, dp(context, 12), 0, dp(context, 6));
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }

    static void divider(Context context, LinearLayout parent) {
        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.workbench_divider));
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(context, 1)));
    }

    static void list(Context context, ListView list) {
        list.setDivider(new ColorDrawable(context.getColor(R.color.workbench_divider)));
        list.setDividerHeight(Math.max(1, dp(context, 1)));
        list.setCacheColorHint(context.getColor(R.color.workbench_background));
        fastScroll(list);
    }

    /** Native fast-scroll uses adapter positions, retaining paging and every supplied row. */
    static void fastScroll(AbsListView list) {
        list.setVerticalScrollBarEnabled(true);
        list.setScrollbarFadingEnabled(false);
        list.setFastScrollEnabled(true);
        list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        if (fastScrollBindings.put(list, Boolean.TRUE) == null) {
            list.addOnLayoutChangeListener(
                    (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            updateFastScroll(list));
            ViewTreeObserver.OnGlobalLayoutListener layout = () -> updateFastScroll(list);
            list.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    view.getViewTreeObserver().addOnGlobalLayoutListener(layout);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
                }
            });
            if (list.isAttachedToWindow())
                list.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        }
        updateFastScroll(list);
    }

    private static void updateFastScroll(AbsListView list) {
        // Android's default fast-scroll threshold is four screens. Force its real thumb for
        // shorter overflowing pages too, while leaving fitting content free of an idle track.
        boolean overflow = list.canScrollList(-1) || list.canScrollList(1);
        if (list.isFastScrollAlwaysVisible() != overflow)
            list.setFastScrollAlwaysVisible(overflow);
    }

    static <T extends AbsListView> T scrolling(T list) {
        fastScroll(list);
        return list;
    }

    /** Generated columns and detail pages get a thumb which changes the actual scroll position. */
    static DraggableScrollView scroll(Context context) {
        return new DraggableScrollView(context);
    }

    static TextView row(Context context, View recycled, CharSequence value) {
        TextView view = recycled instanceof TextView ? (TextView) recycled : text(context, "", 13);
        view.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        view.setTextSize(13);
        view.setTextColor(context.getColor(R.color.workbench_text));
        view.setPadding(dp(context, 8), dp(context, 9), dp(context, 8), dp(context, 9));
        view.setLineSpacing(dp(context, 2), 1.02f);
        view.setMinHeight(dp(context, 48));
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(false);
        view.setText(value);
        return view;
    }

    static <T> ArrayAdapter<T> adapter(
            Context context, List<T> values, Function<T, CharSequence> format) {
        return new ArrayAdapter<T>(context, android.R.layout.simple_list_item_1, values) {
            @Override
            public View getView(int position, View old, ViewGroup parent) {
                return row(context, old, format.apply(getItem(position)));
            }
        };
    }

    static TextView fact(Context context, String label, String value, boolean code) {
        TextView view = text(context, new Lines(context).field(label, value, code).build(), 13);
        view.setTextIsSelectable(true);
        view.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return view;
    }

    /** Only display text is escaped; captured values, search and exports remain untouched. */
    static String visible(String value) {
        StringBuilder output = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean paired =
                    Character.isHighSurrogate(c)
                            && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1));
            if (paired) {
                char low = value.charAt(i + 1);
                if (Character.getType(Character.toCodePoint(c, low)) == Character.FORMAT)
                    output.append(String.format(Locale.ROOT, "\\u%04X\\u%04X", (int) c, (int) low));
                else output.append(c).append(low);
                i++;
                continue;
            }
            if (Character.getType(c) == Character.FORMAT
                    || Character.isSurrogate(c)
                    || Character.isISOControl(c) && c != '\n' && c != '\t')
                output.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
            else output.append(c);
        }
        return output.toString();
    }

    static String inline(String value) {
        return visible(value).replace("\n", "\\n").replace("\t", "\\t");
    }

    static String preview(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = limit;
        if (end > 0
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) end--;
        return value.substring(0, end) + "… [display preview]";
    }

    /** Spans are applied to escaped display ranges, never offsets in the captured bytes. */
    static final class Lines {
        private final Context context;
        private final SpannableStringBuilder text = new SpannableStringBuilder();

        Lines(Context context) {
            this.context = context;
        }

        private int next() {
            if (text.length() != 0) text.append('\n');
            return text.length();
        }

        private void span(Object value, int start, int end) {
            if (end > start) text.setSpan(value, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Lines heading(String value) {
            int start = next();
            text.append(inline(value));
            span(new StyleSpan(Typeface.BOLD), start, text.length());
            span(new RelativeSizeSpan(1.08f), start, text.length());
            return this;
        }

        Lines line(String value) {
            next();
            text.append(visible(value));
            return this;
        }

        Lines muted(String value) {
            int start = next();
            text.append(visible(value));
            span(
                    new ForegroundColorSpan(context.getColor(R.color.workbench_secondary)),
                    start,
                    text.length());
            return this;
        }

        Lines code(String value) {
            int start = next();
            text.append(visible(value));
            span(new TypefaceSpan("monospace"), start, text.length());
            return this;
        }

        Lines field(String label, String value, boolean code) {
            int start = next();
            text.append(inline(label)).append(": ");
            span(new StyleSpan(Typeface.BOLD), start, text.length());
            int data = text.length();
            text.append(visible(value));
            if (code) span(new TypefaceSpan("monospace"), data, text.length());
            return this;
        }

        Lines witness(String label, String value) {
            int start = text.length();
            field(label, value, true);
            span(
                    new ForegroundColorSpan(context.getColor(R.color.workbench_accent)),
                    start,
                    text.length());
            return this;
        }

        CharSequence build() {
            return new android.text.SpannedString(text);
        }
    }
}
