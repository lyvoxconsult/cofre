use serde::{Deserialize, Serialize};

use crate::crypto::Ciphertext;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SyncEnvelope {
    pub magic: String,
    pub app: String,
    pub format_version: u32,
    pub vault_id: String,
    pub source_device_id: String,
    pub created_at_utc: String,
    pub revision_start: i64,
    pub revision_end: i64,
    pub encrypted_changeset: Ciphertext,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SyncChange {
    pub entity_type: String,
    pub entity_id: String,
    pub operation: String,
    pub revision: i64,
    pub updated_at_utc: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum ConflictPolicy {
    PreserveBoth,
    TombstoneWinsWithUserReview,
    FieldLevelSafeMerge,
}

impl SyncEnvelope {
    pub fn validate_shape(&self) -> Result<(), String> {
        if self.magic != "LYVOX_VAULT_SYNC" {
            return Err("invalid sync magic".to_string());
        }
        if self.app != crate::APP_ID {
            return Err("invalid sync app".to_string());
        }
        if self.revision_end < self.revision_start {
            return Err("invalid revision range".to_string());
        }
        Ok(())
    }
}
