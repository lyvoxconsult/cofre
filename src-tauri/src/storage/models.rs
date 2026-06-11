use serde::{Deserialize, Serialize};

/// Representa uma entrada no cofre de senhas.
/// Campos sensíveis (password, notes) são armazenados criptografados.
/// url não é sensível — armazenado em texto puro.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultEntry {
    pub id: i64,
    pub service_name: String,
    pub login: String,
    pub encrypted_password: String, // base64
    pub password_nonce: String,     // base64
    pub encrypted_notes: String,    // base64
    pub notes_nonce: Option<String>, // base64
    pub url: String,
    pub category_id: Option<i64>,
    pub created_at: String,
    pub updated_at: String,
}

/// Versão descriptografada do VaultEntry para enviar ao frontend.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultEntryDecrypted {
    pub id: i64,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: String,
    pub url: String,
    pub category_id: Option<i64>,
    pub category_name: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

/// Payload para criar uma nova entrada no cofre.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateEntryPayload {
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: Option<String>,
    pub url: String,
    pub category_id: Option<i64>,
}

/// Payload para atualizar uma entrada existente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateEntryPayload {
    pub id: i64,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: Option<String>,
    pub url: String,
    pub category_id: Option<i64>,
}

/// Representa uma categoria para organização de entradas.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: i64,
    pub name: String,
    pub color: String,
    pub icon: String,
    pub sort_order: i32,
}

/// Representa uma nota segura no bloco de notas criptografado.
/// O conteúdo é armazenado criptografado; título e categoria são texto puro.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecureNote {
    pub id: i64,
    pub title: String,
    pub encrypted_content: String, // base64
    pub content_nonce: String,     // base64
    pub category: String,
    pub created_at: String,
    pub updated_at: String,
}

/// Versão descriptografada do SecureNote para enviar ao frontend.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecureNoteDecrypted {
    pub id: i64,
    pub title: String,
    pub content: String,
    pub category: String,
    pub created_at: String,
    pub updated_at: String,
}

/// Payload para criar uma nova nota.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateNotePayload {
    pub title: String,
    pub content: String,
    pub category: String,
}

/// Payload para atualizar uma nota existente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateNotePayload {
    pub id: i64,
    pub title: String,
    pub content: String,
    pub category: String,
}

/// Configuração para geração de senhas.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PasswordGenConfig {
    pub length: u8,
    pub use_uppercase: bool,
    pub use_lowercase: bool,
    pub use_numbers: bool,
    pub use_special: bool,
    pub special_chars: String,
    pub exclude_ambiguous: bool,
}

impl Default for PasswordGenConfig {
    fn default() -> Self {
        Self {
            length: 24,
            use_uppercase: true,
            use_lowercase: true,
            use_numbers: true,
            use_special: true,
            special_chars: "!@#$%^&*()-_=+[]{}|;:,.<>?".to_string(),
            exclude_ambiguous: false,
        }
    }
}

/// Resultado da geração de senha.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PasswordGenResult {
    pub password: String,
    pub entropy_bits: f64,
    pub strength_label: String,
}

/// Configurações do aplicativo.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfigData {
    pub auto_lock_minutes: u32,
    pub clipboard_clear_seconds: u32,
    pub theme: String,
    pub password_gen_defaults: PasswordGenConfig,
}

impl Default for AppConfigData {
    fn default() -> Self {
        Self {
            auto_lock_minutes: 5,
            clipboard_clear_seconds: 30,
            theme: "dark".to_string(),
            password_gen_defaults: PasswordGenConfig::default(),
        }
    }
}
