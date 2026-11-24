// SPDX-License-Identifier: GPL-3.0-only
/* Exercise the production pre-allocation, native-node and output ownership gates. */
#include "../media-engine/frame.c"
#include <assert.h>

static mpv_node map(mpv_node_list *list) {
    return (mpv_node){.format=MPV_FORMAT_NODE_MAP, .u.list=list};
}

static void metadata_bounds(void) {
    char *keys[] = {"type"};
    mpv_node value = {.format=MPV_FORMAT_STRING, .u.string="video"};
    mpv_node_list fields = {.num=1, .keys=keys, .values=&value};
    mpv_node track = map(&fields);
    mpv_node_list entries = {.num=1, .values=&track};
    mpv_node tracks = {.format=MPV_FORMAT_NODE_ARRAY, .u.list=&entries};
    assert(video_track_status(&tracks, now_ms()+1000) == 1);
    value.u.string = "audio";
    assert(video_track_status(&tracks, now_ms()+1000) == 0);
    entries.num = 0; entries.values = NULL;
    assert(video_track_status(&tracks, now_ms()+1000) == 0);
    entries.num = 1;
    assert(video_track_status(&tracks, now_ms()+1000) < 0);
    entries.values = &track; entries.num = (int)NODE_LIMIT+1;
    assert(video_track_status(&tracks, now_ms()+1000) < 0);
    entries.num = -1;
    assert(video_track_status(&tracks, now_ms()+1000) < 0);
    entries.num = 1;
    fields.values = NULL;
    assert(!map_field(&track, "type") && video_track_status(&tracks, now_ms()+1000) < 0);
    fields.values = &value; fields.keys = NULL;
    assert(!map_field(&track, "type"));
    fields.keys = keys; fields.num = (int)NODE_LIMIT+1;
    assert(!map_field(&track, "type"));
    fields.num = 1;
    assert(video_track_status(&tracks, now_ms()-1) < 0);
    char *duplicates[] = {"type", "type"};
    mpv_node values[] = {value, value};
    fields = (mpv_node_list){.num=2, .keys=duplicates, .values=values};
    assert(!map_field(&track, "type") && video_track_status(&tracks, now_ms()+1000) < 0);
    char unterminated[256]; memset(unterminated, 'x', sizeof(unterminated));
    keys[0] = unterminated;
    fields = (mpv_node_list){.num=1, .keys=keys, .values=&value};
    assert(!map_field(&track, "type"));
}

static void before_screenshot_allocation(void) {
    char *keys[] = {"w", "h", "dw", "dh", "par"};
    mpv_node values[] = {
        {.format=MPV_FORMAT_INT64, .u.int64=64},
        {.format=MPV_FORMAT_INT64, .u.int64=48},
        {.format=MPV_FORMAT_INT64, .u.int64=64},
        {.format=MPV_FORMAT_INT64, .u.int64=48},
        {.format=MPV_FORMAT_DOUBLE, .u.double_=1.0}
    };
    mpv_node_list fields = {.num=5, .keys=keys, .values=values};
    mpv_node params = map(&fields);
    int64_t width = 0, height = 0;
    assert(display_geometry(&params, 64, &width, &height) && width == 64 && height == 48);
    // Encoded pixels fit, but display/SAR expansion must be refused before screenshot-raw.
    values[2].u.int64 = INT64_MAX; values[4].u.double_ = 1000000.0;
    assert(!display_geometry(&params, 64, &width, &height));
    values[2].u.int64 = 64; values[4].u.double_ = 1.0;
    values[3].u.int64 = 65;
    assert(!display_geometry(&params, 64, &width, &height));
    values[3].u.int64 = 48; values[4].u.double_ = NAN;
    assert(!display_geometry(&params, 64, &width, &height));
    values[4].u.double_ = 0;
    assert(!display_geometry(&params, 64, &width, &height));
    values[4].u.double_ = 1.0; values[0].u.int64 = 0;
    assert(!display_geometry(&params, 64, &width, &height));
    values[0].u.int64 = 64; values[1].format = MPV_FORMAT_DOUBLE;
    assert(!display_geometry(&params, 64, &width, &height));
    fields.num = 0; fields.keys = NULL; fields.values = NULL;
    assert(!display_geometry(&params, 64, &width, &height));
    char filter[512];
    assert(frame_filter(filter, sizeof(filter), 64) && strstr(filter, "iw*sar") && strstr(filter, "setsar=1"));
    assert(!frame_filter(filter, 8, 64));
    assert(!frame_filter(filter, sizeof(filter), 0) && !frame_filter(filter, sizeof(filter), 1537));
}

static void raw_pixel_contract(void) {
    unsigned char data[16] = {10,20,30,0,40,50,60,17,99,99,99,99,99,99,99,99};
    mpv_byte_array bytes = {.data=data, .size=sizeof(data)};
    char *keys[] = {"w", "h", "stride", "format", "data"};
    mpv_node values[] = {
        {.format=MPV_FORMAT_INT64, .u.int64=2},
        {.format=MPV_FORMAT_INT64, .u.int64=1},
        {.format=MPV_FORMAT_INT64, .u.int64=16},
        {.format=MPV_FORMAT_STRING, .u.string="bgr0"},
        {.format=MPV_FORMAT_BYTE_ARRAY, .u.ba=&bytes}
    };
    mpv_node_list fields = {.num=5, .keys=keys, .values=values};
    mpv_node frame = map(&fields);
    FrameRaster raster = {0};
    assert(frame_raster(&frame, 64, &raster) && raster.row_bytes == 8 && raster.pixel_bytes == 8);
    unsigned char rgba[8]; rgba_row(rgba, raster.data, (size_t)raster.width);
    const unsigned char expected[] = {30,20,10,255,60,50,40,255};
    assert(!memcmp(rgba, expected, sizeof(expected))); // Neither undefined alpha nor padding escapes.
    bytes.size--;
    assert(!frame_raster(&frame, 64, &raster));
    bytes.size++; bytes.data = NULL;
    assert(!frame_raster(&frame, 64, &raster));
    bytes.data = data; values[2].u.int64 = -1;
    assert(!frame_raster(&frame, 64, &raster));
    values[2].u.int64 = INT64_MAX;
    assert(!frame_raster(&frame, 64, &raster));
    values[2].u.int64 = 16; values[0].u.int64 = INT64_MAX;
    assert(!frame_raster(&frame, 64, &raster));
    values[0].u.int64 = 2; values[3].u.string = "rgba";
    assert(!frame_raster(&frame, 64, &raster));
    values[3].u.string = "bgr0";
    assert(!frame_raster(&frame, 1, &raster) && !frame_raster(&frame, 1537, &raster));
}

static void events_and_io(void) {
    char reason[256]; bool ended = false;
    mpv_event event = {.event_id=MPV_EVENT_COMMAND_REPLY, .error=MPV_ERROR_LOADING_FAILED};
    assert(frame_event_failure(&event, reason, sizeof(reason), &ended) && !ended);
    event = (mpv_event){.event_id=MPV_EVENT_SHUTDOWN};
    assert(frame_event_failure(&event, reason, sizeof(reason), &ended));
    mpv_event_end_file end = {.reason=MPV_END_FILE_REASON_EOF};
    event = (mpv_event){.event_id=MPV_EVENT_END_FILE, .data=&end};
    assert(!frame_event_failure(&event, reason, sizeof(reason), &ended) && ended);
    end.reason = MPV_END_FILE_REASON_ERROR;
    assert(frame_event_failure(&event, reason, sizeof(reason), &ended));
    event.data = NULL;
    assert(frame_event_failure(&event, reason, sizeof(reason), &ended));
    assert(frame_event_failure(NULL, reason, sizeof(reason), &ended));

    FILE *file = tmpfile(); assert(file);
    assert(fseek(file, 11, SEEK_SET) == 0);
    unsigned char pixels[] = {0,0,0,255};
    assert(!write_pixels(fileno(file), pixels, sizeof(pixels), 0, now_ms()-1));
    struct stat before, after; assert(!fstat(fileno(file), &before) && before.st_size == 0);
    assert(write_pixels(fileno(file), pixels, sizeof(pixels), 0, now_ms()+1000));
    assert(ftell(file) == 11 && !fstat(fileno(file), &before) && before.st_size == 4);
    assert(!write_pixels(-1, pixels, sizeof(pixels), 0, now_ms()+1000));
    assert(!fstat(fileno(file), &after) && same_input(&before, &after));
    struct timespec times[] = {before.st_atim, before.st_mtim}; times[1].tv_sec++;
    assert(!futimens(fileno(file), times) && !fstat(fileno(file), &after) && !same_input(&before, &after));
    before = after; assert(!ftruncate(fileno(file), 3) && !fstat(fileno(file), &after));
    assert(!same_input(&before, &after));
    fclose(file);
}

static void failed_raster_rollback(void) {
    char name[] = "/tmp/diempv-rollback-XXXXXX";
    int output = mkstemp(name); assert(output >= 0);
    int read_only = open(name, O_RDONLY | O_CLOEXEC); assert(read_only >= 0);
    assert(!unlink(name));
    const unsigned char partial[] = {30,20,10,255};
    assert(pwrite(output, partial, sizeof(partial), 0) == (ssize_t)sizeof(partial));
    assert(lseek(output, 11, SEEK_SET) == 11);
    char principal[] = "MPV pixel output exceeded its deadline", cleanup_failure[512];
    const char *reason = rollback_pixels(output, principal, cleanup_failure, sizeof(cleanup_failure));
    struct stat status;
    assert(reason == principal && !fstat(output, &status) && status.st_size == 0);
    assert(lseek(output, 0, SEEK_CUR) == 11);

    // A real read-only descriptor cannot clear a failed raster. Keep its original
    // reason and bytes; the parent must discard the private file after any error.
    assert(pwrite(output, partial, sizeof(partial), 0) == (ssize_t)sizeof(partial));
    assert(lseek(read_only, 7, SEEK_SET) == 7);
    reason = rollback_pixels(read_only, principal, cleanup_failure, sizeof(cleanup_failure));
    assert(reason == cleanup_failure);
    assert(!strcmp(reason, "MPV pixel output exceeded its deadline; pixel output cleanup failed"));
    assert(!strcmp(principal, "MPV pixel output exceeded its deadline"));
    assert(!fstat(output, &status) && status.st_size == (off_t)sizeof(partial));
    assert(lseek(output, 0, SEEK_CUR) == 11 && lseek(read_only, 0, SEEK_CUR) == 7);
    unsigned char actual[sizeof(partial)];
    assert(pread(read_only, actual, sizeof(actual), 0) == (ssize_t)sizeof(actual));
    assert(!memcmp(actual, partial, sizeof(partial)));
    reason = rollback_pixels(-1, principal, cleanup_failure, sizeof(cleanup_failure));
    assert(reason == cleanup_failure && strstr(reason, principal) == reason);
    assert(!close(read_only) && !close(output));
}

int main(void) {
    metadata_bounds(); before_screenshot_allocation(); raw_pixel_contract(); events_and_io();
    failed_raster_rollback();
    puts("MPV frame C contracts passed: bounded/empty/malformed maps and tracks, display/SAR preflight, raw raster dimensions/stride/alpha, immediate failure events, positional output, deadlines, source-change gates and checked failed-raster rollback.");
    return 0;
}
