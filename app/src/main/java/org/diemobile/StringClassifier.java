// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;
import org.json.*;

/** Offline, explainable string observations. A pattern match is never a verdict. */
final class StringClassifier {
    static final String ENGINE = "DiE Android string classifier", VERSION = "1.1.0";
    static final int MAX_MATCHES = 2048, MAX_TEXT_UNITS = 1024 * 1024;
    enum Group {
        URLS("URLs / URIs", "A URI scheme and address-like syntax can identify a resource or protocol. The matched literal is evidence of text, not of a request or successful access."),
        ENDPOINTS("Service / API endpoints", "A service path, URI or address can describe where software exchanges data. A path may need a separate host/base URL; no listening or reachable service is established."),
        DOMAINS("Domain candidates", "DNS-shaped labels can identify hosts, but filenames and package/class names share this syntax. Registration and resolution are not checked."),
        ADDRESSES("IP address literals", "Locally validated address syntax can name a network destination or interface. It can also be test data, a bind address or an unused constant; no connection is observed."),
        EMAILS("Email candidates", "Mailbox-shaped text can name a contact or account. Syntax alone does not show that the mailbox exists, belongs to someone or receives mail."),
        PATHS("File / registry paths", "Path syntax identifies a possible location in a filesystem, registry or provider. Actual access depends on code, permissions and the runtime environment."),
        COMMANDS("Commands / interpreters", "Interpreter/utility names and arguments can be passed to a command execution API. Here they are text; a caller and its arguments must be traced before claiming execution."),
        APIS("System / execution APIs", "API names refer to operations a program could request from its runtime or operating system. A name may be documentation or unused data; imports and call sites are separate evidence."),
        SECRETS("Credentials / secret candidates", "A recognized token shape or credential-related assignment could contain authentication material. Examples, placeholders and inactive/public values can match; validity and privileges are not tested."),
        CRYPTO("Cryptographic material / algorithms", "Algorithm names or encoded key/certificate markers suggest cryptographic context. Actual purpose, parameters, key contents and secure use need separate inspection."),
        PERSISTENCE("Persistence / startup indicators", "Boot, startup and scheduling mechanisms can arrange work after launch or reboot. Legitimate installers and administrators use them too; a string does not establish a registration or unwanted persistence."),
        EVASION("Anti-analysis / introspection indicators", "Debugger, instrumentation or environment checks can reveal analysis conditions; display controls can obscure what an analyst sees. These mechanisms can support evasion, diagnostics or compatibility. This category does not establish an evasion branch or behavior."),
        VULNERABILITIES("Vulnerability / exploit indicators", "A weakness identifier, review-sensitive API, legacy security primitive or attack-shaped input can point to a security question. A vulnerability needs corroborating affected code/version, unsafe parameters and a reachable input path; none is established by the string alone."),
        DANGEROUS("Dangerous operation / input indicators", "The matched operation, transport or input can have security impact in some contexts: execution, destructive changes, injection or unencrypted transfer. Arguments, permissions and actual use determine impact; no harmful action is observed."),
        ENCODINGS("Encoded data candidates", "A recognized encoding or encoded-command shape can wrap other bytes. Encoding is a representation, not proof of encryption or concealment; no decoded command is executed."),
        IDENTIFIERS("Hashes / identifiers", "A recognized identifier layout or digest-length token can link related records. Its shape does not establish an algorithm, object authenticity or a current database entry.");
        final String label, rationale;
        Group(String label, String rationale) { this.label = label; this.rationale = rationale; }
        long bit() { return 1L << ordinal(); }
        JSONObject json() throws JSONException { return new JSONObject().put("group", name()).put("label", label).put("reason", rationale); }
    }
    interface ByteLocation { long at(int utf16Index) throws Exception; }
    interface Validator { boolean accepts(String matched); }
    static final class Rule {
        final String id, title, expression, explanation, implication;
        final List<Group> groups;
        final Pattern pattern;
        final Validator validator;
        Rule(String id, String title, String expression, String explanation, String implication, Validator validator, Group... groups) {
            this.id = id; this.title = title; this.expression = expression; this.explanation = explanation; this.implication = implication;
            this.groups = Collections.unmodifiableList(Arrays.asList(groups)); this.pattern = Pattern.compile(expression); this.validator = validator;
        }
        long mask() { long value = 0; for (Group group : groups) value |= group.bit(); return value; }
        JSONObject json() throws JSONException {
            JSONArray categories = new JSONArray(), rationales = new JSONArray();
            for (Group group : groups) {
                categories.put(group.name());
                rationales.put(group.json());
            }
            return new JSONObject().put("ruleId", id).put("title", title).put("expression", expression).put("groups", categories)
                .put("categoryRationales", rationales)
                .put("observation", explanation).put("implicationsAndNextStep", implication).put("matchMethod", "Built-in Java regular expression with optional local syntactic validation")
                .put("validator", validator == null ? JSONObject.NULL : id).put("source", "DiE Android built-in catalog " + VERSION).put("license", "GPL-3.0-only");
        }
    }
    static final class Hit {
        final Rule rule;
        final int start, end;
        Hit(Rule rule, int start, int end) { this.rule = rule; this.start = start; this.end = end; }
    }
    static final class Result {
        final List<Hit> hits;
        final long groups;
        Result(List<Hit> hits, long groups) { this.hits = Collections.unmodifiableList(hits); this.groups = groups; }
        String summary(String preferredGroup) {
            if (hits.isEmpty()) return "No built-in rule matched this candidate; this is not a safety finding.";
            Hit chosen = hits.get(0);
            Group preferred = null;
            try { preferred = Group.valueOf(preferredGroup); } catch (IllegalArgumentException | NullPointerException ignored) { }
            if (preferred != null) for (Hit hit : hits) if (hit.rule.groups.contains(preferred)) { chosen = hit; break; }
            return "Why " + (preferred == null ? "classified" : preferred.label) + ": "
                    + chosen.rule.title + " — " + chosen.rule.explanation;
        }
        JSONArray json(String text, ByteLocation physical) throws Exception {
            JSONArray values = new JSONArray();
            for (Hit hit : hits) {
                JSONObject value = hit.rule.json();
                // Group definitions belong once to the row, not once per repeated occurrence.
                value.remove("categoryRationales");
                value.put("startUtf16", hit.start).put("endUtf16", hit.end)
                    .put("matchedTextPreview", text.substring(hit.start, Math.min(hit.end, hit.start + 512)))
                    .put("previewComplete", hit.end - hit.start <= 512).put("physicalMatchOffset", physical == null ? JSONObject.NULL : physical.at(hit.start))
                    .put("physicalMatchLengthBytes", physical == null ? JSONObject.NULL : physical.at(hit.end) - physical.at(hit.start));
                values.put(value);
            }
            return values;
        }
        JSONArray groupNames() { JSONArray values = new JSONArray(); for (Group group : Group.values()) if ((groups & group.bit()) != 0) values.put(group.name()); return values; }
        JSONArray categoryRationales() throws JSONException {
            JSONArray values = new JSONArray();
            for (Group group : Group.values()) if ((groups & group.bit()) != 0) values.put(group.json());
            return values;
        }
    }
    private static Rule rule(String id, String title, String expression, String observation, String next, Group... groups) {
        return new Rule(id,title,expression,observation,next,null,groups);
    }
    private static Rule validated(String id, String title, String expression, String observation, String next, Validator validator, Group... groups) {
        return new Rule(id,title,expression,observation,next,validator,groups);
    }
    private static final String NO_EXECUTION = "Presence does not establish execution, malicious intent or reachability. Follow references and input/data flow to determine actual use.";
    private static final String NO_NETWORK = "No DNS lookup, connection, reputation service or external validation is performed. Correlate with references/configuration before treating it as an active destination.";
    static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
        rule("net.uri", "Network URI", "(?i)\\b(?:https?|wss?|ftps?|sftp|ssh|tcp|udp|ldaps?|smb|rtsp|mqtts?|amqps?)://[^\\s\\p{Cntrl}<>\"'`]+",
            "A network-scheme URI occurs in the decoded string; the exact token and span are preserved.", NO_NETWORK, Group.URLS,Group.ENDPOINTS),
        rule("net.cleartext", "Cleartext transport scheme", "(?i)\\b(?:http|ws|ftp|ldap|mqtt|amqp)://[^\\s\\p{Cntrl}<>\"'`]+",
            "The URI names a scheme commonly used without transport encryption.", "A literal does not prove a connection or secret transmission; inspect protocol upgrades, configuration and callers.", Group.URLS,Group.ENDPOINTS,Group.DANGEROUS),
        rule("net.uri-credentials", "URI user information with password separator", "(?i)\\b[a-z][a-z0-9+.-]{1,20}://[^\\s/:@]{1,128}:[^\\s/@]{1,256}@",
            "A URI authority contains user:password-style information.", "This may be an example or placeholder. Check actual use and whether the value is sensitive; validity is not tested.", Group.URLS,Group.SECRETS),
        rule("android.uri", "Android / local URI", "(?i)\\b(?:content|android\\.resource|file|intent)://[^\\s\\p{Cntrl}<>\"'`]+",
            "A local/provider/intent URI is present; these schemes have different resolution and permission mechanisms.", "Inspect the provider authority, intent handling and granted URI permissions. A URI is not proof that the caller can access it.", Group.URLS,Group.PATHS),
        rule("net.api-path", "API or service path", "(?i)(?<![A-Za-z0-9_])/(?:api|graphql|v[0-9]{1,3}|oauth2?|rest|admin|login|upload|download|socket|health)(?:[/?#][^\\s\\p{Cntrl}<>\"'`]*)?(?![A-Za-z0-9_])",
            "A path begins with a common API/service segment.", "This is a path candidate, not a complete URL or evidence of a listening/reachable service. Correlate with a base URL and callers.", Group.ENDPOINTS),
        validated("net.ipv4", "IPv4 literal / optional port", "(?<![A-Za-z0-9_.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![A-Za-z0-9_.:])",
            "Four decimal octets fit 0–255; an optional decimal port fits 0–65535.", NO_NETWORK, StringClassifier::ipv4Endpoint, Group.ADDRESSES,Group.ENDPOINTS),
        validated("net.ipv6", "IPv6 literal / bracketed endpoint", "\\[[0-9A-Fa-f:.]{2,45}(?:%[A-Za-z0-9_.-]{1,32})?\\](?::[0-9]{1,5})?|(?<![A-Za-z0-9_:])(?:[0-9A-Fa-f]{0,4}:){2,8}[0-9A-Fa-f:.]{0,45}(?![A-Za-z0-9_:])",
            "The token has a locally validated IPv6 group/compression structure. Bracketed literals may have a scope identifier/port.", NO_NETWORK, StringClassifier::ipv6Endpoint, Group.ADDRESSES,Group.ENDPOINTS),
        rule("net.domain", "DNS-shaped name", "(?i)(?<![A-Za-z0-9_.-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.){1,10}(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?![A-Za-z0-9_.-])",
            "A token has DNS-label syntax and an alphabetic or punycode-shaped suffix.", "No public-suffix/TLD database or DNS lookup is used; filenames and class/package names can match. A DNS-shaped token need not be a registered domain.", Group.DOMAINS),
        rule("net.email", "Email-shaped address", "(?i)(?<![A-Za-z0-9_.+-])[a-z0-9_.+%-]{1,64}@[a-z0-9.-]{1,190}\\.[a-z]{2,63}(?![A-Za-z0-9_.-])",
            "A token has a common email-address shape, not a complete RFC mailbox validation.", "May be a contact, example, identifier or data. Existence and delivery are not tested.", Group.EMAILS),
        rule("path.windows", "Windows drive / UNC path", "(?i)(?<![A-Za-z0-9_])(?:[a-z]:[\\\\/]|\\\\\\\\[a-z0-9._-]{1,128}\\\\)[^\\r\\n\\x00\"'<>|]{1,512}",
            "A drive-rooted or UNC-shaped path is present.", "A path may refer to a build machine, documentation or runtime file. Inspect surrounding strings and references.", Group.PATHS),
        rule("path.unix", "Unix / Android absolute path", "(?<![A-Za-z0-9_:/])/(?:bin|sbin|usr|etc|var|tmp|home|root|opt|dev|proc|sys|data|system|vendor|product|apex|sdcard|storage|mnt)(?:/[^\\s\\p{Cntrl}\"'<>;]*)?(?![A-Za-z0-9_])",
            "An absolute path starts at a common Unix/Android root.", "Presence does not establish file access or permissions. Source/build paths and diagnostic text can also match.", Group.PATHS),
        rule("path.registry", "Windows registry path", "(?i)\\b(?:HKEY_(?:LOCAL_MACHINE|CURRENT_USER|CLASSES_ROOT|USERS|CURRENT_CONFIG)|HKLM|HKCU|HKCR|HKU)\\\\[^\\r\\n\\x00\"']{1,512}",
            "A registry hive/path token is present.", "Look for actual registry operations; a path may be configuration, documentation or a removal target.", Group.PATHS),
        rule("cmd.interpreter", "Shell / command interpreter", "(?i)(?<![A-Za-z0-9_])(?:cmd(?:\\.exe)?\\s+/[ck]\\b|powershell(?:\\.exe)?\\b|pwsh(?:\\.exe)?\\b|/bin/(?:ba|da|z|a)?sh\\b|sh\\s+-c\\b|bash\\s+-c\\b)",
            "A command interpreter name or shell command prefix is present.", NO_EXECUTION, Group.COMMANDS,Group.DANGEROUS),
        rule("cmd.transfer", "Transfer / remote administration command", "(?i)(?<![A-Za-z0-9_])(?:curl|wget|certutil|bitsadmin|rclone|scp|sftp|netcat|ncat|nc)(?:\\.exe)?(?=\\s+[-A-Za-z0-9/]|$)",
            "A transfer/administration utility name occurs in command-like context.", "These utilities have legitimate uses. Inspect arguments, references and whether a command is ever passed to an execution API.", Group.COMMANDS),
        rule("cmd.destructive", "Destructive / privilege command pattern", "(?i)\\b(?:rm\\s+-[a-z]*[rf][a-z]*|chmod\\s+(?:777|[+]?s|[ugoa]*[+]s)|(?:mkfs(?:\\.[a-z0-9]+)?|diskpart|format\\.com|vssadmin)\\b|reg\\s+(?:add|delete)\\b)",
            "A command token/option can delete data, change privileges or alter system configuration.", "Arguments, context and actual execution determine impact; examples and defensive tooling can contain the same strings.", Group.COMMANDS,Group.DANGEROUS),
        rule("cmd.encoded", "Encoded interpreter argument", "(?i)(?<![A-Za-z0-9_])-(?:enc|encodedcommand)\\s+[A-Za-z0-9+/]{8,4096}={0,2}",
            "An option resembles a PowerShell encoded-command argument.", "The classifier does not execute or recursively decode commands. Confirm the interpreter and decode bytes in a separate explicit investigation.", Group.COMMANDS,Group.ENCODINGS,Group.DANGEROUS),
        rule("api.windows-exec", "Windows execution / injection API", "\\b(?:CreateRemoteThread(?:Ex)?|WriteProcessMemory|VirtualAlloc(?:Ex)?|VirtualProtect(?:Ex)?|QueueUserAPC|SetThreadContext|NtCreateThreadEx|ShellExecute(?:Ex)?[AW]?|WinExec|CreateProcess[AW]?|URLDownloadToFile[AW]?)\\b",
            "Depending on the matched name, these Windows APIs can start processes/threads, change memory protection, access another process's memory or download files. Such operations can support injection or ordinary application/debugger work; only the name was found here.", NO_EXECUTION, Group.APIS,Group.DANGEROUS),
        rule("api.loader", "Dynamic loading / symbol lookup API", "\\b(?:LoadLibrary(?:Ex)?[AW]?|GetProcAddress|dlopen|dlsym|DexClassLoader|InMemoryDexClassLoader|PathClassLoader|loadLibrary)\\b",
            "A dynamic loader or symbol lookup name appears.", "Dynamic loading is common in ordinary applications and plugins. Resolve the referenced library/class and call sites.", Group.APIS),
        rule("api.process", "Process / shell execution API", "\\b(?:execve|execvp|execl|popen|system|ProcessBuilder|Runtime[.;/]exec|Runtime\\.getRuntime|java/lang/Runtime|java/lang/ProcessBuilder)\\b",
            "A process-execution API or class name appears; generic names such as system can be ambiguous.", NO_EXECUTION, Group.APIS,Group.DANGEROUS),
        rule("api.unsafe-c", "C memory / formatting API requiring review", "\\b(?:strcpy|strcat|gets|sprintf|vsprintf|memcpy|scanf|sscanf)\\b",
            "This C API name is associated with copying or formatted input/output. Missing or incorrect bounds, input widths or format handling can cause memory errors; memcpy and scanf can also be used safely.", "No vulnerability is established. Check actual imports/callers, buffer capacities, format strings and attacker-controlled data; memcpy and scanf can be used safely.", Group.APIS,Group.VULNERABILITIES),
        rule("api.android-capability", "Android sensitive capability API", "\\b(?:AccessibilityService|DeviceAdminReceiver|PackageInstaller|REQUEST_INSTALL_PACKAGES|BIND_ACCESSIBILITY_SERVICE|MediaProjection|VpnService|TelephonyManager|SmsManager|KeyStore|BiometricPrompt)\\b",
            "An Android capability/API/permission name appears in text.", "A reference is distinct from a manifest declaration, granted permission or observed use. Check the manifest, call sites and platform restrictions.", Group.APIS),
        rule("secret.aws-id", "AWS access key identifier", "\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b",
            "A token has the shape of an AWS access-key ID (not the secret access key itself).", "It may be an example or inactive ID. No account lookup or validity test occurs. Correlate with configuration and credential handling.", Group.SECRETS),
        rule("secret.google-api", "Google API key-shaped token", "\\bAIza[A-Za-z0-9_-]{35}\\b",
            "A token matches a common Google API key format.", "A format match does not reveal validity, restrictions or privileges; public client keys can be intentionally distributed.", Group.SECRETS),
        rule("secret.github", "GitHub token-shaped value", "\\b(?:gh[pousr]_[A-Za-z0-9]{20,255}|github_pat_[A-Za-z0-9_]{20,255})\\b",
            "A token resembles a GitHub credential format.", "Examples/placeholders/inactive tokens can match. The app does not contact GitHub or test the value.", Group.SECRETS),
        rule("secret.slack", "Slack token-shaped value", "\\bxox[baprs]-[A-Za-z0-9-]{10,255}\\b",
            "A token resembles a Slack credential format.", "Syntax is not evidence of a valid or privileged credential. No service lookup is performed.", Group.SECRETS),
        rule("secret.assignment", "Credential-like assignment", "(?i)\\b(?:api[_-]?key|access[_-]?token|client[_-]?secret|password|passwd|pwd|authorization)\\b\\s*[:=]\\s*[\"']?[^\\s\\p{Cntrl}\"';,}]{4,512}",
            "A credential-related key is followed by a value-like token.", "This is a candidate, often a placeholder, example or configuration name. Inspect surrounding data and use; no credential validity or compromise is established.", Group.SECRETS),
        rule("secret.jwt", "JWT-shaped token", "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{1,2048}\\.[A-Za-z0-9_-]{2,4096}\\.[A-Za-z0-9_-]{0,2048}(?![A-Za-z0-9_.-])",
            "Three base64url-shaped segments resemble a JSON Web Token.", "No signature, issuer, audience, expiry or claim is verified; a token can be unsigned, synthetic or invalid.", Group.SECRETS,Group.ENCODINGS),
        rule("crypto.private-key", "Private-key PEM marker", "-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----",
            "A PEM envelope explicitly labels private-key material.", "A header alone does not prove a complete key or its use. Inspect the bounded payload and surrounding configuration without uploading it.", Group.SECRETS,Group.CRYPTO),
        rule("crypto.public", "Certificate / public-key marker", "-----BEGIN (?:CERTIFICATE|PUBLIC KEY|RSA PUBLIC KEY|PGP PUBLIC KEY BLOCK)-----",
            "A PEM/armored marker labels certificate or public-key data.", "Public material is not itself a secret. A marker does not validate the object, trust chain, issuer or usage.", Group.CRYPTO),
        rule("crypto.algorithms", "Cryptographic algorithm / construction name", "(?i)\\b(?:AES(?:[-_/](?:GCM|CBC|CTR|ECB|CCM|CFB|OFB))?|3DES|DES|RC4|MD5|SHA[-_ ]?(?:1|224|256|384|512)|HMAC|PBKDF2|Argon2(?:id|i|d)?|scrypt|bcrypt|ChaCha20|Poly1305|Ed25519|Curve25519|RSA|ECDSA)\\b",
            "A recognized cryptographic algorithm/construction name occurs.", "Names do not identify actual algorithms, modes, parameters, keys or secure implementation. Inspect code and configuration.", Group.CRYPTO),
        rule("crypto.legacy", "Legacy primitive / ECB marker", "(?i)\\b(?:RC4|(?:3)?DES|MD5|SHA[-_ ]?1|AES[-_/]ECB)\\b",
            "A legacy primitive/mode is named: MD5/SHA-1 collision weaknesses can undermine signatures; DES has a small key space, RC4 has keystream biases, 3DES has legacy block-size limits, and ECB exposes repeated blocks. Relevance depends on which token matched and its security purpose.", "This is not a vulnerability verdict. MD5/SHA-1 can identify content, and compatibility/test data can name old algorithms. Determine the security purpose and parameters.", Group.CRYPTO,Group.VULNERABILITIES),
        rule("persist.windows", "Windows startup / task marker", "(?i)(?:\\\\(?:CurrentVersion\\\\Run(?:Once)?|Start Menu\\\\Programs\\\\Startup)(?=\\\\|$)|\\bschtasks(?:\\.exe)?\\b|\\bRegister-ScheduledTask\\b)",
            "A startup registry/folder or scheduled-task token appears.", "Persistence, installation, administration and cleanup tools share these markers. Identify actual writes/registrations and owners.", Group.PERSISTENCE,Group.PATHS),
        rule("persist.unix", "Unix / Android startup marker", "(?:/etc/(?:cron(?:tab|\\.[a-z]+)?|init\\.d|systemd/system)(?:/|\\b)|\\b(?:BOOT_COMPLETED|LOCKED_BOOT_COMPLETED|RECEIVE_BOOT_COMPLETED)\\b)",
            "A startup/scheduler path or Android boot broadcast name appears.", "A string is distinct from a registration or granted capability. Check manifest receivers, service units and write permissions.", Group.PERSISTENCE),
        rule("evasion.debug", "Debugger / tracing introspection", "\\b(?:IsDebuggerPresent|CheckRemoteDebuggerPresent|NtQueryInformationProcess|ptrace|TracerPid|Debug[.;/]isDebuggerConnected|isDebuggerConnected|PTRACE_TRACEME)\\b",
            "The token names debugger/tracing introspection: debugger queries or TracerPid can reveal a debugger, and ptrace can trace a process or expose tracing conflicts. Code could use the result to change behavior during analysis; NtQueryInformationProcess also has unrelated uses, depending on its information class.", "Debugging, crash reporting and anti-tamper code can all use it. Resolve call sites and branch conditions; no evasion behavior is observed.", Group.EVASION,Group.APIS),
        rule("text.direction", "Invisible text direction control", "[\u202a-\u202e\u2066-\u2069]",
            "An invisible Unicode bidirectional control can change display order while leaving stored bytes unchanged.", "The UI shows these controls as escapes; exports retain the original value. Such controls can also occur in ordinary multilingual text.", Group.EVASION),
        rule("evasion.environment", "Virtualization / emulator marker", "(?i)(?:\\b(?:frida|xposed|qemu|vbox|vmware|goldfish|ranchu)\\b|ro\\.kernel\\.qemu|/dev/qemu_pipe)",
            "The token names instrumentation, virtualization or emulator artifacts. Checking such names, properties or device paths could distinguish an analysis/test environment and alter behavior, but the literal alone does not show that a check or branch exists.", "May be support, diagnostics, tests or detection code. The selected file is not thereby shown to run in, or evade, that environment.", Group.EVASION),
        rule("vuln.cve", "CVE identifier", "(?i)\\bCVE-(?:19|20)[0-9]{2}-[0-9]{4,19}\\b",
            "CVE means Common Vulnerabilities and Exposures: identifiers reference disclosed security issues. This token has that identifier's shape; it does not show that this file contains the issue.", "No vulnerability database lookup, affected-version match or exploit verification occurs. It may be documentation, a fix note, test data or an exploit reference.", Group.VULNERABILITIES,Group.IDENTIFIERS),
        rule("vuln.cwe", "CWE identifier", "(?i)\\bCWE-[1-9][0-9]{0,5}\\b",
            "CWE means Common Weakness Enumeration: identifiers name classes of implementation weaknesses, rather than a specific affected product/version. This is an identifier-shaped literal, not a verified weakness in this file.", "The classifier does not verify that the file has this weakness or that the identifier exists in a current database.", Group.VULNERABILITIES,Group.IDENTIFIERS),
        rule("vuln.terminology", "Exploit / vulnerability terminology", "(?i)\\b(?:buffer overflow|heap overflow|use[- ]after[- ]free|SQL injection|command injection|remote code execution|privilege escalation|shellcode)\\b",
            "The string contains vulnerability/exploitation terminology.", "Documentation, warnings, defensive engines and tests commonly contain these terms. Corroborate against actual code and inputs.", Group.VULNERABILITIES),
        rule("vuln.input-pattern", "Injection / traversal-shaped input", "(?i)(?:\\$\\{jndi:(?:ldap|rmi|dns)://|(?:\\.\\.[/\\\\]){2,}|\\bunion\\s+(?:all\\s+)?select\\b|%n(?=[^A-Za-z]|$))",
            "A token resembles an injection, repeated traversal or C format-write pattern.", "These can be probes, fixtures, examples or literal data. A vulnerable sink, attacker influence and actual execution are not established.", Group.VULNERABILITIES,Group.DANGEROUS),
        validated("encoded.base64", "Base64 data candidate", "(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{48,8192}={0,2}(?![A-Za-z0-9+/=])",
            "A long token round-trips through a local Base64 decoder and contains at least one non-alphanumeric Base64 symbol.", "Encoding is not encryption or evidence of concealment. The classifier does not execute or recursively interpret decoded data.", StringClassifier::base64, Group.ENCODINGS),
        rule("id.hex-digest", "Common digest-length hexadecimal token", "(?i)(?<![A-Za-z0-9])(?:[0-9a-f]{128}|[0-9a-f]{96}|[0-9a-f]{64}|[0-9a-f]{40}|[0-9a-f]{32})(?![A-Za-z0-9])",
            "A hex token has a common digest byte length.", "Length alone cannot identify the algorithm or distinguish a digest from another identifier/key. Compare only with independently measured evidence.", Group.IDENTIFIERS),
        rule("id.uuid", "UUID / GUID-shaped identifier", "(?i)(?<![A-Za-z0-9])[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?![A-Za-z0-9])",
            "A token has the familiar UUID/GUID layout; variant/version semantics are not assumed.", "It may identify a component, object, protocol or arbitrary data. Resolve uses in the surrounding format.", Group.IDENTIFIERS)
    ));
    static final String RULES_SHA256 = identity();
    private StringClassifier() {}
    private static String identity() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Rule rule : RULES) {
                digest.update((rule.id + "\n" + rule.title + "\n" + rule.expression + "\n" + rule.groups + "\n" + rule.explanation + "\n" + rule.implication + "\n" + (rule.validator == null ? "none" : rule.id) + "\n").getBytes(StandardCharsets.UTF_8));
                for (Group group : rule.groups) digest.update(("categoryRationale\n" + group.name() + "\n" + group.label + "\n" + group.rationale + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return Inspector.toHex(digest.digest());
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static Result classify(String text, BooleanSupplier cancelled) throws IOException {
        if (text.length() > MAX_TEXT_UNITS) throw new IOException("String classification exceeds one Mi UTF-16 units");
        List<Hit> hits = new ArrayList<>(); long groups = 0;
        for (Rule rule : RULES) {
            EvidenceRows.check(cancelled); Matcher matcher = rule.pattern.matcher(text);
            while (matcher.find()) {
                EvidenceRows.check(cancelled);
                if (rule.validator != null && !rule.validator.accepts(matcher.group())) continue;
                if (hits.size() == MAX_MATCHES) throw new IOException("String exceeds 2,048 classification matches; no successful partial classification returned");
                hits.add(new Hit(rule,matcher.start(),matcher.end())); groups |= rule.mask();
            }
        }
        hits.sort(Comparator.comparingInt((Hit value) -> value.start).thenComparing(value -> value.rule.id)); return new Result(hits,groups);
    }
    static JSONObject provenance() throws JSONException {
        return new JSONObject().put("engine",ENGINE).put("engineVersion",VERSION).put("rulesSha256",RULES_SHA256).put("ruleCount",RULES.size())
            .put("networkUsed",false).put("executionObserved",false).put("credentialValidityTested",false).put("vulnerabilityVerified",false);
    }
    static EvidenceRows catalog() {
        return new EvidenceRows("string-rules","String classification rule catalog") {
            int size() { return RULES.size(); }
            Row row(int i, BooleanSupplier cancelled) throws Exception { EvidenceRows.check(cancelled); Rule rule = RULES.get(i); return new Row(i,rule.id + " • " + rule.title,rule.explanation,null,null,rule.json()); }
            JSONObject metadata() throws Exception { return provenance(); }
        };
    }
    private static boolean port(String value) { try { return value.isEmpty() || Integer.parseInt(value) <= 65535; } catch (NumberFormatException e) { return false; } }
    private static boolean ipv4(String value) {
        String[] octets = value.split("\\.",-1); if (octets.length != 4) return false;
        for (String octet : octets) try { if (octet.isEmpty() || octet.length() > 3 || Integer.parseInt(octet) > 255) return false; } catch (NumberFormatException e) { return false; }
        return true;
    }
    private static boolean ipv4Endpoint(String value) { int colon = value.indexOf(':'); return ipv4(colon < 0 ? value : value.substring(0,colon)) && (colon < 0 || port(value.substring(colon+1))); }
    private static boolean ipv6Endpoint(String token) {
        String value = token;
        if (token.startsWith("[")) {
            int end = token.indexOf(']'); if (end < 0 || end + 1 < token.length() && (token.charAt(end+1) != ':' || !port(token.substring(end+2)))) return false;
            value = token.substring(1,end); int scope = value.indexOf('%'); if (scope >= 0) value = value.substring(0,scope);
        }
        int compressed = value.indexOf("::"); if (compressed >= 0 && value.indexOf("::",compressed+2) >= 0) return false;
        String[] halves = compressed < 0 ? new String[]{value} : new String[]{value.substring(0,compressed),value.substring(compressed+2)};
        int count = 0;
        for (int side = 0; side < halves.length; side++) {
            if (halves[side].isEmpty()) continue;
            String[] groups = halves[side].split(":",-1);
            for (int i = 0; i < groups.length; i++) {
                String group = groups[i];
                if (group.indexOf('.') >= 0) { if (side != halves.length-1 || i != groups.length-1 || !ipv4(group)) return false; count += 2; }
                else { if (group.isEmpty() || group.length() > 4 || !group.matches("[0-9A-Fa-f]+")) return false; count++; }
            }
        }
        return compressed < 0 ? count == 8 : count < 8;
    }
    private static boolean base64(String value) {
        if (value.indexOf('+') < 0 && value.indexOf('/') < 0 && value.indexOf('=') < 0) return false;
        try { return Base64.getEncoder().withoutPadding().encodeToString(Base64.getDecoder().decode(value)).equals(value.replace("=","")); } catch (IllegalArgumentException e) { return false; }
    }
}
