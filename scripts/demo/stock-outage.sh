#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
start_scenario 'Recuperação após queda do Estoque' docker

# Usar o mesmo COMPOSE_PROJECT_NAME da stack de demonstração, quando definido.
cd -- "$(dirname "${BASH_SOURCE[0]}")/../.."
needs_restart=false

restore_stock() {
    local status=$1
    if [[ "$needs_restart" == true ]]; then
        docker compose start stock-service || {
            printf 'Falha ao religar stock-service. Execute docker compose start stock-service.\n' >&2
            status=1
        }
    fi
    report_result "$status"
}

trap 'restore_stock "$?"' EXIT
[[ -n $(docker compose ps --status running -q stock-service) ]] \
    || fail 'Suba a stack de demonstração no projeto Compose selecionado.'
[[ $(docker compose port api-gateway 8080) == "${gateway_url#http://}" ]] \
    || fail "O Gateway do projeto Compose selecionado deve atender em $gateway_url."

product=a0000000-0000-4000-8000-000000000004 # CHV-FEN-6
expect_balance "$product" 25
payload=$(invoice_payload "$product" 1)
create_key=$(new_key)
invoice=$(create_invoice "$payload" "$create_key")
invoice_id=$(jq -ber '.id' <<<"$invoice")
print_key=$(new_key)

needs_restart=true
docker compose stop stock-service
print_invoice "$invoice_id" "$print_key"

deadline=$((SECONDS + 30))
while :; do
    invoice=$(http 200 GET "$invoices_path/$invoice_id")
    expect_json "$invoice" 'A nota deve permanecer em PROCESSING com o Estoque parado.' \
        '.status == "PROCESSING"'
    (( SECONDS >= deadline )) && break
    sleep 1
done

docker compose start stock-service
needs_restart=false
deadline=$((SECONDS + 90))
until docker compose ps --format json stock-service | jq -be '.Health == "healthy"' >/dev/null; do
    (( SECONDS < deadline )) || fail 'O Estoque não ficou saudável em 90 s após religar.'
    sleep 1
done
invoice=$(wait_for_invoice_result "$invoice_id")
expect_closed "$invoice"
expect_balance "$product" 24
expect_debits "$product" "$invoice_id" 1 1 24
