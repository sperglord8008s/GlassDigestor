// SPDX-License-Identifier: GPL-3.0-only
/* One bounded decoded raster. Only fixed internal commands/options reach libmpv. */
#define _POSIX_C_SOURCE 200809L
#include "mpv_support.h"

#define FRAME_MAX_DIMENSION 1536
#define FRAME_MAX_NATIVE_BYTES (16U * 1024 * 1024)
#define FRAME_MAX_PIXEL_BYTES (FRAME_MAX_DIMENSION * FRAME_MAX_DIMENSION * 4U)

static bool node_list_valid(const mpv_node *node, mpv_format format) {
    if (!node || node->format != format || !node->u.list) return false;
    const mpv_node_list *list = node->u.list;
    return list->num >= 0 && list->num <= (int)NODE_LIMIT
        && (!list->num || (list->values && (format != MPV_FORMAT_NODE_MAP || list->keys)));
}

static mpv_node *map_field(mpv_node *map, const char *name) {
    if (!node_list_valid(map, MPV_FORMAT_NODE_MAP)) return NULL;
    mpv_node *found = NULL;
    for (int i = 0; i < map->u.list->num; i++) {
        if (!map->u.list->keys[i] || strnlen(map->u.list->keys[i], 256) >= 256) return NULL;
        if (!strcmp(map->u.list->keys[i], name)) {
            if (found) return NULL;
            found = &map->u.list->values[i];
        }
    }
    return found;
}

static int video_track_status(mpv_node *tracks, int64_t deadline) {
    if (!node_list_valid(tracks, MPV_FORMAT_NODE_ARRAY)) return -1;
    bool found = false;
    uint64_t fields = 0;
    for (int i = 0; i < tracks->u.list->num; i++) {
        mpv_node *track = &tracks->u.list->values[i];
        if (now_ms() > deadline || !node_list_valid(track, MPV_FORMAT_NODE_MAP)) return -1;
        fields += (unsigned)track->u.list->num;
        if (fields > NODE_LIMIT) return -1;
        mpv_node *kind = map_field(track, "type");
        if (!kind || kind->format != MPV_FORMAT_STRING || !kind->u.string
                || strnlen(kind->u.string, 32) >= 32) return -1;
        if (!strcmp(kind->u.string, "video")) found = true;
    }
    return found ? 1 : 0;
}

static int has_video_track(mpv_handle *mpv, int64_t deadline) {
    mpv_node tracks = {0};
    int found = mpv_get_property(mpv, "track-list", MPV_FORMAT_NODE, &tracks) >= 0
        ? video_track_status(&tracks, deadline) : -1;
    mpv_free_node_contents(&tracks);
    return found;
}

static bool frame_filter(char *filter, size_t capacity, int maximum_dimension) {
    if (maximum_dimension < 1 || maximum_dimension > FRAME_MAX_DIMENSION) return false;
    // Fit display dimensions iw*sar by ih, then make pixels square. Unlike
    // scale's newer reset_sar option, these expressions/setsar also work with
    // the retained host runtime. Clamp before scale converts its result to int.
    int count = snprintf(filter, capacity,
        "lavfi=[scale=w='max(1,trunc(min(%d,iw*sar*min(1,%d/max(iw*sar,ih)))))':"
        "h='max(1,trunc(min(%d,ih*min(1,%d/max(iw*sar,ih)))))',setsar=1]",
        maximum_dimension, maximum_dimension, maximum_dimension, maximum_dimension);
    return count > 0 && (size_t)count < capacity;
}

static bool display_geometry(mpv_node *parameters, int maximum_dimension,
                             int64_t *width, int64_t *height) {
    mpv_node *w = map_field(parameters, "w"), *h = map_field(parameters, "h"),
        *dw = map_field(parameters, "dw"), *dh = map_field(parameters, "dh"),
        *par = map_field(parameters, "par");
    if (!w || !h || !dw || !dh || !par || w->format != MPV_FORMAT_INT64
            || h->format != MPV_FORMAT_INT64 || dw->format != MPV_FORMAT_INT64
            || dh->format != MPV_FORMAT_INT64 || par->format != MPV_FORMAT_DOUBLE
            || !isfinite(par->u.double_) || par->u.double_ != 1.0) return false;
    mpv_node *dimensions[] = {w, h, dw, dh};
    for (size_t i = 0; i < sizeof(dimensions) / sizeof(dimensions[0]); i++)
        if (dimensions[i]->u.int64 < 1 || dimensions[i]->u.int64 > maximum_dimension) return false;
    *width = dw->u.int64; *height = dh->u.int64;
    return true;
}

typedef struct {
    int64_t width, height, stride;
    const unsigned char *data;
    size_t row_bytes, pixel_bytes;
} FrameRaster;

static bool frame_raster(mpv_node *frame, int maximum_dimension, FrameRaster *result) {
    mpv_node *w = map_field(frame, "w"), *h = map_field(frame, "h"),
        *row = map_field(frame, "stride"), *format = map_field(frame, "format"),
        *pixels = map_field(frame, "data");
    if (maximum_dimension < 1 || maximum_dimension > FRAME_MAX_DIMENSION
            || !w || !h || !row || !format || !pixels || w->format != MPV_FORMAT_INT64
            || h->format != MPV_FORMAT_INT64 || row->format != MPV_FORMAT_INT64
            || format->format != MPV_FORMAT_STRING || !format->u.string
            || strnlen(format->u.string, 5) != 4 || strcmp(format->u.string, "bgr0")
            || pixels->format != MPV_FORMAT_BYTE_ARRAY || !pixels->u.ba || !pixels->u.ba->data)
        return false;
    int64_t width = w->u.int64, height = h->u.int64, stride = row->u.int64;
    if (width < 1 || height < 1 || width > maximum_dimension || height > maximum_dimension
            || stride < width * 4 || stride > width * 4 + 4096
            || (uint64_t)stride * (uint64_t)height > FRAME_MAX_NATIVE_BYTES
            || pixels->u.ba->size != (uint64_t)stride * (uint64_t)height) return false;
    *result = (FrameRaster){.width=width, .height=height, .stride=stride,
        .data=pixels->u.ba->data, .row_bytes=(size_t)width * 4,
        .pixel_bytes=(size_t)width * 4 * (size_t)height};
    return true;
}

static void rgba_row(unsigned char *rgba, const unsigned char *native, size_t width) {
    for (size_t x = 0; x < width; x++) {
        rgba[x * 4] = native[x * 4 + 2];
        rgba[x * 4 + 1] = native[x * 4 + 1];
        rgba[x * 4 + 2] = native[x * 4];
        rgba[x * 4 + 3] = 255; // bgr0 alpha and native row padding are undefined.
    }
}

static const char *frame_event_failure(const mpv_event *event, char *failure, size_t capacity,
                                       bool *ended) {
    if (!event) return "MPV returned no frame event";
    if (event->event_id == MPV_EVENT_SHUTDOWN) return "MPV shut down before a decoded image was captured";
    if (event->event_id == MPV_EVENT_COMMAND_REPLY && event->error < 0) {
        snprintf(failure, capacity, "MPV frame load command failed: %s", mpv_error_string(event->error));
        return failure;
    }
    if (event->event_id == MPV_EVENT_END_FILE) {
        *ended = true;
        const mpv_event_end_file *end = event->data;
        if (!end) return "MPV returned an incomplete end-of-file event";
        if (end->error < 0) {
            snprintf(failure, capacity, "MPV could not decode a frame: %s", mpv_error_string(end->error));
            return failure;
        }
        if (end->reason == MPV_END_FILE_REASON_ERROR) return "MPV ended decoding with an unspecified error";
    }
    return NULL;
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
        JNIEnv *env, jclass type, jint source, jint destination, jint maximum_dimension) {
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
    char failure[256] = {0}, filter[512] = {0};
    int64_t width = 0, height = 0, stride = 0;
    int64_t display_width = 0, display_height = 0;
    size_t packed = 0;
    locale_t previous_locale = (locale_t)0;
    locale_t numeric_locale = newlocale(LC_NUMERIC_MASK, "C", (locale_t)0);
    if (!numeric_locale) { error = "Cannot establish MPV's C numeric locale"; goto done; }
    previous_locale = uselocale(numeric_locale);
#ifdef DIE_MPV_ANDROID_BUNDLE
    if (!setlocale(LC_NUMERIC, "C")) { error = "Cannot establish libmpv's C numeric locale"; goto done; }
#endif
    if (maximum_dimension < 1 || maximum_dimension > FRAME_MAX_DIMENSION) {
        error = "MPV maximum raster dimension must be 1–1536 pixels per edge"; goto done;
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
    // Bound square-pixel display dimensions before MPV's screenshot conversion
    // allocates a raster. Decoder-internal allocations remain separately unbounded.
    if (!frame_filter(filter, sizeof(filter), maximum_dimension)
            || mpv_set_option_string(mpv, "vf", filter) < 0
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
        if (atomic_load(&input.budget_hit)) {
            error = "MPV frame exhausted its 32 MiB cumulative input-read budget"; goto done;
        }
        if (atomic_load(&input.denied)) {
            error = "MPV frame requested an ungranted callback URI"; goto done;
        }
        bool ended = false;
        error = frame_event_failure(event, failure, sizeof(failure), &ended);
        if (error) goto done;
        if (event->event_id == MPV_EVENT_FILE_LOADED) {
            int video = has_video_track(mpv, input.deadline);
            if (video < 0) { error = "MPV track metadata is unavailable, malformed or over budget"; goto done; }
            if (!video) { error = "No MPV video/image track was found; no pixel file was produced"; goto done; }
        }
        // This getter requires vo_get_current_frame() to return an actual image.
        // A loaded file, redraw event or nonzero dimensions alone is insufficient.
        mpv_node current = {0};
        int ready = mpv_get_property(mpv, "video-frame-info", MPV_FORMAT_NODE, &current);
        mpv_free_node_contents(&current);
        if (ready < 0) {
            if (ended) { error = "MPV input ended without a complete decoded image"; goto done; }
            continue;
        }
        mpv_node parameters = {0};
        int geometry = mpv_get_property(mpv, "video-out-params", MPV_FORMAT_NODE, &parameters);
        bool bounded = geometry >= 0 && display_geometry(&parameters, maximum_dimension, &display_width, &display_height);
        mpv_free_node_contents(&parameters);
        if (!bounded) { error = "MPV display geometry is not bounded square-pixel output; screenshot not requested"; goto done; }
        mpv_node arguments[2] = {
            {.format = MPV_FORMAT_STRING, .u.string = "screenshot-raw"},
            {.format = MPV_FORMAT_STRING, .u.string = "video"}
        };
        mpv_node_list list = {.num = 2, .values = arguments};
        mpv_node command = {.format = MPV_FORMAT_NODE_ARRAY, .u.list = &list};
        if (mpv_command_node(mpv, &command, &frame) >= 0) { captured = true; break; }
        mpv_free_node_contents(&frame);
        if (ended) { error = "MPV input ended without a complete screenshot"; goto done; }
    }
    if (!captured) { error = "MPV returned no complete decoded image within the frame budget"; goto done; }
    FrameRaster raster = {0};
    if (!frame_raster(&frame, maximum_dimension, &raster) || raster.width != display_width || raster.height != display_height) {
        error = "MPV raw-frame dimensions, stride or bytes differ from the bounded display contract"; goto done;
    }
    width = raster.width; height = raster.height; stride = raster.stride;
    packed = raster.row_bytes;
    rgba = malloc(packed);
    if (!rgba) { error = "Cannot allocate the bounded MPV pixel row"; goto done; }
    for (int64_t y = 0; y < height; y++) {
        const unsigned char *native = raster.data + y * stride;
        rgba_row(rgba, native, (size_t)width);
        if (!write_pixels(output, rgba, packed, (off_t)y * (off_t)packed, input.deadline)) {
            error = "MPV pixel output failed or exceeded its deadline"; goto done;
        }
    }
    literal(&json, "{\"schema\":\"org.diemobile.mpv-frame\",\"schemaVersion\":1,\"ok\":true,\"engine\":\"libmpv\",\"adapterVersion\":1,");
    number(&json, "\"widthPixels\":%" PRId64 ",\"heightPixels\":%" PRId64
           ",\"rowBytes\":%zu,\"pixelBytes\":%zu,\"maximumDimensionPixels\":%d,",
           width, height, packed, packed * (size_t)height, maximum_dimension);
    literal(&json, "\"pixelFormat\":\"rgba8\",\"alphaMode\":\"opaque\","
                  "\"sourceAlphaPreserved\":false,\"nativeScreenshotFormat\":\"bgr0\","
                  "\"decodedCurrentFrameConfirmed\":true,\"everyFrameRendered\":false,"
                  "\"completeFileValidityVerified\":false,\"frameTimestampVerified\":false,"
                  "\"displayGeometryPreflightPassed\":true,\"squarePixelRaster\":true,");
    number(&json, "\"runtimeApiVersion\":%lu,\"headersApiVersion\":%u,\"fileSizeBytes\":%" PRId64 ",", api, MPV_CLIENT_API_VERSION, input.size);
#ifdef DIE_MPV_ANDROID_BUNDLE
    literal(&json, "\"engineBinaryOrigin\":\"mpv-android-2026-09-17-arm64\",\"engineSourceRevision\":\"" HEADERS_SOURCE "\",");
#else
    literal(&json, "\"engineBinaryOrigin\":\"external-host-runtime\",\"engineSourceRevision\":null,");
#endif
    literal(&json, "\"headersSourceRevision\":\"" HEADERS_SOURCE "\",\"propertyCount\":7,\"properties\":[");
    const char *getters[] = {"mpv-version", "ffmpeg-version", "file-format", "video-params",
                             "video-out-params", "video-frame-info", "track-list"};
    for (size_t i = 0; i < sizeof(getters) / sizeof(getters[0]); i++) {
        if (i) literal(&json, ",");
        literal(&json, "{\"name\":"); string(&json, getters[i]);
        mpv_node value = {0};
        int result = mpv_get_property(mpv, getters[i], MPV_FORMAT_NODE, &value);
        number(&json, ",\"errorCode\":%d,\"node\":", result >= 0 ? 0 : result);
        if (result >= 0) node(&json, &value, 0); else literal(&json, "null");
        mpv_free_node_contents(&value); literal(&json, "}");
    }
    literal(&json, "],\"filter\":"); string(&json, filter);
    literal(&json, ",\"options\":[");
    for (size_t i = 0; i < sizeof(options) / sizeof(options[0]); i++) {
        if (i) literal(&json, ",");
        literal(&json, "{\"name\":"); string(&json, options[i][0]);
        literal(&json, ",\"value\":"); string(&json, options[i][1]); literal(&json, "}");
    }
    literal(&json, ",{\"name\":\"vf\",\"value\":"); string(&json, filter);
    literal(&json, "},{\"name\":\"screenshot-sw\",\"value\":\"yes\"}]");
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
               ",\"streamOpenCalls\":%" PRIuFAST64 ",\"ungrantedCallbackUris\":%" PRIuFAST64
               ",\"maxInputBytes\":%lld,\"maxReadBytes\":%llu,\"nativeDeadlineMs\":%d"
               ",\"maxNativeRasterBytes\":%u,\"maxPixelBytes\":%u,\"maxOutputBytes\":%u,\"maxNodes\":%u,\"nodeCount\":%u}",
               atomic_load(&input.bytes), atomic_load(&input.seeks), atomic_load(&input.opens), atomic_load(&input.denied),
               INPUT_LIMIT, READ_LIMIT, DEADLINE_MS, FRAME_MAX_NATIVE_BYTES, FRAME_MAX_PIXEL_BYTES, OUTPUT_LIMIT, NODE_LIMIT, json.nodes);
    if ((error || json.error) && output_owned) (void)ftruncate(output, 0);
    if (input.fd >= 0) close(input.fd);
    if (output >= 0) close(output);
    free(rgba);
    if (numeric_locale) { uselocale(previous_locale); freelocale(numeric_locale); }
    return result_bytes(env, &json, error);
}
