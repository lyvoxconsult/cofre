use tauri::State;

use crate::config::app_config::ConfigState;
use crate::storage::models::AppConfigData;

/// Retorna as configurações atuais do aplicativo.
#[tauri::command]
pub fn get_settings(config: State<'_, ConfigState>) -> Result<AppConfigData, String> {
    config.read(|c| AppConfigData {
        auto_lock_minutes: c.auto_lock_minutes,
        clipboard_clear_seconds: c.clipboard_clear_seconds,
        theme: c.theme.clone(),
        privacy_mode: c.privacy_mode,
        read_only_mode: c.read_only_mode,
        password_gen_defaults: crate::storage::models::PasswordGenConfig {
            length: c.password_gen_length,
            use_uppercase: c.password_gen_uppercase,
            use_lowercase: c.password_gen_lowercase,
            use_numbers: c.password_gen_numbers,
            use_special: c.password_gen_special,
            special_chars: c.password_gen_special_chars.clone(),
            exclude_ambiguous: c.password_gen_exclude_ambiguous,
        },
    })
}

/// Atualiza as configurações do aplicativo.
#[tauri::command]
pub fn update_settings(
    auto_lock_minutes: u32,
    clipboard_clear_seconds: u32,
    theme: String,
    privacy_mode: bool,
    read_only_mode: bool,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    config.with_config(|c| {
        c.auto_lock_minutes = if auto_lock_minutes == 0 {
            0
        } else {
            auto_lock_minutes.clamp(1, 60)
        };
        c.clipboard_clear_seconds = clipboard_clear_seconds.clamp(5, 120);
        c.theme = if theme == "light" || theme == "dark" || theme == "system" {
            theme
        } else {
            "dark".to_string()
        };
        c.privacy_mode = privacy_mode;
        c.read_only_mode = read_only_mode;
        Ok(())
    })
}

/// Atualiza as configurações padrão do gerador de senhas.
#[tauri::command]
pub fn update_password_gen_defaults(
    length: u8,
    use_uppercase: bool,
    use_lowercase: bool,
    use_numbers: bool,
    use_special: bool,
    special_chars: String,
    exclude_ambiguous: bool,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    config.with_config(|c| {
        c.password_gen_length = length.clamp(4, 128);
        c.password_gen_uppercase = use_uppercase;
        c.password_gen_lowercase = use_lowercase;
        c.password_gen_numbers = use_numbers;
        c.password_gen_special = use_special;
        c.password_gen_special_chars = if special_chars.is_empty() {
            "!@#$%^&*()-_=+[]{}|;:,.<>?".to_string()
        } else {
            special_chars
        };
        c.password_gen_exclude_ambiguous = exclude_ambiguous;
        Ok(())
    })
}
