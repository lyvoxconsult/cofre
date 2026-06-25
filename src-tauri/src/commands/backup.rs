use tauri::State;
use zeroize::Zeroizing;

use crate::crypto::{cipher, key_derivation};
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;
use crate::storage::models;

/// Estrutura de uma entrada no backup (dados já descriptografados).
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct BackupEntry {
    pub service_name: String,
    pub login: String,
    pub password: String,
    pub notes: String,
    pub url: String,
    pub category_id: Option<String>,
}

/// Estrutura de uma nota no backup (conteúdo já descriptografado).
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct BackupNote {
    pub title: String,
    pub content: String,
    pub category: String,
}

/// Dados completos do backup.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct BackupPayload {
    pub app: String,
    pub version: String,
    pub created_at: String,
    pub entries: Vec<BackupEntry>,
    pub notes: Vec<BackupNote>,
}

/// Envelope do backup (contém metadados + dados criptografados).
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct BackupEnvelope {
    pub app: String,
    pub version: String,
    pub created_at: String,
    /// Dados criptografados em base64 (AES-256-GCM)
    pub encrypted_data: String,
    /// Nonce usado na criptografia (base64)
    pub nonce: String,
    /// Salt usado na derivação da chave de backup (hex)
    pub salt: String,
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

/// Descriptografa uma entrada individual.
fn decrypt_entry(
    key: &Zeroizing<[u8; 32]>,
    entry: models::VaultEntry,
) -> Result<BackupEntry, String> {
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
            &entry.notes_nonce.as_ref().unwrap(),
        ) {
            Ok(n) => n,
            Err(_) => String::new(),
        }
    };

    Ok(BackupEntry {
        service_name: entry.service_name,
        login: entry.login,
        password,
        notes,
        url: entry.url,
        category_id: entry.category_id,
    })
}

/// Descriptografa uma nota individual.
fn decrypt_note(key: &Zeroizing<[u8; 32]>, note: models::SecureNote) -> Result<BackupNote, String> {
    let content = if note.encrypted_content.is_empty() || note.content_nonce.is_empty() {
        String::new()
    } else {
        cipher::decrypt_field(key, &note.encrypted_content, &note.content_nonce)?
    };

    Ok(BackupNote {
        title: note.title,
        content,
        category: note.category,
    })
}

/// Exporta um backup criptografado de todas as entradas e notas.
///
/// Retorna o conteúdo do arquivo de backup como string base64 para que o
/// frontend possa salvá-lo via diálogo de arquivo.
#[tauri::command]
pub fn export_backup(
    backup_password: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    if backup_password.len() < 8 {
        return Err("A senha de backup deve ter no mínimo 8 caracteres.".to_string());
    }

    let key = get_session_key(&session)?;

    // Coleta todas as entradas e notas
    let entries = db.with_db(|database| {
        database
            .list_entries()
            .map_err(|e| format!("Erro ao listar entradas: {e}"))
    })?;

    let notes = db.with_db(|database| {
        database
            .list_notes()
            .map_err(|e| format!("Erro ao listar notas: {e}"))
    })?;

    // Descriptografa tudo
    let mut backup_entries = Vec::new();
    for entry in &entries {
        backup_entries.push(decrypt_entry(&key, entry.clone())?);
    }

    let mut backup_notes = Vec::new();
    for note in &notes {
        backup_notes.push(decrypt_note(&key, note.clone())?);
    }

    // Monta o payload
    let now = chrono::Utc::now().to_rfc3339();
    let payload = BackupPayload {
        app: "lyvox-vault".to_string(),
        version: "1".to_string(),
        created_at: now,
        entries: backup_entries,
        notes: backup_notes,
    };

    // Serializa para JSON
    let json_data =
        serde_json::to_string(&payload).map_err(|e| format!("Erro ao serializar backup: {e}"))?;

    // Deriva chave de backup (Argon2id com salt próprio)
    let backup_salt = key_derivation::generate_salt();
    let backup_key = key_derivation::derive_key(&backup_password, &backup_salt)?;

    // Criptografa com AES-256-GCM
    let (encrypted_bytes, nonce) = cipher::encrypt(&backup_key, json_data.as_bytes())?;

    // Codifica em base64
    use base64::Engine;
    let encrypted_b64 = base64::engine::general_purpose::STANDARD.encode(&encrypted_bytes);
    let nonce_b64 = base64::engine::general_purpose::STANDARD.encode(&nonce);
    let salt_hex = hex::encode(backup_salt);

    // Monta envelope
    let envelope = BackupEnvelope {
        app: "lyvox-vault".to_string(),
        version: "1".to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        encrypted_data: encrypted_b64,
        nonce: nonce_b64,
        salt: salt_hex,
    };

    let envelope_json = serde_json::to_string_pretty(&envelope)
        .map_err(|e| format!("Erro ao serializar envelope: {e}"))?;

    Ok(envelope_json)
}

/// Importa/restaura um backup criptografado.
///
/// Se `replace` for true, os dados existentes são substituídos.
/// Caso contrário, os dados do backup são adicionados aos existentes.
#[tauri::command]
pub fn import_backup(
    backup_data: String,
    backup_password: String,
    replace: bool,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
) -> Result<String, String> {
    let key = get_session_key(&session)?;

    // Parseia o envelope
    let envelope: BackupEnvelope = serde_json::from_str(&backup_data)
        .map_err(|e| format!("Arquivo de backup inválido: {e}"))?;

    // Valida metadados
    if envelope.app != "lyvox-vault" {
        return Err("Arquivo de backup não reconhecido.".to_string());
    }

    // Decodifica base64
    use base64::Engine;
    let encrypted_bytes = base64::engine::general_purpose::STANDARD
        .decode(&envelope.encrypted_data)
        .map_err(|_| "Arquivo de backup corrompido (base64 inválido).".to_string())?;

    let nonce_bytes = base64::engine::general_purpose::STANDARD
        .decode(&envelope.nonce)
        .map_err(|_| "Backup corrompido (nonce inválido).".to_string())?;

    if nonce_bytes.len() != cipher::NONCE_LENGTH {
        return Err("Backup corrompido (nonce com tamanho inválido).".to_string());
    }

    let mut nonce_arr = [0u8; cipher::NONCE_LENGTH];
    nonce_arr.copy_from_slice(&nonce_bytes);

    let salt_bytes = hex::decode(&envelope.salt)
        .map_err(|_| "Backup corrompido (salt inválido).".to_string())?;

    if salt_bytes.len() != 16 {
        return Err("Backup corrompido (salt com tamanho inválido).".to_string());
    }

    // Deriva chave de backup
    let backup_key = key_derivation::derive_key(&backup_password, &salt_bytes)?;

    // Descriptografa (AES-256-GCM já valida integridade via tag de autenticação)
    let decrypted_bytes = cipher::decrypt(&backup_key, &encrypted_bytes, &nonce_arr)?;

    let json_str = String::from_utf8(decrypted_bytes)
        .map_err(|_| "Backup corrompido (dados inválidos).".to_string())?;

    let payload: BackupPayload =
        serde_json::from_str(&json_str).map_err(|e| format!("Backup corrompido: {e}"))?;

    // Se for substituir, limpa os dados existentes
    if replace {
        db.with_db(|database| {
            database
                .clear_all_data()
                .map_err(|e| format!("Erro ao limpar dados existentes: {e}"))
        })?;
    }

    // Restaura entradas
    let mut entries_restored = 0u32;
    for entry in &payload.entries {
        let (enc_pwd, pwd_nonce) = cipher::encrypt_field(&key, &entry.password)?;
        let (enc_notes, notes_nonce) = if entry.notes.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&key, &entry.notes)?
        };

        let notes_nonce_opt = if notes_nonce.is_empty() {
            None
        } else {
            Some(notes_nonce.as_str())
        };

        db.with_db(|database| {
            database
                .create_entry(
                    &models::CreateEntryPayload {
                        id: None,
                        service_name: entry.service_name.clone(),
                        login: entry.login.clone(),
                        password: String::new(),
                        notes: None,
                        url: entry.url.clone(),
                        category_id: entry.category_id.clone(),
                        is_favorite: None,
                    },
                    &enc_pwd,
                    &pwd_nonce,
                    &enc_notes,
                    notes_nonce_opt,
                )
                .map_err(|e| format!("Erro ao restaurar entrada: {e}"))
        })?;
        entries_restored += 1;
    }

    // Restaura notas
    let mut notes_restored = 0u32;
    for note in &payload.notes {
        let (enc_content, content_nonce) = if note.content.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&key, &note.content)?
        };

        db.with_db(|database| {
            database
                .create_note(
                    &models::CreateNotePayload {
                        id: None,
                        title: note.title.clone(),
                        content: String::new(),
                        category: note.category.clone(),
                        is_favorite: None,
                    },
                    &enc_content,
                    &content_nonce,
                )
                .map_err(|e| format!("Erro ao restaurar nota: {e}"))
        })?;
        notes_restored += 1;
    }

    Ok(format!(
        "Restauradas {} entradas e {} notas.",
        entries_restored, notes_restored
    ))
}

/// Escreve um arquivo de backup no disco.
/// Comando customizado em vez de depender de plugin:fs para evitar
/// configuração adicional de plugin e ACL no Tauri.
#[tauri::command]
pub fn write_backup_file(path: String, content: String) -> Result<(), String> {
    use std::path::Path;

    let path = Path::new(&path);

    // Garante que o diretório pai existe
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("Erro ao criar diretório: {e}"))?;
    }

    std::fs::write(path, &content)
        .map_err(|e| format!("Erro ao escrever arquivo de backup: {e}"))?;

    Ok(())
}

/// Lê um arquivo de backup do disco e retorna seu conteúdo como string.
#[tauri::command]
pub fn read_backup_file(path: String) -> Result<String, String> {
    std::fs::read_to_string(&path).map_err(|e| format!("Erro ao ler arquivo de backup: {e}"))
}

/// Exclui todos os dados do cofre (entradas e notas) após verificar a senha mestra.
///
/// A senha fornecida é validada contra a chave da sessão atual — apenas
/// o usuário que sabe a senha mestra pode executar esta operação destrutiva.
#[tauri::command]
pub fn clear_all_vault_data(
    password: String,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, crate::config::app_config::ConfigState>,
) -> Result<String, String> {
    if password.len() < 8 {
        return Err("A senha mestra deve ter no mínimo 8 caracteres.".to_string());
    }

    // Pega a chave da sessão atual (garante que o cofre está desbloqueado)
    let session_key = get_session_key(&session)?;

    // Lê o salt da configuração
    let salt_hex = config.read(|c| c.salt.clone())?;
    let salt_hex = salt_hex.ok_or("Configuração inválida: salt não encontrado.".to_string())?;
    let salt_bytes =
        hex::decode(&salt_hex).map_err(|_| "Salt inválido na configuração.".to_string())?;
    if salt_bytes.len() != 16 {
        return Err("Salt com tamanho inválido.".to_string());
    }
    let mut salt = [0u8; 16];
    salt.copy_from_slice(&salt_bytes);

    // Deriva chave da senha fornecida
    let provided_key = key_derivation::derive_key(&password, &salt)?;

    // Compara com a chave da sessão (senha correta?)
    if *session_key != *provided_key {
        return Err("Senha mestra incorreta. Operação cancelada.".to_string());
    }

    // Conta registros para feedback
    let entries_count = db.with_db(|database| {
        database
            .list_entries()
            .map(|e| e.len())
            .map_err(|e| format!("Erro ao contar entradas: {e}"))
    })?;

    let notes_count = db.with_db(|database| {
        database
            .list_notes()
            .map(|n| n.len())
            .map_err(|e| format!("Erro ao contar notas: {e}"))
    })?;

    // Limpa todos os dados
    db.with_db(|database| {
        database
            .clear_all_data()
            .map_err(|e| format!("Erro ao limpar dados: {e}"))
    })?;

    // Registra atividade para não disparar auto-lock
    session.with_session(|s| {
        s.record_activity();
        Ok(())
    })?;

    Ok(format!(
        "Todos os dados foram excluídos: {} entrada(s) e {} nota(s) removidas.",
        entries_count, notes_count
    ))
}
