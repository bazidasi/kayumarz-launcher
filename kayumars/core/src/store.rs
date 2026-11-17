//! Atomic bincode disk persistence for Kayumars cache and launch logs.
//! Safe against unexpected shutdowns and file corruptions.

use serde::{Deserialize, Serialize};
use std::fs::{self, File};
use std::io::{BufReader, BufWriter, Write};
use std::path::{Path, PathBuf};

use crate::history::HistoryData;
use crate::model::AppEntry;

const STORE_FILENAME: &str = "kayumars_cache.bin";
const TEMP_FILENAME: &str = "kayumars_cache.bin.tmp";

/// Root container persisted to bincode.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PersistentState {
    pub version: u32,
    pub entries: Vec<AppEntry>,
    pub history: HistoryData,
}

impl Default for PersistentState {
    fn default() -> Self {
        Self {
            version: 1,
            entries: Vec::new(),
            history: HistoryData::default(),
        }
    }
}

pub struct StateStore {
    base_dir: PathBuf,
}

impl StateStore {
    pub fn new<P: AsRef<Path>>(base_dir: P) -> Self {
        let path = base_dir.as_ref().to_path_buf();
        let _ = fs::create_dir_all(&path);
        Self { base_dir: path }
    }

    fn file_path(&self) -> PathBuf {
        self.base_dir.join(STORE_FILENAME)
    }

    fn temp_path(&self) -> PathBuf {
        self.base_dir.join(TEMP_FILENAME)
    }

    /// Loads persisted state. If file is missing or corrupted, returns default empty state without crashing.
    pub fn load(&self) -> PersistentState {
        let path = self.file_path();
        if !path.exists() {
            return PersistentState::default();
        }

        match File::open(&path) {
            Ok(file) => {
                let reader = BufReader::new(file);
                match bincode::deserialize_from(reader) {
                    Ok(state) => state,
                    Err(_) => {
                        // Corrupt file detected: fall back safely to empty state
                        PersistentState::default()
                    }
                }
            }
            Err(_) => PersistentState::default(),
        }
    }

    /// Writes state atomically using write-to-temp + rename pattern.
    pub fn save(&self, state: &PersistentState) -> Result<(), std::io::Error> {
        let temp_path = self.temp_path();
        let final_path = self.file_path();

        {
            let file = File::create(&temp_path)?;
            let mut writer = BufWriter::new(file);
            bincode::serialize_into(&mut writer, state)
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?;
            writer.flush()?;
            // Durability: without fsync a power loss after rename can leave a zero-length file.
            writer
                .into_inner()
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?
                .sync_all()?;
        }

        fs::rename(temp_path, final_path)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_store_roundtrip() {
        let tmp = tempfile::tempdir().unwrap();
        let store = StateStore::new(tmp.path());

        let state = PersistentState {
            version: 1,
            entries: vec![AppEntry {
                package: "com.test".into(),
                label: "Test".into(),
                user_serial: 0,
                flags: 0,
            }],
            history: HistoryData::default(),
        };

        store.save(&state).unwrap();
        let loaded = store.load();
        assert_eq!(loaded.entries.len(), 1);
        assert_eq!(loaded.entries[0].label, "Test");
    }
}
