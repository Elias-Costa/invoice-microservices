#!/usr/bin/env bash

# Funções compartilhadas pelos roteiros de demonstração.
gateway_url=http://127.0.0.1:8080
invoices_path=/api/billing/invoices
products_path=/api/stock/products
demo_customer=c0000000-0000-4000-8000-000000000001

report_result() {
    local status=$1
    if (( status == 0 )); then
        printf 'PASS — %s\n' "$scenario"
    else
        printf 'FAIL — %s\n' "$scenario" >&2
    fi
    exit "$status"
}

start_scenario() {
    scenario=$1
    shift
    trap 'report_result "$?"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM

    local tool
    for tool in curl jq od tr "$@"; do
        command -v "$tool" >/dev/null || fail "Comando necessário: $tool"
    done
}

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

new_key() {
    local bytes
    bytes=$(od -An -N16 -tx1 /dev/urandom | tr -d '[:space:]') || return
    [[ ${#bytes} == 32 ]] || fail 'Não foi possível gerar a chave de idempotência.'
    printf '%s-%s-4%s-8%s-%s\n' \
        "${bytes:0:8}" "${bytes:8:4}" "${bytes:13:3}" "${bytes:17:3}" "${bytes:20:12}"
}

http() {
    local expected=$1 method=$2 path=$3 response status body
    shift 3
    response=$(curl --silent --show-error --max-time 10 --request "$method" \
        --write-out '\n%{http_code}' "$@" "$gateway_url$path") \
        || fail "Falha na chamada $method $path. Confira o Gateway em $gateway_url."
    status=${response##*$'\n'}
    body=${response%$'\n'*}
    [[ "$status" == "$expected" ]] \
        || fail "$method $path retornou HTTP $status (esperado $expected). Resposta: $body"
    printf '%s\n' "$body"
}

expect_json() {
    local body=$1 message=$2 filter=$3
    shift 3
    jq -be "$@" "$filter" <<<"$body" >/dev/null || fail "$message Resposta: $body"
}

invoice_payload() {
    jq -bcn --arg customer "$demo_customer" --arg product "$1" --argjson quantity "$2" \
        '{customerId: $customer, items: [{productId: $product, quantity: $quantity}]}'
}

create_invoice() {
    local body=$1 key=$2 invoice
    invoice=$(http 201 POST "$invoices_path" -H "Idempotency-Key: $key" \
        -H 'Content-Type: application/json' --data-binary "$body") || return
    expect_json "$invoice" 'Esperava uma nota aberta com ID e número.' \
        '.status == "OPEN" and (.id | type == "string" and length > 0) and (.number | type == "number")'
    printf '%s\n' "$invoice"
}

print_invoice() {
    local id=$1 key=$2 response
    response=$(http 202 POST "$invoices_path/$id/print" -H "Idempotency-Key: $key")
    expect_json "$response" 'O aceite de impressão deve identificar a nota em PROCESSING.' \
        '.id == $id and .status == "PROCESSING"' --arg id "$id"
}

expect_open_seed_invoice() {
    local id=$1 number=$2 invoice
    invoice=$(http 200 GET "$invoices_path/$id")
    expect_json "$invoice" "Esperava a NF $number aberta com os dados iniciais do seed." \
        '.number == $number and .status == "OPEN" and .version == 0 and .lastFailureReason == null' \
        --argjson number "$number"
}

wait_for_invoice_result() {
    local id=$1 deadline=$((SECONDS + 60)) invoice
    while (( SECONDS < deadline )); do
        invoice=$(http 200 GET "$invoices_path/$id") || return
        if jq -be '.status == "CLOSED" or (.status == "OPEN" and .lastFailureReason != null)' \
            <<<"$invoice" >/dev/null; then
            printf '%s\n' "$invoice"
            return
        fi
        sleep 1
    done
    fail "A nota $id não saiu do processamento em 60 s. Última resposta: $invoice"
}

expect_closed() {
    expect_json "$1" 'Esperava fechamento com data e sem motivo de falha.' \
        '.status == "CLOSED" and (.closedAt | type == "string") and .lastFailureReason == null'
}

expect_balance() {
    local product=$1 balance=$2 response
    response=$(http 200 GET "$products_path/$product")
    expect_json "$response" "Saldo esperado para $product: $balance." \
        '.balance == $balance' --argjson balance "$balance"
}

expect_debits() {
    local product=$1 invoice=$2 count=$3 quantity=${4:-0} balance=${5:-0} response
    response=$(http 200 GET "$products_path/$product/movements?page=0&size=100")
    expect_json "$response" "Esperava $count baixa(s) da nota $invoice no produto $product." \
        '.page.totalPages <= 1 and
         (.content | map(select(.type == "INVOICE_DEBIT" and .referenceId == $invoice)) |
          length == $count and all(.[]; .quantity == $quantity and .balanceAfter == $balance))' \
        --arg invoice "$invoice" --argjson count "$count" \
        --argjson quantity "$quantity" --argjson balance "$balance"
}
