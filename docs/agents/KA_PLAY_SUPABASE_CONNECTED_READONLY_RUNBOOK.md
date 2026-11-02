# KA Play — validação conectada sem conta sintética

Este procedimento usa somente a conta já existente do operador e não executa `DELETE`, `UPDATE`, `INSERT`, migration ou alteração de schema no Supabase. A escrita de teste continua sendo feita exclusivamente pelo APK Debug, usando um título real escolhido pelo operador; os `SELECT`s abaixo são somente leitura.

## 1. Capturar o identificador da conta atual

No Dashboard do Supabase, abrir **SQL Editor** com uma sessão administrativa e preencher apenas o UUID da conta atualmente autenticada no APK:

```sql
-- Substitua somente pelo UUID da conta já existente; não cria usuário.
select id, email, created_at
from auth.users
where id = '<EXISTING_USER_UUID>';

select id, user_id, profile_index, name
from public.ka_profiles
where user_id = '<EXISTING_USER_UUID>'
order by profile_index;
```

## 2. Snapshot antes do teste

Escolha um `profile_id` retornado acima e registre o snapshot. Estas consultas não alteram dados:

```sql
select 'before_library' as checkpoint, count(*) as rows
from ka_sync.library_items
where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>';

select 'before_progress' as checkpoint, count(*) as rows
from ka_sync.watch_progress
where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>';

select 'before_watched' as checkpoint, count(*) as rows
from ka_sync.watched_items
where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>';
```

## 3. Exercício no APK Debug

1. Login com a conta existente.
2. Selecionar o perfil correspondente.
3. Adicionar um favorito, assistir parte de um vídeo e marcar um item como assistido.
4. Aguardar o estado visual **cloud-saved**; se ficar **save-failed**, forçar reconexão e aguardar o retry.
5. Sair e entrar novamente.
6. Conferir a recuperação dos três estados sem limpar os dados atuais.

## 4. SELECT-after-write

Substitua os valores pelos identificadores exibidos no próprio item testado. Execute somente leitura:

```sql
select 'after_library' as checkpoint, user_id, profile_id, content_id, content_type, updated_at
from ka_sync.library_items
where user_id = '<EXISTING_USER_UUID>'
  and profile_id = '<PROFILE_UUID>'
  and content_id = '<CONTENT_ID>';

select 'after_progress' as checkpoint, user_id, profile_id, progress_key, position, duration, updated_at
from ka_sync.watch_progress
where user_id = '<EXISTING_USER_UUID>'
  and profile_id = '<PROFILE_UUID>'
  and progress_key = '<PROGRESS_KEY>';

select 'after_watched' as checkpoint, user_id, profile_id, content_id, content_type, watched_at, updated_at
from ka_sync.watched_items
where user_id = '<EXISTING_USER_UUID>'
  and profile_id = '<PROFILE_UUID>'
  and content_id = '<CONTENT_ID>';
```

Para verificar duplicação no replay:

```sql
select 'event_idempotency' as checkpoint,
       (select count(*) from ka_sync.library_events where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>' and content_id = '<CONTENT_ID>') as library_events,
       (select count(*) from ka_sync.watch_progress_events where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>' and progress_key = '<PROGRESS_KEY>') as progress_events,
       (select count(*) from ka_sync.watched_events where user_id = '<EXISTING_USER_UUID>' and profile_id = '<PROFILE_UUID>' and content_id = '<CONTENT_ID>') as watched_events;
```

## Limite de evidência

Sem acesso à sessão autenticada do operador e sem um segundo dispositivo/emulador funcional, este runbook não constitui evidência executada. Ele evita inventar SELECTs de produção e permite fechar a validação com a conta existente sem criar usuário sintético ou alterar o schema.
