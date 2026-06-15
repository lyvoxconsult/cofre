use rand::rngs::OsRng;
use rand::Rng;
use serde::Deserialize;

use crate::storage::models::PasswordGenResult;

/// Conjuntos de caracteres
const UPPERCASE: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
const LOWERCASE: &str = "abcdefghijklmnopqrstuvwxyz";
const NUMBERS: &str = "0123456789";
const AMBIGUOUS: &str = "il1Lo0O";

/// Configuração para geração de senha (recebida do frontend)
#[derive(Debug, Clone, Deserialize)]
pub struct GeneratePasswordRequest {
    pub length: u8,
    pub use_uppercase: bool,
    pub use_lowercase: bool,
    pub use_numbers: bool,
    pub use_special: bool,
    pub special_chars: Option<String>,
    pub exclude_ambiguous: bool,
}

/// Gera uma senha aleatória baseada na configuração fornecida.
#[tauri::command]
pub fn generate_password(config: GeneratePasswordRequest) -> Result<PasswordGenResult, String> {
    let length = config.length.max(4).min(128) as usize;

    // Monta o charset
    let mut charset = String::new();
    if config.use_uppercase {
        charset.push_str(UPPERCASE);
    }
    if config.use_lowercase {
        charset.push_str(LOWERCASE);
    }
    if config.use_numbers {
        charset.push_str(NUMBERS);
    }
    if config.use_special {
        let special = config
            .special_chars
            .as_deref()
            .unwrap_or("!@#$%^&*()-_=+[]{}|;:,.<>?");
        charset.push_str(special);
    }

    // Fallback mínimo
    if charset.is_empty() {
        charset.push_str(LOWERCASE);
    }

    // Remove ambíguos se solicitado
    let charset: Vec<char> = if config.exclude_ambiguous {
        charset
            .chars()
            .filter(|c| !AMBIGUOUS.contains(*c))
            .collect()
    } else {
        charset.chars().collect()
    };

    if charset.is_empty() {
        return Err("Conjunto de caracteres vazio após filtros.".to_string());
    }

    // Gera usando CSPRNG
    let mut rng = OsRng;
    let password: String = (0..length)
        .map(|_| {
            let idx = rng.gen_range(0..charset.len());
            charset[idx]
        })
        .collect();

    // Calcula entropia
    let pool_size = charset.len() as f64;
    let entropy_bits = length as f64 * pool_size.log2();

    let strength_label = match entropy_bits {
        e if e < 40.0 => "Muito Fraca".to_string(),
        e if e < 60.0 => "Fraca".to_string(),
        e if e < 80.0 => "Média".to_string(),
        e if e < 100.0 => "Forte".to_string(),
        _ => "Muito Forte".to_string(),
    };

    Ok(PasswordGenResult {
        password,
        entropy_bits,
        strength_label,
    })
}

/// Avalia a força de uma senha existente.
#[tauri::command]
pub fn evaluate_password(password: String) -> PasswordGenResult {
    let length = password.len() as f64;

    let mut pool_size = 0f64;
    if password.chars().any(|c| c.is_ascii_uppercase()) {
        pool_size += 26.0;
    }
    if password.chars().any(|c| c.is_ascii_lowercase()) {
        pool_size += 26.0;
    }
    if password.chars().any(|c| c.is_ascii_digit()) {
        pool_size += 10.0;
    }
    if password.chars().any(|c| !c.is_ascii_alphanumeric()) {
        pool_size += 33.0;
    }

    if pool_size == 0.0 {
        pool_size = 26.0;
    }

    let entropy_bits = length * pool_size.log2();

    let strength_label = match entropy_bits {
        e if e < 40.0 => "Muito Fraca".to_string(),
        e if e < 60.0 => "Fraca".to_string(),
        e if e < 80.0 => "Média".to_string(),
        e if e < 100.0 => "Forte".to_string(),
        _ => "Muito Forte".to_string(),
    };

    PasswordGenResult {
        password,
        entropy_bits,
        strength_label,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_password_default() {
        let config = GeneratePasswordRequest {
            length: 24,
            use_uppercase: true,
            use_lowercase: true,
            use_numbers: true,
            use_special: true,
            special_chars: None,
            exclude_ambiguous: false,
        };

        let result = generate_password(config).unwrap();
        assert_eq!(result.password.len(), 24);
        assert!(result.entropy_bits > 100.0);
    }

    #[test]
    fn test_generate_password_lowercase_only() {
        let config = GeneratePasswordRequest {
            length: 8,
            use_uppercase: false,
            use_lowercase: true,
            use_numbers: false,
            use_special: false,
            special_chars: None,
            exclude_ambiguous: false,
        };

        let result = generate_password(config).unwrap();
        assert_eq!(result.password.len(), 8);
        assert!(result.password.chars().all(|c| c.is_ascii_lowercase()));
    }

    #[test]
    fn test_generate_password_different_each_time() {
        let config = GeneratePasswordRequest {
            length: 32,
            use_uppercase: true,
            use_lowercase: true,
            use_numbers: true,
            use_special: true,
            special_chars: None,
            exclude_ambiguous: false,
        };

        let r1 = generate_password(config.clone()).unwrap();
        let r2 = generate_password(config).unwrap();

        assert_ne!(r1.password, r2.password);
    }

    #[test]
    fn test_evaluate_password_weak() {
        let result = evaluate_password("abc".to_string());
        assert_eq!(result.strength_label, "Muito Fraca");
    }

    #[test]
    fn test_evaluate_password_strong() {
        let result = evaluate_password("aB3$xK9#mN2@pQ8".to_string());
        assert_eq!(result.strength_label, "Forte");
    }
}
