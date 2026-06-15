use serde::{Deserialize, Serialize};

use crate::crypto::{Ciphertext, KdfParams};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BackupEnvelope {
    pub magic: String,
    pub app: String,
    pub format_version: u32,
    pub created_at_utc: String,
    pub vault_id: String,
    pub kdf: KdfParams,
    pub encrypted_manifest: Ciphertext,
    pub chunks: Vec<BackupChunk>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BackupChunk {
    pub id: String,
    pub entity_type: String,
    pub encrypted_data: Ciphertext,
    pub sha256_hex: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BackupManifest {
    pub schema_version: u32,
    pub crypto_version: u32,
    pub app_version: String,
    pub includes: Vec<String>,
    pub chunk_count: usize,
}

impl BackupEnvelope {
    pub fn validate_shape(&self) -> Result<(), String> {
        if self.magic != "LYVOX_VAULT_BACKUP" {
            return Err("invalid backup magic".to_string());
        }
        if self.app != crate::APP_ID {
            return Err("invalid backup app".to_string());
        }
        if self.format_version != crate::FORMAT_VERSION {
            return Err("unsupported backup format version".to_string());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_wrong_magic() {
        let env = BackupEnvelope {
            magic: "WRONG".to_string(),
            app: crate::APP_ID.to_string(),
            format_version: 1,
            created_at_utc: "2026-06-15T00:00:00Z".to_string(),
            vault_id: "vault".to_string(),
            kdf: KdfParams::v1([1u8; 16]),
            encrypted_manifest: Ciphertext {
                algorithm: "aes-256-gcm".to_string(),
                nonce_b64: String::new(),
                data_b64: String::new(),
            },
            chunks: vec![],
        };
        assert!(env.validate_shape().is_err());
    }
}
