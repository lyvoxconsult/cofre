use std::sync::Mutex;
use std::time::Instant;
use zeroize::Zeroizing;

/// Estado da sessão do cofre.
pub struct Session {
    /// Chave de criptografia derivada (None = bloqueado)
    pub encryption_key: Option<Zeroizing<[u8; 32]>>,
    /// Timestamp do último desbloqueio
    pub unlocked_at: Option<Instant>,
    /// Timestamp da última atividade do usuário
    pub last_activity: Option<Instant>,
    /// Número de tentativas de desbloqueio (para rate limiting)
    pub failed_attempts: u32,
}

impl Session {
    pub fn new() -> Self {
        Self {
            encryption_key: None,
            unlocked_at: None,
            last_activity: None,
            failed_attempts: 0,
        }
    }

    /// Define a chave e marca como desbloqueado
    pub fn unlock(&mut self, key: Zeroizing<[u8; 32]>) {
        self.encryption_key = Some(key);
        self.unlocked_at = Some(Instant::now());
        self.last_activity = Some(Instant::now());
        self.failed_attempts = 0;
    }

    /// Bloqueia o cofre: zeroiza a chave e limpa o estado
    pub fn lock(&mut self) {
        self.encryption_key = None;
        self.unlocked_at = None;
        self.last_activity = None;
    }

    /// Verifica se o cofre está desbloqueado
    pub fn is_unlocked(&self) -> bool {
        self.encryption_key.is_some()
    }

    /// Obtém referência à chave, se desbloqueado
    pub fn get_key(&self) -> Option<&Zeroizing<[u8; 32]>> {
        self.encryption_key.as_ref()
    }

    /// Registra atividade do usuário (para reset do timer de auto-lock)
    pub fn record_activity(&mut self) {
        self.last_activity = Some(Instant::now());
    }

    /// Verifica se o tempo de inatividade excedeu o limite
    pub fn is_idle_expired(&self, timeout_minutes: u32) -> bool {
        match (self.last_activity, self.is_unlocked()) {
            (Some(last), true) => {
                let elapsed = last.elapsed();
                elapsed.as_secs() > (timeout_minutes as u64 * 60)
            }
            _ => false,
        }
    }
}

/// Wrapper thread-safe para Session.
pub struct SessionState {
    pub session: Mutex<Session>,
}

impl SessionState {
    pub fn new() -> Self {
        Self {
            session: Mutex::new(Session::new()),
        }
    }

    pub fn with_session<F, T>(&self, f: F) -> Result<T, String>
    where
        F: FnOnce(&mut Session) -> Result<T, String>,
    {
        let mut guard = self.session.lock().map_err(|e| format!("Erro de lock: {e}"))?;
        f(&mut guard)
    }
}
