// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

/** Test-only foreground window for real clipboard policy, never a production entry point. */
public final class TextActionRuntimeActivity extends Activity {
    private final Object focusChanged = new Object();
    private final ArrayDeque<JSONObject> events = new ArrayDeque<>();
    private JSONObject latest = new JSONObject();
    private boolean resumed, ready, lastAttached, lastShown;
    private int focusGains, focusLosses;
    private long lastEventElapsedMs;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Native large-file checks can outlast the emulator's idle display timeout.
        // Keep the real foreground/focus requirement; wake only this trusted test window.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow()
                    .addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        TextView label = new TextView(this);
        label.setText("GlassDigestor: trusted text-action runtime fixture");
        setContentView(label);
        View decor = getWindow().getDecorView();
        decor.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                        publishFocus("attached");
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        publishFocus("detached");
                    }
                });
        decor.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        () -> {
                            if (decor.isAttachedToWindow() != lastAttached
                                    || decor.isShown() != lastShown) publishFocus("visibility");
                        });
        publishFocus("created");
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        publishFocus("resumed");
    }

    @Override
    protected void onPause() {
        resumed = false;
        publishFocus("paused");
        super.onPause();
    }

    @Override
    protected void onStop() {
        resumed = false;
        publishFocus("stopped");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        publishFocus("destroying");
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) focusGains++;
        else focusLosses++;
        publishFocus(hasFocus ? "focus-gained" : "focus-lost");
    }

    public boolean awaitFocus() throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Focus readiness cannot block the main thread");
        long deadline = SystemClock.elapsedRealtime() + 10000;
        boolean[] refreshed = {false};
        runOnUiThread(
                () -> {
                    publishFocus("readiness-sampled");
                    synchronized (focusChanged) {
                        refreshed[0] = true;
                        focusChanged.notifyAll();
                    }
                });
        synchronized (focusChanged) {
            while (!refreshed[0] || !ready) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return false;
                focusChanged.wait(remaining);
            }
            return true;
        }
    }

    public boolean currentFocusReady() {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Current focus must be checked on the main thread");
        publishFocus("clipboard-main-thread-check");
        synchronized (focusChanged) {
            return ready;
        }
    }

    private void publishFocus(String event) {
        View decor = getWindow().getDecorView();
        boolean attached = decor.isAttachedToWindow(), shown = decor.isShown();
        boolean hasFocus = hasWindowFocus();
        synchronized (focusChanged) {
            ready = resumed && attached && shown && hasFocus && !isFinishing() && !isDestroyed();
            lastAttached = attached;
            lastShown = shown;
            lastEventElapsedMs = SystemClock.elapsedRealtime();
            try {
                latest =
                        new JSONObject()
                                .put("event", event)
                                .put("elapsedMs", lastEventElapsedMs)
                                .put("ready", ready)
                                .put("resumed", resumed)
                                .put("focused", hasFocus)
                                .put("attached", attached)
                                .put("shown", shown)
                                .put("finishing", isFinishing())
                                .put("destroyed", isDestroyed())
                                .put("focusGainCount", focusGains)
                                .put("focusLossCount", focusLosses)
                                .put(
                                        "displayId",
                                        decor.getDisplay() == null
                                                ? JSONObject.NULL
                                                : decor.getDisplay().getDisplayId())
                                .put("windowFlags", getWindow().getAttributes().flags);
                events.addLast(new JSONObject(latest.toString()));
                while (events.size() > 16) events.removeFirst();
            } catch (Exception ignored) {
                // Readiness uses the actual primitive state, not diagnostic serialization.
            }
            focusChanged.notifyAll();
        }
    }

    public String focusState() {
        synchronized (focusChanged) {
            return latest.toString();
        }
    }

    /**
     * Binder-backed diagnostics run only inside the instrumentation's bounded background capture.
     */
    public JSONObject systemFocusState() throws Exception {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return new JSONObject()
                .put("interactive", power != null && power.isInteractive())
                .put("keyguardLocked", keyguard != null && keyguard.isKeyguardLocked())
                .put("taskId", getTaskId());
    }

    public JSONObject focusSnapshot() {
        synchronized (focusChanged) {
            try {
                return new JSONObject(latest.toString())
                        .put("snapshotAgeMs", SystemClock.elapsedRealtime() - lastEventElapsedMs)
                        .put("events", new JSONArray(events));
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }
}
