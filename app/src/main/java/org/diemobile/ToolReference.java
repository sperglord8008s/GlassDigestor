// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

/** Technical explanations remain separate from adapters, records and widgets. */
final class ToolReference {
    static final String[] TITLES = {"Structures", "Memory map", "Imports", "Exports", "Symbols", "Resources",
        "Disassembly", "Hashes", "Unicode strings", "Hex", "Entropy regions"};
    static final String[] HELP = {
        "Pinned CDiE PE/ELF/thin and fat Mach-O DataStructs expose typed structures and fields, raw/display/variant values, comments, semantic flags and parent relationships. Inventory omits fields; NAME[#FIELD] selects native records. Field storage offsets are distinct from their declared offset/VA values. Fat slices use an explicit index or host architecture / first; other slices remain raw. Unsupported readers and budget failures return no partial success.",
        "Physical offsets locate stored file bytes; VAs belong to the loader's preferred address space before relocation/ASLR. Virtual-only tails have no file bytes. All map records, overlaps and invisible entries are retained. Tap a row for fields/actions.",
        "Imports describe references to external interfaces. For PE, an offset/VA identifies an IAT slot, not the imported function body or import-by-name structure. Delay-load and ordinal records are preserved. An import does not prove a call.",
        "Exports identify named or ordinal entries. Forwarders redirect resolution to another module/entry. PE's native provider retains the first alias per slot. Presence does not prove execution.",
        "Symbol targets and source-table records have different offsets. Undefined, absolute, debug and file records need not have mapped target bytes. Zero size can mean unknown. Native kind/binding/storage fields remain visible.",
        "Resource records identify stored payload ranges, IDs, language and code page. Select a leaf to inspect or export its exact physical bytes; compressed payloads remain compressed. Unsupported readers fail explicitly.",
        "Instructions are linear byte decoding in the selected mode, not a control-flow trace or execution record. Address domain states whether the decoder used a VA or physical offset. Row pages and native byte blocks are distinct; use Next block for continuation.",
        "Whole-file CRC32, MD4, MD5, SHA1, SHA224, SHA256, SHA384 and SHA512 use pinned xxfclib/RHash contexts in one bounded isolated-worker pass. An empty selector returns all; an algorithm name selects one. CRC32 detects accidental errors; MD4/MD5/SHA1 have collision weaknesses. SHA-2 digests are not publisher authentication. File info opens the typed hash viewer with per-algorithm references.",
        "Strict UTF-8 and UTF-16LE/BE extraction is bounded by the native reader. Both UTF-16 alignments are scanned. Minimum length and encoding are captured with exports. Coincidental and unused strings are possible.",
        "Read-only 4 KiB pages show physical bytes. A file offset is not a VA. Go to offset/VA translates through a native map and asks you to choose when records overlap.",
        "Shannon entropy measures byte distributions, in bits per byte. This complete snapshot retains whole-input entropy, all eligible native physical regions, every contiguous block covering the input and all 256 histogram bins, including zeros. Regions can overlap; blocks partition the entire input. Histogram bins have no individual file offset. Native packed/not packed display labels use a 6.5 threshold and do not establish packing, encryption or maliciousness. Select physical measurements for range/byte actions; unsupported/malformed regions and budget failures remain explicit."
    };
    private ToolReference() {}
    static String[] modes(int tool) {
        if (tool == 0) return new String[]{"All structures", "Structure inventory"};
        if (tool == 1) return new String[]{"Default", "Sections", "Segments", "Flat physical Data"};
        if (tool == 6) return new String[]{"Auto", "x86 16", "x86 32", "x86 64", "ARM", "Thumb", "ARM64", "ARM BE", "Thumb BE", "ARM64 BE"};
        if (tool == 8) return new String[]{"UTF-8 + UTF-16", "ASCII", "UTF-8", "UTF-16LE", "UTF-16BE"};
        return new String[]{"Default"};
    }
    static String field(String key) {
        switch (key) {
            case "entropyBitsPerByte": return "Shannon byte entropy, -sum(p * log2(p)), over every byte in this exact physical interval. Range 0..8 bits/byte, with zero for empty input. It is a distribution statistic, not a packing/encryption/malware verdict.";
            case "entropyIeeeBitsHex": return "Exact native IEEE-754 binary64 bits for the entropy value; the JSON number uses the pinned locale-independent shortest round-trip formatter.";
            case "upstreamDisplayLabel": return "Pinned CDiE legacy display heuristic: packed at entropy >= 6.5, otherwise not packed. Ordinary compressed media can exceed the threshold; the label does not verify a packer or rule match.";
            case "byteValue": case "probability": return "Whole-input histogram bin (0..255) and derived count/fileSize, with zero for empty input. Every bin is retained. These values are aggregate statistics with no single storage offset or payload range.";
            case "rowType": return "Structures use structure/field rows and parentStructureIndex. Entropy uses whole-file/region/block measurements and aggregate byte-frequency bins; the latter have no individual physical location. All returned records remain exportable.";
            case "parentStructureIndex": return "Index of the containing structure in this same snapshot. Parent offsets are absolute in the whole input, including a chosen fat Mach-O slice.";
            case "relativeOffset": return "Signed native field offset relative to the containing structure. The derived physicalOffset is usable only when both parent and field fit the input.";
            case "rawValue": case "displayValue": return "Exact native hexadecimal/raw representation and formatted interpretation. Native reader display limits can apply; the complete stored range remains separately exportable.";
            case "nativeComment": return "Upstream format-specific explanation/decoded flags. Static declarations do not prove runtime use, authenticity or checksum validation.";
            case "properties": case "propertyNames": case "unknownPropertyBits": return "Semantic bits: ID 0x1, VA 0x2, file offset 0x4, size 0x8, count 0x10, pointer 0x20, flags 0x40, timestamp 0x80, string 0x100, reserved 0x200, signature 0x400, checksum 0x800. Unknown bits are retained. Flags alone do not distinguish PE RVAs, resource-relative offsets or fat Mach-O slice offsets. Only reviewed field-specific relationships enable value navigation, using a captured base when required. Pointer alone supplies no address domain; checksum alone is not verification.";
            case "nativeValue": return "Exact xx_var type and value. Integers are decimal strings, floating values retain IEEE bits, strings/bytes retain full returned hex, and wide strings retain native units. A missing variant is not zero. Native process pointers remain opaque.";
            case "nativeTextUnits": return "Exact native wchar code units for all returned field text, as eight hexadecimal digits per unit. JSON text preserves UTF-16; invalid 32-bit code points render U+FFFD while these units preserve their original value.";
            case "physicalRangeValid": return "Whether the entire declared stored range fits the parent and whole input. Invalid/unknown ranges have no physical navigation target; raw native coordinates remain present.";
            case "nativeStructureId": case "nativeStructureType": return "Unmodified format-specific structure ID and classification: 0 unknown, 1 structure, 2 entry, 3 footer, 4 locator, 5 raw data. RAW_DATA has no named-field schema.";
            case "nativeOffset": case "nativeAddress": case "nativeEntrySize": case "nativeTotalSize": case "nativeSize": return "Exact signed native descriptor value; negative sentinels remain distinct from usable offsets/sizes. nativeAddress retains raw signed bits while virtualAddress renders the available unsigned address.";
            case "count": return "For entropy histogram bins this is the exact whole-input occurrence count of the byte value. For structures it is the unsigned number of represented occurrences. CDiE expands fixed-size tables to individual structures when reading fields; inventory can retain an aggregate table descriptor.";
            case "index": return "Position in this detached snapshot, not a byte offset or native symbol-table index.";
            case "physicalOffset": return "Absolute physical file offset, or null when no stored target is available. For imports this may locate an indirection slot.";
            case "virtualAddress": return "Preferred virtual address before ASLR/relocation; null means unavailable. Runtime addresses require separate module/load observations.";
            case "sizeBytes": return "Exact native size. Null is unavailable; zero can mean unknown (especially symbols) or an empty item. Bounds must fit the file before range operations.";
            case "recordOffset": return "Physical location of the native symbol-table entry, distinct from the symbol target. Useful for corroborating the raw metadata.";
            case "tableIndex": return "Native symbol-table index, which can repeat across ELF tables. Null for entries without a native table index; use recordOffset to identify source bytes.";
            case "kind": return "Native symbol kind: 0 unknown, 1 function, 2 data, 3 section, 4 file, 5 label.";
            case "binding": return "Native symbol binding: 0 unknown, 1 local, 2 global, 3 weak. Visibility/resolution rules depend on the format and loader.";
            case "filePartFlags": return "Bit mask: region 0x1, section 0x2, segment 0x4, header 0x10, overlay 0x20, resource 0x40, debug 0x80, stream 0x100, signature 0x200, footer 0x400, data 0x1000, object 0x2000, table 0x4000, value 0x8000. Preserve unknown bits.";
            case "isVirtual": return "A virtual-only mapping describes loader memory without corresponding file bytes. It must not be extracted as zero-filled data from the file.";
            case "isInvisible": return "Native map visibility metadata. The record remains included here because it can affect address relationships.";
            case "value": return "Raw unsigned symbol value. It may be an absolute value or another native encoding; do not assume it is a physical offset or even an address.";
            case "nativeType": return "Native type encoding. A structure field supplies a declared type string; a symbol supplies a numeric format-specific encoding.";
            case "storageClass": case "sectionNumber": case "auxiliaryCount":
                return "Unmodified native symbol encoding. PE section numbers: 0 undefined, -1 absolute, -2 debug; positive values are one-based. Auxiliary entries may contribute metadata without separate displayed rows.";
            case "byOrdinal": case "ordinal": return "Ordinal imports/exports identify entries numerically. Named imports normally retain ordinal zero; null export ordinal means the format provides none.";
            case "hint": return "PE import lookup hint from the import-by-name record. It is a loader search aid, not a stable API identity or risk score.";
            case "isDelay": return "PE delay-load declaration: resolution can be deferred until use. This declaration does not establish that the interface was invoked.";
            case "library": case "forwarder": return "Declared module association or forwarding target. This is static metadata; it does not establish which runtime file would satisfy resolution.";
            case "typeId": case "nameId": case "languageId": return "Native resource identifier. 4294967295 (UINT32_MAX) marks a named identifier; preserve the corresponding name text. Numeric zero is not automatically missing.";
            case "codePage": return "Resource-declared code page, commonly PE metadata. It does not independently prove the actual encoding of every payload byte.";
            case "decodeAddress": case "addressDomain": return "Exact address passed to the decoder and its domain: virtual, file-offset or unknown. Unmapped decoding uses file offsets, which must not be mislabeled as VAs.";
            case "nameBytesHex": return "Hex of the native map's bounded name bytes (maximum 127 bytes), preserving invalid UTF-8. This is not a payload range or an unlimited original section-name string.";
            case "isUndefined": case "isAbsolute": case "isDebug": return "Native symbol classification. These entries can legitimately have no physical target; inspect the source-table record rather than inventing a file mapping.";
            case "bytes": case "instruction": case "comment": return "Native linear decoding and its diagnostic comment. An invalid/unsupported/truncated byte may be rendered as db; decoding bytes does not prove they execute.";
            default: return "Native reader metadata, retained without inferring runtime behavior. Corroborate with source bytes, other tables and format documentation.";
        }
    }
}
