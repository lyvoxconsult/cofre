use std::path::PathBuf;
use tauri::State;

use crate::config::app_config::ConfigState;
use crate::crypto::key_derivation;
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;

/// Retorna true se é a primeira execução (nenhum salt configurado).
#[tauri::command]
pub fn is_first_run(config: State<'_, ConfigState>) -> Result<bool, String> {
    let salt = config.read(|c| c.salt.clone())?;
    Ok(salt.is_none())
}

/// Desbloqueia o cofre com a senha mestra.
/// Deriva a chave via Argon2id e abre o banco de dados SQLite.
#[tauri::command]
pub fn unlock_vault(
    password: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    // Rate limiting: 3 tentativas, 30s de espera
    let _attempt_count = {
        session.with_session(|s| {
            if s.failed_attempts >= 3 {
                match s.last_activity {
                    Some(last) if last.elapsed().as_secs() < 30 => {
                        return Err("Muitas tentativas. Aguarde 30 segundos.".to_string());
                    }
                    _ => {
                        s.failed_attempts = 0;
                    }
                }
            }
            Ok(s.failed_attempts)
        })?
    };

    // Lê o salt da configuração
    let salt_hex = config.read(|c| c.salt.clone())?;

    // Se não há salt, é a primeira execução: cria um novo salt
    let salt = match salt_hex {
        Some(hex) => {
            let salt_bytes =
                hex::decode(&hex).map_err(|_| "Salt inválido na configuração.".to_string())?;
            if salt_bytes.len() != 16 {
                return Err("Salt com tamanho inválido.".to_string());
            }
            let mut arr = [0u8; 16];
            arr.copy_from_slice(&salt_bytes);
            arr
        }
        None => {
            // Primeira execução: gera salt, deriva chave, configura banco
            let new_salt = key_derivation::generate_salt();
            let hex_salt = hex::encode(new_salt);

            let key = key_derivation::derive_key(&password, &new_salt)?;

            // Salva o salt na configuração
            config.with_config(|c| {
                c.salt = Some(hex_salt);
                Ok(())
            })?;

            // Abre o banco de dados
            let db_path = get_db_path()?;
            db.open(&db_path)?;

            // Desbloqueia a sessão
            session.with_session(|s| {
                s.unlock(key);
                Ok(())
            })?;

            return Ok(());
        }
    };

    // Deriva a chave da senha mestra
    let key = key_derivation::derive_key(&password, &salt)?;

    // Abre o banco de dados
    let db_path = get_db_path()?;
    db.open(&db_path)?;

    // Desbloqueia a sessão
    session.with_session(|s| {
        s.unlock(key);
        Ok(())
    })?;

    Ok(())
}

/// Bloqueia o cofre: zeroiza a chave e fecha o banco.
#[tauri::command]
pub fn lock_vault(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<(), String> {
    session.with_session(|s| {
        s.lock();
        Ok(())
    })?;
    db.close()?;
    Ok(())
}

/// Verifica se o cofre está desbloqueado.
#[tauri::command]
pub fn is_unlocked(session: State<'_, SessionState>) -> Result<bool, String> {
    session.with_session(|s| Ok(s.is_unlocked()))
}

/// Verifica e processa auto-lock se tempo de inatividade excedeu.
#[tauri::command]
pub fn check_auto_lock(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<bool, String> {
    let timeout = config.read(|c| c.auto_lock_minutes)?;
    // Se timeout for 0, auto-lock está desabilitado ("Nunca")
    if timeout == 0 {
        // Ainda registra atividade para manter o timer consistente
        let _ = session.with_session(|s| {
            s.record_activity();
            Ok::<_, String>(())
        });
        return Ok(false);
    }
    let should_lock = session.with_session(|s| {
        if s.is_idle_expired(timeout) {
            s.lock();
            Ok(true)
        } else {
            s.record_activity();
            Ok(false)
        }
    })?;

    if should_lock {
        db.close()?;
    }

    Ok(should_lock)
}

/// Registra atividade do usuário (para reset do timer de auto-lock).
#[tauri::command]
pub fn record_activity(session: State<'_, SessionState>) -> Result<(), String> {
    session.with_session(|s| {
        s.record_activity();
        Ok(())
    })
}

/// Verifica se a senha fornecida corresponde à senha mestra atualmente ativa.
#[tauri::command]
pub fn verify_master_password(
    password: String,
    session: State<'_, SessionState>,
    config: State<'_, ConfigState>,
) -> Result<bool, String> {
    let salt_hex = config.read(|c| c.salt.clone())?;
    let salt_hex = match salt_hex {
        Some(hex) => hex,
        None => return Ok(false),
    };
    let salt_bytes =
        hex::decode(&salt_hex).map_err(|_| "Salt inválido na configuração.".to_string())?;
    let mut salt = [0u8; 16];
    salt.copy_from_slice(&salt_bytes);

    let key = key_derivation::derive_key(&password, &salt)?;

    let matches = session.with_session(|s| match s.get_key() {
        Some(session_key) => Ok(**session_key == *key),
        None => Err("Cofre bloqueado. Desbloqueie primeiro.".to_string()),
    })?;

    Ok(matches)
}

/// Retorna o caminho do banco de dados no diretório de dados do app.
fn get_db_path() -> Result<PathBuf, String> {
    let data_dir = dirs_next::data_dir()
        .ok_or_else(|| "Não foi possível determinar o diretório de dados.".to_string())?
        .join("lyvox-vault");

    Ok(data_dir.join("vault.db"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_db_path_returns_valid_path() {
        let path = get_db_path().unwrap();
        assert!(path.to_string_lossy().contains("lyvox-vault"));
        assert!(path.ends_with("vault.db"));
    }
}
