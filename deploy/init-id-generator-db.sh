#!/bin/sh

set -eu

DB_HOST="${DB_HOST:-shared-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-id_generator}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres123456}"

export PGPASSWORD="$DB_PASSWORD"

echo "Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT}..."
until pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres >/dev/null 2>&1; do
  sleep 2
done

echo "Ensuring database ${DB_NAME} exists..."
if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE \"${DB_NAME}\""
fi

echo "Applying schema to ${DB_NAME}..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f /schema/schema.sql

echo "Database initialization completed."
