#!/usr/bin/env bash
# Prueba de humo del backend por el gateway (http://localhost:8080). Requiere `docker compose up -d --build`.
set -u
BASE="${BASE:-http://localhost:8080}"
fail=0
j() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }
check() { # nombre, esperado, obtenido
  if [ "$2" = "$3" ]; then echo "OK    $1 ($3)"; else echo "FALLA $1: esperado $2, obtenido $3"; fail=1; fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
EMAIL="smoke$(date +%s)@mail.cl"

check "productos publicos" 200 "$(code $BASE/api/productos)"
check "admin sin token" 401 "$(code $BASE/api/admin/usuarios)"
REG=$(printf '{"name":"Smoke","email":"%s","password":"Clave1234"}' "$EMAIL")
check "registro cliente" 201 "$(code -X POST $BASE/auth/register -H 'Content-Type: application/json' -d "$REG")"
check "registro repetido" 409 "$(code -X POST $BASE/auth/register -H 'Content-Type: application/json' -d "$REG")"
ADM=$(printf '{"name":"X","email":"x%s","password":"Clave1234","role":"ADMIN"}' "$EMAIL")
check "registro admin prohibido" 400 "$(code -X POST $BASE/auth/register -H 'Content-Type: application/json' -d "$ADM")"

LOG=$(printf '{"email":"%s","password":"Clave1234"}' "$EMAIL")
CLI=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d "$LOG")
CT=$(echo "$CLI" | j "d['accessToken']"); CR=$(echo "$CLI" | j "d['refreshToken']")
check "login cliente" 1 "$([ -n "$CT" ] && echo 1 || echo 0)"
RFR=$(printf '{"refreshToken":"%s"}' "$CR")
check "refresh" 200 "$(code -X POST $BASE/auth/refresh -H 'Content-Type: application/json' -d "$RFR")"
check "me" 200 "$(code $BASE/auth/me -H "Authorization: Bearer $CT")"
check "cliente en admin" 403 "$(code $BASE/api/admin/usuarios -H "Authorization: Bearer $CT")"

PEDIDO=$(curl -s -X POST $BASE/api/pedidos -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{"buyer":{"name":"Smoke","rut":"12345678-5"},"email":"s@mail.cl","delivery":{"method":"despacho","address":{"street":"Calle 1","region":"Región Metropolitana de Santiago","comuna":"Santiago"}},"items":[{"productId":"manga-1","qty":1}]}')
check "pedido creado" "NM-" "$(echo "$PEDIDO" | j "d['id'][:3]")"
check "pedido envio" 3990 "$(echo "$PEDIDO" | j "d['shipping']")"
check "pedido invitado" 201 "$(code -X POST $BASE/api/pedidos -H 'Content-Type: application/json' -d '{"buyer":{"name":"G","rut":"12345678-5"},"email":"g@mail.cl","delivery":{"method":"retiro"},"items":[{"productId":"manga-2","qty":1}]}')"
check "pedido sin stock" 409 "$(code -X POST $BASE/api/pedidos -H 'Content-Type: application/json' -d '{"buyer":{"name":"G","rut":"12345678-5"},"email":"g@mail.cl","delivery":{"method":"retiro"},"items":[{"productId":"manga-3","qty":1}]}')"
check "mis pedidos" 200 "$(code $BASE/api/pedidos/mios -H "Authorization: Bearer $CT")"

MEME=$(curl -s -X POST $BASE/api/memes -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{"productId":"manga-1","language":"es-la","pageIndex":2,"bubbles":[{"x":0.1,"y":0.1,"w":0.3,"h":0.2,"text":"hola","fontFamily":"Impact","fontSize":24,"color":"#000000","background":"#ffffff"}]}')
MID=$(echo "$MEME" | j "d['id']")
check "meme creado" 1 "$([ -n "$MID" ] && echo 1 || echo 0)"
check "memes del cliente" 200 "$(code $BASE/api/memes -H "Authorization: Bearer $CT")"
check "meme borrado" 204 "$(code -X DELETE $BASE/api/memes/$MID -H "Authorization: Bearer $CT")"

VT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"vendedor@nanitomangas.cl","password":"Vendedor1234"}' | j "d['accessToken']")
check "vendedor mios" 200 "$(code $BASE/api/productos/mios -H "Authorization: Bearer $VT")"
check "vendedor memes prohibido" 403 "$(code $BASE/api/memes -H "Authorization: Bearer $VT")"
check "cliente crea producto" 403 "$(code -X POST $BASE/api/productos -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{}')"
PROD=$(curl -s -X POST $BASE/api/productos -H "Authorization: Bearer $VT" -H 'Content-Type: application/json' -d '{"name":"Prueba humo","brand":"X","categoryId":"mangas","genre":"shonen","price":1000,"salePrice":null,"stock":3,"image":"/img/x.png","description":"d","isNew":false,"isBestSeller":false,"isRestock":false}')
PID=$(echo "$PROD" | j "d['id']")
check "vendedor crea producto" 1 "$([ -n "$PID" ] && echo 1 || echo 0)"
check "vendedor borra su producto" 204 "$(code -X DELETE $BASE/api/productos/$PID -H "Authorization: Bearer $VT")"
check "vendedor ve pedidos" 200 "$(code $BASE/api/pedidos/vendedor -H "Authorization: Bearer $VT")"

AT=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@nanitomangas.cl","password":"Admin1234"}' | j "d['accessToken']")
check "admin lista usuarios" 200 "$(code $BASE/api/admin/usuarios -H "Authorization: Bearer $AT")"
check "admin lista pedidos" 200 "$(code $BASE/api/pedidos -H "Authorization: Bearer $AT")"
check "admin no se borra" 409 "$(code -X DELETE $BASE/api/admin/usuarios/seed-admin -H "Authorization: Bearer $AT")"
check "internal no enrutado" 404 "$(code -X POST $BASE/internal/stock/descontar -H 'Content-Type: application/json' -d '[]')"
for p in 8081 8082 8083; do check "swagger $p" 200 "$(code http://localhost:$p/swagger-ui/index.html)"; done

[ $fail -eq 0 ] && echo "TODO OK" || { echo "HAY FALLAS"; exit 1; }
