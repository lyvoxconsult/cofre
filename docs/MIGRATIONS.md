# Migrations

Migration rules:

1. Every migration has version and checksum.
2. Every migration runs inside transaction.
3. Foreign keys are enabled.
4. `foreign_key_check` runs after migration.
5. Destructive migration requires automatic backup.
6. Temp tables must be dropped or committed atomically.
7. Partial migration must fail closed with clear error.

Legacy risks addressed:

- no `new_categories already exists`;
- no migration by table-presence only;
- no untracked schema drift between Desktop and Android.
