// SPDX-License-Identifier: GPL-3.0-only
/* One bounded decoded raster. Only fixed internal commands/options reach libmpv. */
#define _POSIX_C_SOURCE 200809L
#include "mpv_support.h"

#define FRAME_MAX_DIMENSION 1536
#define FRAME_MAX_NATIVE_BYTES (16U * 1024 * 1024)

static mpv_node *map_field(mpv_node *map, const char *name) {
    if (map->format != MPV_FORMAT_NODE_MAP || !map->u.list || !map->u.list->keys)
        return NULL;
    mpv_node *found = NULL;
    for (int i = 0; i < map->u.list->num; i++) {
        if (!map->u.list->keys[i]) return NULL;
        if (!strcmp(map->u.list->keys[i], name)) {
            if (found) return NULL;
            found = &map->u.list->values[i];
        }
    }
    return found;
}

static bool has_video_track(mpv_handle *mpv) {
    mpv_node tracks = {0};
    bool found = false;
    if (mpv_get_property(mpv, "track-list", MPV_FORMAT_NODE, &tracks) >= 0
            && tracks.format == MPV_FORMAT_NODE_ARRAY && tracks.u.list) {
        for (int i = 0; i < tracks.u.list->num; i++) {
            mpv_node *kind = map_field(&tracks.u.list->values[i], "type");
            if (kind && kind->format == MPV_FORMAT_STRING && kind->u.string
                    && !strcmp(kind->u.string, "video")) found = true;
        }
    }
    mpv_free_node_contents(&tracks);
    return found;
}

static bool write_pixels(int fd, const unsigned char *bytes, size_t count,
                         off_t position, int64_t deadline) {
    while (count) {
        if (now_ms() > deadline) return false;
        ssize_t written = pwrite(fd, bytes, count, position);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) return false;
        bytes += written;
        count -= (size_t)written;
        position += written;
    }
    return true;
}

static bool same_input(const struct stat *before, const struct stat *after) {
    return before->st_dev == after->st_dev && before->st_ino == after->st_ino
        && before->st_size == after->st_size
        && before->st_mtim.tv_sec == after->st_mtim.tv_sec
        && before->st_mtim.tv_nsec == after->st_mtim.tv_nsec
        && before->st_ctim.tv_sec == after->st_ctim.tv_sec
        && before->st_ctim.tv_nsec == after->st_ctim.tv_nsec;
}

JNIEXPORT jbyteArray JNICALL Java_org_diemobile_MpvEngine_frameFd(
        JNIEnv *env, jclass type, jint source, jint destination, jint maximum) {
    (void)type;
    Json json = {0};
    mpv_handle *mpv = NULL;
    mpv_node frame = {0};
    struct stat before = {0}, after = {0}, output_before = {0}, output_after = {0};
    Input input = {.fd = -1, .deadline = now_ms() + DEADLINE_MS};
    atomic_init(&input.bytes, 0); atomic_init(&input.seeks, 0);
    atomic_init(&input.opens, 0); atomic_init(&input.denied, 0);
    atomic_init(&input.budget_hit, false);
    int output = -1;
    unsigned char *rgba = NULL;
    bool output_owned = false, captured = false;
    const char *error = NULL;
    char failure[256] = {0}, filter[192] = {0};
    int64_t width = 0, height = 0, stride = 0;
    size_t packed = 0;
    locale_t previous_locale = (locale_t)0;
    locale_t numeric_locale = newlocale(LC_NUMERIC_MASK, "C", (locale_t)0);
    if (!numeric_locale) { error = "Cannot establish MPV's C numeric locale"; goto done; }
    previous_locale = uselocale(numeric_locale);
#ifdef DIE_MPV_ANDROID_BUNDLE
    if (!setlocale(LC_NUMERIC, "C")) { error = "Cannot establish libmpv's C numeric locale"; goto done; }
#endif
    if (maximum < 1 || maximum > FRAME_MAX_DIMENSION) {
        error = "MPV raster target must be 1–1536 pixels"; goto done;
    }
    int flags = fcntl(source, F_GETFL);
    if (flags < 0 || (flags & O_ACCMODE) != O_RDONLY || fstat(source, &before)
            || !S_ISREG(before.st_mode) || before.st_size < 1 || before.st_size > INPUT_LIMIT) {
        error = "MPV frame needs a nonempty read-only regular input, at most 256 MiB"; goto done;
    }
    flags = fcntl(destination, F_GETFL);
    if (flags < 0 || (flags & O_ACCMODE) == O_RDONLY || (flags & O_APPEND)
            || fstat(destination, &output_before) || !S_ISREG(output_before.st_mode)) {
        error = "MPV pixels need a writable non-append regular output descriptor"; goto done;
    }
    if (before.st_dev == output_before.st_dev && before.st_ino == output_before.st_ino) {
        error = "MPV pixel output aliases the source input"; goto done;
    }
    if (output_before.st_size != 0) {
        error = "MPV pixel output must start empty; existing bytes are never replaced"; goto done;
    }
    input.fd = fcntl(source, F_DUPFD_CLOEXEC, 0);
    output = fcntl(destination, F_DUPFD_CLOEXEC, 0);
    if (input.fd < 0 || output < 0) { error = "Cannot acquire MPV frame descriptors"; goto done; }
    output_owned = true;
    input.size = before.st_size;
    unsigned long api = mpv_client_api_version();
    if ((api >> 16) != 2 || api < MPV_MAKE_VERSION(2, 0)) {
        error = "MPV frame capture requires compatible client API 2.x"; goto done;
    }
    mpv = mpv_create();
    if (!mpv) { error = "Cannot create the MPV frame engine"; goto done; }
    for (size_t i = 0; i < sizeof(options) / sizeof(options[0]); i++) {
        int status = mpv_set_option_string(mpv, options[i][0], options[i][1]);
        if (status < 0) {
            snprintf(failure, sizeof(failure), "Mandatory MPV guard %s failed: %s",
                     options[i][0], mpv_error_string(status));
            error = failure; goto done;
        }
    }
    // The fixed software filter bounds the image supplied by screenshot-raw.
    // It does not establish a bound on decoder-internal allocations or work.
    snprintf(filter, sizeof(filter),
             "lavfi=[scale=w='min(%d,iw)':h='min(%d,ih)':force_original_aspect_ratio=decrease]",
             maximum, maximum);
    if (mpv_set_option_string(mpv, "vf", filter) < 0
            || mpv_set_option_string(mpv, "screenshot-sw", "yes") < 0) {
        error = "Required bounded software-frame options are unavailable"; goto done;
    }
    if (mpv_stream_cb_add_ro(mpv, "dieblob", &input, stream_open) < 0
            || mpv_initialize(mpv) < 0) {
        error = "Cannot initialize the guarded MPV frame stream"; goto done;
    }
    const char *load[] = {"loadfile", INPUT_URI, NULL};
    if (mpv_command_async(mpv, 1, load) < 0) {
        error = "MPV rejected the fixed private input command"; goto done;
    }
    while (now_ms() <= input.deadline) {
        mpv_event *event = mpv_wait_event(mpv, 0.01);
        if (atomic_load(&input.budget_hit) || atomic_load(&input.denied)) break;
        if (event->event_id == MPV_EVENT_END_FILE) {
            mpv_event_end_file *end = event->data;
            if (end && end->error < 0) {
                snprintf(failure, sizeof(failure), "MPV could not decode a frame: %s",
                         mpv_error_string(end->error));
                error = failure; goto done;
            }
        }
        if (event->event_id == MPV_EVENT_FILE_LOADED && !has_video_track(mpv)) {
            error = "No MPV video/image track was found; no pixel file was produced"; goto done;
        }
        // This getter requires vo_get_current_frame() to return an actual image.
        // A loaded file, redraw event or nonzero dimensions alone is insufficient.
        mpv_node current = {0};
        int ready = mpv_get_property(mpv, "video-frame-info", MPV_FORMAT_NODE, &current);
        mpv_free_node_contents(&current);
        if (ready < 0) continue;
        mpv_node arguments[2] = {
            {.format = MPV_FORMAT_STRING, .u.string = "screenshot-raw"},
            {.format = MPV_FORMAT_STRING, .u.string = "video"}
        };
        mpv_node_list list = {.num = 2, .values = arguments};
        mpv_node command = {.format = MPV_FORMAT_NODE_ARRAY, .u.list = &list};
        if (mpv_command_node(mpv, &command, &frame) >= 0) { captured = true; break; }
        mpv_free_node_contents(&frame);
    }
    if (!captured) { error = "MPV returned no complete decoded image within the frame budget"; goto done; }
    mpv_node *w = map_field(&frame, "w"), *h = map_field(&frame, "h"),
             *row = map_field(&frame, "stride"), *format = map_field(&frame, "format"),
             *pixels = map_field(&frame, "data");
    if (!w || !h || !row || !format || !pixels || w->format != MPV_FORMAT_INT64
            || h->format != MPV_FORMAT_INT64 || row->format != MPV_FORMAT_INT64
            || format->format != MPV_FORMAT_STRING || !format->u.string
            || strcmp(format->u.string, "bgr0") || pixels->format != MPV_FORMAT_BYTE_ARRAY
            || !pixels->u.ba || !pixels->u.ba->data) {
        error = "MPV returned an unsupported or incomplete raw-frame contract"; goto done;
    }
    width = w->u.int64; height = h->u.int64; stride = row->u.int64;
    if (width < 1 || height < 1 || width > maximum || height > maximum
            || stride < width * 4 || stride > width * 4 + 4096
            || (uint64_t)stride * (uint64_t)height > FRAME_MAX_NATIVE_BYTES
            || pixels->u.ba->size != (uint64_t)stride * (uint64_t)height) {
        error = "MPV raw-frame dimensions, stride or byte count exceed the raster contract"; goto done;
    }
    packed = (size_t)width * 4;
    rgba = malloc(packed);
    if (!rgba) { error = "Cannot allocate the bounded MPV pixel row"; goto done; }
    for (int64_t y = 0; y < height; y++) {
        const unsigned char *native = (const unsigned char *)pixels->u.ba->data + y * stride;
        for (int64_t x = 0; x < width; x++) {
            rgba[x * 4] = native[x * 4 + 2];
            rgba[x * 4 + 1] = native[x * 4 + 1];
            rgba[x * 4 + 2] = native[x * 4];
            rgba[x * 4 + 3] = 255; // bgr0's fourth byte and row padding are undefined.
        }
        if (!write_pixels(output, rgba, packed, (off_t)y * (off_t)packed, input.deadline)) {
            error = "MPV pixel output failed or exceeded its deadline"; goto done;
        }
    }
    literal(&json, "{\"schema\":\"org.diemobile.mpv-frame\",\"schemaVersion\":1,\"ok\":true,");
    number(&json, "\"widthPixels\":%" PRId64 ",\"heightPixels\":%" PRId64
           ",\"rowBytes\":%zu,\"pixelBytes\":%zu,\"maximumDimensionPixels\":%d,",
           width, height, packed, packed * (size_t)height, maximum);
    literal(&json, "\"pixelFormat\":\"rgba8\",\"alphaMode\":\"opaque\","
                  "\"sourceAlphaPreserved\":false,\"nativeScreenshotFormat\":\"bgr0\","
                  "\"decodedCurrentFrameConfirmed\":true,\"everyFrameRendered\":false,"
                  "\"completeFileValidityVerified\":false,\"frameTimestampVerified\":false,");
    number(&json, "\"runtimeApiVersion\":%lu,\"fileSizeBytes\":%" PRId64 ",", api, input.size);
#ifdef DIE_MPV_ANDROID_BUNDLE
    literal(&json, "\"engineBinaryOrigin\":\"mpv-android-2026-09-17-arm64\",");
#else
    literal(&json, "\"engineBinaryOrigin\":\"external-host-runtime\",");
#endif
    literal(&json, "\"headersSourceRevision\":\"" HEADERS_SOURCE "\",\"properties\":[");
    const char *getters[] = {"mpv-version", "ffmpeg-version", "file-format", "video-params",
                             "video-out-params", "video-frame-info", "track-list"};
    for (size_t i = 0; i < sizeof(getters) / sizeof(getters[0]); i++) {
        if (i) literal(&json, ",");
        literal(&json, "{\"name\":"); string(&json, getters[i]);
        mpv_node value = {0};
        int result = mpv_get_property(mpv, getters[i], MPV_FORMAT_NODE, &value);
        number(&json, ",\"errorCode\":%d,\"node\":", result);
        if (result >= 0) node(&json, &value, 0); else literal(&json, "null");
        mpv_free_node_contents(&value); literal(&json, "}");
    }
    literal(&json, "],\"filter\":"); string(&json, filter);
    literal(&json, ",\"referenceAccessEnabled\":false,\"configEnabled\":false,"
                  "\"scriptsEnabled\":false,\"sidecarLoadingEnabled\":false,"
                  "\"networkPermissionRequired\":false,\"audioVideoOutput\":\"null/paused\"");
done:
    mpv_free_node_contents(&frame);
    if (mpv) mpv_terminate_destroy(mpv);
    if (input.fd >= 0 && (!error && (fstat(input.fd, &after) || !same_input(&before, &after))))
        error = "Source input changed during MPV frame capture";
    if (!error && (atomic_load(&input.budget_hit) || atomic_load(&input.denied)))
        error = "MPV frame exhausted its read budget or requested an ungranted URI";
    if (!error && now_ms() > input.deadline) error = "MPV frame/teardown exceeded its deadline";
    if (!error && (fstat(output, &output_after)
            || output_after.st_size != (off_t)(packed * (size_t)height)))
        error = "MPV pixel output length changed or is incomplete";
    if (!error && !json.error)
        number(&json, ",\"bytesRead\":%" PRIuFAST64 ",\"seekCalls\":%" PRIuFAST64
               ",\"maxInputBytes\":%lld,\"maxReadBytes\":%llu,\"nativeDeadlineMs\":%d}",
               atomic_load(&input.bytes), atomic_load(&input.seeks), INPUT_LIMIT, READ_LIMIT, DEADLINE_MS);
    if ((error || json.error) && output_owned) (void)ftruncate(output, 0);
    if (input.fd >= 0) close(input.fd);
    if (output >= 0) close(output);
    free(rgba);
    if (numeric_locale) { uselocale(previous_locale); freelocale(numeric_locale); }
    return result_bytes(env, &json, error);
}
