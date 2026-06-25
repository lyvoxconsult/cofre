use axum::{
    extract::{State, Json},
    routing::{post, get},
    Router,
};
use hmac::{Hmac, Mac};
use serde::{Deserialize, Serialize};
use sha2::Sha256;
use std::collections::HashSet;
use std::net::SocketAddr;
use tokio::sync::Mutex;
use tauri::{AppHandle, Manager, Emitter};
use uuid::Uuid;

use crate::session::state::SessionState;
use crate::storage::database::DatabaseState;

type HmacSha256 = Hmac<Sha256>;

#[derive(Clone)]
pub struct AppState {
    pub app_handle: AppHandle,
    pub session_id: String,
    pub pairing_password: String, // for validating the incoming request
    pub used_nonces: std::sync::Arc<Mutex<HashSet<String>>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SyncRequest {
    pub session_id: String,
    pub sync_data: String,
    pub auth_nonce: String,
    pub auth_tag: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SyncResponse {
    pub success: bool,
    pub message: String,
    pub sync_data: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct QrCodeData {
    pub protocol: String,
    pub version: i32,
    pub session_id: String,
    pub endpoint: String,
    pub sync_password: String,
}

// Global server control
pub struct ServerControl {
    pub shutdown_tx: Option<tokio::sync::oneshot::Sender<()>>,
}

use std::sync::OnceLock;

static SERVER_CONTROL: OnceLock<Mutex<ServerControl>> = OnceLock::new();

fn get_server_control() -> &'static Mutex<ServerControl> {
    SERVER_CONTROL.get_or_init(|| Mutex::new(ServerControl { shutdown_tx: None }))
}

async fn handle_sync(
    State(state): State<AppState>,
    Json(payload): Json<SyncRequest>,
) -> Json<SyncResponse> {
    if payload.session_id != state.session_id {
        return Json(SyncResponse {
            success: false,
            message: "Sessão inválida.".to_string(),
            sync_data: None,
        });
    }

    if payload.auth_tag.is_empty() || payload.auth_nonce.is_empty() {
        return Json(SyncResponse {
            success: false,
            message: "Senha de pareamento inválida.".to_string(),
            sync_data: None,
        });
    }

    if payload.auth_nonce.len() < 16 || payload.auth_nonce.len() > 128 {
        return Json(SyncResponse {
            success: false,
            message: "Autenticacao de pareamento invalida.".to_string(),
            sync_data: None,
        });
    }

    {
        let mut used_nonces = state.used_nonces.lock().await;
        if !used_nonces.insert(payload.auth_nonce.clone()) {
            return Json(SyncResponse {
                success: false,
                message: "Requisicao de sincronizacao repetida.".to_string(),
                sync_data: None,
            });
        }
    }

    let expected_tag = match build_auth_tag(
        &state.pairing_password,
        &payload.session_id,
        &payload.auth_nonce,
        &payload.sync_data,
    ) {
        Ok(tag) => tag,
        Err(_) => {
            return Json(SyncResponse {
                success: false,
                message: "Autenticacao de pareamento invalida.".to_string(),
                sync_data: None,
            });
        }
    };

    if !constant_time_eq(expected_tag.as_bytes(), payload.auth_tag.as_bytes()) {
        return Json(SyncResponse {
            success: false,
            message: "Autenticacao de pareamento invalida.".to_string(),
            sync_data: None,
        });
    }

    let _ = state.app_handle.emit("sync_started", "Celular conectado! Importando/Enviando dados...");

    let session = state.app_handle.state::<SessionState>();
    let db = state.app_handle.state::<DatabaseState>();

    // 1. Export desktop data
    let export_result = crate::commands::sync::export_sync_package(
        state.pairing_password.clone(),
        session.clone(),
        db.clone()
    );

    let desktop_sync_data = match export_result {
        Ok(data) => data,
        Err(e) => {
            let _ = state.app_handle.emit("sync_error", format!("Erro ao exportar: {}", e));
            return Json(SyncResponse {
                success: false,
                message: format!("Erro ao exportar dados do desktop: {}", e),
                sync_data: None,
            });
        }
    };

    // 2. Import android data
    // Tolera falha se o Android ainda não mandar os dados completos no formato SyncEnvelope
    if !payload.sync_data.is_empty() && payload.sync_data != "{}" {
        let import_result = crate::commands::sync::import_sync_package(
            payload.sync_data,
            state.pairing_password.clone(),
            session,
            db
        );

        match import_result {
            Ok(msg) => {
                let _ = state.app_handle.emit("sync_status", format!("Sincronização concluída: {}", msg));
            },
            Err(_e) => {
                let _ = state.app_handle.emit("sync_status", format!("Aviso: Recebeu conexão, mas ignorou importação (dados inválidos/vazios)"));
            }
        }
    } else {
        let _ = state.app_handle.emit("sync_status", "Aviso: Conexão recebida, mas sem dados para importar.".to_string());
    }

    Json(SyncResponse {
        success: true,
        message: "Sincronização concluída.".to_string(),
        sync_data: Some(desktop_sync_data),
    })
}

async fn ping() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok", "app": "lyvox-vault" }))
}

#[tauri::command]
pub async fn start_sync_server(app: AppHandle) -> Result<QrCodeData, String> {
    let mut control = get_server_control().lock().await;
    
    // Se já existe um rodando, desliga
    if let Some(tx) = control.shutdown_tx.take() {
        let _ = tx.send(());
    }

    // ── Seleção de IP local preferencial ─────────────────────────────────────
    // Ordem de prioridade:
    //   1. Interface com nome Wi-Fi / wlan / wireless (mesmo IP privado)
    //   2. Qualquer IP privado que NÃO seja de VPN conhecida nem adaptador virtual
    // Prefixos excluídos:
    //   127.x      → loopback
    //   169.254.x  → APIPA / link-local
    //   26.x       → Radmin VPN
    //   172.16-31.x → VPNs / Docker / WSL
    //   192.168.56.x → VirtualBox Host-Only
    fn is_excluded_ip(ip_str: &str) -> bool {
        if ip_str.starts_with("127.") || ip_str.starts_with("169.254.") || ip_str.starts_with("26.") {
            return true;
        }
        if ip_str.starts_with("192.168.56.") {
            return true; // VirtualBox Host-Only
        }
        if ip_str.starts_with("172.") {
            if let Some(second_octet_str) = ip_str.split('.').nth(1) {
                if let Ok(second_octet) = second_octet_str.parse::<u8>() {
                    if second_octet >= 16 && second_octet <= 31 {
                        return true; // 172.16–172.31 → Docker/WSL/VPN
                    }
                }
            }
        }
        false
    }

    fn is_private_ip(ip_str: &str) -> bool {
        ip_str.starts_with("192.168.") || ip_str.starts_with("10.")
    }

    let mut wifi_ip: Option<std::net::IpAddr> = None;
    let mut fallback_ip: Option<std::net::IpAddr> = None;

    if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
        for (name, ip) in &interfaces {
            if !ip.is_ipv4() {
                continue;
            }
            let ip_str = ip.to_string();
            if is_excluded_ip(&ip_str) {
                continue;
            }
            let name_lower = name.to_lowercase();

            // Prioridade 1: Interface claramente Wi-Fi
            if name_lower.contains("wi-fi") || name_lower.contains("wlan") || name_lower.contains("wireless") {
                if wifi_ip.is_none() {
                    wifi_ip = Some(*ip);
                }
            }

            // Prioridade 2: Qualquer IP privado não excluído (fallback)
            if fallback_ip.is_none() && is_private_ip(&ip_str) {
                fallback_ip = Some(*ip);
            }
        }
    }

    // Escolhe o melhor IP disponível: Wi-Fi > fallback privado > local_ip()
    let my_local_ip = wifi_ip
        .or(fallback_ip)
        .unwrap_or_else(|| {
            local_ip_address::local_ip().unwrap_or_else(|_| "127.0.0.1".parse().unwrap())
        });

    let port = 8765; // Porta fixa para facilitar

    let session_id = Uuid::new_v4().to_string();
    // Gerar uma senha de 12 caracteres aleatória para o sync deste QR code
    let sync_password: String = {
        use rand::Rng;
        let mut rng = rand::thread_rng();
        (0..16)
            .map(|_| {
                let idx = rng.gen_range(0..62);
                let chars = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
                chars[idx] as char
            })
            .collect()
    };

    let state = AppState {
        app_handle: app.clone(),
        session_id: session_id.clone(),
        pairing_password: sync_password.clone(),
        used_nonces: std::sync::Arc::new(Mutex::new(HashSet::new())),
    };

    let router = Router::new()
        .route("/ping", get(ping))
        .route("/sync", post(handle_sync))
        .with_state(state);

    let (shutdown_tx, shutdown_rx) = tokio::sync::oneshot::channel::<()>();
    control.shutdown_tx = Some(shutdown_tx);

    // Bind em 0.0.0.0 para escutar em TODAS as interfaces (VPN, Wi-Fi, Ethernet)
    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    let listener = tokio::net::TcpListener::bind(addr).await.map_err(|e| e.to_string())?;

    let addr_str = format!("http://{}:{}", my_local_ip, port);

    tokio::spawn(async move {
        let _ = axum::serve(listener, router)
            .with_graceful_shutdown(async {
                let _ = shutdown_rx.await;
            })
            .await;
    });

    Ok(QrCodeData {
        protocol: "lyvox-sync".to_string(),
        version: 1,
        session_id,
        endpoint: addr_str,
        sync_password,
    })
}

fn build_auth_tag(
    sync_password: &str,
    session_id: &str,
    nonce: &str,
    sync_data: &str,
) -> Result<String, String> {
    let mut mac = HmacSha256::new_from_slice(sync_password.as_bytes())
        .map_err(|_| "Falha ao preparar autenticacao.".to_string())?;
    mac.update(session_id.as_bytes());
    mac.update(b"\n");
    mac.update(nonce.as_bytes());
    mac.update(b"\n");
    mac.update(sync_data.as_bytes());
    Ok(hex::encode(mac.finalize().into_bytes()))
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (left, right) in a.iter().zip(b.iter()) {
        diff |= left ^ right;
    }
    diff == 0
}

#[tauri::command]
pub async fn stop_sync_server() -> Result<(), String> {
    let mut control = get_server_control().lock().await;
    if let Some(tx) = control.shutdown_tx.take() {
        let _ = tx.send(());
    }
    Ok(())
}
