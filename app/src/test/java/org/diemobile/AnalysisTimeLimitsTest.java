// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Spinner;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Settings, IPC/native capture validation and deterministic watchdog/cancel policy. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class AnalysisTimeLimitsTest {
    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(AnalysisTimeLimits.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void settingsAreBoundedAndCapturedBeforeLaterChanges() {
        AnalysisTimeLimits.Request initial = AnalysisTimeLimits.capture(context, "binwalk");
        assertEquals(120000, initial.analysisDeadlineMs);
        AnalysisTimeLimits.save(context, "binwalk", 317);
        AnalysisTimeLimits.Request captured = AnalysisTimeLimits.capture(context, "binwalk");
        assertEquals(317000, captured.analysisDeadlineMs);
        assertEquals(347000, captured.requestDeadlineMs);
        AnalysisTimeLimits.save(context, "binwalk", 1);
        assertEquals(317000, captured.analysisDeadlineMs);
        assertEquals(120000, initial.analysisDeadlineMs);
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalysisTimeLimits.save(context, "binwalk", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalysisTimeLimits.save(context, "binwalk", 1801));
        assertEquals(1, AnalysisTimeLimits.seconds(context, "binwalk"));
        AnalysisTimeLimits.save(context, "binwalk", 1800);
        assertEquals(1830000, AnalysisTimeLimits.capture(context, "binwalk").requestDeadlineMs);
        assertEquals(45000, AnalysisTimeLimits.capture(context, "hashes").requestDeadlineMs);
        assertEquals(0, AnalysisTimeLimits.capture(context, "resources").analysisDeadlineMs);
        context.getSharedPreferences(AnalysisTimeLimits.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString("binwalk_seconds", "invalid")
                .commit();
        assertEquals(120, AnalysisTimeLimits.seconds(context, "binwalk"));
    }

    @Test
    public void workerReadsCapturedMessageAndRejectsIncompleteOrUnboundedLimits() throws Exception {
        AnalysisTimeLimits.save(context, "binwalk", 317);
        Bundle message = new Bundle();
        AnalysisTimeLimits.capture(context, "binwalk").writeTo(message);
        AnalysisTimeLimits.save(context, "binwalk", 60);
        AnalysisTimeLimits.Request received = AnalysisTimeLimits.fromMessage(message, "binwalk");
        assertEquals(317000, received.analysisDeadlineMs);
        assertEquals(347000, received.requestDeadlineMs);
        message.putLong("requestDeadlineMs", 45000);
        assertThrows(IOException.class, () -> AnalysisTimeLimits.fromMessage(message, "binwalk"));
        for (long invalid : new long[] {-1, 0, 999, 1001, 1800001, Long.MAX_VALUE}) {
            message.putLong("analysisDeadlineMs", invalid);
            message.putLong("requestDeadlineMs", invalid);
            assertThrows(
                    IOException.class, () -> AnalysisTimeLimits.fromMessage(message, "binwalk"));
        }
        message.putLong("analysisDeadlineMs", 60000);
        message.putString("requestDeadlineMs", "90000");
        assertThrows(IOException.class, () -> AnalysisTimeLimits.fromMessage(message, "binwalk"));
        message.remove("requestDeadlineMs");
        assertThrows(IOException.class, () -> AnalysisTimeLimits.fromMessage(message, "binwalk"));
        assertEquals(
                120000, AnalysisTimeLimits.fromMessage(new Bundle(), "binwalk").analysisDeadlineMs);
        assertEquals(
                45000, AnalysisTimeLimits.fromMessage(new Bundle(), "archive").requestDeadlineMs);
    }

    @Test
    public void nativeAndWorkerResponsesMustMatchTheCapturedRequest() throws Exception {
        AnalysisTimeLimits.save(context, "binwalk", 317);
        AnalysisTimeLimits.Request request = AnalysisTimeLimits.capture(context, "binwalk");
        JSONObject worker =
                new JSONObject().put("analysisDeadlineMs", 317000).put("requestDeadlineMs", 347000);
        JSONObject nativeReport =
                new JSONObject()
                        .put("ok", true)
                        .put("adapter", "diemobile-binwalk-3")
                        .put("requestedDeadlineMs", 317000)
                        .put("cooperativeDeadlineMs", 317000);
        request.validateEnvelope(worker, nativeReport);
        AnalysisTimeLimits.save(context, "binwalk", 60);
        request.validateEnvelope(worker, nativeReport);
        worker.put("requestDeadlineMs", 45000);
        assertThrows(IOException.class, () -> request.validateEnvelope(worker, nativeReport));
        worker.put("requestDeadlineMs", 347000);
        nativeReport.put("cooperativeDeadlineMs", 30000);
        assertThrows(IOException.class, () -> request.validateEnvelope(worker, nativeReport));
        nativeReport.put("cooperativeDeadlineMs", 317000.5);
        assertThrows(IOException.class, () -> request.validateEnvelope(worker, nativeReport));
        nativeReport.put("ok", false).put("error", "fixture failure");
        request.validateEnvelope(worker, nativeReport);
    }

    @Test
    public void cancellationTakesPriorityAndTheChosenDeadlineDrivesTheWatchdog() {
        AnalysisTimeLimits.save(context, "binwalk", 317);
        AnalysisTimeLimits.Request request = AnalysisTimeLimits.capture(context, "binwalk");
        assertNull(request.termination(45001, false, false));
        assertNull(request.termination(346999, false, false));
        IOException expired = request.termination(347000, false, false);
        assertNotNull(expired);
        assertFalse(expired instanceof InterruptedIOException);
        assertTrue(expired.getMessage().contains("347-second"));
        assertTrue(request.termination(0, true, false) instanceof InterruptedIOException);
        assertTrue(request.termination(0, false, true) instanceof InterruptedIOException);
        assertTrue(request.termination(347000, true, false) instanceof InterruptedIOException);
    }

    @Test
    public void cancellationDuringSerializationCannotPublishTheReport() throws Exception {
        AnalysisTimeLimits.Request request = AnalysisTimeLimits.capture(context, "binwalk");
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger logged = new AtomicInteger();
        JSONObject report =
                new JSONObject() {
                    @Override
                    public String toString() {
                        cancelled.set(true);
                        return "{\"ok\":true}";
                    }
                };
        assertThrows(
                InterruptedIOException.class,
                () ->
                        BinaryWorkerClient.completeReport(
                                report,
                                logged::incrementAndGet,
                                () -> request.termination(0, cancelled.get(), false)));
        assertEquals(1, logged.get());
    }

    @Test
    public void deadlineDuringJournalingCannotPublishTheSerializedReport() throws Exception {
        AnalysisTimeLimits.Request request = AnalysisTimeLimits.capture(context, "binwalk");
        AtomicLong elapsed = new AtomicLong(0);
        AtomicInteger serializations = new AtomicInteger();
        JSONObject report =
                new JSONObject() {
                    @Override
                    public String toString() {
                        serializations.incrementAndGet();
                        return "{\"ok\":true}";
                    }
                };
        IOException expired =
                assertThrows(
                        IOException.class,
                        () ->
                                BinaryWorkerClient.completeReport(
                                        report,
                                        () -> {
                                            assertEquals(1, serializations.get());
                                            elapsed.set(150000);
                                        },
                                        () -> request.termination(elapsed.get(), false, false)));
        assertFalse(expired instanceof InterruptedIOException);
        assertTrue(expired.getMessage().contains("150-second"));
        assertEquals(1, serializations.get());
    }

    @Test
    public void completedReportUsesOneSerializationAndChecksAfterJournaling() throws Exception {
        AtomicInteger serializations = new AtomicInteger();
        AtomicBoolean logged = new AtomicBoolean();
        JSONObject report =
                new JSONObject() {
                    @Override
                    public String toString() {
                        serializations.incrementAndGet();
                        return "{\"ok\":true,\"captured\":317000}";
                    }
                };
        String encoded =
                BinaryWorkerClient.completeReport(
                        report,
                        () -> logged.set(true),
                        () -> {
                            assertTrue(logged.get());
                            return null;
                        });
        assertEquals(1, serializations.get());
        assertEquals(317000, new JSONObject(encoded).getLong("captured"));
    }

    @Test
    public void dialogSupportsPresetsAndValidatesCustomSecondsBeforeSaving() {
        ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            AtomicInteger changes = new AtomicInteger();
            AnalysisTimeLimitsUi.show(controller.get(), "binwalk", changes::incrementAndGet);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            EditText seconds =
                    dialog.getWindow().getDecorView().findViewWithTag("analysis-time-seconds");
            Spinner presets =
                    dialog.getWindow().getDecorView().findViewWithTag("analysis-time-preset");
            presets.setSelection(4);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("600", seconds.getText().toString());
            presets.setSelection(5);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            seconds.setText("1801");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());
            assertNotNull(seconds.getError());
            assertEquals(120, AnalysisTimeLimits.seconds(context, "binwalk"));
            seconds.setText("317");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());
            assertEquals(317, AnalysisTimeLimits.seconds(context, "binwalk"));
            assertEquals(1, changes.get());
            AnalysisTimeLimitsUi.show(controller.get(), "binwalk", changes::incrementAndGet);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            dialog = ShadowAlertDialog.getLatestAlertDialog();
            seconds = dialog.getWindow().getDecorView().findViewWithTag("analysis-time-seconds");
            assertEquals("317", seconds.getText().toString());
            seconds.setText("60");
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(317, AnalysisTimeLimits.seconds(context, "binwalk"));
            assertEquals(1, changes.get());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
