use rusqlite::{params, Connection, Result as SqlResult};
use std::path::Path;
use std::sync::Mutex;

use super::models::{
    Attachment, Category, CreateEntryPayload, CreateNotePayload, SecureNote, UpdateEntryPayload,
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
             PRAGMA busy_timeout=5000;",
        )?;

        let db = Self { conn };
        db.run_migrations()?;

        // Habilita foreign keys apenas APÓS as migrações concluírem
        db.conn.execute_batch("PRAGMA foreign_keys=ON;")?;

        Ok(db)
    }

    /// Executa as migrações pendentes.
    fn run_migrations(&self) -> SqlResult<()> {
        // Migration 001 - Schema inicial
        self.conn
            .execute_batch(include_str!("../../migrations/001_initial.sql"))?;

        // Migration 002 - Adiciona campo url
        let has_url_column: bool = self
            .conn
            .prepare("SELECT url FROM vault_entries LIMIT 0")
            .is_ok();
        if !has_url_column {
            self.conn
                .execute_batch(include_str!("../../migrations/002_url_field.sql"))?;
        }

        // Migration 003 - Tabela secure_notes
        let has_notes_table: bool = self
            .conn
            .prepare("SELECT id FROM secure_notes LIMIT 0")
            .is_ok();
        if !has_notes_table {
            self.conn
                .execute_batch(include_str!("../../migrations/003_secure_notes.sql"))?;
        }

        // Migration 004 - UUIDs e Sync
        let has_is_favorite: bool = self
            .conn
            .prepare("SELECT is_favorite FROM vault_entries LIMIT 0")
            .is_ok();
        if !has_is_favorite {
            self.conn
                .execute_batch(include_str!("../../migrations/004_uuid_and_sync.sql"))?;
        }

        // Migration 005 - Attachments
        let has_attachments_table: bool = self
            .conn
            .prepare("SELECT id FROM attachments LIMIT 0")
            .is_ok();
        if !has_attachments_table {
            self.conn
                .execute_batch(include_str!("../../migrations/005_attachments.sql"))?;
        }
        self.ensure_column("attachments", "device_id", "TEXT DEFAULT NULL")?;
        self.ensure_column(
            "attachments",
            "last_modified_device_id",
            "TEXT DEFAULT NULL",
        )?;

        // Migration 006 - Media Vault
        let has_media_vault_table: bool = self
            .conn
            .prepare("SELECT id FROM media_vault LIMIT 0")
            .is_ok();
        if !has_media_vault_table {
            self.conn
                .execute_batch(include_str!("../../migrations/006_media_vault.sql"))?;
        }

        Ok(())
    }

    fn ensure_column(&self, table: &str, column: &str, definition: &str) -> SqlResult<()> {
        let exists = {
            let mut stmt = self.conn.prepare(&format!("PRAGMA table_info({table})"))?;
            let columns = stmt.query_map([], |row| row.get::<_, String>(1))?;
            let mut found = false;
            for name in columns {
                if name? == column {
                    found = true;
                    break;
                }
            }
            found
        };

        if !exists {
            self.conn.execute_batch(&format!(
                "ALTER TABLE {table} ADD COLUMN {column} {definition}"
            ))?;
        }
        Ok(())
    }

    fn valid_category_id(&self, category_id: Option<&str>) -> SqlResult<Option<String>> {
        let Some(id) = category_id else {
            return Ok(None);
        };

        let exists = self
            .conn
            .query_row(
                "SELECT 1 FROM categories WHERE id = ?1 AND deleted_at IS NULL LIMIT 1",
                params![id],
                |_| Ok(()),
            )
            .is_ok();

        Ok(exists.then(|| id.to_string()))
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
    ) -> SqlResult<String> {
        let entry_id = payload
            .id
            .clone()
            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());

        self.conn.execute(
            "INSERT INTO vault_entries (id, service_name, login, encrypted_password, password_nonce, encrypted_notes, notes_nonce, url, category_id, is_favorite)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                entry_id,
                payload.service_name,
                payload.login,
                encrypted_password,
                password_nonce,
                encrypted_notes,
                notes_nonce,
                payload.url,
                payload.category_id,
                payload.is_favorite.unwrap_or(false) as i32,
            ],
        )?;
        Ok(entry_id)
    }

    /// Lista todas as entradas (criptografadas), ignorando as excluídas soft-delete.
    pub fn list_entries(&self) -> SqlResult<Vec<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, is_favorite,
                    created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM vault_entries
             WHERE deleted_at IS NULL
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
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;

        entries.collect()
    }

    /// Busca entradas por nome do serviço, login ou URL.
    pub fn search_entries(&self, query: &str) -> SqlResult<Vec<VaultEntry>> {
        let pattern = format!("%{}%", query);
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, is_favorite,
                    created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM vault_entries
             WHERE deleted_at IS NULL AND (service_name LIKE ?1 OR login LIKE ?1 OR url LIKE ?1)
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
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;

        entries.collect()
    }

    /// Obtém uma entrada específica pelo ID.
    pub fn get_entry(&self, id: &str) -> SqlResult<Option<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, is_favorite,
                    created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM vault_entries WHERE id = ?1 AND deleted_at IS NULL",
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
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
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
        let current_is_favorite: i32 = self
            .conn
            .query_row(
                "SELECT is_favorite FROM vault_entries WHERE id = ?1",
                params![payload.id],
                |row| row.get(0),
            )
            .unwrap_or(0);

        let new_is_favorite = payload
            .is_favorite
            .map(|f| f as i32)
            .unwrap_or(current_is_favorite);

        self.conn.execute(
            "UPDATE vault_entries
             SET service_name = ?1, login = ?2, encrypted_password = ?3, password_nonce = ?4,
                 encrypted_notes = ?5, notes_nonce = ?6, url = ?7, category_id = ?8, is_favorite = ?9, updated_at = datetime('now')
             WHERE id = ?10",
            params![
                payload.service_name,
                payload.login,
                encrypted_password,
                password_nonce,
                encrypted_notes,
                notes_nonce,
                payload.url,
                payload.category_id,
                new_is_favorite,
                payload.id,
            ],
        )?;
        Ok(())
    }

    /// Exclui (soft delete) uma entrada pelo ID.
    pub fn delete_entry(&self, id: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE vault_entries SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![id]
        )?;
        Ok(())
    }

    // ─── Categorias ────────────────────────────────────────

    /// Lista todas as categorias.
    pub fn list_categories(&self) -> SqlResult<Vec<Category>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, color, icon, sort_order, created_at, updated_at, deleted_at 
             FROM categories 
             WHERE deleted_at IS NULL
             ORDER BY sort_order",
        )?;

        let categories = stmt.query_map([], |row| {
            Ok(Category {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
                icon: row.get(3)?,
                sort_order: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
                deleted_at: row.get(7)?,
            })
        })?;

        categories.collect()
    }

    /// Obtém o nome da categoria pelo ID.
    pub fn get_category_name(&self, id: &str) -> SqlResult<Option<String>> {
        let mut stmt = self
            .conn
            .prepare("SELECT name FROM categories WHERE id = ?1 AND deleted_at IS NULL")?;
        let mut rows = stmt.query_map(params![id], |row| row.get::<_, String>(0))?;
        match rows.next() {
            Some(Ok(name)) => Ok(Some(name)),
            _ => Ok(None),
        }
    }

    /// Cria uma nova categoria personalizada.
    pub fn create_category(&self, name: &str, color: &str, icon: &str) -> SqlResult<String> {
        let id = uuid::Uuid::new_v4().to_string();
        self.conn.execute(
            "INSERT INTO categories (id, name, color, icon, sort_order)
             VALUES (?1, ?2, ?3, ?4, 0)",
            params![id, name, color, icon],
        )?;
        Ok(id)
    }

    /// Atualiza uma categoria existente.
    pub fn update_category(&self, id: &str, name: &str, color: &str, icon: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE categories
             SET name = ?1, color = ?2, icon = ?3, updated_at = datetime('now')
             WHERE id = ?4",
            params![name, color, icon, id],
        )?;
        Ok(())
    }

    /// Exclui (soft delete) uma categoria pelo ID.
    pub fn delete_category(&self, id: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE categories SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![id],
        )?;
        Ok(())
    }

    // ─── CRUD de Notas Seguras ─────────────────────────────

    /// Cria uma nova nota criptografada.
    pub fn create_note(
        &self,
        payload: &CreateNotePayload,
        encrypted_content: &str,
        content_nonce: &str,
    ) -> SqlResult<String> {
        let note_id = payload
            .id
            .clone()
            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());

        self.conn.execute(
            "INSERT INTO secure_notes (id, title, encrypted_content, content_nonce, category, is_favorite)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                note_id,
                payload.title,
                encrypted_content,
                content_nonce,
                payload.category,
                payload.is_favorite.unwrap_or(false) as i32,
            ],
        )?;
        Ok(note_id)
    }

    /// Lista todas as notas (ordenadas por updated_at DESC).
    pub fn list_notes(&self) -> SqlResult<Vec<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM secure_notes
             WHERE deleted_at IS NULL
             ORDER BY updated_at DESC",
        )?;

        let notes = stmt.query_map([], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                is_favorite: row.get::<_, i32>(5)? != 0,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                deleted_at: row.get(8)?,
                device_id: row.get(9)?,
                last_modified_device_id: row.get(10)?,
            })
        })?;

        notes.collect()
    }

    /// Busca notas por título ou categoria.
    pub fn search_notes(&self, query: &str) -> SqlResult<Vec<SecureNote>> {
        let pattern = format!("%{}%", query);
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM secure_notes
             WHERE deleted_at IS NULL AND (title LIKE ?1 OR category LIKE ?1)
             ORDER BY updated_at DESC",
        )?;

        let notes = stmt.query_map(params![pattern], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                is_favorite: row.get::<_, i32>(5)? != 0,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                deleted_at: row.get(8)?,
                device_id: row.get(9)?,
                last_modified_device_id: row.get(10)?,
            })
        })?;

        notes.collect()
    }

    /// Obtém uma nota pelo ID.
    pub fn get_note(&self, id: &str) -> SqlResult<Option<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM secure_notes WHERE id = ?1 AND deleted_at IS NULL",
        )?;

        let mut rows = stmt.query_map(params![id], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                is_favorite: row.get::<_, i32>(5)? != 0,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                deleted_at: row.get(8)?,
                device_id: row.get(9)?,
                last_modified_device_id: row.get(10)?,
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
        let current_is_favorite: i32 = self
            .conn
            .query_row(
                "SELECT is_favorite FROM secure_notes WHERE id = ?1",
                params![payload.id],
                |row| row.get(0),
            )
            .unwrap_or(0);

        let new_is_favorite = payload
            .is_favorite
            .map(|f| f as i32)
            .unwrap_or(current_is_favorite);

        self.conn.execute(
            "UPDATE secure_notes
             SET title = ?1, encrypted_content = ?2, content_nonce = ?3,
                 category = ?4, is_favorite = ?5, updated_at = datetime('now')
             WHERE id = ?6",
            params![
                payload.title,
                encrypted_content,
                content_nonce,
                payload.category,
                new_is_favorite,
                payload.id,
            ],
        )?;
        Ok(())
    }

    /// Exclui (soft delete) uma nota pelo ID.
    pub fn delete_note(&self, id: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE secure_notes SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![id]
        )?;
        Ok(())
    }

    /// Remove todos os dados do banco (entradas e notas).
    /// Usado durante restauração de backup com substituição.
    pub fn clear_all_data(&self) -> SqlResult<()> {
        self.conn.execute_batch(
            "DELETE FROM vault_entries;
             DELETE FROM secure_notes;",
        )?;
        Ok(())
    }

    // ─── Métodos de Sincronização ────────────────────────

    /// Lista todas as categorias, incluindo as deletadas (soft delete).
    pub fn list_all_categories_for_sync(&self) -> SqlResult<Vec<Category>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, color, icon, sort_order, created_at, updated_at, deleted_at FROM categories",
        )?;
        let categories = stmt.query_map([], |row| {
            Ok(Category {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
                icon: row.get(3)?,
                sort_order: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
                deleted_at: row.get(7)?,
            })
        })?;
        categories.collect()
    }

    /// Lista todas as entradas, incluindo as deletadas (soft delete).
    pub fn list_all_entries_for_sync(&self) -> SqlResult<Vec<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, is_favorite,
                    created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM vault_entries",
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
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        entries.collect()
    }

    /// Lista todas as notas, incluindo as deletadas (soft delete).
    pub fn list_all_notes_for_sync(&self) -> SqlResult<Vec<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM secure_notes",
        )?;
        let notes = stmt.query_map([], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                is_favorite: row.get::<_, i32>(5)? != 0,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                deleted_at: row.get(8)?,
                device_id: row.get(9)?,
                last_modified_device_id: row.get(10)?,
            })
        })?;
        notes.collect()
    }

    /// Obtém uma categoria específica pelo ID, incluindo se deletada.
    pub fn get_category_include_deleted(&self, id: &str) -> SqlResult<Option<Category>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, color, icon, sort_order, created_at, updated_at, deleted_at FROM categories WHERE id = ?1",
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(Category {
                id: row.get(0)?,
                name: row.get(1)?,
                color: row.get(2)?,
                icon: row.get(3)?,
                sort_order: row.get(4)?,
                created_at: row.get(5)?,
                updated_at: row.get(6)?,
                deleted_at: row.get(7)?,
            })
        })?;
        match rows.next() {
            Some(Ok(cat)) => Ok(Some(cat)),
            _ => Ok(None),
        }
    }

    /// Obtém uma entrada específica pelo ID, incluindo se deletada.
    pub fn get_entry_include_deleted(&self, id: &str) -> SqlResult<Option<VaultEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, service_name, login, encrypted_password, password_nonce,
                    encrypted_notes, notes_nonce, url, category_id, is_favorite,
                    created_at, updated_at, deleted_at, device_id, last_modified_device_id
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
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        match rows.next() {
            Some(Ok(entry)) => Ok(Some(entry)),
            _ => Ok(None),
        }
    }

    /// Obtém uma nota específica pelo ID, incluindo se deletada.
    pub fn get_note_include_deleted(&self, id: &str) -> SqlResult<Option<SecureNote>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, encrypted_content, content_nonce, category, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM secure_notes WHERE id = ?1",
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(SecureNote {
                id: row.get(0)?,
                title: row.get(1)?,
                encrypted_content: row.get(2)?,
                content_nonce: row.get(3)?,
                category: row.get(4)?,
                is_favorite: row.get::<_, i32>(5)? != 0,
                created_at: row.get(6)?,
                updated_at: row.get(7)?,
                deleted_at: row.get(8)?,
                device_id: row.get(9)?,
                last_modified_device_id: row.get(10)?,
            })
        })?;
        match rows.next() {
            Some(Ok(note)) => Ok(Some(note)),
            _ => Ok(None),
        }
    }

    /// Insere uma categoria completa para sincronização.
    pub fn insert_category_sync(&self, cat: &Category) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO categories (id, name, color, icon, sort_order, created_at, updated_at, deleted_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                cat.id,
                cat.name,
                cat.color,
                cat.icon,
                cat.sort_order,
                cat.created_at,
                cat.updated_at,
                cat.deleted_at,
            ],
        )?;
        Ok(())
    }

    /// Atualiza uma categoria completa para sincronização.
    pub fn update_category_sync(&self, cat: &Category) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE categories
             SET name = ?1, color = ?2, icon = ?3, sort_order = ?4, created_at = ?5, updated_at = ?6, deleted_at = ?7
             WHERE id = ?8",
            params![
                cat.name,
                cat.color,
                cat.icon,
                cat.sort_order,
                cat.created_at,
                cat.updated_at,
                cat.deleted_at,
                cat.id,
            ],
        )?;
        Ok(())
    }

    /// Insere uma entrada completa para sincronização.
    pub fn insert_entry_sync(&self, entry: &VaultEntry) -> SqlResult<()> {
        let category_id = self.valid_category_id(entry.category_id.as_deref())?;
        self.conn.execute(
            "INSERT INTO vault_entries (id, service_name, login, encrypted_password, password_nonce,
                                       encrypted_notes, notes_nonce, url, category_id, is_favorite,
                                       created_at, updated_at, deleted_at, device_id, last_modified_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
            params![
                entry.id,
                entry.service_name,
                entry.login,
                entry.encrypted_password,
                entry.password_nonce,
                entry.encrypted_notes,
                entry.notes_nonce,
                entry.url,
                category_id,
                entry.is_favorite as i32,
                entry.created_at,
                entry.updated_at,
                entry.deleted_at,
                entry.device_id,
                entry.last_modified_device_id,
            ],
        )?;
        Ok(())
    }

    /// Atualiza uma entrada completa para sincronização.
    pub fn update_entry_sync(&self, entry: &VaultEntry) -> SqlResult<()> {
        let category_id = self.valid_category_id(entry.category_id.as_deref())?;
        self.conn.execute(
            "UPDATE vault_entries
             SET service_name = ?1, login = ?2, encrypted_password = ?3, password_nonce = ?4,
                 encrypted_notes = ?5, notes_nonce = ?6, url = ?7, category_id = ?8, is_favorite = ?9,
                 created_at = ?10, updated_at = ?11, deleted_at = ?12, device_id = ?13, last_modified_device_id = ?14
             WHERE id = ?15",
            params![
                entry.service_name,
                entry.login,
                entry.encrypted_password,
                entry.password_nonce,
                entry.encrypted_notes,
                entry.notes_nonce,
                entry.url,
                category_id,
                entry.is_favorite as i32,
                entry.created_at,
                entry.updated_at,
                entry.deleted_at,
                entry.device_id,
                entry.last_modified_device_id,
                entry.id,
            ],
        )?;
        Ok(())
    }

    /// Insere uma nota completa para sincronização.
    pub fn insert_note_sync(&self, note: &SecureNote) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO secure_notes (id, title, encrypted_content, content_nonce, category, is_favorite,
                                      created_at, updated_at, deleted_at, device_id, last_modified_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![
                note.id,
                note.title,
                note.encrypted_content,
                note.content_nonce,
                note.category,
                note.is_favorite as i32,
                note.created_at,
                note.updated_at,
                note.deleted_at,
                note.device_id,
                note.last_modified_device_id,
            ],
        )?;
        Ok(())
    }

    /// Atualiza uma nota completa para sincronização.
    pub fn update_note_sync(&self, note: &SecureNote) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE secure_notes
             SET title = ?1, encrypted_content = ?2, content_nonce = ?3, category = ?4, is_favorite = ?5,
                 created_at = ?6, updated_at = ?7, deleted_at = ?8, device_id = ?9, last_modified_device_id = ?10
             WHERE id = ?11",
            params![
                note.title,
                note.encrypted_content,
                note.content_nonce,
                note.category,
                note.is_favorite as i32,
                note.created_at,
                note.updated_at,
                note.deleted_at,
                note.device_id,
                note.last_modified_device_id,
                note.id,
            ],
        )?;
        Ok(())
    }

    // ─── Anexos (Cofre de Mídias e Arquivos) ────────────────

    /// Cria um novo registro de anexo no banco de dados.
    pub fn create_attachment(
        &self,
        id: &str,
        entry_id: &str,
        filename: &str,
        mime_type: &str,
        file_size: i64,
        encrypted_key: &str,
        key_nonce: &str,
        file_nonce: &str,
    ) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO attachments (id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                id,
                entry_id,
                filename,
                mime_type,
                file_size,
                encrypted_key,
                key_nonce,
                file_nonce,
            ],
        )?;
        Ok(())
    }

    /// Lista os anexos não excluídos de uma entrada do cofre.
    pub fn list_attachments(&self, entry_id: &str) -> SqlResult<Vec<Attachment>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM attachments
             WHERE entry_id = ?1 AND deleted_at IS NULL
             ORDER BY created_at DESC",
        )?;

        let attachments = stmt.query_map(params![entry_id], |row| {
            Ok(Attachment {
                id: row.get(0)?,
                entry_id: row.get(1)?,
                filename: row.get(2)?,
                mime_type: row.get(3)?,
                file_size: row.get(4)?,
                encrypted_key: row.get(5)?,
                key_nonce: row.get(6)?,
                file_nonce: row.get(7)?,
                created_at: row.get(8)?,
                updated_at: row.get(9)?,
                deleted_at: row.get(10)?,
                device_id: row.get(11)?,
                last_modified_device_id: row.get(12)?,
            })
        })?;

        attachments.collect()
    }

    /// Obtém um anexo específico pelo ID.
    pub fn get_attachment(&self, id: &str) -> SqlResult<Option<Attachment>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM attachments
             WHERE id = ?1 AND deleted_at IS NULL",
        )?;

        let mut rows = stmt.query_map(params![id], |row| {
            Ok(Attachment {
                id: row.get(0)?,
                entry_id: row.get(1)?,
                filename: row.get(2)?,
                mime_type: row.get(3)?,
                file_size: row.get(4)?,
                encrypted_key: row.get(5)?,
                key_nonce: row.get(6)?,
                file_nonce: row.get(7)?,
                created_at: row.get(8)?,
                updated_at: row.get(9)?,
                deleted_at: row.get(10)?,
                device_id: row.get(11)?,
                last_modified_device_id: row.get(12)?,
            })
        })?;

        match rows.next() {
            Some(Ok(att)) => Ok(Some(att)),
            _ => Ok(None),
        }
    }

    /// Exclui (soft delete) um anexo pelo ID.
    pub fn delete_attachment(&self, id: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE attachments SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![id],
        )?;
        Ok(())
    }

    /// Lista todos os anexos para sincronização, incluindo deletados.
    pub fn list_all_attachments_for_sync(&self) -> SqlResult<Vec<Attachment>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id FROM attachments",
        )?;
        let attachments = stmt.query_map([], |row| {
            Ok(Attachment {
                id: row.get(0)?,
                entry_id: row.get(1)?,
                filename: row.get(2)?,
                mime_type: row.get(3)?,
                file_size: row.get(4)?,
                encrypted_key: row.get(5)?,
                key_nonce: row.get(6)?,
                file_nonce: row.get(7)?,
                created_at: row.get(8)?,
                updated_at: row.get(9)?,
                deleted_at: row.get(10)?,
                device_id: row.get(11)?,
                last_modified_device_id: row.get(12)?,
            })
        })?;
        attachments.collect()
    }

    /// Obtém um anexo incluindo deletado para sincronização.
    pub fn get_attachment_include_deleted(&self, id: &str) -> SqlResult<Option<Attachment>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM attachments WHERE id = ?1",
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(Attachment {
                id: row.get(0)?,
                entry_id: row.get(1)?,
                filename: row.get(2)?,
                mime_type: row.get(3)?,
                file_size: row.get(4)?,
                encrypted_key: row.get(5)?,
                key_nonce: row.get(6)?,
                file_nonce: row.get(7)?,
                created_at: row.get(8)?,
                updated_at: row.get(9)?,
                deleted_at: row.get(10)?,
                device_id: row.get(11)?,
                last_modified_device_id: row.get(12)?,
            })
        })?;
        match rows.next() {
            Some(Ok(att)) => Ok(Some(att)),
            _ => Ok(None),
        }
    }

    /// Insere um anexo completo para sincronização.
    pub fn insert_attachment_sync(&self, att: &Attachment) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO attachments (id, entry_id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, created_at, updated_at, deleted_at, device_id, last_modified_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
            params![
                att.id,
                att.entry_id,
                att.filename,
                att.mime_type,
                att.file_size,
                att.encrypted_key,
                att.key_nonce,
                att.file_nonce,
                att.created_at,
                att.updated_at,
                att.deleted_at,
                att.device_id,
                att.last_modified_device_id,
            ],
        )?;
        Ok(())
    }

    /// Atualiza um anexo completo para sincronização.
    pub fn update_attachment_sync(&self, att: &Attachment) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE attachments
             SET entry_id = ?1, filename = ?2, mime_type = ?3, file_size = ?4, encrypted_key = ?5, key_nonce = ?6, file_nonce = ?7, created_at = ?8, updated_at = ?9, deleted_at = ?10, device_id = ?11, last_modified_device_id = ?12
             WHERE id = ?13",
            params![
                att.entry_id,
                att.filename,
                att.mime_type,
                att.file_size,
                att.encrypted_key,
                att.key_nonce,
                att.file_nonce,
                att.created_at,
                att.updated_at,
                att.deleted_at,
                att.device_id,
                att.last_modified_device_id,
                att.id,
            ],
        )?;
        Ok(())
    }

    // ─── Raw Updates (usado na recriptografia) ──────────

    /// Atualiza apenas os campos criptografados de uma entrada (pelo ID).
    /// Usado durante redefinição de senha mestra.
    pub fn raw_update_entry(
        &self,
        id: &str,
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
        id: &str,
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

    // ─── Cofre de Mídia ─────────────────────────────────────

    pub fn create_media(&self, media: &crate::storage::models::MediaItem) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO media_vault (id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
            params![
                media.id,
                media.filename,
                media.mime_type,
                media.file_size,
                media.encrypted_key,
                media.key_nonce,
                media.file_nonce,
                media.thumbnail_data,
                media.thumbnail_nonce,
                media.is_favorite as i32,
            ],
        )?;
        Ok(())
    }

    pub fn list_media(&self) -> SqlResult<Vec<crate::storage::models::MediaItem>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM media_vault
             WHERE deleted_at IS NULL
             ORDER BY created_at DESC",
        )?;
        let items = stmt.query_map([], |row| {
            Ok(crate::storage::models::MediaItem {
                id: row.get(0)?,
                filename: row.get(1)?,
                mime_type: row.get(2)?,
                file_size: row.get(3)?,
                encrypted_key: row.get(4)?,
                key_nonce: row.get(5)?,
                file_nonce: row.get(6)?,
                thumbnail_data: row.get(7)?,
                thumbnail_nonce: row.get(8)?,
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        items.collect()
    }

    pub fn get_media(&self, id: &str) -> SqlResult<Option<crate::storage::models::MediaItem>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM media_vault WHERE id = ?1 AND deleted_at IS NULL",
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(crate::storage::models::MediaItem {
                id: row.get(0)?,
                filename: row.get(1)?,
                mime_type: row.get(2)?,
                file_size: row.get(3)?,
                encrypted_key: row.get(4)?,
                key_nonce: row.get(5)?,
                file_nonce: row.get(6)?,
                thumbnail_data: row.get(7)?,
                thumbnail_nonce: row.get(8)?,
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        match rows.next() {
            Some(Ok(media)) => Ok(Some(media)),
            _ => Ok(None),
        }
    }

    pub fn delete_media(&self, id: &str) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE media_vault SET deleted_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![id]
        )?;
        Ok(())
    }

    pub fn list_all_media_for_sync(&self) -> SqlResult<Vec<crate::storage::models::MediaItem>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM media_vault",
        )?;
        let items = stmt.query_map([], |row| {
            Ok(crate::storage::models::MediaItem {
                id: row.get(0)?,
                filename: row.get(1)?,
                mime_type: row.get(2)?,
                file_size: row.get(3)?,
                encrypted_key: row.get(4)?,
                key_nonce: row.get(5)?,
                file_nonce: row.get(6)?,
                thumbnail_data: row.get(7)?,
                thumbnail_nonce: row.get(8)?,
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        items.collect()
    }

    pub fn get_media_include_deleted(
        &self,
        id: &str,
    ) -> SqlResult<Option<crate::storage::models::MediaItem>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id
             FROM media_vault WHERE id = ?1",
        )?;
        let mut rows = stmt.query_map(params![id], |row| {
            Ok(crate::storage::models::MediaItem {
                id: row.get(0)?,
                filename: row.get(1)?,
                mime_type: row.get(2)?,
                file_size: row.get(3)?,
                encrypted_key: row.get(4)?,
                key_nonce: row.get(5)?,
                file_nonce: row.get(6)?,
                thumbnail_data: row.get(7)?,
                thumbnail_nonce: row.get(8)?,
                is_favorite: row.get::<_, i32>(9)? != 0,
                created_at: row.get(10)?,
                updated_at: row.get(11)?,
                deleted_at: row.get(12)?,
                device_id: row.get(13)?,
                last_modified_device_id: row.get(14)?,
            })
        })?;
        match rows.next() {
            Some(Ok(media)) => Ok(Some(media)),
            _ => Ok(None),
        }
    }

    pub fn insert_media_sync(&self, media: &crate::storage::models::MediaItem) -> SqlResult<()> {
        self.conn.execute(
            "INSERT INTO media_vault (id, filename, mime_type, file_size, encrypted_key, key_nonce, file_nonce, thumbnail_data, thumbnail_nonce, is_favorite, created_at, updated_at, deleted_at, device_id, last_modified_device_id)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
            params![
                media.id,
                media.filename,
                media.mime_type,
                media.file_size,
                media.encrypted_key,
                media.key_nonce,
                media.file_nonce,
                media.thumbnail_data,
                media.thumbnail_nonce,
                media.is_favorite as i32,
                media.created_at,
                media.updated_at,
                media.deleted_at,
                media.device_id,
                media.last_modified_device_id,
            ],
        )?;
        Ok(())
    }

    pub fn update_media_sync(&self, media: &crate::storage::models::MediaItem) -> SqlResult<()> {
        self.conn.execute(
            "UPDATE media_vault
             SET filename = ?1, mime_type = ?2, file_size = ?3, encrypted_key = ?4, key_nonce = ?5, file_nonce = ?6, thumbnail_data = ?7, thumbnail_nonce = ?8, is_favorite = ?9, created_at = ?10, updated_at = ?11, deleted_at = ?12, device_id = ?13, last_modified_device_id = ?14
             WHERE id = ?15",
            params![
                media.filename,
                media.mime_type,
                media.file_size,
                media.encrypted_key,
                media.key_nonce,
                media.file_nonce,
                media.thumbnail_data,
                media.thumbnail_nonce,
                media.is_favorite as i32,
                media.created_at,
                media.updated_at,
                media.deleted_at,
                media.device_id,
                media.last_modified_device_id,
                media.id,
            ],
        )?;
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
            None => {
                Err("Banco de dados não está aberto. Desbloqueie o cofre primeiro.".to_string())
            }
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
            id: None,
            service_name: "GitHub".to_string(),
            login: "user@email.com".to_string(),
            password: String::new(),
            notes: Some("Nota de teste".to_string()),
            url: "https://github.com".to_string(),
            category_id: Some("1".to_string()),
            is_favorite: None,
        };

        let id = db
            .create_entry(&payload, "enc_pwd", "nonce1", "enc_notes", Some("nonce2"))
            .unwrap();
        assert!(!id.is_empty());

        let entries = db.list_entries().unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].service_name, "GitHub");
        assert_eq!(entries[0].url, "https://github.com");
    }

    #[test]
    fn test_search_entries() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            id: None,
            service_name: "Google".to_string(),
            login: "user@gmail.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://google.com".to_string(),
            category_id: Some("1".to_string()),
            is_favorite: None,
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
            id: None,
            service_name: "OldName".to_string(),
            login: "old@email.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://old.com".to_string(),
            category_id: None,
            is_favorite: None,
        };
        let id = db
            .create_entry(&payload, "enc_pwd", "nonce", "", None)
            .unwrap();

        let update = UpdateEntryPayload {
            id: id.clone(),
            service_name: "NewName".to_string(),
            login: "new@email.com".to_string(),
            password: String::new(),
            notes: Some("Updated".to_string()),
            url: "https://new.com".to_string(),
            category_id: Some("2".to_string()),
            is_favorite: None,
        };
        db.update_entry(
            &update,
            "new_enc",
            "new_nonce",
            "enc_notes",
            Some("n_nonce"),
        )
        .unwrap();

        let entry = db.get_entry(&id).unwrap().unwrap();
        assert_eq!(entry.service_name, "NewName");
        assert_eq!(entry.url, "https://new.com");
    }

    #[test]
    fn test_delete_entry() {
        let db = create_test_db();

        let payload = CreateEntryPayload {
            id: None,
            service_name: "ToDelete".to_string(),
            login: "del@email.com".to_string(),
            password: String::new(),
            notes: None,
            url: "https://todelete.com".to_string(),
            category_id: None,
            is_favorite: None,
        };
        let id = db
            .create_entry(&payload, "enc_pwd", "nonce", "", None)
            .unwrap();

        db.delete_entry(&id).unwrap();
        assert!(db.get_entry(&id).unwrap().is_none());
    }

    #[test]
    fn test_list_categories() {
        let db = create_test_db();
        let categories = db.list_categories().unwrap();
        assert!(categories.len() >= 4); // 4 default categories
        assert_eq!(categories[0].name, "Pessoal");
    }
}
