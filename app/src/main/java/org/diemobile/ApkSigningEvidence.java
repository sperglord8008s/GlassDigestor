// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.internal.apk.ApkSigningBlockUtils.Result.SignerInfo.ContentDigest;

import org.json.*;

import java.io.*;
import java.lang.reflect.Array;
import java.security.*;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.*;
import java.security.spec.ECFieldF2m;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Detached public apksig results. Parsed identities are kept separate from verification. */
final class ApkSigningEvidence {
    static final int ADAPTER_VERSION = 1;
    static final int MAX_RECORDS = 20000, MAX_CERTIFICATE_BYTES = 1024 * 1024;
    static final long MAX_CHARACTERS = 32L * 1024 * 1024;
    static final String[] TABLES = {
        "summary", "signers", "certificates", "lineages", "digests", "diagnostics", "stamp"
    };
    final JSONObject data = new JSONObject();
    private final Map<String, Integer> certificateIds = new LinkedHashMap<>();
    private final BooleanSupplier stop;
    private int records, values;
    private long characters;

    private ApkSigningEvidence(BooleanSupplier stop) throws Exception {
        this.stop = stop;
        for (String table : TABLES) data.put(table, new JSONArray());
    }

    static ApkSigningEvidence capture(
            ApkSigningReport.Verification verification, boolean idsigSupplied, BooleanSupplier stop)
            throws Exception {
        ApkSigningEvidence evidence = new ApkSigningEvidence(stop);
        evidence.collect(verification, idsigSupplied);
        EvidenceRows.check(stop);
        return evidence;
    }

    JSONArray table(String name) throws Exception {
        for (String table : TABLES) if (table.equals(name)) return data.getJSONArray(table);
        throw new IOException("Unknown APK signing evidence table");
    }

    private void collect(ApkSigningReport.Verification verification, boolean idsigSupplied)
            throws Exception {
        JSONObject summary =
                new JSONObject()
                        .put("name", "APK signature verification")
                        .put("completed", verification.completed())
                        .put("verified", verification.verified())
                        .put("checkedMinimumApi", "manifest minSdkVersion (apksig default)")
                        .put("checkedMaximumApi", ApkSigningReport.MAX_CHECKED_API)
                        .put("idsigSupplied", idsigSupplied)
                        .put("parseError", verification.error)
                        .put(
                                "schemeBooleanMeaning",
                                "Successfully verified using the scheme in the checked platform"
                                    + " range; false alone does not establish absence.")
                        .put(
                                "interpretation",
                                "Integrity and signer information, not publisher trust, software"
                                    + " safety, installation success, runtime behavior or a device"
                                    + " update decision. Certificate times are recorded without"
                                    + " PKIX/revocation/network validation.");
        summary.put(
                "detachedLayout",
                verification.detachedLayout == null
                        ? JSONObject.NULL
                        : verification.detachedLayout.json());
        if (!verification.completed()) {
            add("summary", summary);
            return;
        }
        ApkVerifier.Result result = verification.result;
        summary.put("apksigResultVerified", result.isVerified())
                .put("v1", result.isVerifiedUsingV1Scheme())
                .put("v2", result.isVerifiedUsingV2Scheme())
                .put("v3", result.isVerifiedUsingV3Scheme())
                .put("v31", result.isVerifiedUsingV31Scheme())
                .put("v4", result.isVerifiedUsingV4Scheme())
                .put("sourceStampVerified", result.isSourceStampVerified());
        JSONArray current = new JSONArray();
        for (X509Certificate certificate : result.getSignerCertificates())
            current.put(certificate(certificate));
        summary.put("verifiedSignerCertificateIndices", current);
        summary.put(
                "verifiedSignerCertificateMeaning",
                "The verified APK signer list returned by apksig; consult the stricter combined"
                    + " verified field when a detached input is supplied.");
        int combined = lineage("result", result.getSigningCertificateLineage());
        summary.put("combinedLineageIndex", nullableIndex(combined));
        issues("result", "error", result.getErrors());
        issues("result", "warning", result.getWarnings());
        jarSigners(result.getV1SchemeSigners(), false);
        jarSigners(result.getV1SchemeIgnoredSigners(), true);
        for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
            String scope = "v2/" + signer.getIndex();
            JSONObject row =
                    signer(
                            scope,
                            "v2",
                            signer.getIndex(),
                            signer.containsErrors(),
                            signer.getCertificates());
            row.put("digestIndices", digests(scope, signer.getContentDigests(), false));
            add("signers", row);
            issues(scope, "error", signer.getErrors());
            issues(scope, "warning", signer.getWarnings());
        }
        targetedSigners("v3", result.getV3SchemeSigners());
        targetedSigners("v3.1", result.getV31SchemeSigners());
        for (int ordinal = 0; ordinal < result.getV4SchemeSigners().size(); ordinal++) {
            ApkVerifier.Result.V4SchemeSignerInfo signer = result.getV4SchemeSigners().get(ordinal);
            // The pinned implementation returns index 0 for both v4 and v4.1. Keep list ordinals
            // too.
            String scope = "v4/" + ordinal;
            JSONObject row =
                    signer(
                            scope,
                            "v4",
                            signer.getIndex(),
                            signer.containsErrors(),
                            signer.getCertificates());
            row.put("ordinalWithinScheme", ordinal)
                    .put("digestIndices", digests(scope, signer.getContentDigests(), true));
            add("signers", row);
            issues(scope, "error", signer.getErrors());
            issues(scope, "warning", signer.getWarnings());
        }
        ApkVerifier.Result.SourceStampInfo stamp = result.getSourceStampInfo();
        JSONObject stampRow =
                new JSONObject()
                        .put("name", "Distribution source stamp")
                        .put("returned", stamp != null)
                        .put("verified", result.isSourceStampVerified())
                        .put(
                                "interpretation",
                                "The distribution signer can differ from the APK signer. A signed"
                                    + " timestamp is a claim, not trusted independent time. This"
                                    + " does not verify a store's identity or distribution"
                                    + " history.");
        if (stamp != null) {
            stampRow.put("status", stamp.getSourceStampVerificationStatus().name())
                    .put("timestampEpochSeconds", stamp.getTimestampEpochSeconds())
                    .put(
                            "zeroTimestampMeaning",
                            "Absent or zero; no separate presence flag is exposed")
                    .put(
                            "certificateIndex",
                            stamp.getCertificate() == null
                                    ? JSONObject.NULL
                                    : certificate(stamp.getCertificate()))
                    .put(
                            "certificateLineageIndices",
                            certificates(stamp.getCertificatesInLineage()));
            try {
                stampRow.put(
                        "timestampUtc",
                        stamp.getTimestampEpochSeconds() == 0
                                ? JSONObject.NULL
                                : java.time.Instant.ofEpochSecond(stamp.getTimestampEpochSeconds())
                                        .toString());
            } catch (java.time.DateTimeException error) {
                stampRow.put("timestampRenderingError", error.toString());
            }
            issues("source-stamp", "error", stamp.getErrors());
            issues("source-stamp", "warning", stamp.getWarnings());
            issues("source-stamp", "info", stamp.getInfoMessages());
        }
        add("stamp", stampRow);
        summary.put("parsedSignerRecordCount", table("signers").length())
                .put("diagnosticCount", table("diagnostics").length());
        add("summary", summary);
    }

    private void jarSigners(List<ApkVerifier.Result.V1SchemeSignerInfo> signers, boolean ignored)
            throws Exception {
        for (int ordinal = 0; ordinal < signers.size(); ordinal++) {
            ApkVerifier.Result.V1SchemeSignerInfo signer = signers.get(ordinal);
            String scope = (ignored ? "v1-ignored/" : "v1/") + ordinal;
            add(
                    "signers",
                    signer(
                                    scope,
                                    "v1",
                                    ordinal,
                                    signer.containsErrors(),
                                    signer.getCertificateChain())
                            .put("ignoredByJarVerification", ignored)
                            .put("jarSignerName", signer.getName())
                            .put("signatureBlockFileName", signer.getSignatureBlockFileName())
                            .put("signatureFileName", signer.getSignatureFileName()));
            issues(scope, "error", signer.getErrors());
            issues(scope, "warning", signer.getWarnings());
        }
    }

    private void targetedSigners(String scheme, List<ApkVerifier.Result.V3SchemeSignerInfo> signers)
            throws Exception {
        for (ApkVerifier.Result.V3SchemeSignerInfo signer : signers) {
            String scope = scheme + "/" + signer.getIndex();
            add(
                    "signers",
                    signer(
                                    scope,
                                    scheme,
                                    signer.getIndex(),
                                    signer.containsErrors(),
                                    signer.getCertificates())
                            .put("minimumApi", signer.getMinSdkVersion())
                            .put("maximumApi", signer.getMaxSdkVersion())
                            .put(
                                    "rotationTargetsDevelopmentRelease",
                                    signer.getRotationTargetsDevRelease())
                            .put(
                                    "lineageIndex",
                                    nullableIndex(
                                            lineage(scope, signer.getSigningCertificateLineage())))
                            .put("digestIndices", digests(scope, signer.getContentDigests(), false))
                            .put(
                                    "interpretation",
                                    "Targeted platform range, not simultaneous co-signers. Each"
                                        + " signer's original lineage and capabilities are retained"
                                        + " separately from the combined lineage."));
            issues(scope, "error", signer.getErrors());
            issues(scope, "warning", signer.getWarnings());
        }
    }

    private JSONObject signer(
            String scope,
            String scheme,
            int index,
            boolean errors,
            List<X509Certificate> certificates)
            throws Exception {
        return new JSONObject()
                .put("name", scope)
                .put("scope", scope)
                .put("scheme", scheme)
                .put("returnedIndex", index)
                .put("containsSignerErrors", errors)
                .put("certificateIndices", certificates(certificates))
                .put(
                        "identityMeaning",
                        "Parsed certificates in this scope; their presence alone does not establish"
                            + " successful signature verification. The first is the signing"
                            + " certificate; later entries are additional returned certificates"
                            + " without independent trust or chain validation.");
    }

    private JSONArray certificates(List<X509Certificate> certificates) throws Exception {
        JSONArray result = new JSONArray();
        for (X509Certificate certificate : certificates) {
            EvidenceRows.check(stop);
            result.put(certificate(certificate));
        }
        return result;
    }

    private int lineage(String scope, SigningCertificateLineage lineage) throws Exception {
        if (lineage == null) return -1;
        int id = table("lineages").length();
        JSONArray nodes = new JSONArray();
        for (X509Certificate certificate : lineage.getCertificatesInLineage()) {
            EvidenceRows.check(stop);
            SigningCertificateLineage.SignerCapabilities caps =
                    lineage.getSignerCapabilities(certificate);
            nodes.put(
                    new JSONObject()
                            .put("position", nodes.length())
                            .put("certificateIndex", certificate(certificate))
                            .put("installedData", caps.hasInstalledData())
                            .put("sharedUid", caps.hasSharedUid())
                            .put("permission", caps.hasPermission())
                            .put("rollback", caps.hasRollback())
                            .put("auth", caps.hasAuth()));
            if (nodes.length() > MAX_RECORDS)
                throw new IOException("Signing lineage exceeds its record budget");
        }
        add(
                "lineages",
                new JSONObject()
                        .put("name", scope + " rotation lineage")
                        .put("scope", scope)
                        .put("nodes", nodes)
                        .put(
                                "encodedAttributeBase64",
                                encoded(lineage.encodeSigningCertificateLineage()))
                        .put(
                                "byteMeaning",
                                "Upstream re-encoded proof-of-rotation attribute, not an original"
                                    + " APK byte range; includes returned flags/signatures beyond"
                                    + " the five public capability getters.")
                        .put(
                                "capabilityMechanisms",
                                new JSONObject()
                                        .put(
                                                "installedData",
                                                "Trust installed data from this predecessor during"
                                                    + " signing-key upgrade")
                                        .put(
                                                "sharedUid",
                                                "Permit shared UID compatibility with apps using"
                                                    + " this predecessor")
                                        .put(
                                                "permission",
                                                "Permit signature-level permission relationships"
                                                    + " with apps using this predecessor")
                                        .put(
                                                "rollback",
                                                "Permit a later version signed with this"
                                                    + " predecessor; an old key can regain signing"
                                                    + " control")
                                        .put(
                                                "auth",
                                                "Permit privileged authenticator-module access"
                                                    + " based on this predecessor's signing"
                                                    + " certificate"))
                        .put(
                                "interpretation",
                                "Ordered predecessor-to-descendant certificates and capabilities in"
                                    + " this exact returned scope. No installed package, inherited"
                                    + " Android permission or update/rollback eligibility is"
                                    + " inferred."));
        return id;
    }

    private JSONArray digests(String scope, List<ContentDigest> digests, boolean v4)
            throws Exception {
        JSONArray ids = new JSONArray();
        for (ContentDigest digest : digests) {
            int id = table("digests").length();
            ids.put(id);
            add(
                    "digests",
                    new JSONObject()
                            .put("name", scope + " content digest " + ids.length())
                            .put("scope", scope)
                            .put("signatureAlgorithmId", digest.getSignatureAlgorithmId())
                            .put("valueHex", Inspector.toHex(digest.getValue()))
                            .put("valueBase64", encoded(digest.getValue()))
                            .put("byteCount", digest.getValue().length)
                            .put(
                                    "interpretation",
                                    v4
                                            ? "APK digest returned by v4; algorithm ID 0 is an"
                                                  + " apksig placeholder, not a parsed wire"
                                                  + " signature algorithm. This is not the raw"
                                                  + " whole-file SHA-256."
                                            : "Content digest paired with a signing algorithm ID."
                                                  + " APK signing chunk/verity domains differ from"
                                                  + " raw whole-file hashes; duplicates are"
                                                  + " retained."));
        }
        return ids;
    }

    private void issues(String scope, String severity, List<ApkVerifier.IssueWithParams> issues)
            throws Exception {
        for (int ordinal = 0; ordinal < issues.size(); ordinal++) {
            EvidenceRows.check(stop);
            ApkVerifier.IssueWithParams issue = issues.get(ordinal);
            JSONArray parameters = new JSONArray();
            for (Object parameter : issue.getParams()) parameters.put(value(parameter, 0));
            add(
                    "diagnostics",
                    new JSONObject()
                            .put("name", issue.getIssue().name())
                            .put("scope", scope)
                            .put("severity", severity)
                            .put("ordinalWithinSeverityAndScope", ordinal)
                            .put("issue", issue.getIssue().name())
                            .put("message", issue.toString())
                            .put("parameters", parameters));
        }
    }

    private int certificate(X509Certificate certificate) throws Exception {
        EvidenceRows.check(stop);
        byte[] der = certificate.getEncoded();
        if (der.length > MAX_CERTIFICATE_BYTES)
            throw new IOException(
                    "Returned signing certificate exceeds the 1 MiB certificate budget");
        String digest = hash("SHA-256", der);
        Integer previous = certificateIds.get(digest);
        if (previous != null) {
            if (!encoded(der)
                    .equals(table("certificates").getJSONObject(previous).getString("derBase64")))
                throw new IOException("Certificate SHA-256 collision");
            return previous;
        }
        int id = table("certificates").length();
        JSONObject row =
                new JSONObject()
                        .put("name", certificate.getSubjectX500Principal().getName())
                        .put("certificateSha256", digest)
                        .put("certificateSha1", hash("SHA-1", der))
                        .put("certificateMd5", hash("MD5", der))
                        .put("derBase64", encoded(der))
                        .put("derByteCount", der.length)
                        .put("version", certificate.getVersion())
                        .put("serialDecimal", certificate.getSerialNumber().toString())
                        .put("serialHex", certificate.getSerialNumber().toString(16))
                        .put("subject", certificate.getSubjectX500Principal().getName())
                        .put(
                                "subjectDerBase64",
                                encoded(certificate.getSubjectX500Principal().getEncoded()))
                        .put("issuer", certificate.getIssuerX500Principal().getName())
                        .put(
                                "issuerDerBase64",
                                encoded(certificate.getIssuerX500Principal().getEncoded()))
                        .put("notBeforeEpochMillis", certificate.getNotBefore().getTime())
                        .put("notAfterEpochMillis", certificate.getNotAfter().getTime())
                        .put("notBeforeUtc", certificate.getNotBefore().toInstant().toString())
                        .put("notAfterUtc", certificate.getNotAfter().toInstant().toString())
                        .put("signatureAlgorithm", certificate.getSigAlgName())
                        .put("signatureAlgorithmOid", certificate.getSigAlgOID())
                        .put(
                                "signatureAlgorithmParametersBase64",
                                encoded(certificate.getSigAlgParams()))
                        .put("signatureBase64", encoded(certificate.getSignature()))
                        .put("tbsCertificateBase64", encoded(certificate.getTBSCertificate()))
                        .put("basicConstraints", certificate.getBasicConstraints())
                        .put("keyUsage", booleans(certificate.getKeyUsage()))
                        .put("issuerUniqueId", booleans(certificate.getIssuerUniqueID()))
                        .put(
                                "keyUsageBitMeanings",
                                new JSONArray(
                                        Arrays.asList(
                                                "digitalSignature",
                                                "nonRepudiation/contentCommitment",
                                                "keyEncipherment",
                                                "dataEncipherment",
                                                "keyAgreement",
                                                "keyCertSign",
                                                "cRLSign",
                                                "encipherOnly",
                                                "decipherOnly")))
                        .put("subjectUniqueId", booleans(certificate.getSubjectUniqueID()))
                        .put(
                                "hasUnsupportedCriticalExtension",
                                certificate.hasUnsupportedCriticalExtension())
                        .put("publicKey", publicKey(certificate.getPublicKey()));
        JSONArray decodingErrors = new JSONArray();
        try {
            row.put("extendedKeyUsage", value(certificate.getExtendedKeyUsage(), 0));
        } catch (CertificateParsingException error) {
            decodingErrors.put("extendedKeyUsage: " + error);
        }
        try {
            row.put("subjectAlternativeNames", value(certificate.getSubjectAlternativeNames(), 0));
        } catch (CertificateParsingException error) {
            decodingErrors.put("subjectAlternativeNames: " + error);
        }
        try {
            row.put("issuerAlternativeNames", value(certificate.getIssuerAlternativeNames(), 0));
        } catch (CertificateParsingException error) {
            decodingErrors.put("issuerAlternativeNames: " + error);
        }
        JSONArray extensions = new JSONArray();
        extensions(certificate, certificate.getCriticalExtensionOIDs(), true, extensions);
        extensions(certificate, certificate.getNonCriticalExtensionOIDs(), false, extensions);
        row.put("extensions", extensions)
                .put("decodingErrors", decodingErrors)
                .put(
                        "interpretation",
                        "Returned X.509 DER/SPKI and JCA-decoded fields. Original field offsets are"
                            + " not supplied. MD5/SHA-1 are compatibility fingerprints, not secure"
                            + " integrity recommendations. No trust-store, certificate-chain,"
                            + " time-validity, revocation or network validation is performed"
                            + " here.");
        add("certificates", row);
        certificateIds.put(digest, id);
        return id;
    }

    private void extensions(
            X509Certificate certificate, Set<String> oids, boolean critical, JSONArray output)
            throws Exception {
        if (oids == null) return;
        for (String oid : new TreeSet<>(oids)) {
            EvidenceRows.check(stop);
            output.put(
                    new JSONObject()
                            .put("oid", oid)
                            .put("critical", critical)
                            .put(
                                    "derOctetStringBase64",
                                    encoded(certificate.getExtensionValue(oid))));
            if (output.length() > MAX_RECORDS)
                throw new IOException("Certificate extension count exceeds signing budget");
        }
    }

    private JSONObject publicKey(PublicKey key) throws Exception {
        byte[] spki = key.getEncoded();
        JSONObject row =
                new JSONObject()
                        .put("algorithm", key.getAlgorithm())
                        .put("format", key.getFormat())
                        .put("spkiBase64", encoded(spki))
                        .put("spkiSha256", spki == null ? JSONObject.NULL : hash("SHA-256", spki));
        if (key instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) key;
            row.put("modulusBits", rsa.getModulus().bitLength())
                    .put("modulusHex", rsa.getModulus().toString(16))
                    .put("publicExponentDecimal", rsa.getPublicExponent().toString());
        } else if (key instanceof ECPublicKey) {
            ECPublicKey ec = (ECPublicKey) key;
            ECParameterSpec params = ec.getParams();
            row.put("point", point(ec.getW()));
            if (params != null) {
                row.put("fieldBits", params.getCurve().getField().getFieldSize())
                        .put("orderHex", params.getOrder().toString(16))
                        .put("cofactor", params.getCofactor())
                        .put("curveAHex", params.getCurve().getA().toString(16))
                        .put("curveBHex", params.getCurve().getB().toString(16))
                        .put("curveSeedBase64", encoded(params.getCurve().getSeed()))
                        .put("generator", point(params.getGenerator()));
                if (params.getCurve().getField() instanceof ECFieldFp)
                    row.put("fieldKind", "prime")
                            .put(
                                    "primeHex",
                                    ((ECFieldFp) params.getCurve().getField()).getP().toString(16));
                else if (params.getCurve().getField() instanceof ECFieldF2m) {
                    ECFieldF2m field = (ECFieldF2m) params.getCurve().getField();
                    row.put("fieldKind", "binary")
                            .put("m", field.getM())
                            .put(
                                    "reductionPolynomialHex",
                                    field.getReductionPolynomial() == null
                                            ? JSONObject.NULL
                                            : field.getReductionPolynomial().toString(16))
                            .put(
                                    "midTermsOfReductionPolynomial",
                                    value(field.getMidTermsOfReductionPolynomial(), 0));
                }
            }
        } else if (key instanceof DSAPublicKey) {
            DSAPublicKey dsa = (DSAPublicKey) key;
            row.put("yHex", dsa.getY().toString(16));
            if (dsa.getParams() != null)
                row.put("pHex", dsa.getParams().getP().toString(16))
                        .put("qHex", dsa.getParams().getQ().toString(16))
                        .put("gHex", dsa.getParams().getG().toString(16));
        }
        return row;
    }

    private static JSONObject point(ECPoint point) throws Exception {
        return new JSONObject()
                .put("infinity", point.equals(ECPoint.POINT_INFINITY))
                .put(
                        "affineXHex",
                        point.getAffineX() == null
                                ? JSONObject.NULL
                                : point.getAffineX().toString(16))
                .put(
                        "affineYHex",
                        point.getAffineY() == null
                                ? JSONObject.NULL
                                : point.getAffineY().toString(16));
    }

    /** Values outside this public parameter subset retain their Java type/rendering explicitly. */
    private Object value(Object input, int depth) throws Exception {
        EvidenceRows.check(stop);
        if (depth > 32 || ++values > 100000)
            throw new IOException("Signing value depth/node budget exceeded");
        if (input == null) return JSONObject.NULL;
        if (input instanceof String
                || input instanceof Boolean
                || input instanceof Integer
                || input instanceof Long
                || input instanceof Short
                || input instanceof Byte) return input;
        if (input instanceof byte[])
            return new JSONObject()
                    .put("javaType", "byte[]")
                    .put("base64", encoded((byte[]) input));
        if (input instanceof Collection<?>) {
            JSONArray output = new JSONArray();
            for (Object child : (Collection<?>) input) output.put(value(child, depth + 1));
            return output;
        }
        if (input.getClass().isArray()) {
            JSONArray output = new JSONArray();
            for (int i = 0; i < Array.getLength(input); i++)
                output.put(value(Array.get(input, i), depth + 1));
            return output;
        }
        return new JSONObject()
                .put("javaType", input.getClass().getName())
                .put("rendering", String.valueOf(input))
                .put("representation", "public API object rendering; not a wire encoding");
    }

    private static Object booleans(boolean[] values) {
        if (values == null) return JSONObject.NULL;
        JSONArray output = new JSONArray();
        for (boolean value : values) output.put(value);
        return output;
    }

    private static Object nullableIndex(int id) {
        return id < 0 ? JSONObject.NULL : id;
    }

    private static Object encoded(byte[] bytes) {
        return bytes == null ? JSONObject.NULL : Base64.getEncoder().encodeToString(bytes);
    }

    private static String hash(String algorithm, byte[] bytes) throws Exception {
        return Inspector.toHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    private void add(String table, JSONObject row) throws Exception {
        EvidenceRows.check(stop);
        long length = row.toString().length();
        if (++records > MAX_RECORDS
                || length > EvidenceRows.MAX_ROW_CHARACTERS - 4096
                || (characters += length) > MAX_CHARACTERS)
            throw new IOException(
                    "Signing evidence exceeds its record / per-record / 32 Mi-character budget; no"
                        + " complete result published");
        table(table).put(row);
    }
}
