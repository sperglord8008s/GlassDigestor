// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Offline project/build information; external links open only after an explicit tap. */
public final class AboutActivity extends Activity {
    static final String PROJECT_URL = "https://github.com/sperglord8008s/GlassDigestor";
    static final String RELEASES_URL = PROJECT_URL + "/releases";
    static final String DOCUMENTATION_URL = PROJECT_URL + "/tree/main/docs";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("About " + BuildConfig.PRODUCT_NAME);
        ScrollView scroll = WorkbenchUi.scroll(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = WorkbenchUi.dp(this, 12);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);

        content.addView(WorkbenchUi.text(this, BuildConfig.PRODUCT_NAME, 22));
        content.addView(
                WorkbenchUi.prose(
                        this,
                        "A free, open-source Android workbench for exploring files, binary"
                            + " structures, Android packages and recovered content. Technical"
                            + " details, original values and their limitations remain available"
                            + " beside explanations."));
        content.addView(WorkbenchUi.section(this, "This build"));
        content.addView(WorkbenchUi.fact(this, "Version", BuildConfig.VERSION_NAME, true));
        content.addView(
                WorkbenchUi.fact(
                        this, "Version code", Integer.toString(BuildConfig.VERSION_CODE), true));
        content.addView(WorkbenchUi.fact(this, "Application", BuildConfig.APPLICATION_ID, true));
        content.addView(WorkbenchUi.fact(this, "Build type", BuildConfig.BUILD_TYPE, true));
        content.addView(
                WorkbenchUi.fact(this, "Source revision", BuildConfig.SOURCE_REVISION, true));
        content.addView(
                WorkbenchUi.fact(
                        this,
                        "Source state",
                        BuildConfig.SOURCE_DIRTY
                                ? "Local changes were present, or Git identity was unavailable, at"
                                      + " build configuration"
                                : "Clean at build configuration",
                        false));
        content.addView(
                button(
                        "Copy build details",
                        "about-copy-build",
                        () -> new TextActions(this).copy(buildDetails())));

        content.addView(WorkbenchUi.section(this, "Offline and private"));
        content.addView(
                WorkbenchUi.prose(
                        this,
                        "Analysis runs locally. The app has no INTERNET permission, telemetry or"
                            + " automatic uploads, and does not execute inspected apps. Private"
                            + " input snapshots support analysis and explicit exports. Android"
                            + " backup is disabled."));
        content.addView(
                WorkbenchUi.prose(
                        this,
                        "The private program debug log keeps bounded operational metadata such as"
                            + " engine names, request outcomes, elapsed times and timeout settings."
                            + " It excludes file contents, file names and paths, classified strings"
                            + " and search or translation queries. You control copying, saving and"
                            + " clearing it."));
        content.addView(
                button(
                        "Open private program debug log",
                        "about-debug",
                        () -> startActivity(new Intent(this, DebugLogActivity.class))));

        content.addView(WorkbenchUi.section(this, "License and credits"));
        content.addView(
                WorkbenchUi.prose(
                        this,
                        "The application frontend and adapters are GPL-3.0-only. Detect It"
                            + " Easy/CDiE and xxfclib provide core binary analysis."
                            + " Android-specific parsers, recovery engines and knowledge sources"
                            + " retain their own licenses and credits. Full component notices are"
                            + " bundled under assets/notices in the APK and corresponding source."
                            + " This is an independent project, not an official Horsicq"
                            + " application."));
        content.addView(
                button(
                        "Read bundled GPL-3.0 license",
                        "about-license",
                        () -> showBundled("GPL-3.0-only", "notices/DiE-Android-LICENSE.txt")));
        content.addView(
                button(
                        "Read component attribution overview",
                        "about-notices",
                        () ->
                                showBundled(
                                        "Component attribution overview",
                                        "notices/DiE-Android-NOTICE.txt")));

        content.addView(WorkbenchUi.section(this, "Project links · external browser"));
        content.addView(
                WorkbenchUi.prose(
                        this,
                        "Tapping a link opens your chosen browser. That external app may use the"
                            + " network. These fixed links include no inspected data. The"
                            + " documentation records implemented features and separate host/device"
                            + " verification limits."));
        content.addView(
                button("GitHub · source and issues", "about-source", () -> openLink(PROJECT_URL)));
        content.addView(
                button(
                        "GitHub · releases and downloads",
                        "about-releases",
                        () -> openLink(RELEASES_URL)));
        content.addView(
                button("GitHub · documentation", "about-docs", () -> openLink(DOCUMENTATION_URL)));
        setContentView(scroll);
    }

    static String buildDetails() {
        return BuildConfig.PRODUCT_NAME
                + " "
                + BuildConfig.VERSION_NAME
                + "\nVersion code: "
                + BuildConfig.VERSION_CODE
                + "\nBuild type: "
                + BuildConfig.BUILD_TYPE
                + "\nApplication: "
                + BuildConfig.APPLICATION_ID
                + "\nSource revision: "
                + BuildConfig.SOURCE_REVISION
                + "\nSource dirty at build configuration: "
                + BuildConfig.SOURCE_DIRTY;
    }

    private Button button(String label, String tag, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTag(tag);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void openLink(String url) {
        try {
            Intent browser =
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(Intent.createChooser(browser, "Open project link in browser"));
        } catch (RuntimeException unavailable) {
            Toast.makeText(
                            this,
                            "No browser accepted the link. Copy it from this dialog.",
                            Toast.LENGTH_LONG)
                    .show();
            showText("Project link", url);
        }
    }

    private void showBundled(String title, String path) {
        try (InputStream input = getAssets().open(path);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) {
                if (count > 128 * 1024 - output.size())
                    throw new IOException("Bundled overview exceeds the display bound");
                output.write(bytes, 0, count);
            }
            showText(title, new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException unavailable) {
            showText(
                    title,
                    "The bundled document could not be displayed. Complete licenses and notices are"
                        + " included in the APK assets/notices directory and project source.");
        }
    }

    private void showText(String title, String text) {
        ScrollView scroll = WorkbenchUi.scroll(this);
        var content = WorkbenchUi.prose(this, text);
        int padding = WorkbenchUi.dp(this, 12);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setNeutralButton("Copy", (dialog, which) -> new TextActions(this).copy(text))
                .setPositiveButton("Close", null)
                .show();
    }
}
