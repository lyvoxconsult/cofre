use base64::Engine;
use std::fs;
use std::path::PathBuf;
use tauri::State;
use zeroize::Zeroizing;

use crate::config::app_config::ConfigState;
use crate::crypto::cipher;
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models::{MediaItem, MediaItemDecrypted};

fn get_media_dir() -> PathBuf {
    dirs_next::data_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("lyvox-vault-next")
        .join("media_vault")
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

#[tauri::command]
pub fn create_media(
    filename: String,
    mime_type: String,
    file_bytes_base64: String,
    thumbnail_base64: Option<String>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<String, String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let session_key = get_session_key(&session)?;

    let file_bytes = base64::engine::general_purpose::STANDARD
        .decode(&file_bytes_base64)
        .map_err(|e| format!("Erro ao decodificar arquivo base64: {e}"))?;

    let file_size = file_bytes.len() as i64;
    let media_id = uuid::Uuid::new_v4().to_string();

    let mut file_key_raw = [0u8; 32];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut file_key_raw);
    let file_key = Zeroizing::new(file_key_raw);

    let (encrypted_file_bytes, file_nonce) = cipher::encrypt(&file_key, &file_bytes)?;
    let (encrypted_key_bytes, key_nonce) = cipher::encrypt(&session_key, file_key.as_ref())?;

    let b64 = &base64::engine::general_purpose::STANDARD;
    let encrypted_key_b64 = base64::Engine::encode(b64, &encrypted_key_bytes);
    let key_nonce_b64 = base64::Engine::encode(b64, &key_nonce);
    let file_nonce_b64 = base64::Engine::encode(b64, &file_nonce);

    let mut encrypted_thumb_b64 = None;
    let mut thumb_nonce_b64 = None;

    if let Some(thumb_b64) = thumbnail_base64 {
        let thumb_bytes = base64::engine::general_purpose::STANDARD
            .decode(&thumb_b64)
            .map_err(|e| format!("Erro ao decodificar thumbnail: {e}"))?;

        let (encrypted_thumb_bytes, thumb_nonce) = cipher::encrypt(&session_key, &thumb_bytes)?;
        encrypted_thumb_b64 = Some(base64::Engine::encode(b64, &encrypted_thumb_bytes));
        thumb_nonce_b64 = Some(base64::Engine::encode(b64, &thumb_nonce));
    }

    let media_dir = get_media_dir();
    if !media_dir.exists() {
        fs::create_dir_all(&media_dir).map_err(|e| format!("Erro ao criar pasta de mídia: {e}"))?;
    }
    let dest_path = media_dir.join(&media_id);
    fs::write(&dest_path, &encrypted_file_bytes)
        .map_err(|e| format!("Erro ao gravar arquivo em disco: {e}"))?;

    let media_item = MediaItem {
        id: media_id.clone(),
        filename,
        mime_type,
        file_size,
        encrypted_key: encrypted_key_b64,
        key_nonce: key_nonce_b64,
        file_nonce: file_nonce_b64,
        thumbnail_data: encrypted_thumb_b64,
        thumbnail_nonce: thumb_nonce_b64,
        is_favorite: false,
        created_at: chrono::Utc::now().to_rfc3339(),
        updated_at: chrono::Utc::now().to_rfc3339(),
        deleted_at: None,
        device_id: None,
        last_modified_device_id: None,
    };

    db.with_db(|database| {
        database
            .create_media(&media_item)
            .map_err(|e| format!("Erro ao registrar mídia no banco: {e}"))?;
        Ok(media_id)
    })
}

#[tauri::command]
pub fn import_media_from_path(
    file_path: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<String, String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }

    let path = std::path::Path::new(&file_path);
    if !path.exists() {
        return Err("Arquivo não encontrado.".to_string());
    }

    let filename = path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| "media".to_string());

    let ext = path
        .extension()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    let mime_type = match ext.as_str() {
        "jpg" | "jpeg" => "image/jpeg",
        "png" => "image/png",
        "gif" => "image/gif",
        "webp" => "image/webp",
        "mp4" => "video/mp4",
        "mov" => "video/quicktime",
        "webm" => "video/webm",
        _ => "application/octet-stream",
    }
    .to_string();

    let file_bytes = std::fs::read(&path).map_err(|e| format!("Erro ao ler arquivo: {}", e))?;

    let file_bytes_base64 = base64::engine::general_purpose::STANDARD.encode(&file_bytes);

    // Pass to create_media
    create_media(
        filename,
        mime_type,
        file_bytes_base64,
        None,
        session,
        db,
        config,
    )
}

#[tauri::command]
pub fn get_media_data(
    id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    let session_key = get_session_key(&session)?;

    let media = db.with_db(|database| {
        database
            .get_media(&id)
            .map_err(|e| format!("Erro ao obter metadados da mídia: {e}"))
    })?;

    let item = match media {
        Some(m) => m,
        None => return Err("Mídia não encontrada.".to_string()),
    };

    let b64 = &base64::engine::general_purpose::STANDARD;

    let encrypted_key_bytes = base64::Engine::decode(b64, &item.encrypted_key)
        .map_err(|e| format!("Erro ao decodificar chave criptografada: {e}"))?;
    let key_nonce_bytes = base64::Engine::decode(b64, &item.key_nonce)
        .map_err(|e| format!("Erro ao decodificar nonce da chave: {e}"))?;
    let file_nonce_bytes = base64::Engine::decode(b64, &item.file_nonce)
        .map_err(|e| format!("Erro ao decodificar nonce do arquivo: {e}"))?;

    if key_nonce_bytes.len() != cipher::NONCE_LENGTH
        || file_nonce_bytes.len() != cipher::NONCE_LENGTH
    {
        return Err("Estrutura criptográfica da mídia corrompida.".to_string());
    }

    let mut key_nonce = [0u8; cipher::NONCE_LENGTH];
    key_nonce.copy_from_slice(&key_nonce_bytes);

    let mut file_nonce = [0u8; cipher::NONCE_LENGTH];
    file_nonce.copy_from_slice(&file_nonce_bytes);

    let decrypted_key_bytes = cipher::decrypt(&session_key, &encrypted_key_bytes, &key_nonce)?;
    if decrypted_key_bytes.len() != 32 {
        return Err("Erro na descriptografia da chave da mídia.".to_string());
    }
    let mut file_key_raw = [0u8; 32];
    file_key_raw.copy_from_slice(&decrypted_key_bytes);
    let file_key = Zeroizing::new(file_key_raw);

    let media_dir = get_media_dir();
    let file_path = media_dir.join(&id);
    if !file_path.exists() {
        return Err("Arquivo físico não encontrado em disco.".to_string());
    }
    let encrypted_file_bytes = fs::read(&file_path)
        .map_err(|e| format!("Erro ao ler arquivo criptografado do disco: {e}"))?;

    let decrypted_file_bytes = cipher::decrypt(&file_key, &encrypted_file_bytes, &file_nonce)?;
    let file_b64 = base64::Engine::encode(b64, &decrypted_file_bytes);
    Ok(file_b64)
}

#[tauri::command]
pub fn list_media(
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<MediaItemDecrypted>, String> {
    let session_key = get_session_key(&session)?;
    let b64 = &base64::engine::general_purpose::STANDARD;

    db.with_db(|database| {
        let list = database
            .list_media()
            .map_err(|e| format!("Erro ao listar mídias: {e}"))?;

        let mut decrypted_list = Vec::new();
        for item in list {
            let mut thumb_dec_b64 = None;

            if let (Some(enc_thumb), Some(nonce_b64)) = (item.thumbnail_data, item.thumbnail_nonce)
            {
                if let (Ok(enc_bytes), Ok(nonce_bytes)) = (
                    base64::Engine::decode(b64, &enc_thumb),
                    base64::Engine::decode(b64, &nonce_b64),
                ) {
                    if nonce_bytes.len() == cipher::NONCE_LENGTH {
                        let mut nonce = [0u8; cipher::NONCE_LENGTH];
                        nonce.copy_from_slice(&nonce_bytes);

                        if let Ok(dec_bytes) = cipher::decrypt(&session_key, &enc_bytes, &nonce) {
                            thumb_dec_b64 = Some(base64::Engine::encode(b64, &dec_bytes));
                        }
                    }
                }
            }

            decrypted_list.push(MediaItemDecrypted {
                id: item.id,
                filename: item.filename,
                mime_type: item.mime_type,
                file_size: item.file_size,
                is_favorite: item.is_favorite,
                thumbnail_data: thumb_dec_b64,
                created_at: item.created_at,
                updated_at: item.updated_at,
            });
        }
        Ok(decrypted_list)
    })
}

#[tauri::command]
pub fn delete_media(
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
            .delete_media(&id)
            .map_err(|e| format!("Erro ao registrar exclusão no banco: {e}"))
    })?;

    let media_dir = get_media_dir();
    let file_path = media_dir.join(&id);
    if file_path.exists() {
        let _ = fs::remove_file(&file_path);
    }

    Ok(())
}
