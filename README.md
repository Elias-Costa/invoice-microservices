# invoice-microservices

![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Angular 22.3](https://img.shields.io/badge/Angular-22.3-DD0031?logo=angular&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Apache Kafka 4.2.1](https://img.shields.io/badge/Apache_Kafka-4.2.1-231F20?logo=apachekafka&logoColor=white)
![Docker Compose v2](https://img.shields.io/badge/Docker_Compose-v2-2496ED?logo=docker&logoColor=white)
![Testes com Testcontainers](https://img.shields.io/badge/testes-Testcontainers-007EC6?logo=docker&logoColor=white)
![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-4C9A2A)

Projeto de portfólio para cadastrar produtos e clientes, acompanhar movimentações de estoque e fechar notas fiscais internas. Ao selecionar **Imprimir**, o Faturamento inicia o fechamento assíncrono. A nota fecha quando o Estoque confirma a baixa. Se faltar saldo, a nota volta a ficar aberta, com o motivo disponível para correção.

A `v0.1.0` é uma demonstração local sem login. As notas representam documentos internos simplificados para demonstrar o fluxo, sem integração com a SEFAZ.

![Lista de notas fiscais com os status Aberta, Cancelada e Fechada, filtros por status, cliente e período, e o botão Imprimir nas notas abertas](docs/images/notas-fiscais.png)

## Arquitetura

```mermaid
flowchart LR
    Browser["Navegador"] --> Frontend["Angular 22 / Nginx"]
    Frontend -->|"/api"| Gateway["Spring Cloud Gateway"]
    Gateway -->|"/api/stock"| Stock["stock-service"]
    Gateway -->|"/api/billing"| Billing["billing-service"]
    Billing -->|"Consulta de produtos por HTTP"| Stock
    Stock --> StockDb[("PostgreSQL / stock_db")]
    Billing --> BillingDb[("PostgreSQL / billing_db")]
    Billing -->|"Solicita baixa"| Kafka["Apache Kafka"]
    Kafka -->|"Entrega solicitação"| Stock
    Stock -->|"Confirma ou rejeita baixa"| Kafka
    Kafka -->|"Entrega resultado"| Billing
```

Os serviços de domínio usam Java 21, Spring Boot 4 e arquitetura hexagonal, cada um com seu banco PostgreSQL. Quando uma mudança de estado gera um evento da Saga, o serviço grava a mudança e o evento na Outbox em uma única transação. O Kafka transporta os eventos, os consumidores tratam reentregas de forma idempotente e o bloqueio otimista protege o saldo de baixas concorrentes. A interface consulta o status até o fechamento, a rejeição ou o limite do acompanhamento automático.

## Execução local

Com Docker em execução e Docker Compose v2 disponível, rode os comandos abaixo no terminal.

```sh
git clone https://github.com/Elias-Costa/invoice-microservices.git
cd invoice-microservices
docker compose up --build -d --wait
docker compose ps --all
```

A stack padrão sobe oito contêineres: dois PostgreSQL, o Kafka, os três serviços Java, o frontend em Nginx e o `kafka-init`, que prepara o volume e encerra. As quatro imagens da aplicação somam cerca de 1,4 GB, além das imagens base de PostgreSQL e Kafka. O primeiro `--build` compila os quatro módulos Maven e roda `npm ci` com o build de produção do Angular, o que leva alguns minutos. O `--wait` espera pelos healthchecks sem limite de tempo; acrescente `--wait-timeout 600` para falhar com mensagem em vez de esperar indefinidamente. O [plano de riscos](docs/requisitos.md#13-riscos-e-mitigações) estimou cerca de 6 GB de memória para a stack completa prevista no projeto — a `v0.1.0` não sobe Keycloak nem observabilidade, então a folga necessária é menor.

Abra [localhost:4200](http://localhost:4200). Os perfis `docker,demo` carregam os dados de demonstração no primeiro uso. O Compose usa os valores padrão documentados em [`.env.example`](.env.example).

| Componente | Endereço local | Uso |
|---|---|---|
| Frontend | `http://localhost:4200` | Interface Angular e proxy de `/api` |
| Gateway | `http://localhost:8080` | Entrada HTTP da API |
| Estoque | `http://localhost:8081` | Acesso direto ao `stock-service` |
| Faturamento | `http://localhost:8082` | Acesso direto ao `billing-service` |
| PostgreSQL do Estoque | `127.0.0.1:5433` | `stock_db` |
| PostgreSQL do Faturamento | `127.0.0.1:5434` | `billing_db` |
| Kafka | `localhost:9092` | Broker para clientes no host |
| Kafka UI | `http://localhost:8090` | Inspeção de tópicos, disponível com o perfil `tools` |

Os serviços persistentes da stack padrão ficam `healthy`. O contêiner `kafka-init` termina com código `0` após preparar o volume. Para iniciar a Kafka UI, use `docker compose --profile tools up -d kafka-ui`. Para encerrar preservando os dados, use `docker compose down`; se a Kafka UI estiver no ar, acrescente `--profile tools` para removê-la junto.

## Demonstração

Em um banco recém-populado, há 12 produtos, 5 clientes e 5 notas. A nota `000001` está fechada, a `000002` está cancelada e as `000003` a `000005` estão abertas. Use a **Oficina Mecânica Silva Ltda** como cliente de exemplo. A primeira nota criada após o seed recebe o número `000006`. [Dados de demonstração](docs/modelo-de-dados.md#13-dados-de-demonstração-seed)

Pela interface, cadastre um produto com saldo inicial 10. Em **Notas fiscais**, crie uma nota para o cliente de exemplo com 2 unidades do produto. Na tela de detalhe, selecione **Imprimir** e aguarde o status **Fechada**. Em **Produtos**, abra o histórico do produto e confira a baixa de 2 unidades e o saldo 8.

As duas telas ao fim do fluxo, aqui com a nota `000001` do seed, que já nasce fechada, e o produto que ela baixou:

![Detalhe da nota fiscal 000001 com o status Fechada, a data de fechamento e os dois itens que somam R$ 55,00](docs/images/nota-fechada.png)

![Histórico de movimentações do PAR-M6-20, com o saldo inicial de 600 e a baixa de 100 unidades referente à NF 1, deixando o saldo em 500](docs/images/historico-produto.png)

Para executar os cinco cenários automatizados, use Bash, `curl` e `jq` a partir da raiz com a stack no ar e os dados do seed intactos. Cada script imprime `PASS` ou `FAIL`. Rode os cinco na mesma instância, na ordem da tabela.

| Cenário | Comando | Resultado esperado |
|---|---|---|
| Fluxo feliz | `bash scripts/demo/happy-path.sh` | A nota fecha e o saldo de `ARR-M6` passa de 1.000 para 950, com uma única baixa. |
| Saldo insuficiente | `bash scripts/demo/insufficient-stock.sh` | A nota `000005` volta a `OPEN` com o motivo e preserva os saldos dos dois itens. |
| Concorrência | `bash scripts/demo/concurrent-prints.sh` | Só uma das notas `000003` e `000004` fecha. O saldo de `TRE-5M` passa de 1 para 0, sem baixa parcial na nota rejeitada. |
| Idempotência | `bash scripts/demo/idempotency.sh` | Dois envios com a mesma `Idempotency-Key` recebem a mesma nota e criam um único registro. |
| Queda do Estoque | `bash scripts/demo/stock-outage.sh` | O roteiro para o Estoque, imprime uma nota já criada e verifica `PROCESSING`. Após religar o serviço, a nota fecha automaticamente e baixa uma unidade de `CHV-FEN-6`. |

Os roteiros consomem saldo e fecham notas do seed, então só valem uma rodada. Para repeti-los a partir dos saldos iniciais, recrie os volumes: `docker compose down -v` apaga os dois bancos e o volume do Kafka, e o `up` seguinte recarrega o seed. Use `-v` apenas nesta instância de demonstração.

## Pontos de interesse no código

| Tema | Arquivo |
|---|---|
| Baixa idempotente por `print_request_id`, com repetição do resultado já gravado e retentativa sob conflito de versão | [`DebitStockService.java`](backend/stock-service/src/main/java/dev/eliascosta/invoice/stock/application/service/DebitStockService.java) |
| Evento gravado na Outbox dentro da transação de quem chama — publicado se e somente se a mudança de estado for confirmada | [`OutboxWriter.java`](backend/common/src/main/java/dev/eliascosta/invoice/common/outbox/OutboxWriter.java) |
| Publicação do que a Outbox acumulou | [`OutboxPublisher.java`](backend/common/src/main/java/dev/eliascosta/invoice/common/outbox/OutboxPublisher.java) |
| Bloqueio otimista sobre o saldo do produto | [`ProductJpaEntity.java`](backend/stock-service/src/main/java/dev/eliascosta/invoice/stock/adapter/out/persistence/ProductJpaEntity.java) |
| `Idempotency-Key` nas rotas HTTP: repete a resposta guardada, recusa payload divergente e libera a chave em erro 5xx | [`IdempotencyInterceptor.java`](backend/common/src/main/java/dev/eliascosta/invoice/common/idempotency/IdempotencyInterceptor.java) |
| Fronteiras da arquitetura hexagonal verificadas no build | [`StockArchitectureTest.java`](backend/stock-service/src/test/java/dev/eliascosta/invoice/stock/StockArchitectureTest.java) |
| Duas impressões disputando a última unidade de um produto | [`ConcurrentDebitIT.java`](backend/stock-service/src/test/java/dev/eliascosta/invoice/stock/application/service/ConcurrentDebitIT.java) |
| Saga de impressão ponta a ponta, com PostgreSQL e Kafka reais | [`InvoicePrintSagaIT.java`](backend/billing-service/src/test/java/dev/eliascosta/invoice/billing/adapter/in/rest/InvoicePrintSagaIT.java) |

## Verificação local

O backend requer JDK 21 e Docker em execução para os testes com Testcontainers. Para o frontend, a imagem de build usa Node.js 22. Rode os comandos abaixo a partir da raiz.

```sh
(cd backend && ./mvnw -q verify)
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
```

A suíte do backend reúne 41 classes de teste: 15 de integração que sobem PostgreSQL e Kafka reais com Testcontainers, duas que verificam as fronteiras da arquitetura com ArchUnit — o build falha se o domínio importar Spring, JPA ou Kafka — e uma que põe duas impressões em disputa pela última unidade de um produto. O frontend tem 23 specs em Vitest.

No PowerShell, rode `.\mvnw.cmd -q verify` dentro de `backend/`. Use `npm.cmd` no lugar de `npm` se a política de execução do PowerShell bloquear o script do npm.

## Como este projeto foi construído

O autor definiu os requisitos, a arquitetura e as decisões de projeto. A implementação foi feita com agentes de código sob um processo documentado, com verificação local e revisão humana antes de cada commit. [Instruções de implementação](AGENTS.md) · [Registro de decisões](docs/decisions.md) · [Guia de trabalho](docs/guia-trabalho-com-agente.md)

## Evoluções possíveis

A F1 corresponde à entrega de portfólio. A F2 reúne propostas de autenticação com Keycloak, PDF, histórico visível da nota e observabilidade. A F3 reúne propostas de CI, testes ponta a ponta e reconciliação de notas em processamento. [Decisão de escopo (D-029)](docs/decisions.md#d-029--encerramento-da-implementação-na-f1) · [Plano de entrega](docs/requisitos.md#11-plano-de-entrega)

## Documentação

- [Requisitos](docs/requisitos.md)
- [Modelo de dados](docs/modelo-de-dados.md)
- [Decisão sobre as versões da plataforma](docs/adr/0001-versoes-da-plataforma.md)

## Licença

[MIT](LICENSE).
