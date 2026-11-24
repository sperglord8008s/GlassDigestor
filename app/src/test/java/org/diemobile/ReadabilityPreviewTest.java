// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Host Skia previews of authored fixtures; never described as handset or browser-service proof. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ReadabilityPreviewTest {
    @Test
    public void compactExploreAndClassifierReasonsRenderReadableNativeGraphics() throws Exception {
        for (String name : new String[] {"Reader", "Inspector"}) {
            PackageInfo info = new PackageInfo();
            info.packageName = "org.preview." + name.toLowerCase();
            ApplicationInfo app = new ApplicationInfo();
            app.packageName = info.packageName;
            app.nonLocalizedLabel = "Research " + name;
            app.enabled = true;
            info.applicationInfo = app;
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                    .installPackage(info);
        }
        ActivityController<SystemExplorerActivity> explorer =
                Robolectric.buildActivity(SystemExplorerActivity.class).setup().visible();
        try {
            settle(explorer.get());
            ReflectionHelpers.<EditText>getField(explorer.get(), "search").setText("org.preview.");
            capture(explorer.get().getWindow().getDecorView(), 360, 640, "explore.png");
        } finally {
            close(explorer);
        }

        File input = File.createTempFile("readable-string-", ".bin");
        Files.write(
                input.toPath(), "ptrace CVE-2024-12345 strcpy\0".getBytes(StandardCharsets.UTF_8));
        ActivityController<InvestigationActivity> strings =
                Robolectric.buildActivity(
                                InvestigationActivity.class,
                                new Intent(
                                                RuntimeEnvironment.getApplication(),
                                                InvestigationActivity.class)
                                        .putExtra("provider", "strings")
                                        .putExtra("path", input.toString())
                                        .putExtra("name", "Authored string fixture"))
                        .setup()
                        .visible();
        try {
            settle(strings.get());
            ReflectionHelpers.<android.widget.Button>getField(strings.get(), "views")
                    .performClick();
            AlertDialog roots = ShadowAlertDialog.getLatestAlertDialog();
            ListView choices = roots.getListView();
            for (int i = 0; i < choices.getAdapter().getCount(); i++)
                if (choices.getAdapter()
                        .getItem(i)
                        .toString()
                        .contains("Vulnerability / exploit")) {
                    choices.performItemClick(null, i, i);
                    break;
                }
            settle(strings.get());
            ListView records = strings.get().findViewById(R.id.investigation_records);
            records.performItemClick(null, 0, 0);
            AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(64, TimeUnit.MILLISECONDS);
            View decor = detail.getWindow().getDecorView();
            capture(decor, 320, 560, "string-reason.png");
            TextView why = decor.findViewWithTag("string-why-1");
            assertNotNull(why);
            Rect visible = new Rect();
            assertTrue(
                    "The classification reason is buried below the compact dialog viewport",
                    why.getGlobalVisibleRect(visible));
            assertTrue(
                    "Fewer than two lines of the mechanism are visible",
                    visible.height() >= why.getLineHeight() * 2);
        } finally {
            close(strings);
            input.delete();
        }
    }

    private static void settle(android.app.Activity activity) throws Exception {
        for (int i = 0; i < 2; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static void close(ActivityController<? extends android.app.Activity> controller)
            throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(controller.get(), "worker");
        controller.pause().stop().destroy();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
    }

    private static void capture(View view, int width, int height, String name) throws Exception {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int first = bitmap.getPixel(0, 0), different = 0;
            for (int y = 0; y < height; y += 4)
                for (int x = 0; x < width; x += 4) if (bitmap.getPixel(x, y) != first) different++;
            assertTrue("Native UI preview is blank", different > 100);
            File directory = new File("build/reports/workbench-ui");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
        } finally {
            bitmap.recycle();
        }
    }
}
