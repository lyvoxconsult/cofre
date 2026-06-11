use argon2::{Algorithm, Argon2, Params, Version};
use rand::rngs::OsRng;
use rand::RngCore;
use zeroize::Zeroizing;

/// Número de bytes da chave derivada (256 bits = 32 bytes)
pub const KEY_LENGTH: usize = 32;

/// Tamanho do salt em bytes
pub const SALT_LENGTH: usize = 16;

/// Parâmetros Argon2id para derivação de chave
/// - 64 MB de memória: inviabiliza brute-force paralelo em GPU
/// - 3 passes: balance entre segurança e latency aceitável (< 2s)
/// - 4 threads: aproveita CPUs multi-core comuns
const ARGON_MEMORY: u32 = 64 * 1024; // 64 MB em KiB
const ARGON_TIME: u32 = 3;
const ARGON_PARALLELISM: u32 = 4;

/// Gera um salt criptograficamente seguro
pub fn generate_salt() -> [u8; SALT_LENGTH] {
    let mut salt = [0u8; SALT_LENGTH];
    OsRng.fill_bytes(&mut salt);
    salt
}

/// Deriva uma chave de 256 bits a partir da senha mestra usando Argon2id.
///
/// # Argumentos
/// * `password` - Senha mestra fornecida pelo usuário
/// * `salt` - Salt de 16 bytes (gerado na primeira execução e persistido)
///
/// # Retorna
/// * `Ok(Zeroizing<[u8; 32]>)` - Chave derivada (zeroizada ao sair do escopo)
/// * `Err(String)` - Erro de derivação
pub fn derive_key(password: &str, salt: &[u8]) -> Result<Zeroizing<[u8; KEY_LENGTH]>, String> {
    let params = Params::new(ARGON_MEMORY, ARGON_TIME, ARGON_PARALLELISM, None)
        .map_err(|e| format!("Erro nos parâmetros Argon2: {e}"))?;

    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);

    let mut output_key = Zeroizing::new([0u8; KEY_LENGTH]);

    argon2
        .hash_password_into(password.as_bytes(), salt, output_key.as_mut())
        .map_err(|e| format!("Erro na derivação de chave: {e}"))?;

    Ok(output_key)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_key_derivation_deterministic() {
        let password = "minha_senha_mestra_segura_123!";
        let salt = generate_salt();

        let key1 = derive_key(password, &salt).unwrap();
        let key2 = derive_key(password, &salt).unwrap();

        assert_eq!(*key1, *key2, "Mesma senha + salt deve produzir mesma chave");
    }

    #[test]
    fn test_key_derivation_different_passwords() {
        let salt = generate_salt();

        let key1 = derive_key("senha_A", &salt).unwrap();
        let key2 = derive_key("senha_B", &salt).unwrap();

        assert_ne!(*key1, *key2, "Senhas diferentes devem produzir chaves diferentes");
    }

    #[test]
    fn test_key_derivation_different_salts() {
        let key1 = derive_key("senha", &generate_salt()).unwrap();
        let key2 = derive_key("senha", &generate_salt()).unwrap();

        assert_ne!(*key1, *key2, "Salts diferentes devem produzir chaves diferentes");
    }

    #[test]
    fn test_key_length() {
        let key = derive_key("senha", &generate_salt()).unwrap();
        assert_eq!(key.len(), KEY_LENGTH, "Chave deve ter 32 bytes (256 bits)");
    }

    #[test]
    fn test_salt_length() {
        let salt = generate_salt();
        assert_eq!(salt.len(), SALT_LENGTH, "Salt deve ter 16 bytes");
    }
}
