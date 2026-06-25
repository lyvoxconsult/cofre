use serde::{Deserialize, Serialize};

/// Representa uma entrada no cofre de senhas.
/// Campos sensíveis (password, notes) são armazenados criptografados.
/// url não é sensível — armazenado em texto puro.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultEntry {
    pub id: String,
    pub service_name: String,
    pub login: String,
    pub encrypted_password: String, // base64
    pub password_nonce: String,     // base64
    pub encrypted_notes: String,    // base64
    pub notes_nonce: Option<String>, // base64
    pub url: String,
    pub category_id: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub device_id: Option<String>,
    pub last_modified_device_id: Option<String>,
}

/// Versão descriptografada do VaultEntry para enviar ao frontend.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultEntryDecrypted {
    pub id: String,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: String,
    pub url: String,
    pub category_id: Option<String>,
    pub category_name: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

/// Payload para criar uma nova entrada no cofre.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateEntryPayload {
    pub id: Option<String>,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: Option<String>,
    pub url: String,
    pub category_id: Option<String>,
    pub is_favorite: Option<bool>,
}

/// Payload para atualizar uma entrada existente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateEntryPayload {
    pub id: String,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: Option<String>,
    pub url: String,
    pub category_id: Option<String>,
    pub is_favorite: Option<bool>,
}

/// Representa uma categoria para organização de entradas.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: String,
    pub name: String,
    pub color: String,
    pub icon: String,
    pub sort_order: i32,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

/// Representa uma nota segura no bloco de notas criptografado.
/// O conteúdo é armazenado criptografado; título e categoria são texto puro.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecureNote {
    pub id: String,
    pub title: String,
    pub encrypted_content: String, // base64
    pub content_nonce: String,     // base64
    pub category: String,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub device_id: Option<String>,
    pub last_modified_device_id: Option<String>,
}

/// Versão descriptografada do SecureNote para enviar ao frontend.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecureNoteDecrypted {
    pub id: String,
    pub title: String,
    pub content: String,
    pub category: String,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

/// Payload para criar uma nova nota.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateNotePayload {
    pub id: Option<String>,
    pub title: String,
    pub content: String,
    pub category: String,
    pub is_favorite: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Attachment {
    pub id: String,
    pub entry_id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub encrypted_key: String,
    pub key_nonce: String,
    pub file_nonce: String,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub device_id: Option<String>,
    pub last_modified_device_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaItem {
    pub id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub encrypted_key: String,
    pub key_nonce: String,
    pub file_nonce: String,
    pub thumbnail_data: Option<String>,
    pub thumbnail_nonce: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub device_id: Option<String>,
    pub last_modified_device_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaItemDecrypted {
    pub id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub thumbnail_data: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AttachmentDecrypted {
    pub id: String,
    pub entry_id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub created_at: String,
    pub updated_at: String,
}

/// Payload para atualizar uma nota existente.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateNotePayload {
    pub id: String,
    pub title: String,
    pub content: String,
    pub category: String,
    pub is_favorite: Option<bool>,
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
    pub privacy_mode: bool,
    pub read_only_mode: bool,
    pub password_gen_defaults: PasswordGenConfig,
}

impl Default for AppConfigData {
    fn default() -> Self {
        Self {
            auto_lock_minutes: 5,
            clipboard_clear_seconds: 30,
            theme: "dark".to_string(),
            privacy_mode: false,
            read_only_mode: false,
            password_gen_defaults: PasswordGenConfig::default(),
        }
    }
}
