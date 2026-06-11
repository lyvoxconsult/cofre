use tauri::State;
use zeroize::Zeroizing;

use crate::crypto::cipher;
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models::{
    Category, VaultEntryDecrypted,
};

/// Extrai uma cópia da chave de criptografia da sessão.
/// Isso é seguro pois [u8; 32] implementa Copy, então clonar a chave
/// produz uma cópia independente que será zeroizada ao sair do escopo.
fn get_session_key(session: &SessionState) -> Result<Zeroizing<[u8; 32]>, String> {
    session.with_session(|s| {
        s.record_activity();
        match s.get_key() {
            Some(key) => Ok(key.clone()),
            None => Err("Cofre bloqueado. Desbloqueie primeiro.".to_string()),
        }
    })
}

/// Descriptografa uma entrada individual.
fn decrypt_entry(
    key: &Zeroizing<[u8; 32]>,
    entry: crate::storage::models::VaultEntry,
    database: &crate::storage::database::Database,
) -> Result<VaultEntryDecrypted, String> {
    let password = if entry.encrypted_password.is_empty() {
        String::new()
    } else {
        cipher::decrypt_field(key, &entry.encrypted_password, &entry.password_nonce)?
    };

    let notes = if entry.encrypted_notes.is_empty() || entry.notes_nonce.is_none() {
        String::new()
    } else {
        match cipher::decrypt_field(key, &entry.encrypted_notes, &entry.notes_nonce.as_ref().unwrap()) {
            Ok(n) => n,
            Err(_) => String::new(),
        }
    };

    let category_name = match entry.category_id {
        Some(cid) => database.get_category_name(cid).unwrap_or(None),
        None => None,
    };

    Ok(VaultEntryDecrypted {
        id: entry.id,
        service_name: entry.service_name,
        login: entry.login,
        password,
        notes,
        url: entry.url,
        category_id: entry.category_id,
        category_name,
        created_at: entry.created_at,
        updated_at: entry.updated_at,
    })
}

// ─── Comandos Tauri ─────────────────────────────────────────

#[tauri::command]
pub fn create_entry(
    service_name: String,
    login: String,
    password: String,
    notes: Option<String>,
    url: String,
    category_id: Option<i64>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<i64, String> {
    let key = get_session_key(&session)?;

    let (enc_pwd_b64, pwd_nonce_b64) =
        cipher::encrypt_field(&key, &password)?;

    let notes_str = notes.unwrap_or_default();
    let (enc_notes_b64, notes_nonce_b64) = if notes_str.is_empty() {
        (String::new(), String::new())
    } else {
        cipher::encrypt_field(&key, &notes_str)?
    };

    let payload = crate::storage::models::CreateEntryPayload {
        service_name,
        login,
        password: String::new(),
        notes: None,
        url,
        category_id,
    };

    let notes_nonce_opt = if notes_nonce_b64.is_empty() {
        None
    } else {
        Some(notes_nonce_b64.as_str())
    };

    db.with_db(|database| {
        let id = database
            .create_entry(
                &payload,
                &enc_pwd_b64,
                &pwd_nonce_b64,
                &enc_notes_b64,
                notes_nonce_opt,
            )
            .map_err(|e| format!("Erro ao salvar entrada: {e}"))?;
        Ok(id)
    })
}

#[tauri::command]
pub fn list_entries(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<VaultEntryDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let entries = database
            .list_entries()
            .map_err(|e| format!("Erro ao listar entradas: {e}"))?;

        entries
            .into_iter()
            .map(|entry| decrypt_entry(&key, entry, database))
            .collect::<Result<Vec<_>, String>>()
    })
}

#[tauri::command]
pub fn search_entries(
    query: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<VaultEntryDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let entries = database
            .search_entries(&query)
            .map_err(|e| format!("Erro na busca: {e}"))?;

        entries
            .into_iter()
            .map(|entry| decrypt_entry(&key, entry, database))
            .collect::<Result<Vec<_>, String>>()
    })
}

#[tauri::command]
pub fn get_entry(
    id: i64,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Option<VaultEntryDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let entry = database
            .get_entry(id)
            .map_err(|e| format!("Erro ao obter entrada: {e}"))?;

        match entry {
            Some(e) => decrypt_entry(&key, e, database).map(Some),
            None => Ok(None),
        }
    })
}

#[tauri::command]
pub fn update_entry(
    id: i64,
    service_name: String,
    login: String,
    password: String,
    notes: Option<String>,
    url: String,
    category_id: Option<i64>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<(), String> {
    let key = get_session_key(&session)?;

    let (enc_pwd_b64, pwd_nonce_b64) =
        cipher::encrypt_field(&key, &password)?;

    let notes_str = notes.unwrap_or_default();
    let (enc_notes_b64, notes_nonce_b64) = if notes_str.is_empty() {
        (String::new(), String::new())
    } else {
        cipher::encrypt_field(&key, &notes_str)?
    };

    let payload = crate::storage::models::UpdateEntryPayload {
        id,
        service_name,
        login,
        password: String::new(),
        notes: None,
        url,
        category_id,
    };

    let notes_nonce_opt = if notes_nonce_b64.is_empty() {
        None
    } else {
        Some(notes_nonce_b64.as_str())
    };

    db.with_db(|database| {
        database
            .update_entry(&payload, &enc_pwd_b64, &pwd_nonce_b64, &enc_notes_b64, notes_nonce_opt)
            .map_err(|e| format!("Erro ao atualizar entrada: {e}"))
    })
}

#[tauri::command]
pub fn delete_entry(
    id: i64,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<(), String> {
    let _key = get_session_key(&session)?;

    db.with_db(|database| {
        database
            .delete_entry(id)
            .map_err(|e| format!("Erro ao excluir entrada: {e}"))
    })
}

#[tauri::command]
pub fn list_categories(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<Category>, String> {
    let _key = get_session_key(&session)?;

    db.with_db(|database| {
        database
            .list_categories()
            .map_err(|e| format!("Erro ao listar categorias: {e}"))
    })
}
