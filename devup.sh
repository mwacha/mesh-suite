#!/usr/bin/env bash
# devup.sh — sobe o ambiente de desenvolvimento completo do Mesh Suite:
# Postgres via Docker, backend (Spring Boot) e frontend (Vite/Vue) rodando
# nativamente com hot-reload. Ctrl-C encerra backend e frontend; o Postgres
# continua rodando (use `docker compose down` para parar de vez).

set -euo pipefail

# Job control (set -m): cada processo em background abaixo vira líder do seu
# próprio grupo de processos, então `kill -- -$PID` no cleanup mata a árvore
# inteira (mvnw -> JVM do Spring Boot; npm -> processo do Vite), não só o
# processo imediato. Sem isso, `kill $PID` deixa órfãos para trás.
set -m

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

BACKEND_DIR="$ROOT_DIR/mesh-suite-backend"
FRONTEND_DIR="$ROOT_DIR/mesh-suite-frontend"
BACKEND_PORT=8081
FRONTEND_PORT=5173
DB_HOST_PORT=5433

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
  echo "outro processo/projeto já está usando a porta ${DB_HOST_PORT} nesta máquina —"
  echo "pare-o (docker ps) ou mude o mapeamento de porta do serviço postgres em"
  echo "docker-compose.yml (e ajuste DB_URL abaixo para combinar)."
  exit 1
fi

echo -n "Aguardando Postgres ficar saudável"
until [[ "$(docker compose ps postgres --format '{{.Health}}' 2>/dev/null)" == "healthy" ]]; do
  echo -n "."
  sleep 1
done
echo " ok"

# --- 3. Backend (Spring Boot, nativo) ---------------------------------------

echo "Subindo backend (Spring Boot, http://localhost:${BACKEND_PORT})..."
(
  cd "$BACKEND_DIR"
  export SPRING_PROFILES_ACTIVE=dev
  export DB_URL="jdbc:postgresql://localhost:${DB_HOST_PORT}/${DB_NAME}"
  export DB_USER="${DB_APP_USER}"
  export DB_PASSWORD="${DB_APP_PASSWORD}"
  export SERVER_PORT="${BACKEND_PORT}"
  export FRONTEND_ORIGIN="http://localhost:${FRONTEND_PORT}"
  # JWT_SECRET, SMTP_*, MAIL_FROM já vêm exportados do .env acima.
  exec ./mvnw -q spring-boot:run
) &
BACKEND_PID=$!

# --- 4. Frontend (Vite, nativo) ----------------------------------------------

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Instalando dependências do frontend (primeira execução)..."
  (cd "$FRONTEND_DIR" && npm install)
fi

echo "Subindo frontend (Vite, http://localhost:${FRONTEND_PORT})..."
(
  cd "$FRONTEND_DIR"
  export VITE_API_BASE_URL="http://localhost:${BACKEND_PORT}/api"
  exec npm run dev -- --port "${FRONTEND_PORT}"
) &
FRONTEND_PID=$!

# --- 5. Encerramento limpo ---------------------------------------------------

cleanup() {
  echo ""
  echo "Encerrando backend e frontend..."
  # Sinal para o GRUPO de processos (PID negativo), não só o processo
  # imediato — pega mvnw+JVM e npm+vite juntos. Ver comentário em `set -m`.
  kill -- -"$BACKEND_PID" -"$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  echo "Postgres continua rodando em background (docker compose down para parar)."
  echo "Se algum processo do backend/frontend ficar órfão: lsof -ti:${BACKEND_PORT},${FRONTEND_PORT} | xargs kill"
}
trap cleanup EXIT INT TERM

echo ""
echo "Ambiente no ar:"
echo "  Backend:  http://localhost:${BACKEND_PORT}"
echo "  Frontend: http://localhost:${FRONTEND_PORT}"
echo "  Login de teste (seed dev): marina@aurora.com.br / MeshSuite@123"
echo ""
echo "Ctrl-C para encerrar backend e frontend."
echo ""

wait "$BACKEND_PID" "$FRONTEND_PID"
