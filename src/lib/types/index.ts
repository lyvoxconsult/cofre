export interface VaultEntryDecrypted {
  id: number;
  service_name: string;
  login: string;
  password: string;
  notes: string;
  url: string;
  category_id: number | null;
  category_name: string | null;
  created_at: string;
  updated_at: string;
}

export interface Category {
  id: number;
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
  id: number;
  title: string;
  content: string;
  category: string;
  created_at: string;
  updated_at: string;
}

export type ViewTab = "vault" | "generate" | "notes" | "settings";
