use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::Mutex;

/// Pergunta de recuperação configurada pelo usuário.
///
/// ## Segurança
/// Apenas o salt e o hash (SHA-256) de cada resposta são usados para verificação.
/// As `options` contêm a resposta correta em texto puro entre 4 distratores.
/// Isso é um tradeoff necessário para exibir as 5 alternativas na UI.
///
/// ## Defesa em camadas
/// - Individual: SHA-256 + salt por resposta (impede leitura direta do hash)
/// - Verificação: rate limiting progressivo (60s/300s/1800s)
/// - Chave mestra: Argon2id + AES-256-GCM (impede brute-force mesmo com respostas conhecidas)
/// - Ataque local: requer acesso ao config.json + vault.db simultaneamente
///
/// ## Variação de distratores
/// A cada chamada de `get_recovery_questions()`, a resposta correta é identificada
/// por comparação de hash contra as options armazenadas. Novos distratores são
/// gerados dinamicamente das categorias, garantindo variação entre tentativas
/// e posição aleatória da resposta correta.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RecoveryQuestion {
    /// Índice da pergunta na lista de 10 (0-9)
    pub question_index: usize,
    /// Salt individual para hash da resposta (hex)
    pub answer_salt: String,
    /// SHA-256(salt + resposta_normalizada) em hex
    pub answer_hash: String,
    /// 5 alternativas: 1 correta + 4 distratores (texto puro).
    /// A correta está entre as 5; durante o get_recovery_questions,
    /// é identificada por hash e servida com distratores frescos.
    pub options: Vec<String>,
}

/// Configuração do sistema de recuperação de senha.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RecoveryConfig {
    /// 3 perguntas configuradas pelo usuário
    pub questions: Vec<RecoveryQuestion>,
    /// Chave mestra criptografada com chave derivada das respostas (base64)
    pub wrapped_master_key: Option<String>,
    /// Nonce usado para criptografar a chave mestra (base64)
    pub wrap_nonce: Option<String>,
    /// Número de tentativas de recuperação desde o último reset
    pub attempts: u32,
    /// Timestamp (epoch seconds) até quando a recuperação está bloqueada
    pub blocked_until: Option<u64>,
    /// Salt dinâmico usado para derivação da chave de recuperação (hex, opcional para legado)
    pub recovery_salt: Option<String>,
}

impl Default for RecoveryConfig {
    fn default() -> Self {
        Self {
            questions: Vec::new(),
            wrapped_master_key: None,
            wrap_nonce: None,
            attempts: 0,
            blocked_until: None,
            recovery_salt: None,
        }
    }
}

/// Configuração do aplicativo armazenada em disco (não criptografada).
/// Contém apenas metadados não sensíveis.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AppConfig {
    /// Salt para derivação de chave (hex)
    pub salt: Option<String>,
    /// Tempo de bloqueio automático em minutos
    pub auto_lock_minutes: u32,
    /// Tempo de limpeza do clipboard em segundos
    pub clipboard_clear_seconds: u32,
    /// Tema: "dark", "light", "system"
    pub theme: String,
    /// Modo de privacidade (oculta senhas na listagem)
    pub privacy_mode: bool,
    /// Modo somente leitura (impede edições)
    pub read_only_mode: bool,
    /// Configurações padrão do gerador de senhas
    pub password_gen_length: u8,
    pub password_gen_uppercase: bool,
    pub password_gen_lowercase: bool,
    pub password_gen_numbers: bool,
    pub password_gen_special: bool,
    pub password_gen_special_chars: String,
    pub password_gen_exclude_ambiguous: bool,
    /// Configuração de recuperação de senha (opcional)
    pub recovery: Option<RecoveryConfig>,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            salt: None,
            auto_lock_minutes: 5,
            clipboard_clear_seconds: 30,
            theme: "dark".to_string(),
            privacy_mode: false,
            read_only_mode: false,
            password_gen_length: 24,
            password_gen_uppercase: true,
            password_gen_lowercase: true,
            password_gen_numbers: true,
            password_gen_special: true,
            password_gen_special_chars: "!@#$%^&*()-_=+[]{}|;:,.<>?".to_string(),
            password_gen_exclude_ambiguous: false,
            recovery: None,
        }
    }
}

impl AppConfig {
    /// Carrega a configuração do arquivo. Se não existir, retorna configuração padrão.
    pub fn load(path: &Path) -> Result<Self, String> {
        if path.exists() {
            let content =
                std::fs::read_to_string(path).map_err(|e| format!("Erro ao ler config: {e}"))?;
            serde_json::from_str(&content).map_err(|e| format!("Erro ao parsear config: {e}"))
        } else {
            Ok(Self::default())
        }
    }

    /// Salva a configuração no arquivo.
    pub fn save(&self, path: &Path) -> Result<(), String> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| format!("Erro ao criar diretório: {e}"))?;
        }
        let content = serde_json::to_string_pretty(self)
            .map_err(|e| format!("Erro ao serializar config: {e}))"))?;
        std::fs::write(path, content).map_err(|e| format!("Erro ao salvar config: {e}"))
    }
}

/// Wrapper thread-safe para AppConfig.
pub struct ConfigState {
    pub config: Mutex<AppConfig>,
    pub path: std::path::PathBuf,
}

impl ConfigState {
    pub fn new(path: std::path::PathBuf) -> Result<Self, String> {
        let config = AppConfig::load(&path)?;
        Ok(Self {
            config: Mutex::new(config),
            path,
        })
    }

    pub fn with_config<F, T>(&self, f: F) -> Result<T, String>
    where
        F: FnOnce(&mut AppConfig) -> Result<T, String>,
    {
        let mut guard = self
            .config
            .lock()
            .map_err(|e| format!("Erro de lock: {e}"))?;
        let result = f(&mut guard)?;
        // Auto-save após modificação
        guard.save(&self.path)?;
        Ok(result)
    }

    pub fn read<F, T>(&self, f: F) -> Result<T, String>
    where
        F: FnOnce(&AppConfig) -> T,
    {
        let guard = self
            .config
            .lock()
            .map_err(|e| format!("Erro de lock: {e}"))?;
        Ok(f(&guard))
    }
}
