//! Atomic bincode persistence for gesture profiles and per-icon gesture overrides.

use std::fs::{self, File};
use std::io::{BufReader, BufWriter, Write};
use std::path::{Path, PathBuf};

use crate::gesture_engine::settings::profiles::GestureProfile;

const GESTURE_FILENAME: &str = "kayumars_gestures.bin";
const GESTURE_TEMP: &str = "kayumars_gestures.bin.tmp";

pub struct GesturePersistence {
    base_dir: PathBuf,
}

impl GesturePersistence {
    pub fn new<P: AsRef<Path>>(base_dir: P) -> Self {
        let path = base_dir.as_ref().to_path_buf();
        let _ = fs::create_dir_all(&path);
        Self { base_dir: path }
    }

    fn file_path(&self) -> PathBuf {
        self.base_dir.join(GESTURE_FILENAME)
    }

    fn temp_path(&self) -> PathBuf {
        self.base_dir.join(GESTURE_TEMP)
    }

    pub fn load(&self) -> GestureProfile {
        let path = self.file_path();
        if !path.exists() {
            return GestureProfile::default();
        }
        match File::open(&path) {
            Ok(file) => {
                let reader = BufReader::new(file);
                bincode::deserialize_from(reader).unwrap_or_default()
            }
            Err(_) => GestureProfile::default(),
        }
    }

    pub fn save(&self, profile: &GestureProfile) -> Result<(), std::io::Error> {
        let tmp = self.temp_path();
        let dst = self.file_path();
        {
            let file = File::create(&tmp)?;
            let mut writer = BufWriter::new(file);
            bincode::serialize_into(&mut writer, profile)
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?;
            writer.flush()?;
        }
        fs::rename(tmp, dst)?;
        Ok(())
    }
}
