#!/usr/bin/env bash
# Chamadas reais a API do dashboard (evidencia de teste, RF01-RF07).
# Uso (Git Bash), com a aplicacao rodando:
#   DASH_PASSWORD='<senha do gerente>' ./docs/dashboard/tests/curl-dashboard.sh [http://localhost:8081]
# Variaveis: DASH_USER (padrao: gerente), DASH_PASSWORD (obrigatoria), FROM/TO (padrao: mes atual).
# Cada resposta vai para docs/dashboard/tests/evidencias/<nome>.txt com status HTTP e corpo.
# A senha so passa pelo corpo do POST de login: nunca vai para a URL, para o log nem para os arquivos.
set -euo pipefail

BASE="${1:-http://localhost:8081}"
USER_NAME="${DASH_USER:-gerente}"
: "${DASH_PASSWORD:?defina DASH_PASSWORD com a senha de ${USER_NAME}}"
FROM="${FROM:-$(date +%Y-%m-01)}"
TO="${TO:-$(date +%Y-%m-%d)}"
OUT="$(dirname "$0")/evidencias"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
mkdir -p "$OUT"

# Login por formulario (Spring Security): pega o token CSRF da pagina e posta as credenciais.
csrf=$(curl -s -c "$JAR" "$BASE/login" | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//; s/"$//')
curl -s -o /dev/null -b "$JAR" -c "$JAR" -X POST "$BASE/login" \
     --data-urlencode "username=$USER_NAME" --data-urlencode "password=$DASH_PASSWORD" --data-urlencode "_csrf=$csrf"

call() {   # call <nome> <caminho+query> [opcoes extras do curl]
  local name="$1" url="$BASE$2"; shift 2
  { echo "GET $url"; curl -s -b "$JAR" -H 'Accept: application/json' -w '\nHTTP %{http_code} em %{time_total}s\n' "$@" "$url"; } \
    | tee "$OUT/$name.txt"
  echo
}

P="from=$FROM&to=$TO"
# RF01
call 01-kpis                         "/api/dashboard/kpis?$P"
call 01-kpis-sem-periodo             "/api/dashboard/kpis"
# RF02
call 02-sales-trend-dia              "/api/dashboard/sales-trend?$P&granularity=DAY"
call 02-sales-trend-semana           "/api/dashboard/sales-trend?$P&granularity=WEEK"
call 02-sales-trend-mes              "/api/dashboard/sales-trend?$P&granularity=MONTH"
call 02-sales-by-category            "/api/dashboard/sales-by-category?$P"
# RF03
call 03-orders-status                "/api/dashboard/orders-status?$P"
# RF04
call 04-top-products-quantidade      "/api/dashboard/top-products?$P&sort=QUANTITY&limit=5"
call 04-top-products-receita         "/api/dashboard/top-products?$P&sort=REVENUE&limit=5"
# RF05: filtros combinados
call 05-kpis-pix                     "/api/dashboard/kpis?$P&paymentMethod=PIX"
call 05-kpis-cancelados              "/api/dashboard/kpis?$P&status=CANCELADO"
call 05-top-products-pizza           "/api/dashboard/top-products?$P&category=Pizza"
# Erros (ProblemDetail)
call 90-erro-400-limit               "/api/dashboard/top-products?limit=51"
call 91-erro-400-data                "/api/dashboard/kpis?from=01/10/2026"
call 92-erro-400-status              "/api/dashboard/orders-status?status=ENTREGUE"
call 93-erro-422-periodo-invertido   "/api/dashboard/kpis?from=2026-10-31&to=2026-10-01"
call 94-erro-422-periodo-longo       "/api/dashboard/kpis?from=2024-01-01&to=2026-01-01"
# Seguranca: sem sessao -> 401
{ echo "GET $BASE/api/dashboard/kpis (sem sessao)"; curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$BASE/api/dashboard/kpis"; } \
  | tee "$OUT/95-erro-401-sem-login.txt"
