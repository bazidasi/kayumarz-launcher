//! JNI bridge implementing the exact `external` declarations in
//! `com.kayumars.launcher.bridge.Core` (Kotlin object -> instance methods).
//!
//! The UniFFI surface in `ffi.rs` remains available for generated bindings;
//! this module is what the shipped Kotlin shell links against.
//!
//! Handle convention: `nativeInit` returns a `Box<Arc<Kayumars>>` leaked into
//! a `jlong`; every other entry point borrows it. A zero handle is rejected.
//!
//! Gesture protocol: `nativeProcessGestureTouch` accepts a serde-JSON
//! `TouchEvent` and returns a serde-JSON `DispatchedCommand`. Unparseable
//! input returns "" (the shell treats "" as "no command").

use std::sync::Arc;

use jni::objects::{JObject, JString, JValue};
use jni::sys::{jint, jlong, jobject, jstring};
use jni::JNIEnv;

use crate::classify::{classify_batch, ClassifyInput};
use crate::gesture_engine::core::gesture_state::TouchEvent;
use crate::gesture_engine::settings::profiles::GestureProfilePreset;
use crate::model::{AppEntry, SearchResult};
use crate::Kayumars;

const SEARCH_RESULT_CLASS: &str = "com/kayumars/launcher/bridge/SearchResult";
const SEARCH_RESULT_KIND_CLASS: &str = "com/kayumars/launcher/bridge/SearchResultKind";

unsafe fn core_from_handle<'a>(handle: jlong) -> Option<&'a Arc<Kayumars>> {
    if handle == 0 {
        return None;
    }
    Some(&*(handle as *const Arc<Kayumars>))
}

fn clear_pending_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn jstring_to_string(env: &mut JNIEnv, s: &JString) -> jni::errors::Result<String> {
    Ok(env.get_string(s)?.into())
}

fn results_to_java_list(env: &mut JNIEnv, results: Vec<SearchResult>) -> jni::errors::Result<jobject> {
    let list = env.new_object("java/util/ArrayList", "()V", &[])?;
    if results.is_empty() {
        return Ok(list.into_raw());
    }

    let kind_class = env.find_class(SEARCH_RESULT_KIND_CLASS)?;
    let app_name = env.new_string("APP")?;
    let app_kind = env
        .call_static_method(
            &kind_class,
            "valueOf",
            "(Ljava/lang/String;)Lcom/kayumars/launcher/bridge/SearchResultKind;",
            &[JValue::Object(&app_name)],
        )?
        .l()?;

    let result_class = env.find_class(SEARCH_RESULT_CLASS)?;
    for r in results {
        let pkg = env.new_string(r.package)?;
        let label = env.new_string(r.label)?;
        let obj = env.new_object(
            &result_class,
            "(Ljava/lang/String;Ljava/lang/String;FLcom/kayumars/launcher/bridge/SearchResultKind;)V",
            &[
                JValue::Object(&pkg),
                JValue::Object(&label),
                JValue::Float(r.score),
                JValue::Object(&app_kind),
            ],
        )?;
        env.call_method(&list, "add", "(Ljava/lang/Object;)Z", &[JValue::Object(&obj)])?;
        // ART aborts past 512 live local refs; free per-result refs each iteration.
        env.delete_local_ref(obj)?;
        env.delete_local_ref(pkg)?;
        env.delete_local_ref(label)?;
    }
    Ok(list.into_raw())
}

fn java_list_to_entries(env: &mut JNIEnv, apps: &JObject) -> jni::errors::Result<Vec<AppEntry>> {
    let size = env.call_method(apps, "size", "()I", &[])?.i()?;
    let mut entries = Vec::with_capacity(size.max(0) as usize);
    for i in 0..size {
        let obj = env
            .call_method(apps, "get", "(I)Ljava/lang/Object;", &[JValue::Int(i)])?
            .l()?;
        let pkg_obj = env
            .call_method(&obj, "getPkg", "()Ljava/lang/String;", &[])?
            .l()?;
        let label_obj = env
            .call_method(&obj, "getLabel", "()Ljava/lang/String;", &[])?
            .l()?;
        let user_serial = env
            .call_method(&obj, "getUserSerial", "()I", &[])?
            .i()?;
        let flags = env.call_method(&obj, "getFlags", "()I", &[])?.i()?;
        entries.push(AppEntry {
            package: env.get_string((&pkg_obj).into())?.into(),
            label: env.get_string((&label_obj).into())?.into(),
            user_serial: user_serial as u32,
            flags: flags as u32,
        });
        env.delete_local_ref(obj)?;
        env.delete_local_ref(pkg_obj)?;
        env.delete_local_ref(label_obj)?;
    }
    Ok(entries)
}

/// Pure JSON gesture pipeline, host-testable without a JVM.
pub fn process_touch_json(core: &Kayumars, json: &str) -> String {
    match serde_json::from_str::<TouchEvent>(json) {
        Ok(event) => {
            let cmd = core.process_touch_event(event);
            serde_json::to_string(&cmd).unwrap_or_default()
        }
        Err(_) => String::new(),
    }
}

fn preset_from_name(name: &str) -> Option<GestureProfilePreset> {
    match name.to_ascii_uppercase().as_str() {
        "BALANCED_DEFAULT" | "BALANCEDDEFAULT" => Some(GestureProfilePreset::BalancedDefault),
        "BATTERY_SAVER" | "BATTERYSAVER" => Some(GestureProfilePreset::BatterySaver),
        "ONE_HANDED" | "ONEHANDED" => Some(GestureProfilePreset::OneHanded),
        "POWER_USER" | "POWERUSER" => Some(GestureProfilePreset::PowerUser),
        _ => None,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeInit(
    mut env: JNIEnv,
    _this: JObject,
    config_dir: JString,
) -> jlong {
    let dir = match jstring_to_string(&mut env, &config_dir) {
        Ok(d) => d,
        Err(_) => {
            clear_pending_exception(&mut env);
            return 0;
        }
    };
    let core: Arc<Kayumars> = Kayumars::new(dir);
    Box::into_raw(Box::new(core)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeSearch(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    query: JString,
    max_results: jint,
) -> jobject {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return std::ptr::null_mut();
    };
    let query = match jstring_to_string(&mut env, &query) {
        Ok(q) => q,
        Err(_) => {
            clear_pending_exception(&mut env);
            return std::ptr::null_mut();
        }
    };
    let max = max_results.clamp(0, 255) as u8;
    let results = core.search(query, max);
    match results_to_java_list(&mut env, results) {
        Ok(list) => list,
        Err(_) => {
            clear_pending_exception(&mut env);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeTopApps(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    max_results: jint,
) -> jobject {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return std::ptr::null_mut();
    };
    let max = max_results.clamp(0, 255) as u8;
    let results = core.top_apps(max);
    match results_to_java_list(&mut env, results) {
        Ok(list) => list,
        Err(_) => {
            clear_pending_exception(&mut env);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeReplaceIndex(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    apps: JObject,
) {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return;
    };
    match java_list_to_entries(&mut env, &apps) {
        Ok(entries) => core.replace_index(entries),
        Err(_) => clear_pending_exception(&mut env),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeLogLaunch(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    pkg: JString,
    timestamp_ms: jlong,
) {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return;
    };
    match jstring_to_string(&mut env, &pkg) {
        Ok(pkg) => core.log_launch(pkg, timestamp_ms.max(0) as u64),
        Err(_) => clear_pending_exception(&mut env),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeSaveNow(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    if let Some(core) = unsafe { core_from_handle(handle) } {
        core.save_now();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeResetHistory(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    if let Some(core) = unsafe { core_from_handle(handle) } {
        core.reset_history();
    }
}

fn empty_jstring(env: &mut JNIEnv) -> jstring {
    match env.new_string("") {
        Ok(s) => s.into_raw(),
        Err(_) => {
            clear_pending_exception(env);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeProcessGestureTouch(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    touch_event_json: JString,
) -> jstring {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return empty_jstring(&mut env);
    };
    let json = match jstring_to_string(&mut env, &touch_event_json) {
        Ok(j) => j,
        Err(_) => {
            clear_pending_exception(&mut env);
            return empty_jstring(&mut env);
        }
    };
    let out = process_touch_json(core, &json);
    match env.new_string(out) {
        Ok(s) => s.into_raw(),
        Err(_) => {
            clear_pending_exception(&mut env);
            std::ptr::null_mut()
        }
    }
}

/// Batch App Library classification.
/// In: `[{"pkg":"...","label":"...","cat":-1}, ...]` (serde `ClassifyInput`).
/// Out: `{"pkg":"PRODUCTIVITY|SOCIAL|CREATIVITY|UTILITIES|GAMES|OTHER", ...}`.
/// Invalid input returns `{}`; the Kotlin side then keeps every app in OTHER.
#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeClassifyApps(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    apps_json: JString,
) -> jstring {
    if unsafe { core_from_handle(handle) }.is_none() {
        return empty_jstring(&mut env);
    }
    let json = match jstring_to_string(&mut env, &apps_json) {
        Ok(j) => j,
        Err(_) => {
            clear_pending_exception(&mut env);
            return empty_jstring(&mut env);
        }
    };
    let out = match serde_json::from_str::<Vec<ClassifyInput>>(&json) {
        Ok(apps) => serde_json::to_string(&classify_batch(&apps)).unwrap_or_default(),
        Err(_) => "{}".to_string(),
    };
    match env.new_string(out) {
        Ok(s) => s.into_raw(),
        Err(_) => {
            clear_pending_exception(&mut env);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kayumars_launcher_bridge_Core_nativeSetGestureProfile(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    profile_name: JString,
) {
    let Some(core) = (unsafe { core_from_handle(handle) }) else {
        return;
    };
    let name = match jstring_to_string(&mut env, &profile_name) {
        Ok(n) => n,
        Err(_) => {
            clear_pending_exception(&mut env);
            return;
        }
    };
    if let Some(preset) = preset_from_name(&name) {
        core.set_gesture_profile(preset);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gesture_json_roundtrip_and_legacy_input() {
        let tmp = tempfile::tempdir().unwrap();
        let core = Kayumars::new(tmp.path().to_str().unwrap().to_string());

        // Legacy "KIND:ZONE" notification strings are ignored, not crashes.
        assert_eq!(process_touch_json(&core, "DOUBLE_TAP:HOME_SCREEN"), "");

        let down = r#"{"action":"Down","timestamp_ms":100,"zone":"HomeScreen","target_package":null,"points":[{"id":0,"x_dp":120.0,"y_dp":300.0}]}"#;
        let up = r#"{"action":"Up","timestamp_ms":140,"zone":"HomeScreen","target_package":null,"points":[{"id":0,"x_dp":120.0,"y_dp":300.0}]}"#;
        assert!(!process_touch_json(&core, down).is_empty());
        assert!(!process_touch_json(&core, up).is_empty());

        let down2 = r#"{"action":"Down","timestamp_ms":200,"zone":"HomeScreen","target_package":null,"points":[{"id":0,"x_dp":121.0,"y_dp":301.0}]}"#;
        let up2 = r#"{"action":"Up","timestamp_ms":240,"zone":"HomeScreen","target_package":null,"points":[{"id":0,"x_dp":121.0,"y_dp":301.0}]}"#;
        process_touch_json(&core, down2);
        let cmd = process_touch_json(&core, up2);
        assert!(cmd.contains("\"DoubleTap\""), "expected DoubleTap in {cmd}");
    }

    #[test]
    fn preset_name_mapping() {
        assert_eq!(preset_from_name("BALANCED_DEFAULT"), Some(GestureProfilePreset::BalancedDefault));
        assert_eq!(preset_from_name("BatterySaver"), Some(GestureProfilePreset::BatterySaver));
        assert_eq!(preset_from_name("ONE_HANDED"), Some(GestureProfilePreset::OneHanded));
        assert_eq!(preset_from_name("POWER_USER"), Some(GestureProfilePreset::PowerUser));
        assert_eq!(preset_from_name("nonsense"), None);
    }
}