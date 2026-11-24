// SPDX-License-Identifier: GPL-3.0-only
use aho_corasick::AhoCorasick;
use binwalk::Binwalk;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use serde_json::{Value, json};
use std::collections::HashSet;
use std::fs::File;
use std::os::fd::AsRawFd;
use std::time::{Duration, Instant};

mod input;
use std::panic::{AssertUnwindSafe, catch_unwind};

const LIMIT: usize = input::MAX_BYTES;
const LEGACY_DEADLINE_MS: u64 = 30000;
const MIN_DEADLINE_MS: u64 = 1000;
const MAX_DEADLINE_MS: u64 = 1800000;

struct Budget {
    started: Instant,
    maximum: Duration,
}
impl Budget {
    fn new() -> Self {
        Self {
            started: Instant::now(),
            maximum: Duration::from_millis(LEGACY_DEADLINE_MS),
        }
    }
    fn requested(milliseconds: i64) -> Result<Self, String> {
        if milliseconds < MIN_DEADLINE_MS as i64
            || milliseconds > MAX_DEADLINE_MS as i64
            || milliseconds % 1000 != 0
        {
            return Err("Binwalk analysis deadline requires whole seconds from 1 to 1800".into());
        }
        Ok(Self {
            started: Instant::now(),
            maximum: Duration::from_millis(milliseconds as u64),
        })
    }
    fn check(&self) -> Result<(), String> {
        if self.started.elapsed() >= self.maximum {
            Err(format!(
                "Binwalk exceeded its {}-millisecond cooperative deadline; no complete result",
                self.maximum.as_millis()
            ))
        } else {
            Ok(())
        }
    }
}
const MAX_CANDIDATES: usize = 20000;
const MAX_OUTPUT: usize = 32 * 1024 * 1024;
const SOURCE: &str = "26713972e3f9f52dc37d4a421c4554b0bd9e82ef";

fn scan(data: &[u8], budget: &Budget) -> Result<Value, String> {
    budget.check()?;
    if data.len() > LIMIT {
        return Err("Binwalk input exceeds 64 MiB".into());
    }
    // Full search includes short patterns at every offset. Do not call scan(),
    // which prunes nested results and infers unknown extents to the next hit/EOF.
    let engine =
        Binwalk::configure(None, None, None, None, None, true).map_err(|error| error.message)?;
    let search = AhoCorasick::new(&engine.patterns).map_err(|error| error.to_string())?;
    let mut rows = Vec::new();
    let mut seen = HashSet::new();
    let mut matches = 0;
    let mut accepted = 0;
    let mut rejected = 0;
    let mut panics = 0;
    for hit in search.find_overlapping_iter(data) {
        budget.check()?;
        matches += 1;
        if matches > MAX_CANDIDATES {
            return Err("More than 20,000 raw magic matches; no complete result returned".into());
        }
        let pattern = hit.pattern().as_usize();
        let signature = &engine.pattern_signature_table[&pattern];
        let result = catch_unwind(AssertUnwindSafe(|| (signature.parser)(data, hit.start())));
        budget.check()?;
        let row = match result {
            Ok(Ok(result)) => {
                let fits = result.offset <= data.len() && result.size <= data.len() - result.offset;
                if !fits {
                    return Err(format!(
                        "{} parser returned an out-of-input extent",
                        signature.name
                    ));
                }
                accepted += 1;
                json!({"name":signature.name,"description":result.description,"magicOffset":hit.start(),"patternIndex":pattern,
                    "physicalOffset":result.offset,"reportedSizeBytes":if result.size==0{Value::Null}else{json!(result.size)},
                    "extentBasis":if result.size==0{"unknown; no EOF/next-hit inference"}else{"upstream parser reported, bounded to input"},
                    "parserAccepted":true,"parserStatus":"accepted","confidenceRaw":result.confidence,"extractionDeclined":result.extraction_declined,
                    "shortPattern":signature.short,"alwaysDisplay":signature.always_display})
            }
            Ok(Err(_)) => {
                rejected += 1;
                json!({"name":signature.name,"description":signature.description,"magicOffset":hit.start(),"patternIndex":pattern,
                    "physicalOffset":hit.start(),"reportedSizeBytes":Value::Null,"extentBasis":"unknown",
                    "parserAccepted":false,"parserStatus":"rejected","confidenceRaw":Value::Null,"reason":"upstream SignatureError; no detailed reason supplied", "shortPattern":signature.short})
            }
            Err(_) => {
                panics += 1;
                json!({"name":signature.name,"description":signature.description,"magicOffset":hit.start(),"patternIndex":pattern,"physicalOffset":hit.start(),
                    "reportedSizeBytes":Value::Null,"extentBasis":"unknown","parserAccepted":false,"parserStatus":"panicked","confidenceRaw":Value::Null,"reason":"upstream parser panicked; this candidate remains unresolved"})
            }
        };
        // One candidate per signature, raw magic position and parser result.
        let key = (
            signature.name.clone(),
            hit.start(),
            row["parserAccepted"].as_bool().unwrap(),
        );
        if seen.insert(key) {
            rows.push(row);
        }
    }
    rows.sort_by_key(|row| {
        (
            row["physicalOffset"].as_u64().unwrap_or(0),
            row["patternIndex"].as_u64().unwrap_or(0),
        )
    });
    let patterns: Vec<Value> = engine.patterns.iter().enumerate().map(|(index, bytes)| {
        let signature = &engine.pattern_signature_table[&index];
        json!({"index":index,"name":signature.name,"description":signature.description,"shortPattern":signature.short,
            "magicOffsetForShortMode":signature.magic_offset,"magicHex":bytes.iter().map(|b|format!("{b:02x}")).collect::<String>()})
    }).collect();
    budget.check()?;
    Ok(
        json!({"schema":"org.diemobile.binwalk-evidence","schemaVersion":1,"ok":true,"engine":"Binwalk","engineVersion":"3.1.1","sourceRevision":SOURCE,
        "fileSizeBytes":data.len(),"signatureCount":engine.signature_count,"patternCount":engine.pattern_count,"rawMagicMatches":matches,
        "acceptedParserCalls":accepted,"rejectedParserCalls":rejected,"parserPanics":panics,"parserCoverageComplete":panics==0,"candidateCount":rows.len(),
        "rows":rows,"patterns":patterns,"fullBlobSearch":true,"nestedResultsPruned":false,"maxInputBytes":LIMIT,"maxRawMagicMatches":MAX_CANDIDATES,
        "unknownExtentsInferred":false,"externalExtractorsExecuted":false,"networkUsed":false}),
    )
}
fn read(file: File) -> Result<Value, String> {
    read_with_budget(file, &Budget::new(), false)
}
fn read_with_budget(file: File, budget: &Budget, requested: bool) -> Result<Value, String> {
    let acquired = input::Input::acquire(file, || budget.check())?;
    let mut result = scan(&acquired.bytes, budget)?;
    acquired.verify_identity()?;
    budget.check()?;
    let metadata = json!({
        "adapter":if requested {"diemobile-binwalk-3"} else {"diemobile-binwalk-2"},
        "inputReadMode":"owned-bounded-pread-copy",
        "inputSizedBufferAllocation":true,
        "maximumInputReadBufferBytes":LIMIT,
        "inputReadBufferBytes":acquired.bytes.len(),
        "maximumSourceReadRequestBytes":input::READ_BYTES,
        "sourceReadBytes":acquired.bytes.len(),
        "sourceReadOperations":acquired.read_operations,
        "ownedDescriptorDuplicate":true,
        "callerDescriptorPositionPreserved":true,
        "sourceMetadataUnchanged":true,
        "cooperativeDeadlineMs":budget.maximum.as_millis() as u64,
        "parserCallbacksPreemptible":false
    });
    result
        .as_object_mut()
        .unwrap()
        .extend(metadata.as_object().unwrap().clone());
    if requested {
        let object = result.as_object_mut().unwrap();
        object.insert("requestedDeadlineMs".into(), json!(budget.maximum.as_millis() as u64));
        object.insert("minimumCooperativeDeadlineMs".into(), json!(MIN_DEADLINE_MS));
        object.insert("maximumCooperativeDeadlineMs".into(), json!(MAX_DEADLINE_MS));
    }
    Ok(result)
}
fn respond(mut env: JNIEnv, work: impl FnOnce(&mut JNIEnv) -> Result<Value, String>) -> jstring {
    let value = match catch_unwind(AssertUnwindSafe(|| work(&mut env))) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => json!({"ok":false,"error":error}),
        Err(_) => {
            json!({"ok":false,"error":"Binwalk parser panicked; no complete result returned"})
        }
    };
    let encoded = value.to_string();
    let output = if encoded.len() > MAX_OUTPUT {
        "{\"ok\":false,\"error\":\"Binwalk report exceeds 32 MiB\"}"
    } else {
        &encoded
    };
    match env.new_string(output) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_diemobile_BinwalkEngine_scanFd(
    env: JNIEnv,
    _class: JClass,
    fd: jint,
) -> jstring {
    respond(env, |_| read(input::duplicate(fd)?))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_diemobile_BinwalkEngine_scanFdWithDeadline(
    env: JNIEnv,
    _class: JClass,
    fd: jint,
    milliseconds: jlong,
) -> jstring {
    respond(env, |_| {
        let budget = Budget::requested(milliseconds)?;
        read_with_budget(input::duplicate(fd)?, &budget, true)
    })
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_diemobile_BinwalkEngine_scanPath(
    env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    respond(env, |env| {
        let path: String = env.get_string(&path).map_err(|e| e.to_string())?.into();
        {
            let source = input::open_path(path)?;
            read(input::duplicate(source.as_raw_fd())?)
        }
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn expired_budget_returns_no_success() {
        let budget = Budget {
            started: Instant::now(),
            maximum: Duration::ZERO,
        };
        assert!(scan(&[], &budget).unwrap_err().contains("deadline"));
    }
    #[test]
    fn empty_is_complete_without_unknown_range_inference() {
        let result = scan(&[], &Budget::new()).unwrap();
        assert!(result["ok"].as_bool().unwrap());
        assert!(result["rows"].as_array().unwrap().is_empty());
    }
    #[test]
    fn invalid_requested_limits_fail_before_analysis() {
        for milliseconds in [-1, 0, 999, 1001, 1800001, i64::MAX] {
            assert!(Budget::requested(milliseconds).is_err());
        }
        assert!(Budget::requested(1000).is_ok());
        assert!(Budget::requested(1800000).is_ok());
    }
    #[test]
    fn requested_budget_controls_acquisition_and_report() {
        use std::io::Write;
        let path = std::env::temp_dir().join(format!("die-binwalk-budget-{}", std::process::id()));
        let mut source = File::create(&path).unwrap();
        source.write_all(&[0; 17]).unwrap();
        drop(source);
        let mut budget = Budget::requested(240000).unwrap();
        let result = read_with_budget(File::open(&path).unwrap(), &budget, true).unwrap();
        assert_eq!(result["requestedDeadlineMs"], json!(240000));
        assert_eq!(result["cooperativeDeadlineMs"], json!(240000));
        assert_eq!(result["sourceReadBytes"], json!(17));
        assert_eq!(result["adapter"], json!("diemobile-binwalk-3"));
        // The same captured budget covers descriptor acquisition, not just scan callbacks.
        budget.started = Instant::now() - Duration::from_secs(241);
        let error = read_with_budget(File::open(&path).unwrap(), &budget, true).unwrap_err();
        assert!(error.contains("240000-millisecond cooperative deadline"));
        std::fs::remove_file(path).unwrap();
    }
}
