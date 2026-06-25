use base64::Engine;
use std::fs;
use std::path::PathBuf;
use tauri::State;
use zeroize::Zeroizing;

use crate::config::app_config::ConfigState;
use crate::crypto::cipher;
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models::AttachmentDecrypted;

/// Retorna o diretório onde os anexos criptografados são salvos.
fn get_attachments_dir() -> PathBuf {
    dirs_next::data_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("lyvox-vault")
        .join("attachments")
}

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

// ─── Comandos Tauri ─────────────────────────────────────────

#[tauri::command]
pub fn create_attachment(
    entry_id: String,
    filename: String,
    mime_type: String,
    file_bytes_base64: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<String, String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let session_key = get_session_key(&session)?;

    // 1. Decodificar bytes do arquivo recebidos em base64
    let file_bytes = base64::engine::general_purpose::STANDARD
        .decode(&file_bytes_base64)
        .map_err(|e| format!("Erro ao decodificar arquivo base64: {e}"))?;

    let file_size = file_bytes.len() as i64;

    // 2. Gerar ID do anexo
    let attachment_id = uuid::Uuid::new_v4().to_string();

    // 3. Gerar chave simétrica de 256 bits aleatória para este arquivo
    let mut file_key_raw = [0u8; 32];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut file_key_raw);
    let file_key = Zeroizing::new(file_key_raw);

    // 4. Criptografar bytes do arquivo com a chave do arquivo
    let (encrypted_file_bytes, file_nonce) = cipher::encrypt(&file_key, &file_bytes)?;

    // 5. Criptografar a chave do arquivo com a chave de sessão
    let (encrypted_key_bytes, key_nonce) = cipher::encrypt(&session_key, file_key.as_ref())?;

    // 6. Converter nonces e chaves para Base64 para salvar no banco
    let b64 = &base64::engine::general_purpose::STANDARD;
    let encrypted_key_b64 = base64::Engine::encode(b64, &encrypted_key_bytes);
    let key_nonce_b64 = base64::Engine::encode(b64, &key_nonce);
    let file_nonce_b64 = base64::Engine::encode(b64, &file_nonce);

    // 7. Salvar arquivo criptografado em disco na pasta de attachments
    let attachments_dir = get_attachments_dir();
    if !attachments_dir.exists() {
        fs::create_dir_all(&attachments_dir)
            .map_err(|e| format!("Erro ao criar pasta de anexos: {e}"))?;
    }
    let dest_path = attachments_dir.join(&attachment_id);
    fs::write(&dest_path, &encrypted_file_bytes)
        .map_err(|e| format!("Erro ao gravar arquivo em disco: {e}"))?;

    // 8. Salvar os metadados no SQLite
    db.with_db(|database| {
        database
            .create_attachment(
                &attachment_id,
                &entry_id,
                &filename,
                &mime_type,
                file_size,
                &encrypted_key_b64,
                &key_nonce_b64,
                &file_nonce_b64,
            )
            .map_err(|e| format!("Erro ao registrar anexo no banco: {e}"))?;
        Ok(attachment_id.clone())
    })
}

#[tauri::command]
pub fn get_attachment_data(
    id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    let session_key = get_session_key(&session)?;

    // 1. Obter metadados do anexo
    let attachment = db.with_db(|database| {
        database
            .get_attachment(&id)
            .map_err(|e| format!("Erro ao obter metadados do anexo: {e}"))
    })?;

    let att = match attachment {
        Some(a) => a,
        None => return Err("Anexo não encontrado.".to_string()),
    };

    // 2. Decodificar chaves e nonces salvos
    let b64 = &base64::engine::general_purpose::STANDARD;

    let encrypted_key_bytes = base64::Engine::decode(b64, &att.encrypted_key)
        .map_err(|e| format!("Erro ao decodificar chave criptografada: {e}"))?;
    let key_nonce_bytes = base64::Engine::decode(b64, &att.key_nonce)
        .map_err(|e| format!("Erro ao decodificar nonce da chave: {e}"))?;
    let file_nonce_bytes = base64::Engine::decode(b64, &att.file_nonce)
        .map_err(|e| format!("Erro ao decodificar nonce do arquivo: {e}"))?;

    if key_nonce_bytes.len() != cipher::NONCE_LENGTH
        || file_nonce_bytes.len() != cipher::NONCE_LENGTH
    {
        return Err("Estrutura criptográfica do anexo corrompida.".to_string());
    }

    let mut key_nonce = [0u8; cipher::NONCE_LENGTH];
    key_nonce.copy_from_slice(&key_nonce_bytes);

    let mut file_nonce = [0u8; cipher::NONCE_LENGTH];
    file_nonce.copy_from_slice(&file_nonce_bytes);

    // 3. Descriptografar a chave do arquivo usando a chave de sessão
    let decrypted_key_bytes = cipher::decrypt(&session_key, &encrypted_key_bytes, &key_nonce)?;
    if decrypted_key_bytes.len() != 32 {
        return Err("Erro na descriptografia da chave do anexo.".to_string());
    }
    let mut file_key_raw = [0u8; 32];
    file_key_raw.copy_from_slice(&decrypted_key_bytes);
    let file_key = Zeroizing::new(file_key_raw);

    // 4. Ler o arquivo criptografado de disco
    let attachments_dir = get_attachments_dir();
    let file_path = attachments_dir.join(&id);
    if !file_path.exists() {
        return Err("Arquivo físico do anexo não encontrado em disco.".to_string());
    }
    let encrypted_file_bytes = fs::read(&file_path)
        .map_err(|e| format!("Erro ao ler arquivo criptografado do disco: {e}"))?;

    // 5. Descriptografar os bytes do arquivo usando a chave do arquivo
    let decrypted_file_bytes = cipher::decrypt(&file_key, &encrypted_file_bytes, &file_nonce)?;

    // 6. Retornar os bytes descriptografados codificados em Base64 para o frontend
    let file_b64 = base64::Engine::encode(b64, &decrypted_file_bytes);
    Ok(file_b64)
}

#[tauri::command]
pub fn list_attachments(
    entry_id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<Vec<AttachmentDecrypted>, String> {
    let _key = get_session_key(&session)?;

    db.with_db(|database| {
        let list = database
            .list_attachments(&entry_id)
            .map_err(|e| format!("Erro ao listar anexos: {e}"))?;

        let decrypted_list = list
            .into_iter()
            .map(|att| AttachmentDecrypted {
                id: att.id,
                entry_id: att.entry_id,
                filename: att.filename,
                mime_type: att.mime_type,
                file_size: att.file_size,
                created_at: att.created_at,
                updated_at: att.updated_at,
            })
            .collect();
        Ok(decrypted_list)
    })
}

#[tauri::command]
pub fn delete_attachment(
    id: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if config.read(|c| c.read_only_mode)? {
        return Err("Modo somente leitura ativo.".to_string());
    }
    let _key = get_session_key(&session)?;

    // 1. Executa soft-delete no banco para que a exclusão possa se propagar via sync
    db.with_db(|database| {
        database
            .delete_attachment(&id)
            .map_err(|e| format!("Erro ao registrar exclusão no banco: {e}"))
    })?;

    // 2. Remove o arquivo físico local em disco para economizar espaço
    let attachments_dir = get_attachments_dir();
    let file_path = attachments_dir.join(&id);
    if file_path.exists() {
        let _ = fs::remove_file(&file_path);
    }

    Ok(())
}
