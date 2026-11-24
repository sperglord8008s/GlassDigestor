# Readable evidence and explicit text actions

`WorkbenchUi` supplies the shared hierarchy: bold record identity, labelled facts,
monospace values/bytes, proportional explanations, restrained section color and
thin list separators. Font sizes use Android scaled units. Sections are
accessibility headings where the platform supports them. List adapters retain
the original objects and selection indices; formatted text never becomes an
input to a parser or an export.

Explore groups package identity, APKs, directories, declared components and
permissions. Filtered counts exclude non-actionable headings; empty groups
disappear. A compact scope header expands on tap. Long-press a record for its
complete fields and local copy. Installed-package, split-APK, filesystem,
declared-service, package-knowledge and normal-UID Binder routes remain available.
Declared components and permissions do not establish current runtime behavior.

## Explain the observed string

Classifier 1.1.0 retains the same 43 rules and 16 overlapping groups. Its catalog
identity includes category labels and mechanism/context definitions. Each
returned occurrence retains its rule, original UTF-16 interval and mapped byte
range. `StringExplanation` reads this captured evidence without reclassifying
text. Per-row category definitions are normalized once, rather than repeated
for every occurrence; original occurrence order/indices remain available.

String rows show a concise **Why** for the opened category. Detail pages show
the captured value, matched text, **Why this category**, interpretation/next
checks and exact spans. The opened category is ordered first. Pages contain
eight occurrences; previous/next navigation retains every returned occurrence.
Definitions can collapse individually or together. Source fields and the
original JSON remain available, with the raw view initially expanded. Primary
match explanations precede source bookkeeping and raw JSON on small screens.

For example, a CVE-shaped token references a public vulnerability identifier;
it does not establish that affected code is present or exploitable. A tracing
API name may be relevant to debugger detection or ordinary diagnostics. A
memory-copy API requires caller, input and bounds analysis. No security score,
execution trace or confirmed vulnerability is inferred from a text match.

## Copy, search and translate to English

Whole captured strings and individual matched substrings have **Copy**,
**Search…** and **Translate…** actions. Search offers DuckDuckGo, Google, Bing
and Brave. English translation offers Google Translate, Bing Translator and
DeepL with automatic source-language selection. Selectable text elsewhere also
adds search/translation to Android's selection menu, preserving standard Copy.

The app retains no query history. Only the last engine ID is saved. Choosing an
engine constructs a fixed HTTPS URL and opens Android's browser chooser; the
browser and chosen service receive the text. The engine dialog explains this
before the user chooses, and permits cancellation or local copy. There is no
background submission, automatic translation, web client, new dependency or
INTERNET permission. Translation quality, service availability and browser
behavior are external to GlassDigestor and are not acceptance claims.

Dedicated Copy uses the exact captured UTF-16 value, including controls, NUL
and isolated surrogate units. Android 13+ clips are marked sensitive to suppress
the system content preview. Clipboard transfers above 128 Ki UTF-16 units are
refused explicitly; the previous clip is preserved. Browser URLs have an
8,192-unit encoded limit and reject isolated surrogates instead of silently
substituting or truncating text. Complete local evidence exports remain available.

Display-only escaping makes format/control characters and unpaired surrogates
visible without changing evidence, searches or exports. A selection-menu action
operates on the selected **display text**, which the chooser labels explicitly.
Use the dedicated captured-string/match action for the original value. Literal
newlines/tabs are escaped in single-line labels; ordinary Unicode and emoji
remain readable. Every shortened display is marked as a preview.

## Verification scope

Standalone string/DEX tests cover unchanged rules, exact UTF-8/UTF-16/MUTF-8
spans, normalized captured rationale and category-specific summaries. Host
Android tests cover exact raw/matched copy, all engine URL encodings, English
targets, cancellation, explicit chooser handoff, no saved query, sensitive
clipboard flags, occurrence pagination and Explore's existing routes/filtering.
Native-graphics host captures at 360 × 640 check the Explore pane and that the
first primary match explanation is visible. These captures are not handset
screenshots or screen-reader validation. Actual platform test scope and source
identities are recorded separately in the parity checklist and release evidence.
