use tauri::State;
use zeroize::Zeroizing;

use crate::config::app_config::ConfigState;
use crate::crypto::cipher;
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models::SecureNoteDecrypted;

/// Extrai uma cópia da chave de criptografia da sessão.
fn get_session_key(session: &SessionState) -> Result<Zeroizing<[u8; 32]>, String> {
    session.with_session(|s| {
        s.record_activity();
        match s.get_key() {
            Some(key) => Ok(key.clone()),
            None => Err("Cofre bloqueado. Desbloqueie primeiro.".to_string()),
        }
    })
}

/// Descriptografa uma nota individual.
fn decrypt_note(
    key: &Zeroizing<[u8; 32]>,
    note: crate::storage::models::SecureNote,
) -> Result<SecureNoteDecrypted, String> {
    let content = if note.encrypted_content.is_empty() || note.content_nonce.is_empty() {
        String::new()
    } else {
        cipher::decrypt_field(key, &note.encrypted_content, &note.content_nonce)?
    };

    Ok(SecureNoteDecrypted {
        id: note.id,
        title: note.title,
        content,
        category: note.category,
        is_favorite: note.is_favorite,
        created_at: note.created_at,
        updated_at: note.updated_at,
        deleted_at: note.deleted_at,
    })
}

// ─── Comandos Tauri ─────────────────────────────────────────

#[tauri::command]
pub fn create_note(
    title: String,
    content: String,
    category: String,
    is_favorite: Option<bool>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<String, String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let key = get_session_key(&session)?;

    let (enc_content_b64, content_nonce_b64) = if content.is_empty() {
        (String::new(), String::new())
    } else {
        cipher::encrypt_field(&key, &content)?
    };

    let payload = crate::storage::models::CreateNotePayload {
        id: None,
        title,
        content: String::new(),
        category,
        is_favorite,
    };

    db.with_db(|database| {
        let id = database
            .create_note(&payload, &enc_content_b64, &content_nonce_b64)
            .map_err(|e| format!("Erro ao salvar nota: {e}"))?;
        Ok(id)
    })
}

#[tauri::command]
pub fn list_notes(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<SecureNoteDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let notes = database
            .list_notes()
            .map_err(|e| format!("Erro ao listar notas: {e}"))?;

        notes
            .into_iter()
            .map(|note| decrypt_note(&key, note))
            .collect::<Result<Vec<_>, String>>()
    })
}

#[tauri::command]
pub fn search_notes(
    query: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<SecureNoteDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let notes = database
            .search_notes(&query)
            .map_err(|e| format!("Erro na busca de notas: {e}"))?;

        notes
            .into_iter()
            .map(|note| decrypt_note(&key, note))
            .collect::<Result<Vec<_>, String>>()
    })
}

#[tauri::command]
pub fn get_note(
    id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Option<SecureNoteDecrypted>, String> {
    let key = get_session_key(&session)?;

    db.with_db(|database| {
        let note = database
            .get_note(&id)
            .map_err(|e| format!("Erro ao obter nota: {e}"))?;

        match note {
            Some(n) => decrypt_note(&key, n).map(Some),
            None => Ok(None),
        }
    })
}

#[tauri::command]
pub fn update_note(
    id: String,
    title: String,
    content: String,
    category: String,
    is_favorite: Option<bool>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let key = get_session_key(&session)?;

    let (enc_content_b64, content_nonce_b64) = if content.is_empty() {
        (String::new(), String::new())
    } else {
        cipher::encrypt_field(&key, &content)?
    };

    let payload = crate::storage::models::UpdateNotePayload {
        id,
        title,
        content: String::new(),
        category,
        is_favorite,
    };

    db.with_db(|database| {
        database
            .update_note(&payload, &enc_content_b64, &content_nonce_b64)
            .map_err(|e| format!("Erro ao atualizar nota: {e}"))
    })
}

#[tauri::command]
pub fn delete_note(
    id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let _key = get_session_key(&session)?;

    db.with_db(|database| {
        database
            .delete_note(&id)
            .map_err(|e| format!("Erro ao excluir nota: {e}"))
    })
}
