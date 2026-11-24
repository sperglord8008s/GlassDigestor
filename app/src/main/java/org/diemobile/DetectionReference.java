// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;
import java.util.Locale;
final class DetectionReference {
 static String describe(String type){
  String t=type.toLowerCase(Locale.ROOT);
  String specific;
  if(t.contains("compiler"))specific="A compiler translates source code or intermediate representation into bytecode or native instructions. Recognizing its output can help choose a disassembler, infer ABI conventions and identify compiler-generated functions. A rule may recognize characteristic code, strings or metadata; the result does not establish the complete toolchain.";
  else if(t.contains("linker"))specific="A linker combines compiled objects, resolves references and lays out code/data. Its output structure can reveal build-tool conventions, dependencies and relocation behavior. Identifying a linker does not establish the application source language.";
  else if(t.contains("packer")||t.contains("protector")||t.contains("obfuscat"))specific="A packer transforms an executable's contents, often compressing or encrypting code and adding a loader that reconstructs it at runtime. Protectors and obfuscators can make static analysis harder. Legitimate and malicious software can use them. Inspect entry points, sections, entropy and runtime loading observations before making a stronger claim.";
  else if(t.contains("library")||t.contains("framework")||t.contains("sdk"))specific="A library provides reusable code; a framework provides application structure and services; an SDK includes tools and APIs for development. Embedded code can be present without ever running. Inspect symbols, references, call sites and dependencies before attributing behavior.";
  else if(t.contains("language"))specific="Language detections usually infer the original programming language from runtime structures, metadata or generated code. A binary can contain several languages and a match may describe just one component.";
  else if(t.contains("format")||t.contains("archive")||t.contains("container"))specific="A file format specifies how bytes encode data or executable structures. Containers can hold several embedded formats. Inspect group offsets and sizes; an archive member offset may refer to decompressed data rather than the original input. Recognition is not full structural validation.";
  else if(t.contains("installer"))specific="An installer or package mechanism distributes software and may unpack files, write configuration or run installation steps. Its presence does not establish which actions actually happen. Inspect embedded content and installation metadata.";
  else if(t.contains("crypto")||t.contains("encrypt"))specific="Cryptographic code or markers can indicate an algorithm, library or transformed data. This does not reveal the key, confirm encryption of a particular region or establish that cryptography is used correctly.";
  else specific="This is a CDiE signature category. The raw fields show what the engine returned; consult the named technology and upstream signature rule for its exact definition.";
  return specific+"\n\nSource: CDiE detection record shown above. This app does not yet expose the exact matching byte sequence or rule execution trace. Rule output can be heuristic, outdated or coincidental. Do not treat it as independent proof of author, intent or runtime behavior.";
 }
}
