#!/usr/bin/env bash
# devup.sh — sobe o ambiente de desenvolvimento completo do Mesh Suite:
# Postgres via Docker, backend (Spring Boot) e frontend (Vite/Vue) rodando
# nativamente com hot-reload. Ctrl-C encerra backend e frontend; o Postgres
# continua rodando (use `docker compose down` para parar de vez).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

BACKEND_DIR="$ROOT_DIR/mesh-suite-backend"
FRONTEND_DIR="$ROOT_DIR/mesh-suite-frontend"

# --- 1. .env ---------------------------------------------------------------

if [[ ! -f "$ROOT_DIR/.env" ]]; then
  echo "Nenhum .env encontrado — criando a partir de .env.example..."
  cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"

  if command -v openssl >/dev/null 2>&1; then
    GENERATED_SECRET="$(openssl rand -base64 32)"
    # macOS/BSD sed exige extensão de backup explícita com -i; portável assim.
    sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=${GENERATED_SECRET}|" "$ROOT_DIR/.env"
    rm -f "$ROOT_DIR/.env.bak"
    echo "JWT_SECRET gerado automaticamente."
  else
    echo "AVISO: openssl não encontrado — edite JWT_SECRET em .env manualmente antes de continuar."
  fi

  echo "AVISO: SMTP_HOST/SMTP_USER/SMTP_PASSWORD ficaram em branco — recuperação de senha por e-mail não vai funcionar até você preenchê-los em .env (opcional para login normal)."
  echo ""
fi

# shellcheck disable=SC1091
set -a
source "$ROOT_DIR/.env"
set +a

# --- 2. Postgres (Docker) ---------------------------------------------------

echo "Subindo Postgres..."
if ! docker compose up -d postgres; then
  echo ""
  echo "Falha ao subir o Postgres. Se o erro acima for 'port is already allocated',"
  echo "outro processo/projeto já está usando a porta 5432 nesta máquina — pare-o"
  echo "(ex: docker ps, procure outro container na 5432) ou mude a porta do serviço"
  echo "postgres em docker-compose.yml (e ajuste DB_URL abaixo para combinar)."
  exit 1
fi

echo -n "Aguardando Postgres ficar saudável"
until [[ "$(docker compose ps postgres --format '{{.Health}}' 2>/dev/null)" == "healthy" ]]; do
  echo -n "."
  sleep 1
done
echo " ok"

# --- 3. Backend (Spring Boot, nativo) ---------------------------------------

echo "Subindo backend (Spring Boot, http://localhost:8080)..."
(
  cd "$BACKEND_DIR"
  export SPRING_PROFILES_ACTIVE=dev
  export DB_URL="jdbc:postgresql://localhost:5432/${DB_NAME}"
  export DB_USER="${DB_APP_USER}"
  export DB_PASSWORD="${DB_APP_PASSWORD}"
  # JWT_SECRET, SMTP_*, MAIL_FROM já vêm exportados do .env acima.
  exec ./mvnw -q spring-boot:run
) &
BACKEND_PID=$!

# --- 4. Frontend (Vite, nativo) ----------------------------------------------

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Instalando dependências do frontend (primeira execução)..."
  (cd "$FRONTEND_DIR" && npm install)
fi

echo "Subindo frontend (Vite, http://localhost:5173)..."
(
  cd "$FRONTEND_DIR"
  export VITE_API_BASE_URL="http://localhost:8080/api"
  exec npm run dev
) &
FRONTEND_PID=$!

# --- 5. Encerramento limpo ---------------------------------------------------

cleanup() {
  echo ""
  echo "Encerrando backend e frontend..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  echo "Postgres continua rodando em background (docker compose down para parar)."
  echo "Se algum processo do backend/frontend ficar órfão: lsof -ti:8080,5173 | xargs kill"
}
trap cleanup EXIT INT TERM

echo ""
echo "Ambiente no ar:"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo "  Login de teste (seed dev): marina@aurora.com.br / MeshSuite@123"
echo ""
echo "Ctrl-C para encerrar backend e frontend."
echo ""

wait "$BACKEND_PID" "$FRONTEND_PID"
