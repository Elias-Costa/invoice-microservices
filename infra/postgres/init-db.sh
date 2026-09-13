#!/usr/bin/env bash
# Inicialização do banco da aplicação — executado UMA vez, na primeira subida do container
# (diretório de dados vazio), pelo entrypoint da imagem oficial `postgres`, como superusuário.
#
# Cria uma role de aplicação SEM privilégios de superusuário e o banco de que ela é owner (D-021).
# O mesmo script serve os dois containers (postgres-stock, postgres-billing); o que muda são as
# variáveis definidas no docker-compose.yml:
#   APP_DB_NAME      nome do banco (stock_db | billing_db)
#   APP_DB_USER      role de aplicação, owner do banco
#   APP_DB_PASSWORD  senha da role
#
# `pg_trgm` é extensão *trusted* (PostgreSQL >= 13): o owner do banco executa o
# `CREATE EXTENSION` da migração V1 sem ser superusuário.
#
# O entrypoint pode executar este arquivo (se tiver bit de execução) ou fazer `source` dele;
# por isso não se altera `set -u` globalmente — as variáveis obrigatórias são checadas com `:?`.
set -eo pipefail

: "${APP_DB_NAME:?APP_DB_NAME não definida}"
: "${APP_DB_USER:?APP_DB_USER não definida}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD não definida}"

# Interpolação segura do psql: :"x" cita como identificador, :'x' como literal.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  --set=app_db="$APP_DB_NAME" --set=app_user="$APP_DB_USER" --set=app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user";
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
SQL

echo "init-db: banco '$APP_DB_NAME' criado com owner '$APP_DB_USER' (sem superusuário)"
