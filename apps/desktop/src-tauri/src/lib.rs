mod commands;
mod config;
mod crypto;
mod session;
mod storage;

use config::app_config::ConfigState;
use session::state::SessionState;
use storage::database::DatabaseState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // Caminhos para dados do app
    let data_dir = dirs_next::data_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("lyvox-vault-next");

    let config_path = data_dir.join("config.json");

    // Inicializa estados
    let session_state = SessionState::new();
    let db_state = DatabaseState::new();
    let config_state = match ConfigState::new(config_path) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Erro ao carregar configuração: {e}");
            // Fallback: usa configuração padrão em memória
            ConfigState::new(std::path::PathBuf::from(":memory:")).unwrap()
        }
    };

    tauri::Builder::default()
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_shell::init())
        .manage(session_state)
        .manage(db_state)
        .manage(config_state)
        .invoke_handler(tauri::generate_handler![
            // Session
            commands::session::unlock_vault,
            commands::session::lock_vault,
            commands::session::is_unlocked,
            commands::session::is_first_run,
            commands::session::check_auto_lock,
            commands::session::record_activity,
            commands::session::verify_master_password,
            // Generate
            commands::generate::generate_password,
            commands::generate::evaluate_password,
            commands::vault::create_entry,
            commands::vault::list_entries,
            commands::vault::search_entries,
            commands::vault::get_entry,
            commands::vault::update_entry,
            commands::vault::delete_entry,
            commands::vault::list_categories,
            commands::vault::create_category,
            commands::vault::update_category,
            commands::vault::delete_category,
            // Notes
            commands::notes::create_note,
            commands::notes::list_notes,
            commands::notes::search_notes,
            commands::notes::get_note,
            commands::notes::update_note,
            commands::notes::delete_note,
            // Recovery
            commands::recovery::get_recovery_status,
            commands::recovery::get_recovery_questions,
            commands::recovery::setup_recovery,
            commands::recovery::verify_recovery_answers,
            commands::recovery::reset_master_password,
            // Backup
            commands::backup::export_backup,
            commands::backup::import_backup,
            commands::backup::write_backup_file,
            commands::backup::read_backup_file,
            commands::backup::clear_all_vault_data,
            // Settings
            commands::settings::get_settings,
            commands::settings::update_settings,
            commands::settings::update_password_gen_defaults,
            // Sync
            commands::sync::export_sync_package,
            commands::sync::import_sync_package,
            commands::network_sync::start_sync_server,
            commands::network_sync::stop_sync_server,
            // Attachments
            commands::attachments::create_attachment,
            commands::attachments::get_attachment_data,
            commands::attachments::list_attachments,
            commands::attachments::delete_attachment,
            // Media
            commands::media::create_media,
            commands::media::import_media_from_path,
            commands::media::get_media_data,
            commands::media::list_media,
            commands::media::delete_media,
        ])
        .run(tauri::generate_context!())
        .expect("Erro ao executar lyvox vault");
}
