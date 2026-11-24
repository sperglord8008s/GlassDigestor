// SPDX-License-Identifier: GPL-3.0-only
/* Read-only libmpv probe. No command, option, URI or path comes from the file. */
#define _POSIX_C_SOURCE 200809L
#include "mpv_support.h"

/* Audited file/engine getters, rather than every getter in property-list.
 * Other getters can enumerate audio devices or access clipboard/system state.
 * The full name catalog is retained without invoking those unrelated getters.
 * Missing names are explicit errors, including version-dependent properties. */
static const char *const properties[] = {
    "mpv-version", "mpv-configuration", "ffmpeg-version", "libass-version",
    "decoder-list", "demuxer-lavf-list", "protocol-list",
    "file-format", "current-demuxer", "file-size", "stream-end",
    "seekable", "partially-seekable", "duration", "time-start",
    "demuxer-start-time", "metadata", "filtered-metadata", "chapter-metadata",
    "chapter-list", "chapters", "chapter", "edition-list", "editions", "edition",
    "track-list", "audio-codec", "audio-codec-name", "audio-codec-description",
    "audio-format", "audio-params", "audio-out-params", "audio-samplerate",
    "audio-channels", "video-format", "video-codec", "video-codec-name",
    "video-codec-description", "video-params", "video-dec-params", "video-out-params",
    "container-fps", "estimated-vf-fps", "width", "height", "fps",
    "packet-audio-bitrate", "packet-video-bitrate", "packet-sub-bitrate",
    "audio-bitrate", "video-bitrate", "demuxer-cache-state", "playlist"
};
JNIEXPORT jbyteArray JNICALL Java_org_diemobile_MpvEngine_probeFd(JNIEnv *env, jclass type, jint fd) {
    (void)type;
    Json json = {0}, logs = {0};
    mpv_handle *mpv = NULL;
    mpv_node catalog = {0};
    struct stat before, after;
    const char *error = NULL;
    char failure[256] = {0};
    Input input = {.fd = -1, .deadline = now_ms() + DEADLINE_MS};
    atomic_init(&input.bytes, 0); atomic_init(&input.seeks, 0);
    atomic_init(&input.opens, 0); atomic_init(&input.denied, 0); atomic_init(&input.budget_hit, false);
    bool loaded = false; int load_error = 0, end_reason = -1;
    bool report_ready = false;
    unsigned log_count = 0;
    locale_t previous_locale = (locale_t)0;
    locale_t numeric_locale = newlocale(LC_NUMERIC_MASK, "C", (locale_t)0);
    if (!numeric_locale) { error = "Unable to establish an invariant MPV numeric locale"; goto done; }
    previous_locale = uselocale(numeric_locale);
#ifdef DIE_MPV_ANDROID_BUNDLE
    /* libmpv also checks the process-global C locale. Production executes only
     * inside the dedicated single-job isolated process, never the app UID. */
    if (!setlocale(LC_NUMERIC, "C")) { error = "Unable to establish libmpv's required C numeric locale"; goto done; }
#endif
    input.fd = dup(fd);
    if (input.fd < 0 || fstat(input.fd, &before) || !S_ISREG(before.st_mode)) { error = "MPV requires a granted seekable regular input descriptor"; goto done; }
    if (before.st_size < 0 || before.st_size > INPUT_LIMIT) { error = "MPV input exceeds the 256 MiB snapshot budget"; goto done; }
    input.size = before.st_size;
    unsigned long api = mpv_client_api_version();
    if ((api >> 16) != 2 || api < MPV_MAKE_VERSION(2, 0)) { error = "Unsupported libmpv API: this adapter requires compatible API 2.x"; goto done; }
    mpv = mpv_create();
    if (!mpv) { error = "Unable to create libmpv"; goto done; }
    for (size_t i = 0; i < sizeof(options) / sizeof(options[0]); i++) {
        int status = mpv_set_option_string(mpv, options[i][0], options[i][1]);
        if (status < 0) { snprintf(failure, sizeof(failure), "Mandatory MPV option %s failed: %s", options[i][0], mpv_error_string(status)); error = failure; goto done; }
    }
    int status = mpv_stream_cb_add_ro(mpv, "dieblob", &input, stream_open);
    if (status < 0) { error = "Unable to register the read-only MPV input stream"; goto done; }
    status = mpv_initialize(mpv);
    if (status < 0) { snprintf(failure, sizeof(failure), "MPV initialization failed: %s", mpv_error_string(status)); error = failure; goto done; }
    mpv_request_log_messages(mpv, "warn");
    const char *command[] = {"loadfile", INPUT_URI, NULL};
    status = mpv_command_async(mpv, 1, command);
    if (status < 0) { error = "MPV rejected the fixed input load request"; goto done; }
    literal(&logs, "[");
    while (now_ms() <= input.deadline) {
        mpv_event *event = mpv_wait_event(mpv, 0.1);
        if (event->event_id == MPV_EVENT_LOG_MESSAGE) {
            mpv_event_log_message *log = event->data;
            if (++log_count > 1000 || logs.length > 256 * 1024) { error = "MPV diagnostics exceed 1,000 events or 256 KiB; no silent truncation"; goto done; }
            if (log_count > 1) literal(&logs, ",");
            literal(&logs, "{\"prefix\":"); string(&logs, log->prefix);
            literal(&logs, ",\"level\":"); string(&logs, log->level);
            literal(&logs, ",\"text\":"); string(&logs, log->text);
            literal(&logs, ",\"textUtf8Hex\":"); hex(&logs, log->text, text_length(&logs, log->text)); literal(&logs, "}");
            if (logs.length > 256 * 1024) { error = "MPV diagnostics exceed 256 KiB; no silent truncation"; goto done; }
        } else if (event->event_id == MPV_EVENT_FILE_LOADED) { loaded = true; break; }
        else if (event->event_id == MPV_EVENT_END_FILE) {
            mpv_event_end_file *end = event->data; load_error = end->error; end_reason = end->reason; break;
        } else if (event->event_id == MPV_EVENT_COMMAND_REPLY && event->error < 0) { load_error = event->error; break; }
        else if (event->event_id == MPV_EVENT_SHUTDOWN) { error = "MPV shut down before producing a complete probe"; goto done; }
    }
    literal(&logs, "]");
    if (logs.error) { error = logs.error; goto done; }
    if (now_ms() > input.deadline) { error = "MPV exceeded the 25-second native probe deadline"; goto done; }
    if (atomic_load(&input.budget_hit)) { error = "MPV exhausted its 32 MiB cumulative input-read budget; no complete probe returned"; goto done; }
    if (atomic_load(&input.denied)) { error = "MPV requested an ungranted callback URI"; goto done; }
    status = mpv_get_property(mpv, "property-list", MPV_FORMAT_NODE, &catalog);
    if (status < 0 || catalog.format != MPV_FORMAT_NODE_ARRAY || !catalog.u.list || catalog.u.list->num < 1 || catalog.u.list->num > PROPERTY_LIMIT) { error = "MPV returned an invalid or over-budget property catalog"; goto done; }
    literal(&json, "{\"schema\":\"org.diemobile.mpv-evidence\",\"schemaVersion\":1,\"ok\":true,\"engine\":\"libmpv\",\"adapterVersion\":1,\"headersSourceRevision\":\"" HEADERS_SOURCE "\",");
#ifdef DIE_MPV_ANDROID_BUNDLE
    literal(&json, "\"engineBinaryOrigin\":\"mpv-android-2026-09-17-arm64\",\"engineSourceRevision\":\"" HEADERS_SOURCE "\",");
#else
    literal(&json, "\"engineBinaryOrigin\":\"external-host-runtime\",\"engineSourceRevision\":null,");
#endif
    number(&json, "\"runtimeApiVersion\":%lu,\"headersApiVersion\":%u,\"fileSizeBytes\":%" PRId64 ",\"fileProbeComplete\":%s,\"loadErrorCode\":%d,\"endReason\":%d,", api, MPV_CLIENT_API_VERSION, input.size, loaded ? "true" : "false", load_error, end_reason);
    literal(&json, "\"loadError\":"); string(&json, loaded ? NULL : load_error < 0 ? mpv_error_string(load_error) : "Input did not produce a playable/probeable media stream");
    literal(&json, ",\"diagnostics\":"); append(&json, logs.data, logs.length);
    number(&json, ",\"catalogCount\":%d,\"propertyCatalog\":", catalog.u.list->num);
    node(&json, &catalog, 0);
    number(&json, ",\"propertyCount\":%zu,\"properties\":[", sizeof(properties) / sizeof(properties[0]));
    for (size_t i = 0; i < sizeof(properties) / sizeof(properties[0]) && !json.error; i++) {
        if (now_ms() > input.deadline) { error = "MPV property capture exceeded its native deadline"; goto done; }
        if (i) literal(&json, ",");
        literal(&json, "{\"name\":"); string(&json, properties[i]);
        mpv_node value = {0};
        int result = mpv_get_property(mpv, properties[i], MPV_FORMAT_NODE, &value);
        if (result >= 0) { literal(&json, ",\"status\":\"available\",\"errorCode\":0,\"node\":"); node(&json, &value, 0); }
        else { number(&json, ",\"status\":\"unavailable\",\"errorCode\":%d,\"error\":", result); string(&json, mpv_error_string(result)); }
        mpv_free_node_contents(&value);
        literal(&json, "}");
    }
    literal(&json, "],\"options\":[");
    for (size_t i = 0; i < sizeof(options) / sizeof(options[0]); i++) {
        if (i) literal(&json, ",");
        literal(&json, "{\"name\":"); string(&json, options[i][0]);
        literal(&json, ",\"value\":"); string(&json, options[i][1]); literal(&json, "}");
    }
    literal(&json, "]");
    report_ready = true;
done:
    mpv_free_node_contents(&catalog);
    if (mpv) mpv_terminate_destroy(mpv); /* Must precede releasing stream input. */
    if (input.fd >= 0) {
        if (!error && (fstat(input.fd, &after) || after.st_size != before.st_size || after.st_mtim.tv_sec != before.st_mtim.tv_sec || after.st_mtim.tv_nsec != before.st_mtim.tv_nsec)) error = "Input changed during MPV probing";
        close(input.fd);
    }
    if (report_ready && !error && !json.error) {
        if (atomic_load(&input.budget_hit)) error = "MPV exhausted its 32 MiB cumulative input-read budget";
        else if (atomic_load(&input.denied)) error = "MPV requested an ungranted callback URI";
        else if (now_ms() > input.deadline) error = "MPV exceeded its native probe/teardown deadline";
        else number(&json, ",\"bytesRead\":%" PRIuFAST64 ",\"seekCalls\":%" PRIuFAST64 ",\"streamOpenCalls\":%" PRIuFAST64 ",\"ungrantedCallbackUris\":%" PRIuFAST64 ",\"maxInputBytes\":%lld,\"maxReadBytes\":%llu,\"nativeDeadlineMs\":%d,\"maxNodes\":%u,\"maxOutputBytes\":%u,\"nodeCount\":%u,\"referenceAccessEnabled\":false,\"configEnabled\":false,\"scriptsEnabled\":false,\"sidecarLoadingEnabled\":false,\"networkPermissionRequired\":false,\"playbackOutput\":\"null audio/video; paused software stream initialization may decode initial media packets\"}", atomic_load(&input.bytes), atomic_load(&input.seeks), atomic_load(&input.opens), atomic_load(&input.denied), INPUT_LIMIT, READ_LIMIT, DEADLINE_MS, NODE_LIMIT, OUTPUT_LIMIT, json.nodes);
    }
    free(logs.data);
    if (numeric_locale) { uselocale(previous_locale); freelocale(numeric_locale); }
    return result_bytes(env, &json, error);
}
