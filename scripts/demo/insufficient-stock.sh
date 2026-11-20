#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
start_scenario 'Saldo insuficiente'

invoice_id=b0000000-0000-4000-8000-000000000005
drill_bit=a0000000-0000-4000-8000-000000000009 # BRO-ACO-8
sandpaper=a0000000-0000-4000-8000-000000000011 # LIX-120
failure='Saldo insuficiente para BRO-ACO-8 (solicitado 2, disponível 0)'

expect_open_seed_invoice "$invoice_id" 5
expect_balance "$drill_bit" 0
expect_balance "$sandpaper" 200

key=$(new_key)
print_invoice "$invoice_id" "$key"
invoice=$(wait_for_invoice_result "$invoice_id")
expect_json "$invoice" 'A NF 5 deve voltar a OPEN com o motivo de saldo insuficiente.' \
    '.status == "OPEN" and .lastFailureReason == $reason and (.allowedActions | index("PRINT") != null)' \
    --arg reason "$failure"

expect_balance "$drill_bit" 0
expect_balance "$sandpaper" 200
expect_debits "$drill_bit" "$invoice_id" 0
expect_debits "$sandpaper" "$invoice_id" 0
