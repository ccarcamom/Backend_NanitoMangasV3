# NanitoMangas backend

API Gateway (:8080) más tres servicios Spring Boot sobre MongoDB: `ms-administracion` (:8081, base `nm_admin`), `ms-vendedores` (:8082, `nm_vendedores`) y `ms-clientes` (:8083, `nm_clientes`).

## Levantar

```bash
docker compose up -d --build
```

Variables opcionales: `JWT_SECRET` e `INTERNAL_KEY` (valores por defecto solo para desarrollo: `JWT_SECRET=nanitomangas-dev-secret-key-change-me-0123456789abcdef`, `INTERNAL_KEY=nanitomangas-dev-internal-key`). El frontend (Vite en `http://localhost:5173`) llama al gateway en `http://localhost:8080`.

## Credenciales de semilla

| Rol | Correo | Contraseña |
|---|---|---|
| ADMIN | `admin@nanitomangas.cl` | `Admin1234` |
| VENDEDOR | `vendedor@nanitomangas.cl` | `Vendedor1234` |

## Swagger

- http://localhost:8081/swagger-ui.html (administración)
- http://localhost:8082/swagger-ui.html (vendedores)
- http://localhost:8083/swagger-ui.html (clientes)

## Rutas del gateway

| Ruta | Destino | Acceso |
|---|---|---|
| `/auth/**` | ms-administracion | público |
| `GET /api/productos/**` | ms-vendedores | público |
| `GET /api/productos/mios` | ms-vendedores | VENDEDOR |
| `POST, PUT, DELETE /api/productos/**` | ms-vendedores | VENDEDOR, ADMIN |
| `/api/admin/**` | ms-administracion | ADMIN |
| `/api/usuarios/**` | ms-administracion | sesión |
| `POST /api/pedidos` | ms-clientes | público (el token es opcional) |
| `/api/pedidos/**`, `/api/memes/**` | ms-clientes | sesión |
| `/internal/**` | no se enruta (404) | |

Sin token o con token inválido, vencido o de tipo `refresh`: 401. Rol sin acceso: 403. CORS solo para `http://localhost:5173`. Cada servicio vuelve a verificar la firma y el rol.

## Pruebas del gateway

```bash
cd api-gateway && mvn test
```

## Verificación con Docker

Verificado con MongoDB 7 real: `docker compose up -d --build` y luego, desde la raíz del backend:

```bash
./smoke.sh
```

Corre 30 comprobaciones por el gateway (:8080): registro, login, refresh, `/auth/me`, roles 401/403, pedido con envío $3.990, sin stock 409, memes solo CLIENTE, CRUD de producto del vendedor, listado de usuarios del admin, el admin no puede borrarse, `/internal` no enrutado y Swagger en 8081/8082/8083. Resultado: 30 de 30 OK.

Para reiniciar las bases (semilla incluida): `docker compose down -v`.
