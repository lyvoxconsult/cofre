pub const SCHEMA_V1: &str = include_str!("../../specs/schema/v1.sql");

pub fn apply_schema_v1(conn: &rusqlite::Connection) -> rusqlite::Result<()> {
    conn.execute_batch("PRAGMA foreign_keys=ON;")?;
    let tx = conn.unchecked_transaction()?;
    tx.execute_batch(SCHEMA_V1)?;
    tx.execute_batch("PRAGMA foreign_key_check;")?;
    tx.commit()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn schema_v1_applies_to_empty_database() {
        let conn = rusqlite::Connection::open_in_memory().unwrap();
        apply_schema_v1(&conn).unwrap();
        let count: i64 = conn
            .query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vault_meta'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(count, 1);
    }
}
