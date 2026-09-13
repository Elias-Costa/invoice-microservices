# invoice-microservices — instruções para agentes de código

Sistema de emissão de notas fiscais em microsserviços: Java 21 + Spring Boot 4 (`stock-service`, `billing-service`, `api-gateway`), Apache Kafka, PostgreSQL (um banco por serviço), Keycloak, Angular 20+ com Angular Material, Docker Compose. Projeto de portfólio.

## Papéis e fonte da verdade

- **Você implementa; o humano revisa e faz commit.** Você nunca executa `git commit`, `git push`, `git add` ou qualquer comando que altere índice, histórico, branches ou remoto (bloqueado por hook).
- **Fonte da verdade**, em ordem: instruções do humano na conversa → `docs/decisions.md` → `docs/requisitos.md` e `docs/modelo-de-dados.md` → código existente. Documento vence código.
- **Se algo necessário não está explícito nos documentos, PARE e pergunte.** Não suponha, não "melhore" o escopo. Após a resposta, registre em `docs/decisions.md` (formato no próprio arquivo) e só então continue. Agrupe as perguntas na fase de planejamento sempre que possível.
- Leia `docs/decisions.md` no início de toda tarefa; as decisões mais recentes prevalecem.
- **Plano de implementação**: `.claude/plano-de-implementacao.md` (local, não versionado). Toda tarefa tem um ID (`F1-07`), status, dependências, escopo (**Entrega**) e **Verificação**. Ao iniciar, mude o status para `em andamento`; ao entregar o relatório, para `em revisão` e preencha **Registro**; `concluída` só quando o humano informar o commit. Não implemente nada fora do campo **Entrega** sem registro em `decisions.md`.
- Processo completo e justificativas: `docs/guia-trabalho-com-agente.md`.

## Comandos

> Estado: repositório em fase de documentação. Os comandos abaixo são o contrato definido em `docs/requisitos.md`; a primeira tarefa de bootstrap deve confirmá-los e atualizar esta seção.

```bash
# Backend (Maven multi-módulo, wrapper) — executar em backend/
./mvnw -q verify                                   # build + testes de todos os módulos (usa Testcontainers: Docker precisa estar rodando)
./mvnw -q -pl stock-service -am verify             # um módulo e suas dependências
./mvnw -q -pl billing-service test -Dtest=InvoiceTest   # um teste
./mvnw spotless:apply                              # formatar (google-java-format)

# Frontend (Angular) — executar em frontend/
npm ci
npm run lint
npm test -- --watch=false                          # Vitest
npm run build

# E2E (Playwright) — executar em e2e/, com o compose no ar
npx playwright test

# Infra local — executar na raiz
docker compose up --build -d                       # stack completa (infra: postgres-stock 5433, postgres-billing 5434, kafka 9092 — só em 127.0.0.1)
docker compose --profile tools up -d               # + Kafka UI em http://localhost:8090
docker compose --profile observability up -d       # + Prometheus, Grafana, Zipkin
docker compose ps                                  # todos devem ficar `healthy`
docker compose logs -f stock-service
docker compose down                                # NUNCA use -v sem confirmar: apaga os bancos
```

## Estrutura

```
backend/{common,api-gateway,stock-service,billing-service}   # Maven multi-módulo
frontend/                                                    # Angular workspace
infra/{keycloak,grafana,prometheus,nginx}                    # configs versionadas
e2e/                                                         # Playwright
docs/{requisitos.md,modelo-de-dados.md,decisions.md,adr/,architecture/,api/}
```

Detalhes por área carregam automaticamente de `.claude/rules/` ao tocar `backend/**`, `frontend/**` ou `docs/**`.

## Convenções que diferem do padrão

- **Idioma**: código, identificadores, commits, logs e nomes de domínio em inglês (`Product`, `Invoice`, `StockMovement`); documentação, UI e mensagens ao usuário em português.
- **Java**: arquitetura hexagonal (`domain` sem Spring/JPA/Kafka — ArchUnit falha o build); `record` para DTOs/comandos; **sem Lombok**; MapStruct para mapeamento; erros em `ProblemDetail` (RFC 9457) com o catálogo de `type` do `requisitos.md` §8.8; `@Version` para concorrência; nada de `float/double` para dinheiro.
- **Banco**: schema só via Flyway (`ddl-auto=validate`); DDL de referência em `modelo-de-dados.md` Apêndice A; migração já revisada pelo humano nunca é editada — crie a próxima versão.
- **Angular**: standalone, signals, `@if/@for`, `inject()`, `input()/output()`, `OnPush` em todo componente, Reactive Forms tipados, RxJS apenas para HTTP e streams (polling, debounce).
- **Eventos/REST**: contratos em `requisitos.md` §8.6–8.7 e `docs/api/`; mudar contrato = perguntar antes.

## Testes

- Testes de integração usam **Testcontainers** (PostgreSQL, Kafka) — nunca H2, nunca `@EmbeddedKafka`, nunca `ddl-auto=create`.
- Cada critério de aceite (`CA`) de um RF implementado tem teste correspondente.
- Nunca marque teste como `@Disabled`/`skip`, afrouxe assert ou capture exceção para "fazer passar". Se um teste está errado, diga por quê e pergunte.

## Fluxo git

- Você não faz commit. Ao terminar, deixe a árvore de trabalho pronta para revisão e descreva no relatório como ela deve ser commitada (escopo sugerido e mensagem em Conventional Commits: `feat(stock): …`, `fix(billing): …`, `docs: …`, `chore(infra): …`, `test(billing): …`).
- Não crie, troque ou apague branches. Não altere `.git/`.
- **Não versionados** (`.gitignore`): `.claude/` (configuração da ferramenta) e `docs/escopo.pdf`. Nunca os inclua no commit sugerido. `AGENTS.md`, `CLAUDE.md` e todo o restante de `docs/` **são** versionados e públicos — escreva-os como parte do portfólio.

## Limites

✅ **Sempre**
- Ler as seções relevantes dos documentos antes de codificar e citá-las no plano (`RF-INV-01`, `RN-16`, `§5.3`).
- Rodar build, lint e testes do módulo tocado antes de relatar; mostrar a saída real.
- Registrar em `docs/decisions.md` toda decisão tomada com o humano.
- Manter `requisitos.md`/`modelo-de-dados.md` coerentes com o código quando uma decisão os alterar (após confirmação).

⚠️ **Perguntar antes**
- Qualquer ponto não explícito ou contraditório nos documentos.
- Adicionar ou trocar dependência, biblioteca ou versão.
- Alterar schema, migração, contrato REST, contrato de evento, portas, `docker-compose.yml`, realm do Keycloak.
- Editar `docs/requisitos.md` ou `docs/modelo-de-dados.md`.
- Apagar arquivos, remover código existente que não seja parte explícita da tarefa, rodar comandos destrutivos (`docker compose down -v`, `rm -rf`, `DROP`).

🚫 **Nunca**
- `git add/commit/push/tag/branch/checkout/switch/stash/reset/rebase/merge/cherry-pick/revert/clean`, `gh pr create/merge`, ou contornar o hook que os bloqueia.
- Versionar segredos; alterar `.env` (só `.env.example`).
- Desabilitar testes, lint, ArchUnit ou cobertura para "passar".
- Instalar ferramentas globais na máquina ou alterar configurações fora do repositório.
- Inventar requisito, endpoint, campo ou regra que não esteja nos documentos ou em `decisions.md`.

## Definição de pronto (por tarefa)

1. Código e testes implementados conforme os critérios de aceite citados.
2. `./mvnw -q verify` e/ou `npm run lint && npm test -- --watch=false` verdes para os módulos tocados (saída no relatório).
3. Migração Flyway / OpenAPI / AsyncAPI atualizados quando schema ou contrato mudou.
4. Decisões registradas em `docs/decisions.md`; documentos fonte da verdade atualizados se necessário.
5. Nenhum arquivo fora do escopo da tarefa alterado.

## Relatório ao terminar

Sempre nesta ordem, em português:

1. **Resumo** — o que foi feito, em 2–4 linhas, citando os RFs/RNs cobertos.
2. **Arquivos** — criados / alterados / removidos, com uma linha por arquivo.
3. **Verificação** — cada comando executado e o resultado (trecho da saída: testes passados/falhos, warnings relevantes).
4. **Decisões** — entradas `D-nnn` criadas ou pendentes (status `aberta`).
5. **O que revisar primeiro** — os 2–3 pontos de maior risco ou julgamento.
6. **Commit sugerido** — escopo e mensagem(ns) em Conventional Commits.
7. **Pendências** — o que ficou fora, por quê, e próximo passo sugerido.
