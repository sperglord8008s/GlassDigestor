// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** LibChecker v5 exact-first/priority/full-match contract on a linear-time engine. */
final class NameMatcher {
    static final int MAX_NAME_UNITS=16384,MAX_RULES=20000;
    static final String[] TYPES={"Native library name","Declared service","Declared activity","Declared receiver","Declared provider","DEX package evidence","Declared shared/static library","Permission name","Manifest metadata name","Intent action"};
    /** Preserve exported typeName values; neutral wording belongs to the display label only. */
    static String displayTypeLabel(int type) {
        return type == 5 ? "DEX package names" : TYPES[type];
    }
    static final class Rule {
        final int id,type,priority,index;
        final String name;
        final boolean regex;
        final Pattern compiled;
        final String error;
        final JSONObject original;
        Rule(JSONObject value,int index) throws Exception {
            this.index=index;original=value;
            id=value.getInt("id");type=value.getInt("type");priority=value.getInt("priority");name=value.getString("name");regex=value.getBoolean("isRegexRule");
            if(id<=0||type<0||type>=TYPES.length||priority<0||name.isEmpty()||name.length()>1024) throw new IOException("LibChecker matcher identity/type/name/priority outside its contract");
            Pattern pattern=null;String failure="";
            if(regex) {
                try { portable(name);pattern=Pattern.compile(asciiDigits(name)); }
                catch(IOException|PatternSyntaxException unsupported) { failure=unsupported.toString(); }
            }
            compiled=pattern;error=failure;
        }
    }
    static final class Match {
        final Rule rule;
        final String method,input;
        Match(Rule rule,String method,String input) { this.rule=rule;this.method=method;this.input=input; }
    }
    final List<Rule> rules,unsupported;
    private final List<List<Rule>> regex=new ArrayList<>();
    private final List<Map<String,Rule>> exact=new ArrayList<>();
    NameMatcher(JSONArray source,BooleanSupplier stop) throws Exception {
        if(source.length()>MAX_RULES) throw new IOException("Name rule catalog exceeds 20,000 records");
        for(int i=0;i<TYPES.length;i++) { exact.add(new HashMap<>());regex.add(new ArrayList<>()); }
        List<Rule> values=new ArrayList<>(),errors=new ArrayList<>();Set<Integer> ids=new HashSet<>();
        for(int i=0;i<source.length();i++) {
            EvidenceRows.check(stop);Rule rule=new Rule(source.getJSONObject(i),i);
            if(!ids.add(rule.id)||exact.get(rule.type).put(rule.name,rule)!=null) throw new IOException("Duplicate name-rule ID or type/name pair");
            values.add(rule);
            if(!rule.error.isEmpty()) errors.add(rule);
            if(rule.compiled!=null) regex.get(rule.type).add(rule);
        }
        Comparator<Rule> order=Comparator.comparingInt((Rule rule)->rule.priority).thenComparingInt(rule->rule.id);
        for(List<Rule> group:regex) group.sort(order);
        rules=Collections.unmodifiableList(values);unsupported=Collections.unmodifiableList(errors);
    }
    Match match(int type,String input,BooleanSupplier stop) throws Exception {
        return match(type,input,true,stop);
    }
    Match match(int type,String input,boolean useRegex,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if(type<0||type>=TYPES.length||input.length()>MAX_NAME_UNITS) throw new IOException("Name evidence type/length exceeds its supported contract");
        Rule direct=exact.get(type).get(input);
        // Regex rows also participate in literal exact lookup, per upstream.
        if(direct!=null) return new Match(direct,"exact-name equality",input);
        if(!useRegex||lineBreak(input)) return null;
        for(Rule rule:regex.get(type)) {
            EvidenceRows.check(stop);
            if(rule.compiled.matcher(input).matches()) return new Match(rule,"whole-string regex; ordered by priority then ID",input);
        }
        return null;
    }
    private static boolean lineBreak(String value) {
        for(int i=0;i<value.length();i++) if("\n\r\u0085\u2028\u2029".indexOf(value.charAt(i))>=0) return true;
        return false;
    }
    /** Lexical normalization; escaped backslashes must not become digit classes. */
    static String asciiDigits(String pattern) {
        StringBuilder result=new StringBuilder();boolean bracket=false;
        for(int i=0;i<pattern.length();i++) {
            char unit=pattern.charAt(i);
            if(unit=='\\'&&i+1<pattern.length()) {
                char next=pattern.charAt(++i);
                if(next=='d') result.append(bracket?"0-9":"[0-9]");else result.append('\\').append(next);
            } else {
                if(unit=='[') bracket=true;else if(unit==']') bracket=false;
                result.append(unit);
            }
        }
        return result.toString();
    }
    /** The published portable subset, with explicit unsupported-pattern evidence. */
    private static void portable(String pattern) throws IOException {
        if(lineBreak(pattern)) throw new IOException("Portable regex contains a line terminator");
        boolean bracket=false;
        for(int i=0;i<pattern.length();i++) {
            char unit=pattern.charAt(i);
            if(Character.isSurrogate(unit)) throw new IOException("Portable regex does not support surrogate/non-BMP pattern literals");
            if(unit=='\\') {
                if(++i==pattern.length()) throw new IOException("Trailing regex escape");
                String allowed="d^$\\.*+?()[]{}|/"+(bracket?"-":"");
                if(allowed.indexOf(pattern.charAt(i))<0) throw new IOException("Regex escape is outside the portable subset");
            } else if(unit=='[') {
                if(bracket) throw new IOException("Nested regex character class");bracket=true;
            } else if(unit==']') {
                if(!bracket) throw new IOException("Unmatched regex character class");bracket=false;
            } else if(bracket&&unit=='&'&&i+1<pattern.length()&&pattern.charAt(i+1)=='&') throw new IOException("Regex class intersection is not portable");
            else if(!bracket&&unit=='('&&i+1<pattern.length()&&pattern.charAt(i+1)=='?'&&(i+2>=pattern.length()||pattern.charAt(i+2)!=':')) throw new IOException("Regex flags/lookaround are not portable");
            else if(!bracket&&"*+?}".indexOf(unit)>=0&&i+1<pattern.length()&&pattern.charAt(i+1)=='+') throw new IOException("Possessive regex repetition is not portable");
            else if(!bracket&&unit=='{') {
                int at=i+1;
                while(at<pattern.length()&&pattern.charAt(at)>='0'&&pattern.charAt(at)<='9') at++;
                if(at==i+1) throw new IOException("Nonportable literal regex brace");
                if(at<pattern.length()&&pattern.charAt(at)==',') {
                    at++;while(at<pattern.length()&&pattern.charAt(at)>='0'&&pattern.charAt(at)<='9') at++;
                }
                if(at>=pattern.length()||pattern.charAt(at)!='}') throw new IOException("Unclosed regex repetition");
                i=at;
            } else if(!bracket&&unit=='}') throw new IOException("Unmatched regex repetition end");
        }
        if(bracket) throw new IOException("Unclosed regex character class");
    }
}
