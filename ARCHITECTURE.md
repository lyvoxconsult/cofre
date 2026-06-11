# Lyvox Vault - Arquitetura Local-First

Este documento define os princípios arquiteturais inegociáveis do **Lyvox Vault**, estabelecendo-o como uma solução de cofre de senhas de alta segurança.

## 1. Regra de Ouro: OFFLINE-FIRST e ZERO-CLOUD
O Lyvox Vault **NÃO DEVE** utilizar banco de dados em nuvem. 

**Proibições Absolutas:**
- Firebase, Supabase, MongoDB Atlas, PostgreSQL remoto, MySQL remoto.
- Servidor central de API para armazenar dados do usuário.
- Sincronização automática que dependa de um servidor intermediário na internet.
- Envio de senhas, notas, mídias, backups ou metadados para qualquer servidor do aplicativo.

**Obrigação Local-First:**
- Todos os dados ficam armazenados localmente no dispositivo (Desktop ou Android).
- Nenhum dado do usuário deve ser enviado ou analisado remotamente (telemetria zero-knowledge, se existir).

## 2. Segurança e Criptografia Mandatória
Mesmo armazenado 100% localmente, **nenhum dado sensível pode ficar em texto puro (plaintext)** no disco.

**Devem ser criptografados:**
- Senhas, Logins, Notas, Respostas de recuperação, Backups (`.vault`), Pacotes de sincronização (`.vaultsync`).
- Mídias (Fotos, Vídeos, Anexos).
- Histórico de senhas, Dados importados.
- Arquivos temporários sensíveis.

**Senha Mestra:**
- A senha mestra nunca é armazenada em disco em formato puro, nem enviada para a rede.

## 3. Sincronização (USB / Offline)
Como não há nuvem, a sincronização entre dispositivos (ex: PC e Smartphone) acontece fisicamente ou via transferência segura e criptografada (peer-to-peer / arquivo local).

**Fluxo de Sincronização:**
1. O usuário conecta o celular no PC via cabo USB (ou transfere via Drive manual).
2. O Lyvox Vault (Android ou Desktop) gera um pacote de sincronização criptografado (`.vaultsync`).
3. O outro dispositivo localiza e importa esse pacote via File Picker (Storage Access Framework).
4. O app compara e resolve os conflitos localmente.
5. O usuário confirma as alterações, e a sincronização é aplicada.

## 4. Banco de Dados Local (Schema preparado para Sync Local)
A arquitetura do banco de dados (SQLite no Android e Desktop) usa a seguinte estrutura para viabilizar o sync offline sem corromper dados:

- **IDs Universais:** Uso de UUIDs (`id`) para garantir unicidade em ambientes distribuídos e desconectados.
- **Timestamps:** Uso rigoroso de `createdAt` e `updatedAt`.
- **Soft Deletes:** Nenhuma linha é apagada fisicamente. Uso de `deletedAt` (tombstones).
- **Rastreabilidade de Dispositivo:** Uso de `deviceId` e `lastModifiedDeviceId`.
- **Versionamento:** `schemaVersion` para migrações locais previsíveis.

## 5. Resolução de Conflitos
O aplicativo deve resolver conflitos localmente.
- Antes de sincronizar, um backup automático do banco de dados deve ser feito (`.vault` temporário).
- Não haverá exclusões silenciosas. Se houver discrepância irreconciliável, ambas as versões serão preservadas para o usuário decidir posteriormente.
- Um resumo visual deve ser apresentado antes de aplicar o Merge do arquivo `.vaultsync`.

## 6. Backups
- Gerenciamento 100% sob controle do usuário.
- O backup (`.vault`) é um arquivo criptografado que contém tudo.
- Compartilhamentos via serviços de terceiros (Google Drive, WhatsApp) são apenas **transferências de arquivo opacas** geridas pelo SO (Android Intent / File System do Windows) e não configuram armazenamento em banco Cloud do Lyvox.

---
**Critérios de Aceitação para Novos Desenvolvimentos:**
Nenhuma alteração, feature ou dependência pode ser adicionada se ela violar a premissa de que o App deve funcionar offline, usar SQLite/Local Storage privado e trafegar arquivos sync exclusivamente via interface física ou pacotes criptografados definidos pelo usuário.
