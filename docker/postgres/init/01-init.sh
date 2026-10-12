#!/bin/sh
# Runs once on first start of an empty data volume. Creates one schema + one login role per service, so a
# service can only touch its own tables. Passwords come from the container environment (.env), not from code.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE api_svc    LOGIN PASSWORD '${API_DB_PASSWORD}';
  CREATE ROLE worker_svc LOGIN PASSWORD '${WORKER_DB_PASSWORD}';
  CREATE SCHEMA api    AUTHORIZATION api_svc;
  CREATE SCHEMA worker AUTHORIZATION worker_svc;
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
EOSQL
