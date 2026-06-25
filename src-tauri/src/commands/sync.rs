use tauri::State;
use zeroize::Zeroizing;

use crate::crypto::{cipher, key_derivation};
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models;

fn validate_storage_id(id: &str) -> Result<(), String> {
    uuid::Uuid::parse_str(id)
        .map(|_| ())
        .map_err(|_| "Arquivo de sincronizaÃ§Ã£o contÃ©m ID de arquivo invÃ¡lido.".to_string())
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncCategory {
    pub id: String,
    pub name: String,
    pub color: String,
    pub icon: String,
    pub sort_order: i32,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncEntry {
    pub id: String,
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: String,
    pub url: String,
    pub category_id: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncNote {
    pub id: String,
    pub title: String,
    pub content: String,
    pub category: String,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncAttachment {
    pub id: String,
    pub entry_id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub encrypted_key: String, // base64
    pub key_nonce: String,     // base64
    pub file_nonce: String,    // base64
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub encrypted_file_bytes_base64: Option<String>,
    pub raw_file_key_base64: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncMediaItem {
    pub id: String,
    pub filename: String,
    pub mime_type: String,
    pub file_size: i64,
    pub encrypted_key: String, // base64
    pub key_nonce: String,     // base64
    pub file_nonce: String,    // base64
    pub thumbnail_data: Option<String>,
    pub thumbnail_nonce: Option<String>,
    pub is_favorite: bool,
    pub created_at: String,
    pub updated_at: String,
    pub deleted_at: Option<String>,
    pub device_id: Option<String>,
    pub last_modified_device_id: Option<String>,
    pub encrypted_file_bytes_base64: Option<String>,
    pub raw_file_key_base64: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncPayload {
    pub app: String,
    pub version: String,
    pub created_at: String,
    pub categories: Vec<SyncCategory>,
    pub entries: Vec<SyncEntry>,
    pub notes: Vec<SyncNote>,
    pub attachments: Vec<SyncAttachment>,
    pub media: Vec<SyncMediaItem>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SyncEnvelope {
    pub app: String,
    pub version: String,
    pub created_at: String,
    pub encrypted_data: String,
    pub nonce: String,
    pub salt: String,
}

fn get_session_key(session: &SessionState) -> Result<Zeroizing<[u8; 32]>, String> {
    session.with_session(|s| {
        s.record_activity();
        match s.get_key() {
            Some(key) => Ok(key.clone()),
            None => Err("Cofre bloqueado. Desbloqueie primeiro.".to_string()),
        }
    })
}

fn decrypt_entry_for_sync(
    key: &Zeroizing<[u8; 32]>,
    entry: models::VaultEntry,
) -> Result<SyncEntry, String> {
    let password = if entry.encrypted_password.is_empty() {
        String::new()
    } else {
        cipher::decrypt_field(key, &entry.encrypted_password, &entry.password_nonce)?
    };

    let notes = if entry.encrypted_notes.is_empty() || entry.notes_nonce.is_none() {
        String::new()
    } else {
        match cipher::decrypt_field(
            key,
            &entry.encrypted_notes,
            entry.notes_nonce.as_ref().unwrap(),
        ) {
            Ok(n) => n,
            Err(_) => String::new(),
        }
    };

    Ok(SyncEntry {
        id: entry.id,
        service_name: entry.service_name,
        login: entry.login,
        password,
        notes,
        url: entry.url,
        category_id: entry.category_id,
        is_favorite: entry.is_favorite,
        created_at: entry.created_at,
        updated_at: entry.updated_at,
        deleted_at: entry.deleted_at,
    })
}

fn decrypt_note_for_sync(
    key: &Zeroizing<[u8; 32]>,
    note: models::SecureNote,
) -> Result<SyncNote, String> {
    let content = if note.encrypted_content.is_empty() || note.content_nonce.is_empty() {
        String::new()
    } else {
        cipher::decrypt_field(key, &note.encrypted_content, &note.content_nonce)?
    };

    Ok(SyncNote {
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

#[tauri::command]
pub fn export_sync_package(
    sync_password: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    if sync_password.len() < 8 {
        return Err("A senha de sincronização deve ter no mínimo 8 caracteres.".to_string());
    }

    let key = get_session_key(&session)?;

    // Coleta todas as categorias, entradas e notas (incluindo as deletadas)
    let categories = db.with_db(|database| {
        database
            .list_all_categories_for_sync()
            .map_err(|e| format!("Erro ao listar categorias: {e}"))
    })?;

    let entries = db.with_db(|database| {
        database
            .list_all_entries_for_sync()
            .map_err(|e| format!("Erro ao listar entradas: {e}"))
    })?;

    let notes = db.with_db(|database| {
        database
            .list_all_notes_for_sync()
            .map_err(|e| format!("Erro ao listar notas: {e}"))
    })?;

    // Descriptografa entradas e notas
    let mut sync_categories = Vec::new();
    for cat in categories {
        sync_categories.push(SyncCategory {
            id: cat.id,
            name: cat.name,
            color: cat.color,
            icon: cat.icon,
            sort_order: cat.sort_order,
            created_at: cat.created_at,
            updated_at: cat.updated_at,
            deleted_at: cat.deleted_at,
        });
    }

    let mut sync_entries = Vec::new();
    for entry in entries {
        sync_entries.push(decrypt_entry_for_sync(&key, entry)?);
    }

    let mut sync_notes = Vec::new();
    for note in notes {
        sync_notes.push(decrypt_note_for_sync(&key, note)?);
    }

    let attachments = db.with_db(|database| {
        database
            .list_all_attachments_for_sync()
            .map_err(|e| format!("Erro ao listar anexos: {e}"))
    })?;

    let attachments_dir = dirs_next::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("lyvox-vault")
        .join("attachments");

    let b64 = &base64::engine::general_purpose::STANDARD;
    let mut sync_attachments = Vec::new();
    for att in attachments {
        let mut file_bytes_b64 = None;
        let mut raw_key_b64 = None;

        if att.deleted_at.is_none() {
            let file_path = attachments_dir.join(&att.id);
            if file_path.exists() {
                if let Ok(bytes) = std::fs::read(&file_path) {
                    file_bytes_b64 = Some(base64::Engine::encode(b64, &bytes));
                }
            }

            if let (Ok(enc_key_bytes), Ok(nonce_bytes)) = (
                base64::Engine::decode(b64, &att.encrypted_key),
                base64::Engine::decode(b64, &att.key_nonce),
            ) {
                if nonce_bytes.len() == cipher::NONCE_LENGTH {
                    let mut nonce = [0u8; cipher::NONCE_LENGTH];
                    nonce.copy_from_slice(&nonce_bytes);
                    if let Ok(raw_key) = cipher::decrypt(&key, &enc_key_bytes, &nonce) {
                        raw_key_b64 = Some(base64::Engine::encode(b64, &raw_key));
                    }
                }
            }
        }
        sync_attachments.push(SyncAttachment {
            id: att.id,
            entry_id: att.entry_id,
            filename: att.filename,
            mime_type: att.mime_type,
            file_size: att.file_size,
            encrypted_key: att.encrypted_key,
            key_nonce: att.key_nonce,
            file_nonce: att.file_nonce,
            created_at: att.created_at,
            updated_at: att.updated_at,
            deleted_at: att.deleted_at,
            encrypted_file_bytes_base64: file_bytes_b64,
            raw_file_key_base64: raw_key_b64,
        });
    }

    let media = db.with_db(|database| {
        database
            .list_all_media_for_sync()
            .map_err(|e| format!("Erro ao listar mídias: {e}"))
    })?;

    let media_dir = dirs_next::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("lyvox-vault")
        .join("media_vault");

    let mut sync_media = Vec::new();
    for m in media {
        let mut file_bytes_b64 = None;
        let mut raw_key_b64 = None;

        if m.deleted_at.is_none() {
            let file_path = media_dir.join(&m.id);
            if file_path.exists() {
                if let Ok(bytes) = std::fs::read(&file_path) {
                    file_bytes_b64 = Some(base64::Engine::encode(b64, &bytes));
                }
            }

            if let (Ok(enc_key_bytes), Ok(nonce_bytes)) = (
                base64::Engine::decode(b64, &m.encrypted_key),
                base64::Engine::decode(b64, &m.key_nonce),
            ) {
                if nonce_bytes.len() == cipher::NONCE_LENGTH {
                    let mut nonce = [0u8; cipher::NONCE_LENGTH];
                    nonce.copy_from_slice(&nonce_bytes);
                    if let Ok(raw_key) = cipher::decrypt(&key, &enc_key_bytes, &nonce) {
                        raw_key_b64 = Some(base64::Engine::encode(b64, &raw_key));
                    }
                }
            }
        }
        sync_media.push(SyncMediaItem {
            id: m.id,
            filename: m.filename,
            mime_type: m.mime_type,
            file_size: m.file_size,
            encrypted_key: m.encrypted_key,
            key_nonce: m.key_nonce,
            file_nonce: m.file_nonce,
            thumbnail_data: m.thumbnail_data,
            thumbnail_nonce: m.thumbnail_nonce,
            is_favorite: m.is_favorite,
            created_at: m.created_at,
            updated_at: m.updated_at,
            deleted_at: m.deleted_at,
            device_id: m.device_id,
            last_modified_device_id: m.last_modified_device_id,
            encrypted_file_bytes_base64: file_bytes_b64,
            raw_file_key_base64: raw_key_b64,
        });
    }

    // Monta o payload
    let payload = SyncPayload {
        app: "lyvox-vault".to_string(),
        version: "1".to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        categories: sync_categories,
        entries: sync_entries,
        notes: sync_notes,
        attachments: sync_attachments,
        media: sync_media,
    };

    // Serializa e criptografa
    let json_data =
        serde_json::to_string(&payload).map_err(|e| format!("Erro ao serializar sync: {e}"))?;

    let salt = key_derivation::generate_salt();
    let sync_key = key_derivation::derive_key(&sync_password, &salt)?;

    let (encrypted_bytes, nonce) = cipher::encrypt(&sync_key, json_data.as_bytes())?;

    use base64::Engine;
    let encrypted_b64 = base64::engine::general_purpose::STANDARD.encode(&encrypted_bytes);
    let nonce_b64 = base64::engine::general_purpose::STANDARD.encode(&nonce);
    let salt_hex = hex::encode(salt);

    let envelope = SyncEnvelope {
        app: "lyvox-vault".to_string(),
        version: "1".to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        encrypted_data: encrypted_b64,
        nonce: nonce_b64,
        salt: salt_hex,
    };

    serde_json::to_string_pretty(&envelope)
        .map_err(|e| format!("Erro ao serializar envelope de sync: {e}"))
}

#[tauri::command]
pub fn import_sync_package(
    sync_data: String,
    sync_password: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    if sync_password.len() < 8 {
        return Err("A senha de sincronização deve ter no mínimo 8 caracteres.".to_string());
    }

    let key = get_session_key(&session)?;

    // Parseia envelope
    let envelope: SyncEnvelope =
        serde_json::from_str(&sync_data).map_err(|e| format!("Arquivo de sync inválido: {e}"))?;

    if envelope.app != "lyvox-vault" {
        return Err("Arquivo de sincronização não reconhecido.".to_string());
    }

    // Decodifica base64
    use base64::Engine;
    let encrypted_bytes = base64::engine::general_purpose::STANDARD
        .decode(&envelope.encrypted_data)
        .map_err(|_| "Arquivo corrompido (base64 inválido).".to_string())?;

    let nonce_bytes = base64::engine::general_purpose::STANDARD
        .decode(&envelope.nonce)
        .map_err(|_| "Nonce inválido.".to_string())?;

    if nonce_bytes.len() != cipher::NONCE_LENGTH {
        return Err("Nonce com tamanho inválido.".to_string());
    }

    let mut nonce_arr = [0u8; cipher::NONCE_LENGTH];
    nonce_arr.copy_from_slice(&nonce_bytes);

    let salt_bytes = hex::decode(&envelope.salt).map_err(|_| "Salt inválido.".to_string())?;

    if salt_bytes.len() != 16 {
        return Err("Salt com tamanho inválido.".to_string());
    }

    // Deriva chave
    let sync_key = key_derivation::derive_key(&sync_password, &salt_bytes)?;

    // Descriptografa
    let decrypted_bytes = cipher::decrypt(&sync_key, &encrypted_bytes, &nonce_arr)?;
    let json_str =
        String::from_utf8(decrypted_bytes).map_err(|_| "Dados de sync corrompidos.".to_string())?;

    let payload: SyncPayload = serde_json::from_str(&json_str)
        .map_err(|e| format!("Erro no parse do payload de sync: {e}"))?;

    let mut cat_added = 0;
    let mut cat_updated = 0;
    let mut entry_added = 0;
    let mut entry_updated = 0;
    let mut entry_ignored = 0;
    let mut note_added = 0;
    let mut note_updated = 0;
    let mut note_ignored = 0;

    // 1. Merge de Categorias
    for import_cat in payload.categories {
        let local_cat = db.with_db(|database| {
            database
                .get_category_include_deleted(&import_cat.id)
                .map_err(|e| format!("Erro ao buscar categoria: {e}"))
        })?;

        let cat_model = models::Category {
            id: import_cat.id.clone(),
            name: import_cat.name.clone(),
            color: import_cat.color.clone(),
            icon: import_cat.icon.clone(),
            sort_order: import_cat.sort_order,
            created_at: import_cat.created_at.clone(),
            updated_at: import_cat.updated_at.clone(),
            deleted_at: import_cat.deleted_at.clone(),
        };

        match local_cat {
            Some(local) => {
                // Last write wins baseado no updated_at
                if import_cat.updated_at > local.updated_at {
                    db.with_db(|database| {
                        database
                            .update_category_sync(&cat_model)
                            .map_err(|e| format!("Erro ao atualizar categoria: {e}"))
                    })?;
                    cat_updated += 1;
                }
            }
            None => {
                db.with_db(|database| {
                    database
                        .insert_category_sync(&cat_model)
                        .map_err(|e| format!("Erro ao inserir categoria: {e}"))
                })?;
                cat_added += 1;
            }
        }
    }

    // 2. Merge de Entradas
    for import_entry in payload.entries {
        let local_entry = db.with_db(|database| {
            database
                .get_entry_include_deleted(&import_entry.id)
                .map_err(|e| format!("Erro ao buscar entrada: {e}"))
        })?;

        // Criptografa dados sensíveis com a chave local
        let (enc_pwd, pwd_nonce) = cipher::encrypt_field(&key, &import_entry.password)?;
        let (enc_notes, notes_nonce) = if import_entry.notes.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&key, &import_entry.notes)?
        };

        let notes_nonce_opt = if notes_nonce.is_empty() {
            None
        } else {
            Some(notes_nonce)
        };

        let entry_model = models::VaultEntry {
            id: import_entry.id.clone(),
            service_name: import_entry.service_name.clone(),
            login: import_entry.login.clone(),
            encrypted_password: enc_pwd,
            password_nonce: pwd_nonce,
            encrypted_notes: enc_notes,
            notes_nonce: notes_nonce_opt,
            url: import_entry.url.clone(),
            category_id: import_entry.category_id.clone(),
            is_favorite: import_entry.is_favorite,
            created_at: import_entry.created_at.clone(),
            updated_at: import_entry.updated_at.clone(),
            deleted_at: import_entry.deleted_at.clone(),
            device_id: None,
            last_modified_device_id: None,
        };

        match local_entry {
            Some(local) => {
                if import_entry.updated_at > local.updated_at {
                    db.with_db(|database| {
                        database
                            .update_entry_sync(&entry_model)
                            .map_err(|e| format!("Erro ao atualizar entrada: {e}"))
                    })?;
                    entry_updated += 1;
                } else {
                    entry_ignored += 1;
                }
            }
            None => {
                db.with_db(|database| {
                    database
                        .insert_entry_sync(&entry_model)
                        .map_err(|e| format!("Erro ao inserir entrada: {e}"))
                })?;
                entry_added += 1;
            }
        }
    }

    // 3. Merge de Notas
    for import_note in payload.notes {
        let local_note = db.with_db(|database| {
            database
                .get_note_include_deleted(&import_note.id)
                .map_err(|e| format!("Erro ao buscar nota: {e}"))
        })?;

        let (enc_content, content_nonce) = if import_note.content.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&key, &import_note.content)?
        };

        let note_model = models::SecureNote {
            id: import_note.id.clone(),
            title: import_note.title.clone(),
            encrypted_content: enc_content,
            content_nonce,
            category: import_note.category.clone(),
            is_favorite: import_note.is_favorite,
            created_at: import_note.created_at.clone(),
            updated_at: import_note.updated_at.clone(),
            deleted_at: import_note.deleted_at.clone(),
            device_id: None,
            last_modified_device_id: None,
        };

        match local_note {
            Some(local) => {
                if import_note.updated_at > local.updated_at {
                    db.with_db(|database| {
                        database
                            .update_note_sync(&note_model)
                            .map_err(|e| format!("Erro ao atualizar nota: {e}"))
                    })?;
                    note_updated += 1;
                } else {
                    note_ignored += 1;
                }
            }
            None => {
                db.with_db(|database| {
                    database
                        .insert_note_sync(&note_model)
                        .map_err(|e| format!("Erro ao inserir nota: {e}"))
                })?;
                note_added += 1;
            }
        }
    }

    let mut att_added = 0;
    let mut att_updated = 0;
    let mut att_ignored = 0;

    let attachments_dir = dirs_next::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("lyvox-vault")
        .join("attachments");

    if !attachments_dir.exists() {
        let _ = std::fs::create_dir_all(&attachments_dir);
    }

    // 4. Merge de Anexos
    for import_att in payload.attachments {
        validate_storage_id(&import_att.id)?;
        let local_att = db.with_db(|database| {
            database
                .get_attachment_include_deleted(&import_att.id)
                .map_err(|e| format!("Erro ao buscar anexo: {e}"))
        })?;

        let mut new_enc_key = import_att.encrypted_key.clone();
        let mut new_key_nonce = import_att.key_nonce.clone();

        if let Some(raw_key_b64) = import_att.raw_file_key_base64.as_ref() {
            if let Ok(raw_key_bytes) = base64::engine::general_purpose::STANDARD.decode(raw_key_b64) {
                if let Ok((enc_key_bytes, nonce)) = cipher::encrypt(&key, &raw_key_bytes) {
                    new_enc_key = base64::engine::general_purpose::STANDARD.encode(&enc_key_bytes);
                    new_key_nonce = base64::engine::general_purpose::STANDARD.encode(&nonce);
                }
            }
        }

        let att_model = models::Attachment {
            id: import_att.id.clone(),
            entry_id: import_att.entry_id.clone(),
            filename: import_att.filename.clone(),
            mime_type: import_att.mime_type.clone(),
            file_size: import_att.file_size,
            encrypted_key: new_enc_key,
            key_nonce: new_key_nonce,
            file_nonce: import_att.file_nonce.clone(),
            created_at: import_att.created_at.clone(),
            updated_at: import_att.updated_at.clone(),
            deleted_at: import_att.deleted_at.clone(),
            device_id: None,
            last_modified_device_id: None,
        };

        let mut perform_update = false;
        let mut perform_insert = false;

        match local_att {
            Some(local) => {
                if import_att.updated_at > local.updated_at {
                    perform_update = true;
                } else {
                    att_ignored += 1;
                }
            }
            None => {
                perform_insert = true;
            }
        }

        if perform_insert || perform_update {
            if import_att.deleted_at.is_some() {
                let file_path = attachments_dir.join(&import_att.id);
                if file_path.exists() {
                    let _ = std::fs::remove_file(&file_path);
                }
            } else if let Some(file_bytes_b64) = import_att.encrypted_file_bytes_base64 {
                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(&file_bytes_b64)
                {
                    let file_path = attachments_dir.join(&import_att.id);
                    let _ = std::fs::write(&file_path, &bytes);
                }
            }

            if perform_insert {
                db.with_db(|database| {
                    database
                        .insert_attachment_sync(&att_model)
                        .map_err(|e| format!("Erro ao inserir anexo: {e}"))
                })?;
                att_added += 1;
            } else {
                db.with_db(|database| {
                    database
                        .update_attachment_sync(&att_model)
                        .map_err(|e| format!("Erro ao atualizar anexo: {e}"))
                })?;
                att_updated += 1;
            }
        }
    }

    // 5. Merge de Mídia
    let mut media_added = 0;
    let mut media_updated = 0;
    let mut media_ignored = 0;

    let media_dir = dirs_next::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("lyvox-vault")
        .join("media_vault");

    if !media_dir.exists() {
        let _ = std::fs::create_dir_all(&media_dir);
    }

    for import_m in payload.media {
        validate_storage_id(&import_m.id)?;
        let local_m = db.with_db(|database| {
            database
                .get_media_include_deleted(&import_m.id)
                .map_err(|e| format!("Erro ao buscar mídia: {e}"))
        })?;

        let mut new_enc_key = import_m.encrypted_key.clone();
        let mut new_key_nonce = import_m.key_nonce.clone();

        if let Some(raw_key_b64) = import_m.raw_file_key_base64.as_ref() {
            if let Ok(raw_key_bytes) = base64::engine::general_purpose::STANDARD.decode(raw_key_b64) {
                if let Ok((enc_key_bytes, nonce)) = cipher::encrypt(&key, &raw_key_bytes) {
                    new_enc_key = base64::engine::general_purpose::STANDARD.encode(&enc_key_bytes);
                    new_key_nonce = base64::engine::general_purpose::STANDARD.encode(&nonce);
                }
            }
        }

        let media_model = models::MediaItem {
            id: import_m.id.clone(),
            filename: import_m.filename.clone(),
            mime_type: import_m.mime_type.clone(),
            file_size: import_m.file_size,
            encrypted_key: new_enc_key,
            key_nonce: new_key_nonce,
            file_nonce: import_m.file_nonce.clone(),
            thumbnail_data: import_m.thumbnail_data.clone(),
            thumbnail_nonce: import_m.thumbnail_nonce.clone(),
            is_favorite: import_m.is_favorite,
            created_at: import_m.created_at.clone(),
            updated_at: import_m.updated_at.clone(),
            deleted_at: import_m.deleted_at.clone(),
            device_id: import_m.device_id.clone(),
            last_modified_device_id: import_m.last_modified_device_id.clone(),
        };

        let mut perform_update = false;
        let mut perform_insert = false;

        match local_m {
            Some(local) => {
                if import_m.updated_at > local.updated_at {
                    perform_update = true;
                } else {
                    media_ignored += 1;
                }
            }
            None => {
                perform_insert = true;
            }
        }

        if perform_insert || perform_update {
            if import_m.deleted_at.is_some() {
                let file_path = media_dir.join(&import_m.id);
                if file_path.exists() {
                    let _ = std::fs::remove_file(&file_path);
                }
            } else if let Some(file_bytes_b64) = import_m.encrypted_file_bytes_base64 {
                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(&file_bytes_b64)
                {
                    let file_path = media_dir.join(&import_m.id);
                    let _ = std::fs::write(&file_path, &bytes);
                }
            }

            if perform_insert {
                db.with_db(|database| {
                    database
                        .insert_media_sync(&media_model)
                        .map_err(|e| format!("Erro ao inserir mídia: {e}"))
                })?;
                media_added += 1;
            } else {
                db.with_db(|database| {
                    database
                        .update_media_sync(&media_model)
                        .map_err(|e| format!("Erro ao atualizar mídia: {e}"))
                })?;
                media_updated += 1;
            }
        }
    }

    Ok(format!(
        "Sincronização concluída: Categoria ({} add, {} mod), Entrada ({} add, {} mod, {} ignorado), Nota ({} add, {} mod, {} ignorado), Anexo ({} add, {} mod, {} ignorado), Mídia ({} add, {} mod, {} ignorado).",
        cat_added, cat_updated, entry_added, entry_updated, entry_ignored, note_added, note_updated, note_ignored, att_added, att_updated, att_ignored, media_added, media_updated, media_ignored
    ))
}
