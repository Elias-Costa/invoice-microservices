#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
start_scenario 'Idempotência na criação de nota'

product=a0000000-0000-4000-8000-000000000002 # POR-M6
list_path="$invoices_path?customerId=$demo_customer&size=1"
before=$(http 200 GET "$list_path")
count=$(jq -ber '.page.totalElements | numbers' <<<"$before")

payload=$(invoice_payload "$product" 1)
key=$(new_key)
first=$(create_invoice "$payload" "$key")
replay=$(create_invoice "$payload" "$key")
first_json=$(jq -bcS . <<<"$first")
replay_json=$(jq -bcS . <<<"$replay")
[[ "$first_json" == "$replay_json" ]] \
    || fail "O replay deve devolver o mesmo documento JSON. Primeiro: $first_json. Replay: $replay_json."

after=$(http 200 GET "$list_path")
expect_json "$after" 'Dois pedidos com a mesma chave devem criar uma única nota.' \
    '.page.totalElements == $expected' --argjson expected "$((count + 1))"
