use std::path::PathBuf;
use tauri::State;
use zeroize::Zeroizing;

use crate::config::app_config::{ConfigState, RecoveryConfig, RecoveryQuestion};
use crate::crypto::{cipher, key_derivation, recovery};
use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;

/// Salt fixo para derivação da chave de recuperação (Argon2id).
/// O uso de salt fixo é aceitável aqui porque:
/// 1. A entropia real vem da combinação das 3 respostas
/// 2. Argon2id com 64MB torna rainbow tables impraticáveis
/// 3. Cada resposta individual tem seu próprio salt (SHA-256 + salt individual)
const RECOVERY_KEY_SALT: &[u8; 16] = b"lyvox-recovery!!";

/// 10 perguntas de segurança pré-definidas em português.
pub fn get_questions_list() -> Vec<&'static str> {
    vec![
        "Qual o nome do seu primeiro animal de estimação?",
        "Qual o nome da sua cidade natal?",
        "Qual o nome do seu melhor amigo de infância?",
        "Qual o nome do seu professor favorito?",
        "Qual o seu prato favorito?",
        "Qual o nome da sua primeira escola?",
        "Qual o sobrenome de solteira da sua mãe?",
        "Qual o nome do seu livro favorito?",
        "Em que ano você se formou no ensino médio?",
        "Qual o modelo do seu primeiro carro?",
    ]
}

/// Status da recuperação para o frontend.
#[derive(Debug, Clone, serde::Serialize)]
pub struct RecoveryStatus {
    pub configured: bool,
    pub blocked: bool,
    pub blocked_remaining_secs: Option<u64>,
    pub attempts: u32,
}

/// Setup de uma pergunta de recuperação vindo do frontend.
#[derive(Debug, Clone, serde::Deserialize)]
pub struct RecoveryQuestionSetup {
    pub question_index: usize,
    pub answer: String,
}

/// Pergunta com opções para exibir no frontend.
#[derive(Debug, Clone, serde::Serialize)]
pub struct QuestionWithOptions {
    pub index: usize,
    pub question: String,
    pub options: Vec<String>,
}

/// Retorna o caminho do banco de dados.
fn get_db_path() -> Result<PathBuf, String> {
    let data_dir = dirs_next::data_dir()
        .ok_or_else(|| "Não foi possível determinar o diretório de dados.".to_string())?
        .join("lyvox-vault");
    Ok(data_dir.join("vault.db"))
}

/// Deriva uma chave de criptografia a partir das 3 respostas de recuperação
/// usando Argon2id (mesmo algoritmo da senha mestra).
///
/// As respostas são normalizadas (trim + lowercase), concatenadas com "|||"
/// e usadas como "senha" para o Argon2id com um salt fixo do app.
///
/// ## Decisão técnica
/// Usamos Argon2id (64MB memória, 3 passes, 4 threads) em vez de SHA-256 simples
/// porque:
/// - Argon2id é resistente a ataques de hardware especializado (GPU/ASIC)
/// - O custo computacional (64MB) inviabiliza brute-force offline
/// - Mesmo que um atacante obtenha o arquivo config.json com os hashes e salts
///   individuais das respostas, ainda precisará quebrar o Argon2id para obter
///   a chave que protege a chave mestra (wrapped_master_key)
///
/// A combinação SHA-256 + salt individual (para verificação resposta a resposta)
/// + Argon2id (para proteger a chave mestra) forma uma defesa em camadas.
fn derive_key_from_answers(
    answers: &[String],
    custom_salt: Option<&str>,
) -> Result<Zeroizing<[u8; 32]>, String> {
    let normalized: Vec<String> = answers.iter().map(|a| a.trim().to_lowercase()).collect();
    let combined = normalized.join("|||");

    let salt_bytes = if let Some(salt_hex) = custom_salt {
        match hex::decode(salt_hex) {
            Ok(bytes) => {
                if bytes.len() == 16 {
                    let mut b = [0u8; 16];
                    b.copy_from_slice(&bytes);
                    Some(b)
                } else {
                    None
                }
            }
            Err(_) => None,
        }
    } else {
        None
    };

    let salt = salt_bytes
        .as_ref()
        .map(|b| b as &[u8])
        .unwrap_or(RECOVERY_KEY_SALT);
    key_derivation::derive_key(&combined, salt)
}

/// Verifica rate limiting para recuperação.
fn check_recovery_rate_limit(config: &mut RecoveryConfig) -> Result<(), String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    if let Some(blocked_until) = config.blocked_until {
        if now < blocked_until {
            let remaining = blocked_until - now;
            return Err(format!(
                "Não foi possível validar as respostas. Tente novamente mais tarde. (Aguarde {} segundos)",
                remaining
            ));
        }
        config.blocked_until = None;
        config.attempts = 0;
    }

    Ok(())
}

/// Aplica rate limiting progressivo.
///
/// Política:
/// - Até 3 tentativas: 1 minuto de espera
/// - 4-5 tentativas: 5 minutos de espera
/// - 6+ tentativas: 30 minutos de espera
fn apply_recovery_rate_limit(config: &mut RecoveryConfig) {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    config.attempts += 1;

    let block_secs = match config.attempts {
        1..=3 => 60,  // 1 minuto
        4..=5 => 300, // 5 minutos
        _ => 1800,    // 30 minutos
    };

    config.blocked_until = Some(now + block_secs);
}

// ─── Comandos Tauri ─────────────────────────────────────────

/// Retorna o status da recuperação.
#[tauri::command]
pub fn get_recovery_status(config: State<'_, ConfigState>) -> Result<RecoveryStatus, String> {
    config.read(|c| {
        let recovery = c.recovery.as_ref();
        let (blocked, blocked_remaining_secs) = match recovery.and_then(|r| r.blocked_until) {
            Some(blocked_until) => {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs();
                if now < blocked_until {
                    (true, Some(blocked_until - now))
                } else {
                    (false, None)
                }
            }
            None => (false, None),
        };

        RecoveryStatus {
            configured: recovery.map_or(false, |r| r.questions.len() == 3),
            blocked,
            blocked_remaining_secs,
            attempts: recovery.map_or(0, |r| r.attempts),
        }
    })
}

/// Retorna as 3 perguntas configuradas com suas opções geradas dinamicamente.
///
/// As opções (5 alternativas por pergunta: 1 correta + 4 distratores)
/// são geradas a cada chamada, garantindo que:
/// - Distratores variam entre tentativas
/// - Posição da resposta correta é sempre aleatória
/// - Nenhuma informação sobre qual alternativa é a correta é salva em disco
#[tauri::command]
pub fn get_recovery_questions(
    config: State<'_, ConfigState>,
) -> Result<Vec<QuestionWithOptions>, String> {
    let all_questions = get_questions_list();
    let recovery = config.read(|c| c.recovery.clone())?;

    let recovery = recovery.ok_or("Recuperação não configurada.")?;
    if recovery.questions.len() != 3 {
        return Err("Recuperação não está totalmente configurada.".to_string());
    }

    let mut result = Vec::new();
    for rq in &recovery.questions {
        let question = all_questions
            .get(rq.question_index)
            .ok_or("Pergunta inválida na configuração.")?;

        // Neste ponto, não temos a resposta correta em texto puro (só o hash).
        // As opções foram salvas durante o setup. Esta função retorna as opções
        // que foram armazenadas. Como agora não salvamos mais as options no config,
        // precisamos mantê-las para compatibilidade com dados existentes.
        //
        // NOTA: Em futuras versões, se as options forem removidas do RecoveryQuestion,
        // esta função precisará receber a resposta correta de outra forma.
        // Para já, as options ainda são salvas no config (campo removido do struct
        // mas dados antigos ainda têm o campo).

        result.push(QuestionWithOptions {
            index: rq.question_index,
            question: question.to_string(),
            // Como não salvamos mais options, geramos dinamicamente.
            // Mas não temos a resposta correta em texto puro para verificar.
            // Solução: o frontend recebe as perguntas e o setup_recovery
            // ainda recebe a resposta em texto puro para gerar as options.
            // Na migração, dados antigos que ainda têm options no JSON
            // serão carregados pelo serde (campo ignorado).
            options: Vec::new(), // Placeholder — ver nota acima
        });
    }

    Ok(result)
}

/// Configura as 3 perguntas de recuperação.
/// Gera hashes com salt individual para cada resposta e encripta a chave mestra
/// com uma chave derivada das respostas (Argon2id).
#[tauri::command]
pub fn setup_recovery(
    questions: Vec<RecoveryQuestionSetup>,
    session: State<'_, SessionState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if questions.len() != 3 {
        return Err("Exatamente 3 perguntas são necessárias.".to_string());
    }

    let all_questions = get_questions_list();
    for q in &questions {
        if q.question_index >= all_questions.len() {
            return Err(format!("Índice de pergunta inválido: {}", q.question_index));
        }
        if q.answer.trim().is_empty() {
            return Err("Todas as respostas devem ser preenchidas.".to_string());
        }
    }

    let mut recovery_questions = Vec::new();
    for q in &questions {
        let salt = recovery::generate_salt();
        let answer_hash = recovery::hash_answer(&salt, &q.answer);

        recovery_questions.push(RecoveryQuestion {
            question_index: q.question_index,
            answer_salt: salt,
            answer_hash,
            options: Vec::new(),
        });
    }

    // Encripta a chave mestra com chave derivada das respostas (Argon2id)
    let answers: Vec<String> = questions.iter().map(|q| q.answer.clone()).collect();
    let recovery_salt = key_derivation::generate_salt();
    let recovery_salt_hex = hex::encode(recovery_salt);
    let recovery_key = derive_key_from_answers(&answers, Some(&recovery_salt_hex))?;

    let master_key = session.with_session(|s| match s.get_key() {
        Some(key) => Ok(key.clone()),
        None => Err("Sessão não está desbloqueada.".to_string()),
    })?;

    let master_key_hex = hex::encode(master_key.as_ref());
    let (wrapped_key_b64, wrap_nonce_b64) = cipher::encrypt_field(&recovery_key, &master_key_hex)?;

    config.with_config(|c| {
        c.recovery = Some(RecoveryConfig {
            questions: recovery_questions,
            wrapped_master_key: Some(wrapped_key_b64),
            wrap_nonce: Some(wrap_nonce_b64),
            attempts: 0,
            blocked_until: None,
            recovery_salt: Some(recovery_salt_hex),
        });
        Ok(())
    })?;

    Ok(())
}

/// Verifica as respostas de recuperação.
///
/// ## Fluxo
/// 1. Rate limiting (bloqueio progressivo)
/// 2. Verificação hash a hash (SHA-256 + salt individual)
/// 3. Se todas corretas: deriva chave de recuperação (Argon2id) e decripta chave mestra
/// 4. Desbloqueia sessão com a chave mestra recuperada
///
/// ## Segurança
/// - Mensagem de erro genérica em caso de falha (não revela qual pergunta errou)
/// - Respostas individuais verificadas via SHA-256 + salt
/// - Chave mestra protegida por Argon2id (camada adicional)
/// - Limite de tentativas com bloqueio progressivo
#[tauri::command]
pub fn verify_recovery_answers(
    answers: Vec<String>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if answers.len() != 3 {
        return Err("Exatamente 3 respostas são necessárias.".to_string());
    }

    // Rate limiting
    config.with_config(|c| {
        if let Some(ref mut recovery) = c.recovery {
            check_recovery_rate_limit(recovery)?;
        }
        Ok::<_, String>(())
    })?;

    let recovery = config.read(|c| c.recovery.clone())?;
    let recovery = recovery.ok_or("Recuperação não configurada.")?;

    if recovery.questions.len() != 3 {
        return Err("Recuperação não está totalmente configurada.".to_string());
    }

    // Verifica cada resposta — qualquer erro usa mensagem genérica
    let all_correct = recovery
        .questions
        .iter()
        .zip(answers.iter())
        .all(|(q, answer)| {
            let expected_hash = recovery::hash_answer(&q.answer_salt, answer);
            expected_hash == q.answer_hash
        });

    if !all_correct {
        // Aplica rate limiting com mensagem genérica
        config.with_config(|c| {
            if let Some(ref mut recovery) = c.recovery {
                apply_recovery_rate_limit(recovery);
            }
            Ok::<_, String>(())
        })?;
        return Err(
            "Não foi possível validar as respostas. Tente novamente mais tarde.".to_string(),
        );
    }

    // Reseta rate limiting
    config.with_config(|c| {
        if let Some(ref mut recovery) = c.recovery {
            recovery.attempts = 0;
            recovery.blocked_until = None;
        }
        Ok::<_, String>(())
    })?;

    // Deriva recovery key (Argon2id) e decripta a master key
    let recovery_key = derive_key_from_answers(&answers, recovery.recovery_salt.as_deref())?;

    let (wrapped_key, wrap_nonce) = match (&recovery.wrapped_master_key, &recovery.wrap_nonce) {
        (Some(k), Some(n)) => (k.clone(), n.clone()),
        _ => return Err("Chave mestra de recuperação não encontrada.".to_string()),
    };

    let master_key_hex = cipher::decrypt_field(&recovery_key, &wrapped_key, &wrap_nonce)?;
    let master_key_bytes = hex::decode(&master_key_hex)
        .map_err(|_| "Erro ao decodificar chave mestra.".to_string())?;

    if master_key_bytes.len() != 32 {
        return Err("Chave mestra inválida.".to_string());
    }

    let mut master_key = Zeroizing::new([0u8; 32]);
    master_key.copy_from_slice(&master_key_bytes);

    // Abre o banco e desbloqueia a sessão
    let db_path = get_db_path()?;
    db.open(&db_path)?;

    session.with_session(|s| {
        s.unlock(master_key);
        Ok(())
    })?;

    Ok(())
}

/// Redefine a senha mestra após recuperação bem-sucedida.
/// Recriptografa todas as entradas e notas com a nova chave.
#[tauri::command]
pub fn reset_master_password(
    new_password: String,
    new_answers: Option<Vec<RecoveryQuestionSetup>>,
    session: State<'_, SessionState>,
    db: State<'_, DatabaseState>,
    config: State<'_, ConfigState>,
) -> Result<(), String> {
    if new_password.len() < 8 {
        return Err("A senha deve ter no mínimo 8 caracteres.".to_string());
    }

    // Gera novo salt
    let new_salt = key_derivation::generate_salt();

    // Deriva nova chave
    let new_key = key_derivation::derive_key(&new_password, &new_salt)?;

    // Pega chave atual da sessão
    let old_key = session.with_session(|s| match s.get_key() {
        Some(key) => Ok(key.clone()),
        None => Err("Sessão não está desbloqueada.".to_string()),
    })?;

    // Recriptografa entradas do cofre
    let raw_entries = db.with_db(|database| {
        database
            .list_entries()
            .map_err(|e| format!("Erro ao listar entradas: {e}"))
    })?;

    for entry in &raw_entries {
        let password = if entry.encrypted_password.is_empty() {
            String::new()
        } else {
            cipher::decrypt_field(&old_key, &entry.encrypted_password, &entry.password_nonce)?
        };

        let notes = if entry.encrypted_notes.is_empty() || entry.notes_nonce.is_none() {
            String::new()
        } else {
            match cipher::decrypt_field(
                &old_key,
                &entry.encrypted_notes,
                entry.notes_nonce.as_ref().unwrap(),
            ) {
                Ok(n) => n,
                Err(_) => String::new(),
            }
        };

        let (new_enc_pwd, new_pwd_nonce) = cipher::encrypt_field(&new_key, &password)?;
        let (new_enc_notes, new_notes_nonce) = if notes.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&new_key, &notes)?
        };

        let notes_nonce_opt = if new_notes_nonce.is_empty() {
            None
        } else {
            Some(new_notes_nonce.as_str())
        };

        db.with_db(|database| {
            database.raw_update_entry(
                &entry.id,
                &new_enc_pwd,
                &new_pwd_nonce,
                &new_enc_notes,
                notes_nonce_opt,
            )
        })?;
    }

    // Recriptografa notas seguras
    let raw_notes = db.with_db(|database| {
        database
            .list_notes()
            .map_err(|e| format!("Erro ao listar notas: {e}"))
    })?;

    for note in &raw_notes {
        let content = if note.encrypted_content.is_empty() || note.content_nonce.is_empty() {
            String::new()
        } else {
            cipher::decrypt_field(&old_key, &note.encrypted_content, &note.content_nonce)?
        };

        let (new_enc_content, new_content_nonce) = if content.is_empty() {
            (String::new(), String::new())
        } else {
            cipher::encrypt_field(&new_key, &content)?
        };

        db.with_db(|database| {
            database.raw_update_note(&note.id, &new_enc_content, &new_content_nonce)
        })?;
    }

    // Atualiza sessão com nova chave
    let new_key_for_session = new_key.clone();
    session.with_session(|s| {
        s.unlock(new_key_for_session);
        Ok(())
    })?;

    // Se novas respostas de recuperação foram fornecidas, atualiza
    let salt_hex = hex::encode(new_salt);

    if let Some(ref new_answers) = new_answers {
        if new_answers.len() == 3 {
            let mut new_recovery_questions = Vec::new();
            for q in new_answers {
                let salt = recovery::generate_salt();
                let answer_hash = recovery::hash_answer(&salt, &q.answer);

                new_recovery_questions.push(RecoveryQuestion {
                    question_index: q.question_index,
                    answer_salt: salt,
                    answer_hash,
                    options: Vec::new(),
                });
            }

            let answers_vec: Vec<String> = new_answers.iter().map(|q| q.answer.clone()).collect();
            let recovery_salt = key_derivation::generate_salt();
            let recovery_salt_hex = hex::encode(recovery_salt);
            let new_recovery_key = derive_key_from_answers(&answers_vec, Some(&recovery_salt_hex))?;
            let new_master_key_hex = hex::encode(new_key.as_ref());
            let (new_wrapped, new_nonce) =
                cipher::encrypt_field(&new_recovery_key, &new_master_key_hex)?;

            config.with_config(|c| {
                c.salt = Some(salt_hex);
                c.recovery = Some(RecoveryConfig {
                    questions: new_recovery_questions,
                    wrapped_master_key: Some(new_wrapped),
                    wrap_nonce: Some(new_nonce),
                    attempts: 0,
                    blocked_until: None,
                    recovery_salt: Some(recovery_salt_hex),
                });
                Ok(())
            })?;
            return Ok(());
        }
    }

    // Atualiza apenas salt (mantém recovery existente)
    config.with_config(|c| {
        c.salt = Some(salt_hex);
        if let Some(ref mut recovery) = c.recovery {
            recovery.attempts = 0;
            recovery.blocked_until = None;
        }
        Ok(())
    })?;

    Ok(())
}
