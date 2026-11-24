// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;

/** User settings become immutable request limits before binding an isolated worker. */
final class AnalysisTimeLimits {
    static final int MIN_SECONDS = 1;
    static final int MAX_SECONDS = 1800;
    static final int DEFAULT_SECONDS = 120;
    static final long WORKER_MARGIN_MS = 30000;
    private static final long OTHER_WORKER_MS = 45000;
    static final String PREFERENCES = "analysis_time_limits";
    private static final String ANALYSIS_MS = "analysisDeadlineMs";
    private static final String REQUEST_MS = "requestDeadlineMs";

    private AnalysisTimeLimits() {}

    private static void requireConfigurable(String engine) {
        if (!"binwalk".equals(engine))
            throw new IllegalArgumentException("This engine has no configurable native deadline");
    }

    static int seconds(Context context, String engine) {
        requireConfigurable(engine);
        int saved;
        try {
            saved = preferences(context).getInt(engine + "_seconds", DEFAULT_SECONDS);
        } catch (ClassCastException invalidStoredType) {
            return DEFAULT_SECONDS;
        }
        return saved >= MIN_SECONDS && saved <= MAX_SECONDS ? saved : DEFAULT_SECONDS;
    }

    static void save(Context context, String engine, int seconds) {
        requireConfigurable(engine);
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS)
            throw new IllegalArgumentException("Enter whole seconds from 1 to 1800");
        preferences(context).edit().putInt(engine + "_seconds", seconds).apply();
        DebugJournal.record(
                context, "analysis_limit_saved", "engine=" + engine + " seconds=" + seconds);
    }

    static String summary(Context context, String engine) {
        int seconds = seconds(context, engine);
        return seconds + " s analysis · " + (seconds + WORKER_MARGIN_MS / 1000) + " s worker";
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static Request capture(Context context, String engine) {
        return new Request("binwalk".equals(engine) ? 1000L * seconds(context, engine) : 0);
    }

    /** The isolated worker reads the captured message, never the app's preferences. */
    static Request fromMessage(Bundle message, String engine) throws IOException {
        boolean configurable = "binwalk".equals(engine);
        if (!message.containsKey(ANALYSIS_MS) && !message.containsKey(REQUEST_MS))
            return new Request(configurable ? DEFAULT_SECONDS * 1000L : 0);
        Object analysis = message.get(ANALYSIS_MS), request = message.get(REQUEST_MS);
        if (!(analysis instanceof Long) || !(request instanceof Long))
            throw new IOException(
                    "Worker deadline request requires complete integer millisecond limits");
        long analysisMs = (Long) analysis;
        if (configurable
                ? analysisMs < MIN_SECONDS * 1000L
                        || analysisMs > MAX_SECONDS * 1000L
                        || analysisMs % 1000 != 0
                : analysisMs != 0)
            throw new IOException(
                    "Worker analysis deadline is outside the supported engine bounds");
        Request result = new Request(analysisMs);
        if ((Long) request != result.requestDeadlineMs)
            throw new IOException(
                    "Worker request deadline differs from its analysis and transport budget");
        return result;
    }

    static final class Request {
        final long analysisDeadlineMs;
        final long requestDeadlineMs;

        private Request(long analysisDeadlineMs) {
            this.analysisDeadlineMs = analysisDeadlineMs;
            requestDeadlineMs =
                    analysisDeadlineMs == 0
                            ? OTHER_WORKER_MS
                            : analysisDeadlineMs + WORKER_MARGIN_MS;
        }

        void writeTo(Bundle message) {
            message.putLong(ANALYSIS_MS, analysisDeadlineMs);
            message.putLong(REQUEST_MS, requestDeadlineMs);
        }

        void validateEnvelope(JSONObject envelope, JSONObject report) throws Exception {
            if (integer(envelope, ANALYSIS_MS) != analysisDeadlineMs
                    || integer(envelope, REQUEST_MS) != requestDeadlineMs)
                throw new IOException("Worker response deadline differs from the captured request");
            if (analysisDeadlineMs != 0 && report.optBoolean("ok")) {
                if (!"diemobile-binwalk-3".equals(report.getString("adapter"))
                        || integer(report, "requestedDeadlineMs") != analysisDeadlineMs
                        || integer(report, "cooperativeDeadlineMs") != analysisDeadlineMs)
                    throw new IOException(
                            "Binwalk native deadline differs from the captured request");
            }
        }

        private static long integer(JSONObject object, String field) throws Exception {
            Object value = object.get(field);
            if (!(value instanceof Integer) && !(value instanceof Long))
                throw new IOException("Worker deadline accounting requires integer milliseconds");
            return ((Number) value).longValue();
        }

        IOException termination(long elapsedMs, boolean cancelled, boolean closed) {
            if (cancelled || closed) return new InterruptedIOException("Binary analysis cancelled");
            if (elapsedMs >= requestDeadlineMs)
                return new IOException(
                        "Binary worker exceeded the "
                                + requestDeadlineMs / 1000
                                + "-second request deadline");
            return null;
        }

        String detail(String engine, String token) {
            return "engine="
                    + engine
                    + " token="
                    + token
                    + " analysisMs="
                    + analysisDeadlineMs
                    + " requestMs="
                    + requestDeadlineMs;
        }
    }
}
