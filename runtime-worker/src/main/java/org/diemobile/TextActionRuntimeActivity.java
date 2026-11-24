// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-only foreground window for real clipboard policy, never a production entry point. */
public final class TextActionRuntimeActivity extends Activity {
    private final CountDownLatch focused = new CountDownLatch(1);

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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) focused.countDown();
    }

    public boolean awaitFocus() throws InterruptedException {
        return focused.await(10, TimeUnit.SECONDS);
    }

    public String focusState() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return "focused="
                + hasWindowFocus()
                + ", interactive="
                + (power != null && power.isInteractive())
                + ", keyguardLocked="
                + (keyguard != null && keyguard.isKeyguardLocked());
    }
}
