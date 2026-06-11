use rusqlite::{params, Connection, Result as SqlResult};
use std::path::Path;
use std::sync::Mutex;

use super::models::{
    Category, CreateEntryPayload, CreateNotePayload, SecureNote, UpdateEntryPayload,
    UpdateNotePayload, VaultEntry,
};

/// Gerenciador do banco de dados SQLite.
pub struct Database {
    conn: Connection,
}

impl Database {
    /// Abre (ou cria) o banco de dados no caminho especificado
    /// e executa as migrações pendentes.
    pub fn open(path: &Path) -> SqlResult<Self> {
        // Garante que o diretório pai existe
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).ok();
        }

        let conn = Connection::open(path)?;

        // Configurações de segurança e performance
        conn.execute_batch(
            "PRAGMA journal_mode=WAL;
             PRAGMA foreign_keys=ON;
             PRAGMA busy_timeout=5000;",
        )?;

        let db = Self { conn };
        db.run_migrations()?;
        Ok(db)
    }

    /// Executa as migrações pendentes.
    fn run_migrations(&self) -> SqlResult<()> {
        // Migration 001 - Schema inicial
        self.conn.execute_batch(include_str!("../../migrations/001_initial.sql"))?;

        // Migration 002 - Adiciona campo url
        let has_url_column: bool = self
            .conn
            .prepare("SELECT url FROM vault_entries LIMIT 0")
            .is_ok();
        if !has_url_column {
            self.conn.execute_batch(include_str!("../../migrations/002_url_field.sql"))?;
        }

        // Migration 003 - Tabela secure_notes
        let has_notes_table: bool = self
            .conn
            .prepare("SELECT id FROM secure_notes LIMIT 0")
            .is_ok();
        if !has_notes_table {
            self.conn.execute_batch(include_str!("../../migrations/003_secure_notes.sql"))?;
        }

        Ok(())
    }

    // ─── CRUD de Entradas ───────────────────────────────────

    /// Cria uma nova entrada no cofre.
    pub fn create_entry(
        &self,
        payload: &CreateEntryPayload,
        encrypted_password: &str,
        password_nonce: &str,
        encrypted_notes: &str,
        notes_nonce: Option<&str>,
    ) -> SqlResult<i64> {
        self.conn.execute(
            "INSERT INTO vault_entries (service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, category_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                payload.service_name,
                payload.login,
                encrypted_password,
                password_nonce,
                encrypted_notes,
                notes_nonce,
                payload.url,
                payload.category_id,
            ],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    /// Lista todas as entradas (criptografadas).
    pub fn list_entries(&self) -> SqlResult<Vec<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, created_at, updated_at
             FROM vault_entries
             ORDER BY updated_at DESC",
        )?;

        let entries = stmt.query_map([], |row| {
            Ok(VaultEntry {
                id: row.get(0)?,
                service_name: row.get(1)?,
                login: row.get(2)?,
                encrypted_password: row.get(3)?,
                password_nonce: row.get(4)?,
                encrypted_notes: row.get(5)?,
                notes_nonce: row.get(6)?,
                url: row.get(7)?,
                category_id: row.get(8)?,
                created_at: row.get(9)?,
                updated_at: row.get(10)?,
            })
        })?;

        entries.collect()
    }

    /// Busca entradas por nome do serviço, login ou URL.
    pub fn search_entries(&self, query: &str) -> SqlResult<Vec<VaultEntry>> {
        let pattern = format!("%{}%", query);
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, created_at, updated_at
             FROM vault_entries
             WHERE service_name LIKE ?1 OR login LIKE ?1 OR url LIKE ?1
             ORDER BY updated_at DESC",
        )?;

        let entries = stmt.query_map(params![pattern], |row| {
            Ok(VaultEntry {
                id: row.get(0)?,
                service_name: row.get(1)?,
                login: row.get(2)?,
                encrypted_password: row.get(3)?,
                password_nonce: row.get(4)?,
                encrypted_notes: row.get(5)?,
                notes_nonce: row.get(6)?,
                url: row.get(7)?,
                category_id: row.get(8)?,
                created_at: row.get(9)?,
                updated_at: row.get(10)?,
            })
        })?;

        entries.collect()
    }

    /// Obtém uma entrada específica pelo ID.
    pub fn get_entry(&self, id: i64) -> SqlResult<Option<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, created_at, updated_at
             FROM vault_entries WHERE id = ?1",
        )?;

        let mut rows = stmt.query_map(params![id], |row| {
            Ok(VaultEntry {
                id: row.get(0)?,
                service_name: row.get(1)?,
                login: row.get(2)?,
                encrypted_password: row.get(3)?,
                password_nonce: row.get(4)?,
                encrypted_notes: row.get(5)?,
                notes_nonce: row.get(6)?,
                url: row.get(7)?,
                category_id: row.get(8)?,
                created_at: row.get(9)?,
                updated_at: row.get(10)?,
            })
        })?;

        match rows.next() {
            Some(Ok(entry)) => Ok(Some(entry)),
            _ => Ok(None),
        }
    }

    /// Atualiza uma entrada existente.
    pub fn update_entry(
        &self,
        payload: &UpdateEntryPayload,
        encrypted_password: &str,
        password_nonce: &str,
        encrypted_notes: &str,
        notes_nonce: Option<&str>,
    ) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE vault_entries
             SET service_name = ?1, login = ?2, encrypted_password = ?3, password_nonce = ?4,
                 encrypted_notes = ?5, notes_nonce = ?6, url = ?7, category_id = ?8, updated_at = datetime('now')
             WHERE id = ?9",
            params![
                payload.service_name,
                payload.login,
                encrypted_password,
                password_nonce,
                encrypted_notes,
                notes_nonce,
                payload.url,
                payload.category_id,
                payload.id,
            ],
        )?;
        Ok(())
    }

    /// Exclui uma entrada pelo ID.
    pub fn delete_entry(&self, id: i64) -> SqlResult<()> {
        self.conn.execute("DELETE FROM vault_entries WHERE id = ?1", params![id])?;
        Ok(())
    }

    // ─── Categorias ────────────────────────────────────────

    /// Lista todas as categorias.
    pub fn list_categories(&self) -> SqlResult<Vec<Category>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, color, icon, sort_order FROM categories ORDER BY sort_order",
        )?;

        let categories = stmt.query_map([], |row| {
            Ok(Category {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
                icon: row.get(3)?,
                sort_order: row.get(4)?,
            })
        })?;

        categories.collect()
    }

    /// Obtém o nome da categoria pelo ID.
    pub fn get_category_name(&self, id: i64) -> SqlResult<Option<String>> {
        let mut stmt = self.conn.prepare("SELECT name FROM categories WHERE id = ?1")?;
        let mut rows = stmt.query_map(params![id], |row| row.get::<_, String>(0))?;
        match rows.next() {
            Some(Ok(name)) => Ok(Some(name)),
            _ => Ok(None),
        }
    }

    // ─── CRUD de Notas Seguras ─────────────────────────────

    /// Cria uma nova nota criptografada.
    pub fn create_note(
        &self,
        payload: &CreateNotePayload,
        encrypted_content: &str,
        content_nonce: &str,
    ) -> SqlResult<i64> {
        self.conn.execute(
            "INSERT INTO secure_notes (title, encrypted_content, content_nonce, category)
             VALUES (?1, ?2, ?3, ?4)",
            params![
                payload.title,
                encrypted_content,
                content_nonce,
                payload.category,
            ],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    /// Lista todas as notas (ordenadas por updated_at DESC).
    pub fn list_notes(&self) -> SqlResult<Vec<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at
             FROM secure_notes
             ORDER BY updated_at DESC",
        )?;

        let notes = stmt.query_map([], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
            })
        })?;

        notes.collect()
    }

    /// Busca notas por título ou categoria.
    pub fn search_notes(&self, query: &str) -> SqlResult<Vec<SecureNote>> {
        let pattern = format!("%{}%", query);
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at
             FROM secure_notes
             WHERE title LIKE ?1 OR category LIKE ?1
             ORDER BY updated_at DESC",
        )?;

        let notes = stmt.query_map(params![pattern], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
            })
        })?;

        notes.collect()
    }

    /// Obtém uma nota pelo ID.
    pub fn get_note(&self, id: i64) -> SqlResult<Option<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, created_at, updated_at
             FROM secure_notes WHERE id = ?1",
        )?;

        let mut rows = stmt.query_map(params![id], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
            })
        })?;

        match rows.next() {
            Some(Ok(note)) => Ok(Some(note)),
            _ => Ok(None),
        }
    }

    /// Atualiza uma nota existente.
    pub fn update_note(
        &self,
        payload: &UpdateNotePayload,
        encrypted_content: &str,
        content_nonce: &str,
    ) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE secure_notes
             SET title = ?1, encrypted_content = ?2, content_nonce = ?3,
                 category = ?4, updated_at = datetime('now')
             WHERE id = ?5",
            params![
                payload.title,
                encrypted_content,
                content_nonce,
                payload.category,
                payload.id,
            ],
        )?;
        Ok(())
    }

    /// Exclui uma nota pelo ID.
    pub fn delete_note(&self, id: i64) -> SqlResult<()> {
        self.conn.execute("DELETE FROM secure_notes WHERE id = ?1", params![id])?;
        Ok(())
    }

    /// Remove todos os dados do banco (entradas e notas).
    /// Usado durante restauração de backup com substituição.
    pub fn clear_all_data(&self) -> SqlResult<()> {
        self.conn.execute_batch(
            "DELETE FROM vault_entries;
             DELETE FROM secure_notes;"
        )?;
        Ok(())
    }

    // ─── Raw Updates (usado na recriptografia) ──────────

    /// Atualiza apenas os campos criptografados de uma entrada (pelo ID).
    /// Usado durante redefinição de senha mestra.
    pub fn raw_update_entry(
        &self,
        id: i64,
        encrypted_password: &str,
        password_nonce: &str,
        encrypted_notes: &str,
        notes_nonce: Option<&str>,
    ) -> Result<(), String> {
        self.conn
            .execute(
                "UPDATE vault_entries
                 SET encrypted_password = ?1, password_nonce = ?2,
                     encrypted_notes = ?3, notes_nonce = ?4, updated_at = datetime('now')
                 WHERE id = ?5",
                params![
                    encrypted_password,
                    password_nonce,
                    encrypted_notes,
                    notes_nonce,
                    id,
                ],
            )
            .map_err(|e| format!("Erro ao atualizar entrada (raw): {e}"))?;
        Ok(())
    }

    /// Atualiza apenas o conteúdo criptografado de uma nota (pelo ID).
    /// Usado durante redefinição de senha mestra.
    pub fn raw_update_note(
        &self,
        id: i64,
        encrypted_content: &str,
        content_nonce: &str,
    ) -> Result<(), String> {
        self.conn
            .execute(
                "UPDATE secure_notes
                 SET encrypted_content = ?1, content_nonce = ?2, updated_at = datetime('now')
                 WHERE id = ?3",
                params![encrypted_content, content_nonce, id],
            )
            .map_err(|e| format!("Erro ao atualizar nota (raw): {e}"))?;
        Ok(())
    }

}

/// Wrapper thread-safe para Database usando Mutex.
pub struct DatabaseState {
    pub db: Mutex<Option<Database>>,
}

impl DatabaseState {
    pub fn new() -> Self {
        Self {
            db: Mutex::new(None),
        }
    }

    /// Retorna uma referência ao banco, ou erro se não estiver aberto.
    pub fn with_db<F, T>(&self, f: F) -> Result<T, String>
    where
        F: FnOnce(&Database) -> Result<T, String>,
    {
        let guard = self.db.lock().map_err(|e| format!("Erro de lock: {e}"))?;
        match guard.as_ref() {
            Some(db) => f(db),
            None => Err("Banco de dados não está aberto. Desbloqueie o cofre primeiro.".to_string()),
        }
    }

    /// Abre o banco com o caminho especificado.
    pub fn open(&self, path: &Path) -> Result<(), String> {
        let database = Database::open(path).map_err(|e| format!("Erro ao abrir banco: {e}"))?;
        let mut guard = self.db.lock().map_err(|e| format!("Erro de lock: {e}"))?;
        *guard = Some(database);
        Ok(())
    }

    /// Fecha o banco atual.
    pub fn close(&self) -> Result<(), String> {
        let mut guard = self.db.lock().map_err(|e| format!("Erro de lock: {e}"))?;
        *guard = None;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_db() -> Database {
        Database::open(Path::new(":memory:")).unwrap()
    }

    #[test]
    fn test_create_and_list_entries() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            service_name: "GitHub".to_string(),
            login: "user@email.com".to_string(),
            password: String::new(),
            notes: Some("Nota de teste".to_string()),
            url: "https://github.com".to_string(),
            category_id: Some(1),
        };

        let id = db
            .create_entry(&payload, "enc_pwd", "nonce1", "enc_notes", Some("nonce2"))
            .unwrap();
        assert!(id > 0);

        let entries = db.list_entries().unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].service_name, "GitHub");
        assert_eq!(entries[0].url, "https://github.com");
    }

    #[test]
    fn test_search_entries() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            service_name: "Google".to_string(),
            login: "user@gmail.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://google.com".to_string(),
            category_id: Some(1),
        };
        db.create_entry(&payload, "enc", "n1", "", None).unwrap();

        let results = db.search_entries("Google").unwrap();
        assert_eq!(results.len(), 1);

        let results = db.search_entries("gmail").unwrap();
        assert_eq!(results.len(), 1);

        let results = db.search_entries("google.com").unwrap();
        assert_eq!(results.len(), 1);

        let results = db.search_entries("NotFound").unwrap();
        assert_eq!(results.len(), 0);
    }

    #[test]
    fn test_update_entry() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            service_name: "OldName".to_string(),
            login: "old@email.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://old.com".to_string(),
            category_id: None,
        };
        let id = db
            .create_entry(&payload, "enc_pwd", "nonce", "", None)
            .unwrap();

        let update = UpdateEntryPayload {
            id,
            service_name: "NewName".to_string(),
            login: "new@email.com".to_string(),
            password: String::new(),
            notes: Some("Updated".to_string()),
            url: "https://new.com".to_string(),
            category_id: Some(2),
        };
        db.update_entry(&update, "new_enc", "new_nonce", "enc_notes", Some("n_nonce"))
            .unwrap();

        let entry = db.get_entry(id).unwrap().unwrap();
        assert_eq!(entry.service_name, "NewName");
        assert_eq!(entry.url, "https://new.com");
    }

    #[test]
    fn test_delete_entry() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            service_name: "ToDelete".to_string(),
            login: "del@email.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://todelete.com".to_string(),
            category_id: None,
        };
        let id = db
            .create_entry(&payload, "enc_pwd", "nonce", "", None)
            .unwrap();

        db.delete_entry(id).unwrap();
        assert!(db.get_entry(id).unwrap().is_none());
    }

    #[test]
    fn test_list_categories() {
        let db = create_test_db();
        let categories = db.list_categories().unwrap();
        assert!(categories.len() >= 4); // 4 default categories
        assert_eq!(categories[0].name, "Pessoal");
    }
}
