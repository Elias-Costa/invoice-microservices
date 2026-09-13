#!/usr/bin/env bash
# Healthcheck do broker Kafka (imagem apache/kafka-native, que não inclui os scripts CLI).
#
# Um teste de porta (`nc -z`) daria falso positivo: o broker abre o socket cedo, mas só passa
# a atender requisições depois de transitar para RUNNING. Por isso enviamos uma requisição
# ApiVersions e consideramos o broker saudável quando ele devolve qualquer resposta (D-019).
#
#   00 00 00 0a   tamanho do corpo (10 bytes)
#   00 12         api_key = 18 (ApiVersions)
#   00 00         api_version = 0
#   00 00 00 01   correlation_id = 1
#   ff ff         client_id = null
#
# Uso: healthcheck.sh [porta]   (padrão 9092, o listener INTERNAL)
set -o pipefail

port="${1:-9092}"
request='\x00\x00\x00\x0a\x00\x12\x00\x00\x00\x00\x00\x01\xff\xff'

bytes=$(printf "$request" | nc -w 2 localhost "$port" | wc -c)
[ "${bytes:-0}" -gt 0 ]
