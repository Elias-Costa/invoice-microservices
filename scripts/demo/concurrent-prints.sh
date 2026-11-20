#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
start_scenario 'Impressões concorrentes'

third_id=b0000000-0000-4000-8000-000000000003
fourth_id=b0000000-0000-4000-8000-000000000004
tape_measure=a0000000-0000-4000-8000-000000000007 # TRE-5M
insulating_tape=a0000000-0000-4000-8000-000000000010 # FIT-ISO-19
failure='Saldo insuficiente para TRE-5M (solicitado 1, disponível 0)'

expect_open_seed_invoice "$third_id" 3
expect_open_seed_invoice "$fourth_id" 4
expect_balance "$tape_measure" 1
expect_balance "$insulating_tape" 60

third_key=$(new_key)
fourth_key=$(new_key)
print_invoice "$third_id" "$third_key" &
third_pid=$!
print_invoice "$fourth_id" "$fourth_key" &
fourth_pid=$!

print_failed=0
wait "$third_pid" || print_failed=1
wait "$fourth_pid" || print_failed=1
(( print_failed == 0 )) || fail 'As duas impressões devem ser aceitas.'

third=$(wait_for_invoice_result "$third_id")
fourth=$(wait_for_invoice_result "$fourth_id")
if [[ $(jq -ber '.status' <<<"$third") == CLOSED ]]; then
    winner=$third_id loser=$fourth_id closed=$third rejected=$fourth
    expect_balance "$insulating_tape" 60
    expect_debits "$insulating_tape" "$fourth_id" 0
else
    winner=$fourth_id loser=$third_id closed=$fourth rejected=$third
    expect_balance "$insulating_tape" 55
    expect_debits "$insulating_tape" "$fourth_id" 1 5 55
fi

expect_closed "$closed"
expect_json "$rejected" 'A outra nota deve voltar a OPEN com saldo insuficiente.' \
    '.status == "OPEN" and .lastFailureReason == $reason' --arg reason "$failure"
expect_balance "$tape_measure" 0
expect_debits "$tape_measure" "$winner" 1 1 0
expect_debits "$tape_measure" "$loser" 0
