use uuid::Uuid;

pub fn validate_uuid(value: &str, field: &str) -> Result<(), String> {
    Uuid::parse_str(value)
        .map(|_| ())
        .map_err(|_| format!("{field} must be a UUID"))
}

pub fn validate_utc_timestamp(value: &str, field: &str) -> Result<(), String> {
    if value.ends_with('Z') && value.contains('T') {
        Ok(())
    } else {
        Err(format!("{field} must be UTC RFC3339"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn uuid_validation_rejects_path_like_ids() {
        assert!(validate_uuid("../secret", "id").is_err());
    }

    #[test]
    fn timestamp_requires_utc_shape() {
        assert!(validate_utc_timestamp("2026-06-15 10:00:00", "updated_at").is_err());
        assert!(validate_utc_timestamp("2026-06-15T10:00:00Z", "updated_at").is_ok());
    }
}
