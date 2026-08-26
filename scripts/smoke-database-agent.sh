#!/usr/bin/env bash
# Smoke the local Database Agent → web-sql path.
# Assumes auth-service, SQL service, and AI platform are already running.
set -euo pipefail

AUTH_BASE="${AUTH_BASE:-http://127.0.0.1:8090}"
SQL_BASE="${SQL_BASE:-http://127.0.0.1:8080}"
AI_BASE="${AI_BASE:-http://127.0.0.1:8081}"
AUTH_USERNAME="${AUTH_USERNAME:-zor}"
AUTH_PASSWORD="${AUTH_PASSWORD:-localdevpassword123}"

: "${DATASOURCE_ID:?set DATASOURCE_ID to a web-sql datasource id visible to this token}"
: "${DATABASE:?set DATABASE; many datasources have no default_database}"

login_body=$(curl -sS -f "${AUTH_BASE}/ldap/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${AUTH_USERNAME}\",\"password\":\"${AUTH_PASSWORD}\",\"productType\":\"chinaBank\"}")
token=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<<"${login_body}")
if [[ -z "${token}" ]]; then
  echo "login failed: missing token" >&2
  exit 1
fi

echo "SQL tables (first page):"
curl -sS -f \
  "${SQL_BASE}/api/v1/data-sources/${DATASOURCE_ID}/tables?database=${DATABASE}&types=TABLE&pageSize=20" \
  -H "Authorization: Bearer ${token}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(", ".join(i.get("name","") for i in d.get("items") or []) or "(none)")'

echo
echo "Agent:"
agent_body=$(curl -sS -f "${AI_BASE}/api/v1/ai/agent" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${token}" \
  -d "{\"message\":\"这个数据库里有哪些表？只需要列出表名。\",\"datasourceId\":\"${DATASOURCE_ID}\",\"database\":\"${DATABASE}\",\"conversationId\":\"local-sql-smoke\"}")
python3 -c 'import json,sys; print(json.load(sys.stdin).get("content",""))' <<<"${agent_body}"

echo
echo "Confirm listTables SUCCESS in the AI process log. Matching table names is the pass condition."
