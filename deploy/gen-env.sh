#!/usr/bin/env bash
# Crea el archivo .env con secretos aleatorios. Uso: bash deploy/gen-env.sh [ruta]   (por defecto ./.env)
set -eu
OUT="${1:-.env}"
if [ -e "$OUT" ]; then echo "Ya existe $OUT: no lo toco. Bórralo si quieres generar otro."; exit 0; fi
umask 077
cat > "$OUT" <<EOF
JWT_SECRET=$(openssl rand -hex 32)
INTERNAL_KEY=$(openssl rand -hex 24)
MONGO_ROOT_USER=nanito
MONGO_ROOT_PASSWORD=$(openssl rand -hex 16)
EOF
echo "Creado $OUT con secretos aleatorios (permisos 600). No lo subas a git."
