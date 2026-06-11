use aes_gcm::{
    aead::{Aead, KeyInit, OsRng},
    Aes256Gcm, Key, Nonce,
};
use rand::RngCore;
use zeroize::Zeroizing;

/// Tamanho do nonce para AES-256-GCM (96 bits = 12 bytes)
pub const NONCE_LENGTH: usize = 12;

// Tamanho da tag de autenticação GCM (128 bits = 16 bytes) — embutido no ciphertext pelo aes-gcm
// pub const TAG_LENGTH: usize = 16;

/// Gera um nonce criptograficamente seguro
pub fn generate_nonce() -> [u8; NONCE_LENGTH] {
    let mut nonce = [0u8; NONCE_LENGTH];
    OsRng.fill_bytes(&mut nonce);
    nonce
}

/// Criptografa um texto plano usando AES-256-GCM.
///
/// Retorna: (ciphertext_com_tag, nonce)
/// - ciphertext_com_tag: ciphertext || tag (concatenados)
/// - nonce: nonce usado para esta operação
///
/// # Argumentos
/// * `key` - Chave de 256 bits
/// * `plaintext` - Dados a serem criptografados
pub fn encrypt(
    key: &Zeroizing<[u8; 32]>,
    plaintext: &[u8],
) -> Result<(Vec<u8>, [u8; NONCE_LENGTH]), String> {
    let aes_key = Key::<Aes256Gcm>::from_slice(key.as_ref());
    let cipher = Aes256Gcm::new(aes_key);

    let nonce = generate_nonce();
    let nonce_ref = Nonce::from_slice(&nonce);

    let ciphertext = cipher
        .encrypt(nonce_ref, plaintext)
        .map_err(|e| format!("Erro na criptografia: {e}"))?;

    Ok((ciphertext, nonce))
}

/// Descriptografa um ciphertext previamente criptografado com `encrypt`.
///
/// # Argumentos
/// * `key` - Chave de 256 bits (mesma usada na criptografia)
/// * `ciphertext` - Dados criptografados (ciphertext || tag)
/// * `nonce` - Nonce usado na criptografia (12 bytes)
pub fn decrypt(
    key: &Zeroizing<[u8; 32]>,
    ciphertext: &[u8],
    nonce: &[u8; NONCE_LENGTH],
) -> Result<Vec<u8>, String> {
    let aes_key = Key::<Aes256Gcm>::from_slice(key.as_ref());
    let cipher = Aes256Gcm::new(aes_key);
    let nonce_ref = Nonce::from_slice(nonce);

    let plaintext = cipher
        .decrypt(nonce_ref, ciphertext)
        .map_err(|_| "Erro na descriptografia: chave inválida ou dados corrompidos".to_string())?;

    Ok(plaintext)
}

/// Criptografa um campo de texto (string) e retorna (ciphertext_base64, nonce_base64)
pub fn encrypt_field(
    key: &Zeroizing<[u8; 32]>,
    plaintext: &str,
) -> Result<(String, String), String> {
    let (ciphertext, nonce) = encrypt(key, plaintext.as_bytes())?;
    Ok((base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &ciphertext),
        base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &nonce)))
}

/// Descriptografa um campo de texto
pub fn decrypt_field(
    key: &Zeroizing<[u8; 32]>,
    ciphertext_b64: &str,
    nonce_b64: &str,
) -> Result<String, String> {
    use base64::Engine;
    let ciphertext = base64::engine::general_purpose::STANDARD
        .decode(ciphertext_b64)
        .map_err(|e| format!("Erro ao decodificar base64: {e}"))?;
    let nonce_bytes = base64::engine::general_purpose::STANDARD
        .decode(nonce_b64)
        .map_err(|e| format!("Erro ao decodificar nonce base64: {e}"))?;
    
    if nonce_bytes.len() != NONCE_LENGTH {
        return Err("Nonce inválido".to_string());
    }
    let mut nonce_arr = [0u8; NONCE_LENGTH];
    nonce_arr.copy_from_slice(&nonce_bytes);

    let plaintext = decrypt(key, &ciphertext, &nonce_arr)?;
    String::from_utf8(plaintext).map_err(|_| "Erro na decodificação UTF-8".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::key_derivation::{derive_key, generate_salt};

    fn test_key() -> Zeroizing<[u8; 32]> {
        derive_key("senha_teste", &generate_salt()).unwrap()
    }

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let key = test_key();
        let plaintext = b"senha_super_secreta_123!@#";

        let (ciphertext, nonce) = encrypt(&key, plaintext).unwrap();
        let decrypted = decrypt(&key, &ciphertext, &nonce).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_encrypt_decrypt_empty() {
        let key = test_key();
        let plaintext = b"";

        let (ciphertext, nonce) = encrypt(&key, plaintext).unwrap();
        let decrypted = decrypt(&key, &ciphertext, &nonce).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_encrypt_different_nonce() {
        let key = test_key();
        let plaintext = b"mesma_senha";

        let (c1, n1) = encrypt(&key, plaintext).unwrap();
        let (c2, n2) = encrypt(&key, plaintext).unwrap();

        // Nonces devem ser diferentes (a menos que colisão extremamente improvável)
        assert_ne!(n1, n2, "Nonces devem ser únicos");
        // Ciphertexts devem ser diferentes devido a nonces diferentes
        assert_ne!(c1, c2, "Ciphertexts devem diferir com nonces diferentes");
    }

    #[test]
    fn test_decrypt_wrong_key() {
        let key1 = test_key();
        let key2 = derive_key("outra_senha", &generate_salt()).unwrap();

        let (ciphertext, nonce) = encrypt(&key1, b"dados_secretos").unwrap();
        let result = decrypt(&key2, &ciphertext, &nonce);

        assert!(result.is_err(), "Chave errada deve falhar na descriptografia");
    }

    #[test]
    fn test_decrypt_tampered_ciphertext() {
        let key = test_key();
        let (mut ciphertext, nonce) = encrypt(&key, b"dados_secretos").unwrap();

        // Altera um byte do ciphertext
        ciphertext[0] ^= 0xFF;

        let result = decrypt(&key, &ciphertext, &nonce);
        assert!(result.is_err(), "Dados adulterados devem falhar na descriptografia");
    }

    #[test]
    fn test_field_roundtrip() {
        let key = test_key();
        let original = "minha_senha_123!@#";

        let (ct_b64, nonce_b64) = encrypt_field(&key, original).unwrap();
        let decrypted = decrypt_field(&key, &ct_b64, &nonce_b64).unwrap();

        assert_eq!(decrypted, original);
    }
}
