// SPDX-License-Identifier: GPL-3.0-only
/* Internal shared JSON, descriptor callbacks and mandatory libmpv guards. */
#ifndef DIE_MPV_SUPPORT_H
#define DIE_MPV_SUPPORT_H
#include <jni.h>
#include <mpv/client.h>
#include <mpv/stream_cb.h>
#include <errno.h>
#include <inttypes.h>
#include <math.h>
#include <locale.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <fcntl.h>

#define INPUT_LIMIT (256LL * 1024 * 1024)
#define READ_LIMIT (32ULL * 1024 * 1024)
#define OUTPUT_LIMIT (32U * 1024 * 1024)
#define NODE_LIMIT 100000U
#define PROPERTY_LIMIT 4096
#define TEXT_LIMIT (8U * 1024 * 1024)
#define DEADLINE_MS 25000
#define HEADERS_SOURCE "0b7ed670f7c353dd3dd4f8ae0fc788a181a15aa6"
#define INPUT_URI "dieblob://selected"

typedef struct {
    char *data;
    size_t length, capacity;
    unsigned nodes;
    const char *error;
} Json;

static inline void append(Json *json, const char *text, size_t count) {
    if (json->error) return;
    if (count > OUTPUT_LIMIT - json->length) {
        json->error = "MPV report exceeds the 32 MiB output budget; no partial report returned";
        return;
    }
    size_t needed = json->length + count + 1;
    if (needed > json->capacity) {
        size_t capacity = needed < 4096 ? 4096 : needed * 2;
        if (capacity > OUTPUT_LIMIT + 1U) capacity = OUTPUT_LIMIT + 1U;
        char *next = realloc(json->data, capacity);
        if (!next) { json->error = "Unable to allocate the MPV report"; return; }
        json->data = next;
        json->capacity = capacity;
    }
    memcpy(json->data + json->length, text, count);
    json->length += count;
    json->data[json->length] = 0;
}
static inline void literal(Json *json, const char *text) { append(json, text, strlen(text)); }
static inline void number(Json *json, const char *format, ...) {
    char buffer[4096];
    va_list args;
    va_start(args, format);
    int count = vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    if (count < 0 || count >= (int)sizeof(buffer)) json->error = "MPV numeric rendering failed";
    else append(json, buffer, (size_t)count);
}
static inline size_t text_length(Json *json, const char *text) {
    if (!text) return 0;
    size_t count = strnlen(text, TEXT_LIMIT + 1U);
    if (count > TEXT_LIMIT) { json->error = "MPV string exceeds the 8 MiB value budget"; return 0; }
    return count;
}
/* JSON strings are ASCII escaped. Invalid UTF-8 is replaced visibly; every
 * engine-provided string/map key also retains its exact pre-decoding bytes. */
static inline void quoted(Json *json, const char *text, size_t count) {
    literal(json, "\"");
    for (size_t i = 0; i < count && !json->error;) {
        unsigned char byte = (unsigned char)text[i++];
        if (byte == '"' || byte == '\\') { char pair[2] = {'\\', (char)byte}; append(json, pair, 2); }
        else if (byte >= 0x20 && byte < 0x7f) append(json, (const char *)&byte, 1);
        else {
            uint32_t value = byte;
            if (byte >= 0x80) {
                unsigned extra = byte >= 0xc2 && byte <= 0xdf ? 1 : byte >= 0xe0 && byte <= 0xef ? 2 : byte >= 0xf0 && byte <= 0xf4 ? 3 : 0;
                value = extra ? byte & ((1U << (6 - extra)) - 1U) : 0xfffd;
                bool valid = extra && extra <= count - i;
                for (unsigned k = 0; valid && k < extra; k++) {
                    unsigned char next = (unsigned char)text[i + k];
                    if ((next & 0xc0) != 0x80) valid = false;
                    else value = (value << 6) | (next & 0x3f);
                }
                if (valid && (value < (extra == 1 ? 0x80U : extra == 2 ? 0x800U : 0x10000U) || value > 0x10ffffU || (value >= 0xd800 && value <= 0xdfff))) valid = false;
                if (valid) i += extra; else value = 0xfffd;
            }
            if (value <= 0xffff) number(json, "\\u%04" PRIx32, value);
            else { value -= 0x10000; number(json, "\\u%04" PRIx32 "\\u%04" PRIx32, 0xd800 + (value >> 10), 0xdc00 + (value & 0x3ff)); }
        }
    }
    literal(json, "\"");
}
static inline void string(Json *json, const char *text) {
    if (!text) literal(json, "null"); else quoted(json, text, text_length(json, text));
}
static inline void hex(Json *json, const void *bytes, size_t count) {
    const unsigned char *data = bytes;
    const char digits[] = "0123456789abcdef";
    literal(json, "\"");
    for (size_t i = 0; i < count && !json->error; i++) {
        char pair[2] = {digits[data[i] >> 4], digits[data[i] & 15]};
        append(json, pair, 2);
    }
    literal(json, "\"");
}
static inline void node(Json *json, const mpv_node *value, unsigned depth) {
    if (json->error) return;
    if (depth > 64 || ++json->nodes > NODE_LIMIT) { json->error = "MPV node tree exceeds depth 64 or 100,000 nodes"; return; }
    number(json, "{\"format\":%d,", value->format);
    switch (value->format) {
    case MPV_FORMAT_NONE: literal(json, "\"type\":\"none\",\"value\":null"); break;
    case MPV_FORMAT_STRING: {
        size_t count = text_length(json, value->u.string);
        literal(json, "\"type\":\"string\",\"value\":"); string(json, value->u.string);
        literal(json, ",\"utf8Hex\":"); hex(json, value->u.string, count);
        number(json, ",\"byteLength\":%zu", count); break;
    }
    case MPV_FORMAT_FLAG: literal(json, value->u.flag ? "\"type\":\"flag\",\"value\":true" : "\"type\":\"flag\",\"value\":false"); break;
    case MPV_FORMAT_INT64: number(json, "\"type\":\"int64\",\"value\":%" PRId64 ",\"decimal\":\"%" PRId64 "\"", value->u.int64, value->u.int64); break;
    case MPV_FORMAT_DOUBLE: {
        uint64_t bits; memcpy(&bits, &value->u.double_, sizeof(bits));
        literal(json, "\"type\":\"double\",\"value\":");
        if (isfinite(value->u.double_)) number(json, "%.17g", value->u.double_); else literal(json, "null");
        number(json, ",\"ieee754Bits\":\"%016" PRIx64 "\"", bits); break;
    }
    case MPV_FORMAT_NODE_ARRAY:
    case MPV_FORMAT_NODE_MAP: {
        mpv_node_list *list = value->u.list;
        if (!list || list->num < 0 || list->num > (int)NODE_LIMIT || (list->num && (!list->values || (value->format == MPV_FORMAT_NODE_MAP && !list->keys)))) { json->error = "MPV returned an invalid node list"; return; }
        literal(json, value->format == MPV_FORMAT_NODE_MAP ? "\"type\":\"map\",\"children\":[" : "\"type\":\"array\",\"children\":[");
        for (int i = 0; i < list->num && !json->error; i++) {
            if (i) literal(json, ",");
            if (value->format == MPV_FORMAT_NODE_MAP) {
                literal(json, "{\"key\":"); string(json, list->keys[i]);
                literal(json, ",\"keyUtf8Hex\":"); hex(json, list->keys[i], text_length(json, list->keys[i]));
                literal(json, ",\"node\":"); node(json, &list->values[i], depth + 1); literal(json, "}");
            } else node(json, &list->values[i], depth + 1);
        }
        literal(json, "]"); break;
    }
    case MPV_FORMAT_BYTE_ARRAY: {
        mpv_byte_array *array = value->u.ba;
        if (!array || array->size > TEXT_LIMIT || (array->size && !array->data)) { json->error = "MPV returned an invalid or oversized byte array"; return; }
        literal(json, "\"type\":\"bytes\",\"dataHex\":"); hex(json, array->data, array->size);
        number(json, ",\"byteLength\":%zu", array->size); break;
    }
    default: json->error = "MPV returned an unsupported node type; no partial report returned"; return;
    }
    literal(json, "}");
}

static inline int64_t now_ms(void) {
    struct timespec time; clock_gettime(CLOCK_MONOTONIC, &time);
    return (int64_t)time.tv_sec * 1000 + time.tv_nsec / 1000000;
}
typedef struct {
    int fd;
    int64_t size, deadline;
    atomic_uint_fast64_t bytes, seeks, opens, denied;
    atomic_bool budget_hit;
} Input;
typedef struct { Input *input; int64_t position; atomic_bool cancelled; } Stream;
static inline int64_t stream_read(void *cookie, char *buffer, uint64_t requested) {
    Stream *stream = cookie; Input *input = stream->input;
    if (atomic_load(&stream->cancelled) || now_ms() > input->deadline) return -1;
    uint64_t available = stream->position < input->size ? (uint64_t)(input->size - stream->position) : 0;
    if (!available || !requested) return 0;
    if (requested > available) requested = available;
    if (requested > 1024 * 1024) requested = 1024 * 1024;
    uint_fast64_t used = atomic_load(&input->bytes);
    for (;;) {
        if (used >= READ_LIMIT) { atomic_store(&input->budget_hit, true); return -1; }
        if (requested > READ_LIMIT - used) requested = READ_LIMIT - used;
        if (atomic_compare_exchange_weak(&input->bytes, &used, used + requested)) break;
    }
    ssize_t count;
    do { count = pread(input->fd, buffer, (size_t)requested, stream->position); } while (count < 0 && errno == EINTR && now_ms() <= input->deadline);
    if (count > 0) stream->position += count;
    atomic_fetch_sub(&input->bytes, requested - (count > 0 ? (uint64_t)count : 0));
    return count;
}
static inline int64_t stream_seek(void *cookie, int64_t position) {
    Stream *stream = cookie;
    if (atomic_load(&stream->cancelled) || now_ms() > stream->input->deadline || position < 0 || position > stream->input->size) return MPV_ERROR_GENERIC;
    stream->position = position; atomic_fetch_add(&stream->input->seeks, 1); return position;
}
static inline int64_t stream_size(void *cookie) { return ((Stream *)cookie)->input->size; }
static inline void stream_close(void *cookie) { free(cookie); }
static inline void stream_cancel(void *cookie) { atomic_store(&((Stream *)cookie)->cancelled, true); }
static inline int stream_open(void *data, char *uri, mpv_stream_cb_info *info) {
    Input *input = data;
    if (strcmp(uri, INPUT_URI) != 0 || now_ms() > input->deadline) { atomic_fetch_add(&input->denied, 1); return MPV_ERROR_LOADING_FAILED; }
    Stream *stream = calloc(1, sizeof(*stream));
    if (!stream) return MPV_ERROR_NOMEM;
    stream->input = input; atomic_init(&stream->cancelled, false);
    info->cookie = stream; info->read_fn = stream_read; info->seek_fn = stream_seek;
    info->size_fn = stream_size; info->close_fn = stream_close; info->cancel_fn = stream_cancel;
    atomic_fetch_add(&input->opens, 1); return 0;
}

/* Setting every guard is mandatory. A removed/unsupported option fails closed. */
static const char *const options[][2] = {
    {"config","no"}, {"load-scripts","no"}, {"load-auto-profiles","no"},
    {"autoload-files","no"}, {"access-references","no"}, {"ytdl","no"},
    {"osc","no"}, {"input-default-bindings","no"}, {"input-vo-keyboard","no"},
    {"input-terminal","no"}, {"terminal","no"}, {"hwdec","no"},
    {"vo","null"}, {"ao","null"}, {"pause","yes"}, {"idle","yes"},
    {"keep-open","always"}, {"sid","no"}, {"cache","no"},
    {"demuxer","+lavf"}, {"demuxer-lavf-o","protocol_whitelist=dieblob"},
    {"demuxer-max-bytes","16MiB"}, {"demuxer-max-back-bytes","0"},
    {"demuxer-readahead-secs","0"}, {"demuxer-lavf-probesize","8388608"},
    {"demuxer-lavf-analyzeduration","10"}
};
static inline jbyteArray result_bytes(JNIEnv *env, Json *json, const char *error) {
    if (error || json->error) {
        const char *reason = error ? error : json->error;
        free(json->data); *json = (Json){0};
        literal(json, "{\"ok\":false,\"error\":"); string(json, reason); literal(json, "}");
    }
    if (json->error || !json->data) { free(json->data); return NULL; }
    jbyteArray result = (*env)->NewByteArray(env, (jsize)json->length);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)json->length, (const jbyte *)json->data);
    free(json->data); return result;
}

#endif
