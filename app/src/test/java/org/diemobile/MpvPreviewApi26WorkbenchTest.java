// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** One SDK per native test class: Gradle's forkEvery=1 gives API 26 its own JNI loader. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 26, shadows = {MpvPreviewWorkbenchTest.HostFrame.class,
        MpvPreviewWorkbenchTest.Images.class, MpvPreviewWorkbenchTest.Video.class})
@LooperMode(LooperMode.Mode.PAUSED)
public final class MpvPreviewApi26WorkbenchTest {
    private final MpvPreviewWorkbenchTest fixture = new MpvPreviewWorkbenchTest();

    @Before public void setup() throws Exception { fixture.setup(); }

    @After public void close() throws Exception { fixture.close(); }

    @Test public void api26MetadataOnlyVideoCanUseBoundedIsolatedMpvStill() throws Exception {
        // Reuse the same real host MPV pixels, exact-range/source checks and separate platform
        // metadata assertions as API 28. No unbounded platform frame request is permitted.
        fixture.videoThumbnail();
        assertEquals(0, MpvPreviewWorkbenchTest.Video.frames);
    }
}
