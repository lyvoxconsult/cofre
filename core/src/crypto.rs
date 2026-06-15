use aes_gcm::{
    aead::{Aead, KeyInit, OsRng},
    Aes256Gcm, Key, Nonce,
};
use argon2::{Algorithm, Argon2, Params, Version};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use zeroize::Zeroizing;

pub const KEY_LENGTH: usize = 32;
pub const SALT_LENGTH: usize = 16;
pub const NONCE_LENGTH: usize = 12;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct KdfParams {
    pub algorithm: String,
    pub version: u32,
    pub memory_kib: u32,
    pub iterations: u32,
    pub parallelism: u32,
    pub salt_hex: String,
}

impl KdfParams {
    pub fn v1(salt: [u8; SALT_LENGTH]) -> Self {
        Self {
            algorithm: "argon2id".to_string(),
            version: 0x13,
            memory_kib: 64 * 1024,
            iterations: 3,
            parallelism: 4,
            salt_hex: hex::encode(salt),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Aad {
    pub vault_id: String,
    pub entity_type: String,
    pub entity_id: String,
    pub field_name: String,
    pub schema_version: u32,
    pub crypto_version: u32,
}

impl Aad {
    pub fn bytes(&self) -> Result<Vec<u8>, String> {
        serde_json::to_vec(self).map_err(|e| format!("aad serialize failed: {e}"))
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Ciphertext {
    pub algorithm: String,
    pub nonce_b64: String,
    pub data_b64: String,
}

pub fn random_bytes<const N: usize>() -> [u8; N] {
    let mut out = [0u8; N];
    OsRng.fill_bytes(&mut out);
    out
}

pub fn derive_kek(password: &str, params: &KdfParams) -> Result<Zeroizing<[u8; KEY_LENGTH]>, String> {
    if params.algorithm != "argon2id" {
        return Err("unsupported kdf".to_string());
    }
    let salt = hex::decode(&params.salt_hex).map_err(|_| "invalid kdf salt".to_string())?;
    if salt.len() != SALT_LENGTH {
        return Err("invalid kdf salt length".to_string());
    }
    let argon_params = Params::new(
        params.memory_kib,
        params.iterations,
        params.parallelism,
        Some(KEY_LENGTH),
    )
    .map_err(|e| format!("invalid argon2 params: {e}"))?;
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, argon_params);
    let mut out = Zeroizing::new([0u8; KEY_LENGTH]);
    argon2
        .hash_password_into(password.as_bytes(), &salt, out.as_mut())
        .map_err(|e| format!("kdf failed: {e}"))?;
    Ok(out)
}

pub fn encrypt_with_aad(
    key: &[u8; KEY_LENGTH],
    plaintext: &[u8],
    aad: &Aad,
) -> Result<Ciphertext, String> {
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
    let nonce = random_bytes::<NONCE_LENGTH>();
    let aad_bytes = aad.bytes()?;
    let payload = aes_gcm::aead::Payload {
        msg: plaintext,
        aad: &aad_bytes,
    };
    let encrypted = cipher
        .encrypt(Nonce::from_slice(&nonce), payload)
        .map_err(|_| "encrypt failed".to_string())?;
    Ok(Ciphertext {
        algorithm: "aes-256-gcm".to_string(),
        nonce_b64: base64::Engine::encode(&base64::engine::general_purpose::STANDARD, nonce),
        data_b64: base64::Engine::encode(&base64::engine::general_purpose::STANDARD, encrypted),
    })
}

pub fn decrypt_with_aad(
    key: &[u8; KEY_LENGTH],
    ciphertext: &Ciphertext,
    aad: &Aad,
) -> Result<Vec<u8>, String> {
    if ciphertext.algorithm != "aes-256-gcm" {
        return Err("unsupported cipher".to_string());
    }
    let nonce = base64::Engine::decode(
        &base64::engine::general_purpose::STANDARD,
        &ciphertext.nonce_b64,
    )
    .map_err(|_| "invalid nonce base64".to_string())?;
    if nonce.len() != NONCE_LENGTH {
        return Err("invalid nonce length".to_string());
    }
    let data = base64::Engine::decode(
        &base64::engine::general_purpose::STANDARD,
        &ciphertext.data_b64,
    )
    .map_err(|_| "invalid data base64".to_string())?;
    let aad_bytes = aad.bytes()?;
    let payload = aes_gcm::aead::Payload {
        msg: &data,
        aad: &aad_bytes,
    };
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
    cipher
        .decrypt(Nonce::from_slice(&nonce), payload)
        .map_err(|_| "decrypt failed".to_string())
}

pub fn wrap_vault_key(
    kek: &[u8; KEY_LENGTH],
    vault_key: &[u8; KEY_LENGTH],
    vault_id: &str,
) -> Result<Ciphertext, String> {
    let aad = Aad {
        vault_id: vault_id.to_string(),
        entity_type: "vault".to_string(),
        entity_id: vault_id.to_string(),
        field_name: "vault_key".to_string(),
        schema_version: 1,
        crypto_version: 1,
    };
    encrypt_with_aad(kek, vault_key, &aad)
}

pub fn unwrap_vault_key(
    kek: &[u8; KEY_LENGTH],
    wrapped: &Ciphertext,
    vault_id: &str,
) -> Result<Zeroizing<[u8; KEY_LENGTH]>, String> {
    let aad = Aad {
        vault_id: vault_id.to_string(),
        entity_type: "vault".to_string(),
        entity_id: vault_id.to_string(),
        field_name: "vault_key".to_string(),
        schema_version: 1,
        crypto_version: 1,
    };
    let bytes = decrypt_with_aad(kek, wrapped, &aad)?;
    if bytes.len() != KEY_LENGTH {
        return Err("invalid wrapped vault key length".to_string());
    }
    let mut out = Zeroizing::new([0u8; KEY_LENGTH]);
    out.copy_from_slice(&bytes);
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn kdf_is_deterministic_for_same_params() {
        let salt = [7u8; SALT_LENGTH];
        let params = KdfParams::v1(salt);
        let a = derive_kek("correct horse battery staple", &params).unwrap();
        let b = derive_kek("correct horse battery staple", &params).unwrap();
        assert_eq!(*a, *b);
    }

    #[test]
    fn aad_mismatch_rejects_decrypt() {
        let key = [3u8; KEY_LENGTH];
        let aad = Aad {
            vault_id: "vault-a".to_string(),
            entity_type: "entry".to_string(),
            entity_id: "entry-1".to_string(),
            field_name: "password".to_string(),
            schema_version: 1,
            crypto_version: 1,
        };
        let encrypted = encrypt_with_aad(&key, b"secret", &aad).unwrap();
        let mut wrong_aad = aad.clone();
        wrong_aad.field_name = "notes".to_string();
        assert!(decrypt_with_aad(&key, &encrypted, &wrong_aad).is_err());
    }

    #[test]
    fn vault_key_wrap_roundtrip() {
        let vault_id = Uuid::new_v4().to_string();
        let kek = [1u8; KEY_LENGTH];
        let vault_key = [9u8; KEY_LENGTH];
        let wrapped = wrap_vault_key(&kek, &vault_key, &vault_id).unwrap();
        let opened = unwrap_vault_key(&kek, &wrapped, &vault_id).unwrap();
        assert_eq!(*opened, vault_key);
    }
}
