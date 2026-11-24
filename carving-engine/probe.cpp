// SPDX-License-Identifier: GPL-3.0-only
// PhotoRec owns the signatures and format callbacks. This adapter owns only
// whole-blob matching, bounded read-only inputs, evidence and JNI transport.
#include "config.h"
#include "blob_input.h"
#include "read_only_file.h"
#include <jni.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <mutex>
#include <queue>
#include <sstream>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

extern "C" {
#include "types.h"
#include "filegen.h"
#include "log.h"
extern file_enable_t array_file_enable[];
extern file_check_list_t file_check_list;
extern const file_hint_t file_hint_mpg;
}

namespace {
constexpr uint64_t MAX_BLOB_BYTES = 256ULL * 1024 * 1024;
constexpr uint64_t MAX_WORK = 256ULL * 1024 * 1024;
constexpr uint64_t MAX_CANDIDATE_DATA_WORK = 64ULL * 1024 * 1024;
constexpr uint64_t MAX_CANDIDATE_FILE_WORK = 8ULL * 1024 * 1024;
constexpr size_t MAX_ROWS = 250000;
constexpr size_t MAX_REPORT = 24 * 1024 * 1024;
constexpr unsigned BLOCK = 65536;
constexpr const char *REVISION = "281be432dd79121e08e0898887a9ee1f30fb3e96";
std::mutex engine_mutex; // Upstream format modules have process-wide state.

std::string quoted(const char *text) {
    std::string result = "\"";
    if (text != nullptr) {
        constexpr char hex[] = "0123456789abcdef";
        for (const unsigned char *p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
            if (*p == '"' || *p == '\\') { result += '\\'; result += char(*p); }
            else if (*p < 32) {
                result += "\\u00"; result += hex[*p >> 4]; result += hex[*p & 15];
            } else result += char(*p);
        }
    }
    return result + '"';
}

std::string hex_bytes(const void *data, size_t size) {
    const auto *bytes = static_cast<const unsigned char *>(data);
    constexpr char hex[] = "0123456789abcdef";
    std::string result;
    result.reserve(size * 2);
    for (size_t i = 0; i < size; ++i) {
        result += hex[bytes[i] >> 4]; result += hex[bytes[i] & 15];
    }
    return result;
}

struct Node {
    std::map<unsigned char, size_t> edges;
    size_t failure = 0;
    std::vector<size_t> outputs;
};

struct Registry {
    file_stat_t *stats = nullptr;
    std::vector<const file_check_t *> patterns;
    std::vector<Node> nodes{1};
    std::array<size_t, 256> root{};
    size_t format_count = 0;
    size_t searched_patterns = 0;
    std::string catalog;

    Registry() {
        log_set_levels(0); // No native stdout/log file is part of an evidence report.
        for (file_enable_t *p = array_file_enable; p->file_hint; ++p) {
            // User-defined photorec.sig performs auxiliary filesystem acquisition.
            p->enable = std::strcmp(p->file_hint->extension, "custom") != 0;
            if (p->enable && p->file_hint->register_header_check) ++format_count;
        }
        stats = init_file_stats(array_file_enable);
        const td_list_head *group;
        td_list_for_each(group, &file_check_list.list) {
            const auto *bucket = td_list_entry_const(group, const file_check_list_t, list);
            for (unsigned b = 0; b < 256; ++b) {
                const td_list_head *entry;
                td_list_for_each(entry, &bucket->file_checks[b].list) {
                    const auto *p = td_list_entry_const(entry, const file_check_t, list);
                    if (!p->length || p->length > PHOTOREC_MAX_SIG_SIZE ||
                        p->offset > PHOTOREC_MAX_SIG_OFFSET || !p->file_stat ||
                        !p->file_stat->file_hint || !p->header_check)
                        throw std::runtime_error("Invalid upstream PhotoRec registration");
                    patterns.push_back(p);
                }
            }
        }
        if (patterns.size() > 10000) throw std::runtime_error("PhotoRec catalog exceeds 10000 patterns");
        // Stable original registration identity, independent of upstream search buckets.
        std::stable_sort(patterns.begin(), patterns.end(), [](const auto *a, const auto *b) {
            int order = std::strcmp(a->file_stat->file_hint->extension, b->file_stat->file_hint->extension);
            if (order != 0) return order < 0;
            if (a->offset != b->offset) return a->offset < b->offset;
            if (a->length != b->length) return a->length < b->length;
            return std::memcmp(a->value, b->value, a->length) < 0;
        });
        std::ostringstream out;
        out << '[';
        for (size_t i = 0; i < patterns.size(); ++i) {
            const auto *p = patterns[i];
            const auto *hint = p->file_stat->file_hint;
            const auto *bytes = static_cast<const unsigned char *>(p->value);
            bool all_zero = std::all_of(bytes, bytes + p->length, [](unsigned char byte) { return byte == 0; });
            bool searched = p->length >= 2 && !all_zero;
            if (i) out << ',';
            out << "{\"index\":" << i << ",\"name\":" << quoted(hint->extension)
                << ",\"description\":" << quoted(hint->description)
                << ",\"magicHex\":" << quoted(hex_bytes(p->value, p->length).c_str())
                << ",\"magicRelativeOffset\":" << p->offset
                << ",\"magicSizeBytes\":" << p->length
                << ",\"searched\":" << (searched ? "true" : "false")
                << ",\"searchExclusion\":" << (searched ? "null" : quoted(all_zero ? "all-zero signature requires anchored scope" : "one-byte signature requires anchored scope"))
                << ",\"upstreamRecover\":" << (hint->recover ? "true" : "false")
                << ",\"upstreamEnabledByDefault\":" << (hint->enable_by_default ? "true" : "false")
                << ",\"upstreamMaximumFileSizeBytes\":" << hint->max_filesize << '}';
            if (!searched) continue;
            ++searched_patterns;
            size_t node = 0;
            for (unsigned j = 0; j < p->length; ++j) {
                auto found = nodes[node].edges.find(bytes[j]);
                if (found == nodes[node].edges.end()) {
                    size_t next = nodes.size();
                    nodes[node].edges.emplace(bytes[j], next);
                    nodes.emplace_back();
                    node = next;
                } else node = found->second;
                if (nodes.size() > 262144) throw std::runtime_error("PhotoRec index exceeds state budget");
            }
            nodes[node].outputs.push_back(i);
        }
        out << ']'; catalog = out.str();
        std::queue<size_t> pending;
        for (const auto &edge : nodes[0].edges) { root[edge.first] = edge.second; pending.push(edge.second); }
        while (!pending.empty()) {
            size_t parent = pending.front(); pending.pop();
            for (const auto &edge : nodes[parent].edges) {
                size_t failure = nodes[parent].failure;
                while (failure && !nodes[failure].edges.count(edge.first)) failure = nodes[failure].failure;
                auto found = nodes[failure].edges.find(edge.first);
                if (found != nodes[failure].edges.end()) failure = found->second;
                nodes[edge.second].failure = failure;
                const auto &outputs = nodes[failure].outputs;
                nodes[edge.second].outputs.insert(nodes[edge.second].outputs.end(), outputs.begin(), outputs.end());
                pending.push(edge.second);
            }
        }
    }
    ~Registry() { free_header_check(); std::free(stats); }
};

struct Budget {
    uint64_t bytes = 0;
    size_t limited_candidates = 0;
    size_t suppressed_candidates = 0;
    size_t file_attempted = 0, file_checked = 0, file_limited = 0, file_metadata_limited = 0;
    std::chrono::steady_clock::time_point deadline = std::chrono::steady_clock::now() + std::chrono::seconds(30);
    void check(uint64_t amount = 0) {
        if (amount > MAX_WORK - bytes) throw std::runtime_error("PhotoRec callback buffer work exceeds 256 MiB; no complete result");
        bytes += amount;
        if (std::chrono::steady_clock::now() > deadline) throw std::runtime_error("PhotoRec exceeded 30-second cooperative deadline; no complete result");
    }
};

struct MpegOwner {
    size_t ordinal = 0;
    uint64_t start = 0, size = 0;
    bool contains_pack(uint64_t position) const {
        return size >= 4 && position > start && position - start <= size - 4;
    }
};

struct CandidateResult {
    std::string row;
    uint64_t bounded_mpeg_size = 0;
};

// Keep the standalone header result. This additional, tightly scoped upstream
// check distinguishes ignored in-stream packs from intentional new SCR=0 starts.
bool mpeg_continuation(const file_check_t *pattern, const unsigned char *header,
                       const file_recovery_t &standalone, uint64_t start, const MpegOwner &owner) {
    static constexpr unsigned char pack_magic[] = {0, 0, 1, 0xba};
    if (!owner.contains_pack(start) || pattern->file_stat->file_hint != &file_hint_mpg ||
            pattern->offset != 0 || pattern->length != sizeof(pack_magic) ||
            std::memcmp(pattern->value, pack_magic, sizeof(pack_magic)) != 0 ||
            standalone.file_check == nullptr || standalone.data_check == nullptr) return false;
    file_recovery_t active{}, contextual{};
    reset_file_recovery(&active); reset_file_recovery(&contextual);
    active.blocksize = BLOCK; contextual.blocksize = BLOCK;
    active.file_stat = pattern->file_stat;
    active.file_check = standalone.file_check;
    // The reviewed pack callback only consults active file_stat/file_check. No
    // file handle, filesystem recovery or upstream file-check callback is used.
    header_ignored(nullptr);
    const int accepted = pattern->header_check(header, BLOCK, 1, &active, &contextual);
    header_ignored(nullptr);
    return accepted == 0;
}

CandidateResult candidate(const Registry &registry, size_t pattern, uint64_t magic,
                      diemobile::BlobInput &input, uint64_t source_size, Budget &budget,
                      const MpegOwner &owner) {
    const auto *p = registry.patterns[pattern];
    const auto *hint = p->file_stat->file_hint;
    uint64_t start = magic - p->offset;
    uint64_t remaining = source_size - start;
    unsigned actual = unsigned(std::min<uint64_t>(remaining, BLOCK));
    budget.check();
    // fidentify uses a 64 KiB header window. Zero padding is explicitly exposed;
    // an accepted padded header never establishes a missing extent by itself.
    // Borrow a cached source window. Near-EOF padding is owned scratch, never
    // an invented source extent; repeated rejections do not copy 64 KiB each.
    const unsigned char *header = input.header(start);
    file_recovery_t old{}, recovered{};
    reset_file_recovery(&old); reset_file_recovery(&recovered);
    old.blocksize = BLOCK; recovered.blocksize = BLOCK;
    header_ignored_cond_reset(0, 0);
    int accepted = p->header_check(header, BLOCK, 1, &old, &recovered);
    if (!accepted) {
        // Rejected headers dominate large blobs. Keep every occurrence and all
        // returned state in a dictionary-described tuple instead of repeating
        // hundreds of field-name bytes. The Java viewer expands it per row.
        std::ostringstream rejected;
        rejected << '['
            << quoted(std::to_string(recovered.calculated_file_size).c_str()) << ','
            << quoted(std::to_string(recovered.min_filesize).c_str()) << ','
            << (recovered.data_check ? "true" : "false") << ','
            << (recovered.file_check ? "true" : "false") << ',' << int64_t(recovered.time) << ',' << recovered.flags << ','
            << quoted(std::to_string(recovered.offset_ok).c_str()) << ','
            << quoted(std::to_string(recovered.offset_error).c_str()) << ','
            << quoted(std::to_string(recovered.extra).c_str()) << ','
            << quoted(std::to_string(recovered.checkpoint_offset).c_str()) << ','
            << recovered.checkpoint_status << ',' << recovered.data_check_tmp << ','
            << quoted(std::to_string(recovered.file_size).c_str()) << ','
            << (recovered.extension ? quoted(recovered.extension) : "null") << ']';
        return {rejected.str(), 0};
    }
    uint64_t initial_calculated = recovered.calculated_file_size;
    uint64_t minimum = recovered.min_filesize;
    bool has_data = recovered.data_check != nullptr;
    bool has_file = recovered.file_check != nullptr;
    uint64_t extent = 0;
    uint64_t processed = 0;
    uint64_t padded = 0;
    int status = -1;
    bool limited = false;
    const char *limit_reason = nullptr;
    const char *data_mode = "not-run";
    const char *meaning = accepted ? "header-accepted/extent-unknown" : "header-rejected";
    const bool suppressed = mpeg_continuation(p, header, recovered, start, owner);
    if (suppressed) {
        ++budget.suppressed_candidates;
        data_mode = "upstream-active-mpeg-header-ignored/data-callback-suppressed";
        meaning = "header-accepted/inside-bounded-mpeg/independent-extent-unknown";
    } else if (accepted && has_data && recovered.calculated_file_size >= PHOTOREC_MAX_FILE_SIZE)
        meaning = "header-accepted/calculated-size-outside-upstream-data-contract";
    else if (accepted && has_data) {
        if (recovered.data_check == &data_check_size) {
            // Reviewed filegen.c: this callback ignores buffer contents and
            // compares only the declared size with the recovery counter.
            static const std::array<unsigned char, 2 * BLOCK> empty_window{};
            recovered.file_size = remaining > BLOCK ? remaining-BLOCK : 0;
            status = int(data_check_size(empty_window.data(), 2 * BLOCK, &recovered));
            data_mode = "upstream-declared-size-callback/no-payload-read";
        } else {
          data_mode = "upstream-streaming-data-callback";
          std::vector<unsigned char> window(2 * BLOCK, 0);
          for (uint64_t offset = 0; offset < remaining && recovered.data_check; offset += BLOCK) {
            unsigned count = unsigned(std::min<uint64_t>(remaining - offset, BLOCK));
            if (count > MAX_WORK-budget.bytes || count > MAX_CANDIDATE_DATA_WORK-processed) {
                limited = true;
                limit_reason = count > MAX_WORK-budget.bytes ? "aggregate-data-buffer-budget" : "per-candidate-data-buffer-budget";
                ++budget.limited_candidates;
                break;
            }
            budget.check(count);
            std::fill(window.begin() + BLOCK, window.end(), 0);
            input.read_at(start + offset, window.data() + BLOCK, count);
            recovered.file_size = offset;
            status = int(recovered.data_check(window.data(), 2 * BLOCK, &recovered));
            processed = offset + count;
            padded = BLOCK - count;
            if (status != DC_CONTINUE || recovered.data_check == nullptr ||
                recovered.calculated_file_size >= PHOTOREC_MAX_FILE_SIZE) break;
            std::memmove(window.data(), window.data() + BLOCK, BLOCK);
          }
        }
        if (limited) meaning = "header-accepted/data-budget-limited/extent-unknown";
        else if (status == DC_ERROR) meaning = "header-accepted/data-rejected";
        else if (status == DC_STOP) {
            meaning = "header-accepted/data-stop/out-of-source-or-invalid-extent";
            uint64_t size = recovered.calculated_file_size;
            if (size > 0 && size >= minimum && size <= remaining && size <= hint->max_filesize &&
                recovered.offset_error == 0) {
                extent = size;
                meaning = "header-accepted/upstream-data-stop-bounded-extent";
            }
        } else meaning = "header-accepted/data-incomplete/extent-unknown";
    }
    // Only this reviewed upstream module's file callback is enabled. Its footer/date readers
    // use the supplied FILE; no rename or arbitrary format callback is executed.
    const bool file_attempted = accepted && has_file && std::strcmp(hint->extension, "pdf") == 0;
    bool file_checked = false;
    uint64_t file_check_work = 0, file_check_size = 0;
    bool file_read_limited = false, file_metadata_limited = false;
    std::string file_failure;
    if (file_attempted) {
        ++budget.file_attempted;
        diemobile::ReadOnlyFile view(input, start, remaining, MAX_CANDIDATE_FILE_WORK,
                [&budget](uint64_t count) {
                    if (count > MAX_WORK - budget.bytes) throw diemobile::ReadOnlyFile::WorkLimit();
                    budget.check(count);
                });
        FILE *stream = view.open();
        if (stream != nullptr) {
            file_checked = true;
            ++budget.file_checked;
            recovered.handle = stream;
            recovered.file_size = remaining; // Parse scope only; never published as a recovered size.
            recovered.file_check(&recovered);
            recovered.handle = nullptr;
            fclose(stream);
            file_check_size = recovered.file_size;
        }
        file_check_work = view.work();
        if (view.failure() != nullptr) file_failure = view.failure();
        if (!file_failure.empty()) ++budget.file_limited;
        file_read_limited = view.read_limit_reached();
        extent = 0;
        const bool bounded_footer = file_checked && file_check_size > 0 && file_check_size >= minimum
                && file_check_size <= remaining && file_check_size <= hint->max_filesize
                && recovered.offset_error == 0;
        // Reviewed file_pdf.c: both PDF callbacks clear file_size on footer/read failure.
        // A positive size can survive a work limit only after the footer is established:
        // file_allow_nl may append an optional newline; file_date_pdf never changes its size.
        // Neither optional reader can invent a footer. No IO/deadline error is exempted.
        file_metadata_limited = bounded_footer && file_read_limited;
        if (file_metadata_limited) ++budget.file_metadata_limited;
        if (bounded_footer && (file_failure.empty() || file_metadata_limited)) {
            extent = file_check_size;
            meaning = file_metadata_limited
                    ? "header-accepted/upstream-pdf-footer-bounded/optional-date-read-limited"
                    : "header-accepted/upstream-pdf-file-check-bounded-extent";
        } else meaning = file_failure.empty() ? "header-accepted/upstream-pdf-file-check-rejected"
                : "header-accepted/upstream-pdf-file-check-limited/extent-unknown";
    }
    std::ostringstream out;
    out << "{\"name\":" << quoted(hint->extension) << ",\"description\":" << quoted(hint->description)
        << ",\"patternIndex\":" << pattern << ",\"physicalOffset\":" << start
        << ",\"candidateStartPhysicalOffset\":" << start
        << ",\"magicOffset\":" << magic << ",\"magicRelativeOffset\":" << p->offset
        << ",\"headerAccepted\":" << (accepted ? "true" : "false")
        << ",\"parserAccepted\":" << (accepted ? "true" : "false")
        << ",\"parserStatus\":" << quoted(meaning)
        << ",\"reportedSizeBytes\":" << (extent ? std::to_string(extent) : "null")
        << ",\"reportedExtension\":" << (recovered.extension ? quoted(recovered.extension) : "null")
        << ",\"headerWindowSourceBytes\":" << actual << ",\"headerWindowZeroPaddingBytes\":" << BLOCK - actual
        << ",\"headerCalculatedSizeBytes\":" << quoted(std::to_string(initial_calculated).c_str())
        << ",\"minimumFileSizeBytes\":" << quoted(std::to_string(minimum).c_str())
        << ",\"dataCheckAvailable\":" << (has_data ? "true" : "false")
        << ",\"dataCheckExecuted\":" << (status >= 0 ? "true" : "false")
        << ",\"dataCheckMode\":" << quoted(data_mode);
    if (suppressed) out << ",\"dataCheckSuppressedByCandidate\":" << owner.ordinal;
    out
        << ",\"dataCheckLimited\":" << (limited ? "true" : "false")
        << ",\"dataCheckLimitReason\":" << (limit_reason ? quoted(limit_reason) : "null")
        << ",\"dataCheckStatus\":" << (status < 0 ? "null" : std::to_string(status))
        << ",\"dataCheckSourceBytesProcessed\":" << processed << ",\"lastBlockZeroPaddingBytes\":" << padded
        << ",\"finalCalculatedSizeBytes\":" << quoted(std::to_string(recovered.calculated_file_size).c_str())
        << ",\"upstreamOffsetOk\":" << quoted(std::to_string(recovered.offset_ok).c_str())
        << ",\"upstreamOffsetError\":" << quoted(std::to_string(recovered.offset_error).c_str())
        << ",\"dataCheckFileSizeCounterBytes\":" << quoted(std::to_string(recovered.file_size).c_str())
        << ",\"upstreamExtra\":" << quoted(std::to_string(recovered.extra).c_str())
        << ",\"upstreamCheckpointOffset\":" << quoted(std::to_string(recovered.checkpoint_offset).c_str())
        << ",\"upstreamCheckpointStatus\":" << recovered.checkpoint_status
        << ",\"upstreamDataCheckTemporary\":" << recovered.data_check_tmp
        << ",\"fileCheckAvailable\":" << (has_file ? "true" : "false")
        << ",\"fileCheckAttempted\":" << (file_attempted ? "true" : "false")
        << ",\"fileCheckExecuted\":" << (file_checked ? "true" : "false")
        << ",\"fileCheckMode\":" << quoted(file_checked ? "upstream-pdf/read-only-relative-stdio" : "not-run")
        << ",\"fileCheckSourceBytesProcessed\":" << file_check_work
        << ",\"fileCheckSizeBytes\":" << (file_checked ? quoted(std::to_string(file_check_size).c_str()) : "null")
        << ",\"fileCheckFailureReason\":" << (file_failure.empty() ? "null" : quoted(file_failure.c_str()))
        << ",\"fileCheckReadBudgetLimited\":" << (file_read_limited ? "true" : "false")
        << ",\"fileCheckMetadataReadLimited\":" << (file_metadata_limited ? "true" : "false")
        << ",\"fileCheckBoundaryEstablished\":" << (file_attempted && extent > 0 ? "true" : "false")
        << ",\"renameCheckExecuted\":false"
        << ",\"upstreamTime\":" << int64_t(recovered.time) << ",\"upstreamFlags\":" << recovered.flags << '}';
    return {out.str(), hint == &file_hint_mpg ? extent : 0};
}

std::string scan(int fd) {
    std::lock_guard<std::mutex> serial(engine_mutex);
    Budget budget;
    diemobile::BlobInput input(fd, MAX_BLOB_BYTES, [&budget] { budget.check(); });
    const size_t source_size = size_t(input.size());
    static Registry registry;
    std::vector<std::string> rows;
    std::vector<std::string> rejected_states;
    std::map<std::string, size_t> state_indices;
    size_t compact_rejections = 0;
    std::vector<size_t> occurrences(registry.patterns.size(), 0);
    size_t report_bytes = registry.catalog.size();
    uint64_t underflow = 0;
    MpegOwner mpeg_owner;
    size_t node = 0;
    std::vector<unsigned char> scan_buffer(diemobile::BlobInput::SCAN_BYTES);
    for (size_t block_start = 0; block_start < source_size; block_start += scan_buffer.size()) {
        size_t available = std::min(scan_buffer.size(), source_size - block_start);
        input.read_at(block_start, scan_buffer.data(), available);
        for (size_t local = 0; local < available; ++local) {
            size_t offset = block_start + local;
            if ((offset & 4095) == 0) budget.check();
            unsigned char byte = scan_buffer[local];
            while (node && !registry.nodes[node].edges.count(byte)) node = registry.nodes[node].failure;
            if (!node) node = registry.root[byte];
            else node = registry.nodes[node].edges.at(byte);
            for (size_t pattern : registry.nodes[node].outputs) {
                const auto *p = registry.patterns[pattern];
                uint64_t magic = offset + 1 - p->length;
                if (rows.size() >= MAX_ROWS) {
                    size_t common = size_t(std::max_element(occurrences.begin(), occurrences.end())-occurrences.begin());
                    throw std::runtime_error("PhotoRec exceeds " + std::to_string(MAX_ROWS) + " candidate records at physical byte " + std::to_string(offset)
                        + "; dominant observed magic " + registry.patterns[common]->file_stat->file_hint->extension
                        + " (pattern " + std::to_string(common) + ", " + std::to_string(occurrences[common])
                        + " occurrences); no complete result");
                }
                ++occurrences[pattern];
                std::string row;
                if (magic < p->offset) {
                    ++underflow;
                    const auto *hint = p->file_stat->file_hint;
                    std::ostringstream rejected;
                    rejected << "{\"name\":" << quoted(hint->extension) << ",\"description\":" << quoted(hint->description)
                        << ",\"patternIndex\":" << pattern << ",\"physicalOffset\":" << magic
                        << ",\"magicOffset\":" << magic << ",\"magicRelativeOffset\":" << p->offset
                        << ",\"candidateStartPhysicalOffset\":null,\"derivedCandidateStart\":" << int64_t(magic) - p->offset
                        << ",\"physicalOffsetMeaning\":\"Observed magic only; derived candidate starts before input\""
                        << ",\"reportedSizeBytes\":null,\"headerAccepted\":false,\"parserAccepted\":false"
                        << ",\"fileCheckExecuted\":false,\"renameCheckExecuted\":false"
                        << ",\"parserStatus\":\"magic-matched/negative-candidate-start/header-not-called\"}";
                    row = rejected.str();
                } else {
                    CandidateResult result = candidate(registry, pattern, magic, input, source_size, budget, mpeg_owner);
                    row = std::move(result.row);
                    const uint64_t start = magic - p->offset;
                    // All candidates are retained. An independent SCR=0 stream
                    // may overlap this owner; never split or inherit its extent.
                    if (result.bounded_mpeg_size > 0 && start + result.bounded_mpeg_size > mpeg_owner.start + mpeg_owner.size)
                        mpeg_owner = {rows.size(), start, result.bounded_mpeg_size};
                }
                if (!row.empty() && row.front() == '[') {
                    auto found = state_indices.find(row);
                    size_t state;
                    if (found == state_indices.end()) {
                        state = rejected_states.size();
                        report_bytes += row.size();
                        rejected_states.push_back(row);
                        state_indices.emplace(std::move(row), state);
                    } else state = found->second;
                    row = '[' + std::to_string(pattern) + ',' + std::to_string(magic) + ',' + std::to_string(state) + ']';
                    ++compact_rejections;
                }
                report_bytes += row.size();
                if (report_bytes > MAX_REPORT) throw std::runtime_error("PhotoRec report exceeds 24 MiB; no complete result");
                rows.push_back(std::move(row));
            }
        }
    }
    input.verify_identity();
    std::ostringstream out;
    out << "{\"ok\":true,\"schema\":\"org.diemobile.photorec-evidence\",\"schemaVersion\":2"
        << ",\"engine\":\"PhotoRec\",\"engineVersion\":\"7.2\",\"sourceRevision\":" << quoted(REVISION)
        << ",\"sourceArchiveSha256\":\"0cfd82ca6890188a3a4fe01c9b970f654eb3af396580f408ed024789e2873616\""
        << ",\"adapter\":\"diemobile-photorec-6\",\"fileSizeBytes\":" << source_size
        << ",\"compactRejectedCandidateCount\":" << compact_rejections
        << ",\"rejectedRecoveryStateCount\":" << rejected_states.size()
        << ",\"compactRejectedEncoding\":\"[patternIndex,magicOffset,recoveryStateIndex]; original row ordinal is unchanged\""
        << ",\"maximumInputBytes\":" << MAX_BLOB_BYTES << ",\"maximumCandidateRecords\":" << MAX_ROWS
        << ",\"maximumReportBytes\":" << MAX_REPORT << ",\"cooperativeDeadlineMs\":30000"
        << ",\"inputReadMode\":\"bounded-pread-windows\",\"inputSizedBufferAllocation\":false"
        << ",\"maximumInputReadBufferBytes\":" << diemobile::BlobInput::MAX_INPUT_READ_BUFFERS + diemobile::ReadOnlyFile::BUFFER_BYTES
        << ",\"scanReadBufferBytes\":" << diemobile::BlobInput::SCAN_BYTES
        << ",\"headerCacheWindows\":" << diemobile::BlobInput::HEADER_WINDOWS
        << ",\"headerCacheWindowBytes\":" << diemobile::BlobInput::WINDOW_BYTES
        << ",\"sourceReadBytes\":" << input.bytes_read() << ",\"sourceReadOperations\":" << input.operations()
        << ",\"ownedDescriptorDuplicate\":true,\"callerDescriptorPositionPreserved\":true"
        << ",\"headerWindowMode\":\"borrowed bounded pread cache; explicit zero padding near EOF\""
        << ",\"rejectedRecoveryStateFields\":[\"headerCalculatedSizeBytes\",\"minimumFileSizeBytes\",\"dataCheckAvailable\",\"fileCheckAvailable\",\"upstreamTime\",\"upstreamFlags\",\"upstreamOffsetOk\",\"upstreamOffsetError\",\"upstreamExtra\",\"upstreamCheckpointOffset\",\"upstreamCheckpointStatus\",\"upstreamDataCheckTemporary\",\"dataCheckFileSizeCounterBytes\",\"reportedExtension\"]"
        << ",\"fullBlobSearch\":true,\"nestedResultsPruned\":false,\"unknownExtentsInferred\":false"
        << ",\"externalExtractorsExecuted\":false,\"fileCheckCallbacksExecuted\":" << (budget.file_checked > 0 ? "true" : "false")
        << ",\"fileCheckCallbackScope\":\"pdf-only/read-only-relative-stdio\""
        << ",\"fileCheckBoundaryPolicy\":\"native-positive-footer-survives-optional-post-footer-read-limit\""
        << ",\"fileCheckMetadataReadLimitedCount\":" << budget.file_metadata_limited
        << ",\"fileCheckCandidatesAttempted\":" << budget.file_attempted
        << ",\"fileCheckCandidatesExecuted\":" << budget.file_checked
        << ",\"fileCheckCandidatesLimited\":" << budget.file_limited
        << ",\"maximumCandidateFileReadBytes\":" << MAX_CANDIDATE_FILE_WORK
        << ",\"fileCheckReadBufferBytes\":" << diemobile::ReadOnlyFile::BUFFER_BYTES
        << ",\"renameCallbacksExecuted\":false,\"filesystemRecoveryExecuted\":false"
        << ",\"safeHeaderOnly\":true,\"auxiliarySignatureFiles\":false,\"optionalLibraries\":[]"
        << ",\"callbackBufferWorkBytes\":" << budget.bytes << ",\"maximumCallbackBufferWorkBytes\":" << MAX_WORK
        << ",\"maximumCandidateDataBufferBytes\":" << MAX_CANDIDATE_DATA_WORK
        << ",\"dataCallbacksLimitedCount\":" << budget.limited_candidates
        << ",\"dataCallbacksSuppressedCount\":" << budget.suppressed_candidates
        << ",\"mpegContinuationPolicy\":\"standalone-header-retained/active-mpeg-header-ignored/bounded-owner-only\""
        << ",\"negativeStartMagicMatches\":" << underflow
        << ",\"signatureCount\":" << registry.format_count << ",\"patternCount\":" << registry.patterns.size()
        << ",\"searchedPatternCount\":" << registry.searched_patterns
        << ",\"excludedPatternCount\":" << registry.patterns.size() - registry.searched_patterns
        << ",\"searchCoverage\":\"Every byte for catalog patterns marked searched; one-byte/all-zero signatures remain catalogued but require an anchored scope not implemented by this adapter\""
        << ",\"candidateCount\":" << rows.size() << ",\"patterns\":" << registry.catalog << ",\"rejectedRecoveryStates\":[";
    for (size_t i = 0; i < rejected_states.size(); ++i) { if (i) out << ','; out << rejected_states[i]; }
    out << "],\"rows\":[";
    for (size_t i = 0; i < rows.size(); ++i) { if (i) out << ','; out << rows[i]; }
    out << "]}";
    std::string report = out.str();
    if (report.size() > MAX_REPORT) throw std::runtime_error("PhotoRec complete report exceeds 24 MiB; no complete result");
    return report;
}

jstring result(JNIEnv *env, int descriptor) {
    std::string output;
    try { output = scan(descriptor); }
    catch (const std::exception &error) { output = "{\"ok\":false,\"error\":" + quoted(error.what()) + '}'; }
    jbyteArray bytes = env->NewByteArray(jsize(output.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, jsize(output.size()), reinterpret_cast<const jbyte *>(output.data()));
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto value = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, encoding));
    env->DeleteLocalRef(encoding); env->DeleteLocalRef(bytes); env->DeleteLocalRef(string_class);
    return value;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_org_diemobile_PhotoRecEngine_scanFd(JNIEnv *env, jclass, jint descriptor) {
    return result(env, descriptor);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_diemobile_PhotoRecEngine_scanPath(JNIEnv *env, jclass, jstring path) {
    const char *name = env->GetStringUTFChars(path, nullptr);
    if (!name) return nullptr;
    int fd = open(name, O_RDONLY | O_CLOEXEC);
    env->ReleaseStringUTFChars(path, name);
    jstring report = result(env, fd);
    if (fd >= 0) close(fd);
    return report;
}
