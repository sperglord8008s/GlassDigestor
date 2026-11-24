// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-only foreground window for real clipboard policy, never a production entry point. */
public final class TextActionRuntimeActivity extends Activity {
    private final CountDownLatch focused = new CountDownLatch(1);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
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
}
