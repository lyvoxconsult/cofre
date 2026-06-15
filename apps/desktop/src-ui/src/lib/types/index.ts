export interface VaultEntryDecrypted {
  id: string;
  service_name: string;
  login: string;
  password: string;
  notes: string;
  url: string;
  category_id: string | null;
  category_name: string | null;
  is_favorite: boolean;
  created_at: string;
  updated_at: string;
  deleted_at?: string | null;
}

export interface Category {
  id: string;
  name: string;
  color: string;
  icon: string;
  sort_order: number;
}

export interface PasswordGenResult {
  password: string;
  entropy_bits: number;
  strength_label: string;
}

export interface AppSettings {
  auto_lock_minutes: number;
  clipboard_clear_seconds: number;
  theme: string;
  privacy_mode: boolean;
  read_only_mode: boolean;
  password_gen_defaults: PasswordGenConfig;
}

export interface PasswordGenConfig {
  length: number;
  use_uppercase: boolean;
  use_lowercase: boolean;
  use_numbers: boolean;
  use_special: boolean;
  special_chars: string;
  exclude_ambiguous: boolean;
}

export interface RecoveryStatus {
  configured: boolean;
  blocked: boolean;
  blocked_remaining_secs: number | null;
  attempts: number;
}

export interface QuestionWithOptions {
  index: number;
  question: string;
  options: string[];
}

export interface SecureNoteDecrypted {
  id: string;
  title: string;
  content: string;
  category: string;
  is_favorite?: boolean;
  created_at: string;
  updated_at: string;
  deleted_at?: string | null;
}

export type ViewTab = "vault" | "generate" | "notes" | "settings" | "audit" | "csv_import" | "media";

export interface AttachmentDecrypted {
  id: string;
  entry_id: string;
  filename: string;
  mime_type: string;
  file_size: number;
  created_at: string;
  updated_at: string;
}

export interface MediaItemDecrypted {
  id: string;
  filename: string;
  mime_type: string;
  file_size: number;
  is_favorite: boolean;
  thumbnail_base64?: string | null;
  thumbnail_data?: string | null;
  created_at: string;
  updated_at: string;
}
