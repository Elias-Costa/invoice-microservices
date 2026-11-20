#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
start_scenario 'Fluxo feliz'

# Executar com Bash, curl e jq, após subir o Compose com o seed demo.
product=a0000000-0000-4000-8000-000000000003 # ARR-M6
expect_balance "$product" 1000

payload=$(invoice_payload "$product" 50)
create_key=$(new_key)
invoice=$(create_invoice "$payload" "$create_key")
invoice_id=$(jq -ber '.id' <<<"$invoice")
print_key=$(new_key)
print_invoice "$invoice_id" "$print_key"

invoice=$(wait_for_invoice_result "$invoice_id")
expect_closed "$invoice"
expect_balance "$product" 950
expect_debits "$product" "$invoice_id" 1 50 950
