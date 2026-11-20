# Registro de decisões — invoice-microservices

Registro corrente e cronológico das decisões tomadas durante o projeto: dúvidas surgidas na implementação, trade-offs, mudanças de escopo e desvios em relação aos documentos de especificação. É o **segundo nível da fonte da verdade** (abaixo apenas das instruções diretas do humano; acima de `requisitos.md` e `modelo-de-dados.md`, que devem ser atualizados para refletir as decisões aqui registradas).

## Como usar

| Regra | Detalhe |
|---|---|
| Quando registrar | Toda vez que o agente precisou perguntar algo não explícito nos documentos; toda mudança de escopo, tecnologia, contrato, schema ou regra de negócio; toda vez que o humano decidir contra a recomendação do agente (o "porquê" precisa sobreviver à sessão) |
| Quando **não** registrar | Escolhas triviais e reversíveis que seguem padrão já existente (nome de variável, ordem de métodos, posição de arquivo no pacote correto) — registro irrelevante degrada o log |
| Quem decide | O humano. O agente propõe opções e recomendação; nunca registra suposição como decisão |
| Imutabilidade | Entradas fechadas não são editadas. Para mudar, cria-se nova entrada com `Substitui: D-nnn`; a antiga recebe status `substituída` (única edição permitida) |
| Relação com ADRs | Decisões caras de reverter (tecnologia, schema, contrato, segurança, padrão arquitetural) são **promovidas** a ADR em `docs/adr/NNNN-titulo.md` (formato MADR); a entrada aponta para o ADR. A lista de ADRs previstos está em `requisitos.md` §14 |
| Numeração | `D-nnn` sequencial, nunca reutilizada |
| Status | `aberta` (pergunta feita ou proposta aplicada provisoriamente, aguardando o humano) · `aceita` · `substituída` · `revogada` |

## Formato de uma entrada

```markdown
### D-nnn — Título curto

- **Data**: AAAA-MM-DD
- **Status**: aberta | aceita | substituída | revogada
- **Decisor**: humano (proposta: agente) | humano | agente (trivial, informado)
- **Contexto**: o que exigiu a decisão, em 2–4 linhas; qual seção dos documentos era omissa ou contraditória
- **Opções consideradas**: 1) … 2) … 3) … (com o principal prós/contras de cada uma)
- **Decisão**: a opção escolhida e por quê
- **Consequências**: + benefícios / − custos e riscos aceitos
- **Y-statement**: No contexto de [situação], diante de [preocupação], decidimos [escolha] para [objetivo], aceitando [trade-off].
- **Documentos afetados**: requisitos.md §x (atualizado em …) · modelo-de-dados.md §y · docs/api/… · —
- **ADR**: — | ADR-nnn
- **Substitui**: — | D-nnn
```

## Índice

| ID | Data | Tema | Status | ADR |
|---|---|---|---|---|
| [D-001](#d-001--modelo-de-trabalho-humano--agente) | 2026-09-12 | Modelo de trabalho humano ↔ agente | aceita | — |
| [D-002](#d-002--decisões-da-entrevista-de-requisitos-que-divergiram-da-recomendação) | 2026-09-12 | Decisões da entrevista que divergiram da recomendação | aceita | ADR-006, 008, 013 |
| [D-003](#d-003--sem-funcionalidade-de-ia-no-produto) | 2026-09-12 | Sem funcionalidade de IA no produto | aceita | — |
| [D-004](#d-004--bloqueio-determinístico-de-operações-git-pelo-agente) | 2026-09-12 | Bloqueio determinístico de operações git pelo agente | aceita | — |
| [D-005](#d-005--identificador-de-tentativa-de-impressão-print_request_id-e-tabela-invoice_debits) | 2026-09-12 | `print_request_id` e tabela `invoice_debits` | aceita | ADR-009 |
| [D-006](#d-006--usuário-de-auditoria-armazena-preferred_username) | 2026-09-12 | Usuário de auditoria armazena `preferred_username` | aceita | — |
| [D-007](#d-007--repositório-público-contém-código-readme-e-documentação-de-arquitetura-especificação-e-config-do-agente-ficam-locais) | 2026-09-12 | Repositório público: código, README e docs de arquitetura; especificação e config do agente locais | revogada | — |
| [D-008](#d-008--versões-da-plataforma-spring-boot-41-java-21-groupid-develiascostainvoice) | 2026-09-13 | Versões da plataforma: Spring Boot 4.1, Java 21, `groupId` `dev.eliascosta.invoice` | aceita | ADR-001 |
| [D-009](#d-009--subclasse-genérica-invalidrequestexception-400-na-hierarquia-de-erros-do-common) | 2026-09-13 | Subclasse genérica `InvalidRequestException` (400) na hierarquia de erros do `common` | aceita | ADR-014 |
| [D-010](#d-010--origem-do-traceid-no-problemdetail-antes-do-tracing-distribuído) | 2026-09-13 | Origem do `traceId` no `ProblemDetail` antes do tracing distribuído | aceita | ADR-014 |
| [D-011](#d-011--erros-fora-do-catálogo-88-violação-de-integridade-e-erros-de-protocolo-http) | 2026-09-13 | Erros fora do catálogo §8.8: violação de integridade e erros de protocolo HTTP | aceita | ADR-014 |
| [D-012](#d-012--escopo-da-idempotency-key-rota-é-o-path-real-da-requisição-não-o-padrão-do-handler) | 2026-09-13 | Escopo da `Idempotency-Key`: rota é o path real da requisição, não o padrão do handler | aceita | ADR-009 |
| [D-013](#d-013--chave-de-idempotência-in_flight-órfã-permanece-409-até-expirar) | 2026-09-13 | Chave de idempotência `IN_FLIGHT` órfã permanece 409 até expirar | aceita | ADR-009 |
| [D-014](#d-014--resposta-replayada-pela-idempotência-é-o-mesmo-documento-json-não-os-mesmos-bytes) | 2026-09-13 | Resposta replayada pela idempotência é o mesmo documento JSON, não os mesmos bytes | aceita | ADR-009 |
| [D-015](#d-015--evento-da-outbox-é-abandonado-após-n-falhas-definitivas-de-publicação-coluna-failed_at) | 2026-09-13 | Evento da outbox é abandonado após N falhas definitivas de publicação (coluna `failed_at`) | aceita | ADR-007 |
| [D-016](#d-016--businessruleexception-em-listener-kafka-não-retentável-vai-direto-ao-dlt) | 2026-09-13 | `BusinessRuleException` em listener Kafka: não-retentável, vai direto ao DLT | aceita | ADR-006 |
| [D-017](#d-017--tópicos-kafka-criados-pelo-kafkaadmin-a-partir-de-invoicekafkatopics) | 2026-09-13 | Tópicos Kafka criados pelo `KafkaAdmin` a partir de `invoice.kafka.topics` | aceita | ADR-006 |
| [D-018](#d-018--bancos-e-kafka-publicam-portas-apenas-em-loopback-sem-perfil-dev-no-compose) | 2026-09-13 | Bancos e Kafka publicam portas apenas em loopback; sem perfil `dev` no Compose | aceita | — |
| [D-019](#d-019--imagem-apachekafka-native421-com-healthcheck-por-probe-apiversions-e-volume-inicializado-por-kafka-init) | 2026-09-13 | Imagem `apache/kafka-native:4.2.1` com healthcheck por probe `ApiVersions` e volume inicializado por `kafka-init` | aceita | ADR-001 |
| [D-020](#d-020--kafka-ui-ghcriokafbatkafka-uiv150-fork-mantido) | 2026-09-13 | Kafka UI: `ghcr.io/kafbat/kafka-ui:v1.5.0` (fork mantido) | aceita | ADR-001 |
| [D-021](#d-021--role-de-aplicação-não-superusuário-por-banco-criada-por-script-de-init-variáveis-postgres_) | 2026-09-13 | Role de aplicação não-superusuário por banco, criada por script de init; variáveis `POSTGRES_*` | aceita | ADR-004 |
| [D-022](#d-022--integração-local-do-codex-ao-fluxo-de-trabalho) | 2026-11-19 | Integração local do Codex ao fluxo de trabalho | aceita | — |
| [D-023](#d-023--uso-alternado-de-claude-code-e-codex) | 2026-11-19 | Uso alternado de Claude Code e Codex | aceita | — |
| [D-024](#d-024--shell-angular-antes-da-integração-com-o-gateway) | 2026-11-19 | Shell Angular antes da integração com o Gateway | aceita | ADR-001 |
| [D-025](#d-025--dependências-maven-do-stock-service-na-f1-06) | 2026-11-19 | Dependências Maven do `stock-service` na F1-06 | aceita | — |
| [D-026](#d-026--entrada-manual-em-produto-inativo) | 2026-11-19 | Entrada manual em produto inativo | aceita | — |
| [D-027](#d-027--limite-de-saldo-na-entrada-manual) | 2026-11-19 | Limite de saldo na entrada manual | aceita | — |
| [D-028](#d-028--mapstruct-e-testes-jpa-na-f1-08) | 2026-11-19 | MapStruct e testes JPA na F1-08 | aceita | — |
| [D-029](#d-029--encerramento-da-implementação-na-f1) | 2026-11-19 | Encerramento da implementação na F1 | aceita | — |
| [D-030](#d-030--starter-de-testes-mvc-do-estoque) | 2026-11-19 | Starter de testes MVC do Estoque | aceita | — |
| [D-031](#d-031--contrato-rest-de-produtos-na-f1-09) | 2026-11-19 | Contrato REST de produtos na F1-09 | aceita | — |
| [D-032](#d-032--auditoria-e-idempotência-http-de-produtos-na-f1) | 2026-11-19 | Auditoria e idempotência HTTP de produtos na F1 | aceita | — |
| [D-033](#d-033--alinhamento-dos-documentos-ao-contrato-de-produtos) | 2026-11-19 | Alinhamento dos documentos ao contrato de produtos | aceita | — |
| [D-034](#d-034--respostas-rest-de-movimentações-de-estoque) | 2026-11-19 | Respostas REST de movimentações de estoque | aceita | — |
| [D-035](#d-035--motivos-e-detalhes-da-rejeição-de-baixa-por-nota) | 2026-11-19 | Motivos e detalhes da rejeição de baixa por nota | aceita | — |
| [D-036](#d-036--nova-tentativa-para-nota-com-baixa-confirmada) | 2026-11-19 | Nova tentativa para nota com baixa confirmada | aceita | — |
| [D-037](#d-037--execução-local-do-estoque-no-compose) | 2026-11-19 | Execução local do Estoque no Compose | aceita | — |
| [D-038](#d-038--dependências-e-migrações-iniciais-do-faturamento) | 2026-11-19 | Dependências e migrações iniciais do Faturamento | aceita | — |
| [D-039](#d-039--histórico-e-dados-da-tentativa-de-impressão) | 2026-11-19 | Histórico e dados da tentativa de impressão | aceita | — |
| [D-040](#d-040--limite-dos-totais-de-item-e-de-nota) | 2026-11-19 | Limite dos totais de item e de nota | aceita | — |
| [D-041](#d-041--mapstruct-e-testes-jpa-no-faturamento) | 2026-11-19 | MapStruct e testes JPA no Faturamento | aceita | — |
| [D-042](#d-042--limites-do-filtro-por-período-das-notas) | 2026-11-19 | Limites do filtro por período das notas | aceita | — |
| [D-043](#d-043--starter-de-testes-mvc-do-faturamento) | 2026-11-19 | Starter de testes MVC do Faturamento | aceita | — |
| [D-044](#d-044--contrato-rest-e-auditoria-de-clientes-na-f1) | 2026-11-19 | Contrato REST e auditoria de clientes na F1 | aceita | — |
| [D-045](#d-045--busca-e-ordenação-da-lista-de-clientes) | 2026-11-19 | Busca e ordenação da lista de clientes | aceita | — |
| [D-046](#d-046--alinhamento-dos-documentos-ao-contrato-de-clientes) | 2026-11-19 | Alinhamento dos documentos ao contrato de clientes | aceita | — |
| [D-047](#d-047--dependências-da-integração-http-com-o-estoque) | 2026-11-19 | Dependências da integração HTTP com o Estoque | aceita | — |
| [D-048](#d-048--janela-do-circuito-do-estoque) | 2026-11-19 | Janela do circuito do Estoque | aceita | — |
| [D-049](#d-049--lotes-e-falhas-http-na-consulta-ao-estoque) | 2026-11-19 | Lotes e falhas HTTP na consulta ao Estoque | aceita | — |
| [D-050](#d-050--alinhamento-do-exemplo-de-resiliência-do-estoque) | 2026-11-19 | Alinhamento do exemplo de resiliência do Estoque | aceita | — |
| [D-051](#d-051--referências-inexistentes-na-api-de-notas) | 2026-11-19 | Referências inexistentes na API de notas | aceita | — |
| [D-052](#d-052--respostas-e-ordenação-da-api-de-notas) | 2026-11-19 | Respostas e ordenação da API de notas | aceita | — |
| [D-053](#d-053--edição-período-e-auditoria-da-api-de-notas) | 2026-11-19 | Edição, período e auditoria da API de notas | aceita | — |
| [D-054](#d-054--mensagem-de-rejeição-do-fechamento) | 2026-11-20 | Mensagem de rejeição do fechamento | aceita | — |
| [D-055](#d-055--resultado-de-baixa-para-nota-inexistente) | 2026-11-20 | Resultado de baixa para nota inexistente | aceita | — |
| [D-056](#d-056--execução-local-do-faturamento-no-compose) | 2026-11-20 | Execução local do Faturamento no Compose | aceita | — |
| [D-057](#d-057--sequência-de-notas-no-seed-do-faturamento) | 2026-11-20 | Sequência de notas no seed do Faturamento | aceita | — |
| [D-058](#d-058--dependências-e-execução-local-do-gateway) | 2026-11-20 | Dependências e execução local do Gateway | aceita | — |
| [D-059](#d-059--correlação-http-e-cors-no-gateway) | 2026-11-20 | Correlação HTTP e CORS no Gateway | aceita | — |
| [D-060](#d-060--origem-da-api-e-imagens-do-frontend) | 2026-11-20 | Origem da API e imagens do frontend | aceita | — |
| [D-061](#d-061--feedback-e-repetição-de-requisições-no-frontend) | 2026-11-20 | Feedback e repetição de requisições no frontend | aceita | — |
| [D-062](#d-062--rotas-lazy-antes-das-telas-de-domínio) | 2026-11-20 | Rotas lazy antes das telas de domínio | aceita | — |
| [D-063](#d-063--validação-do-código-no-cadastro-de-produtos) | 2026-11-20 | Validação do código no cadastro de produtos | aceita | — |
| [D-064](#d-064--confirmação-de-saída-dos-formulários-de-produto) | 2026-11-20 | Confirmação de saída dos formulários de produto | aceita | — |
| [D-065](#d-065--máscara-de-telefone-nos-dados-do-cliente) | 2026-11-20 | Máscara de telefone nos dados do cliente | aceita | — |

---

## Decisões

### D-001 — Modelo de trabalho humano ↔ agente

- **Data**: 2026-09-12
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: o projeto será implementado por agente de IA (Claude Code) com revisão humana. Era preciso definir papéis, fonte da verdade, o que fazer diante de lacunas na especificação e como registrar decisões. A pesquisa de boas práticas está em `docs/guia-trabalho-com-agente.md`.
- **Opções consideradas**: 1) agente decide lacunas sozinho e relata — rápido, mas desvia do escopo e perde o "porquê"; 2) agente para e pergunta, registra e continua — mais interrupções, mas decisões rastreáveis; 3) tudo em ADRs formais — rigor alto, atrito alto para dúvidas pequenas; 4) somente um `decisions.md` — leve, mas perde o valor de portfólio dos ADRs.
- **Decisão**: (2) + híbrido de (3)/(4): o agente implementa; o humano revisa e é o único que faz commit. Fonte da verdade em ordem: instruções do humano → `decisions.md` → `requisitos.md`/`modelo-de-dados.md` → código. Lacuna não explícita → o agente pergunta (`AskUserQuestion`), registra aqui e continua. `decisions.md` é o registro corrente; decisões caras de reverter são promovidas a ADR. Instruções ao agente em `AGENTS.md` (formato aberto) importado por `CLAUDE.md`; convenções por área em `.claude/rules/`; arquivos de instrução em português.
- **Consequências**: + rastreabilidade de todo desvio; + o humano mantém controle do histórico git; + configuração portável para outros agentes via `AGENTS.md`. − mais pausas para perguntas; − disciplina de manter os documentos sincronizados.
- **Y-statement**: No contexto de um projeto implementado por agente com revisão humana, diante do risco de o agente inventar escopo e de o raciocínio se perder entre sessões, decidimos "parar, perguntar, registrar, continuar" com documentos como fonte da verdade, para manter fidelidade ao escopo e rastreabilidade, aceitando um ritmo de implementação mais pausado.
- **Documentos afetados**: `AGENTS.md`, `CLAUDE.md`, `.claude/rules/*`, `.claude/settings.json`, `docs/guia-trabalho-com-agente.md` (criados)
- **ADR**: —
- **Substitui**: —

### D-002 — Decisões da entrevista de requisitos que divergiram da recomendação

- **Data**: 2026-09-12
- **Status**: aceita
- **Decisor**: humano
- **Contexto**: `requisitos.md` nasceu de uma entrevista estruturada (≈40 decisões, todas refletidas no documento). Registram-se aqui apenas as que o humano tomou **contra a recomendação do agente**, para que o motivo não se perca e não sejam "corrigidas" por engano em sessões futuras.
- **Opções consideradas / Decisão**:
  1. **Locking otimista (`@Version`) para o saldo** em vez de `UPDATE … WHERE balance >= qty` (recomendado por ser mais simples e sem retry). Motivo: demonstrar domínio de JPA/ORM. Custo aceito: retry no consumidor em `OptimisticLockException`. → ADR-008.
  2. **Apache Kafka** em vez de RabbitMQ (recomendado pela leveza e DLQ nativa). Motivo: relevância em vagas e log durável/replay. Custo aceito: stack mais pesada no Compose, DLT manual. → ADR-006.
  3. **Keycloak (OIDC)** em vez de nenhuma autenticação (recomendado para reduzir atrito do avaliador). Motivo: demonstrar Spring Security/OAuth2 ponta a ponta. Custo aceito: +1 container, realm a manter. → ADR-013.
  4. **Client credentials** para Faturamento → Estoque em vez de propagar o token do usuário (recomendado pela simplicidade). Motivo: padrão máquina-a-máquina, permite chamadas fora de contexto de usuário (jobs). → ADR-013.
  5. **Cadastro completo de clientes** em vez de campo de texto na nota (recomendado para conter escopo). Motivo: nota fiscal realista e mais telas Angular. Custo aceito: mais um agregado e CRUD.
- **Consequências**: ver cada item; todas já incorporadas em `requisitos.md` e `modelo-de-dados.md`.
- **Y-statement**: No contexto de um portfólio, diante da tensão entre simplicidade e demonstração de competências, decidimos privilegiar tecnologias de maior visibilidade no mercado (Java, Kafka, Keycloak, JPA) para maximizar o sinal a recrutadores, aceitando uma stack mais pesada e mais código.
- **Documentos afetados**: `requisitos.md` (§8.1, §8.10, §8.11, §14) — já refletidos
- **ADR**: ADR-006, ADR-008, ADR-013 (a escrever na F2)
- **Substitui**: —

### D-003 — Sem funcionalidade de IA no produto

- **Data**: 2026-09-12
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: durante a entrevista de requisitos avaliou-se incluir uma funcionalidade baseada em LLM para ampliar o alcance do portfólio. O custo seria uma dependência externa (chave de API), variabilidade de comportamento em demonstrações e esforço deslocado do foco do projeto.
- **Opções consideradas**: 1) geração de descrição de produto no cadastro, via API de LLM com Spring AI (recomendada: escopo pequeno, visível, desligável sem chave); 2) assistente de criação de nota em linguagem natural com *tool use* consultando produtos (mais impressionante, mais complexo e propenso a erro); 3) resumo/insights de estoque baixo (opera sobre dados agregados); 4) nenhuma funcionalidade de IA.
- **Decisão**: (4). O projeto demonstra Angular, Java e arquitetura de microsserviços; IA fica registrada como evolução futura no README.
- **Consequências**: + escopo contido e reproduzível sem chaves externas; + nenhuma variabilidade não determinística nas demonstrações. − perde-se uma demonstração de integração com LLM (pode ser adicionada depois como feature isolada no `stock-service`).
- **Y-statement**: No contexto de um portfólio focado em Angular, Java e microsserviços, diante da tentação de ampliar o escopo com IA, decidimos não incluir funcionalidade de LLM para manter o foco e a reprodutibilidade, aceitando abrir mão desse sinal adicional.
- **Documentos afetados**: `requisitos.md` §2.7 (fora de escopo)
- **ADR**: —
- **Substitui**: —

### D-004 — Bloqueio determinístico de operações git pelo agente

- **Data**: 2026-09-12
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: somente o humano faz commit. A documentação do Claude Code é explícita: instruções em `CLAUDE.md` são conselhos; hooks e regras de permissão são garantias.
- **Opções consideradas**: 1) só instrução escrita; 2) bloquear apenas `commit/push/tag`, permitindo `add/checkout/stash`; 3) bloquear tudo que altera índice, histórico, branches, working tree ou remoto, permitindo leitura.
- **Decisão**: (3). Duas camadas: regras `permissions.deny` em `.claude/settings.json` (prefixos `Bash(git commit *)` etc., também para `PowerShell`) e hook `PreToolUse` (`.claude/hooks/block-git-write.sh`) com regex sobre cada segmento do comando, cobrindo formas como `git -C . push` que as regras de prefixo não pegam. `gh` de escrita (pr create/merge, release, api POST) também bloqueado. Permitidos: `status`, `diff`, `log`, `show`, `blame`, `branch --list`, `stash list`, `remote -v`, `config --get`. Documentos fonte da verdade e `docker-compose.yml` ficam sob regra `ask` (confirmação obrigatória).
- **Consequências**: + impossível o agente commitar por descuido ou por instrução mal interpretada; + revisão humana garantida antes de cada commit. − o humano precisa fazer staging e commit manualmente a cada entrega; − falsos positivos possíveis em comandos exóticos (o hook devolve o motivo e o agente informa).
- **Y-statement**: No contexto de um agente com acesso ao shell, diante do requisito de que apenas o humano altere o histórico git, decidimos bloquear deterministicamente todas as operações de escrita git/gh para garantir revisão humana em cada commit, aceitando o trabalho manual de staging e eventuais falsos positivos.
- **Documentos afetados**: `.claude/settings.json`, `.claude/hooks/block-git-write.sh`, `AGENTS.md` (Limites)
- **ADR**: —
- **Substitui**: —

### D-005 — Identificador de tentativa de impressão (`print_request_id`) e tabela `invoice_debits`

- **Data**: 2026-09-12
- **Status**: aceita (validada pelo humano em 2026-09-12)
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` (RN-17, §6.7) dizia "uma nota só pode ter uma baixa confirmada", com deduplicação por `eventId` e constraint única em `stock_movements(reference_id)`. Ao modelar, dois problemas: (a) notas com vários itens geram várias movimentações com o mesmo `reference_id` — a constraint estava errada; (b) uma nota rejeitada por saldo volta a `OPEN`, é editada e reimpressa — "uma baixa por `invoiceId`" bloquearia a segunda tentativa; e o job de reconciliação (RF-PRT-06) republica o pedido com **novo** `eventId`, escapando da deduplicação por evento.
- **Opções consideradas**: 1) manter dedupe só por `eventId` e constraint por `(reference_id, product_id)` — não resolve (b) nem a republicação; 2) chave de negócio por tentativa: cada `POST /print` gera `printRequestId`, que viaja nos eventos; o Estoque registra o resultado por tentativa em `invoice_debits` (PK `print_request_id`) e reemite o resultado gravado se a mesma tentativa chegar de novo; índice único parcial garante no máximo uma baixa `CONFIRMED` por `invoice_id`; o Faturamento ignora resultados cujo `printRequestId` não seja o corrente; 3) reserva de estoque na criação da nota — muda o domínio (RN-09) e o escopo.
- **Decisão**: (2), aplicada em `modelo-de-dados.md` §4.4, §5.3 e em `requisitos.md` §6.7, RN-17, §8.5, §8.7. Constraint corrigida para `ux_stock_movements_invoice_product (reference_id, product_id) WHERE type = 'INVOICE_DEBIT'`.
- **Consequências**: + idempotência de negócio independente da entrega de mensagens; + reimpressão após rejeição funciona; + reconciliação segura. − uma tabela e um campo a mais; − os eventos carregam `printRequestId` (contrato AsyncAPI).
- **Y-statement**: No contexto da Saga de fechamento, diante de reentregas, republicações e reimpressões após rejeição, decidimos identificar cada tentativa de impressão com `printRequestId` e registrar seu resultado no Estoque, para garantir no máximo uma baixa confirmada por nota sem impedir novas tentativas, aceitando uma tabela e um campo adicionais nos contratos.
- **Documentos afetados**: `modelo-de-dados.md` §4.4, §5.3, Apêndice A · `requisitos.md` §6.7, RN-17, §8.5, §8.7 (já atualizados)
- **ADR**: ADR-009 (idempotência) deve incorporar esta decisão ao ser escrito
- **Substitui**: —

### D-006 — Usuário de auditoria armazena `preferred_username`

- **Data**: 2026-09-12
- **Status**: aceita (validada pelo humano em 2026-09-12; alternativas `sub` e "ambos" rejeitadas)
- **Decisor**: humano (proposta: agente)
- **Contexto**: colunas `created_by`/`updated_by`/`changed_by` precisam identificar o usuário. O JWT do Keycloak oferece `sub` (UUID estável, ilegível) e `preferred_username` (legível, pode mudar se o usuário for renomeado).
- **Opções consideradas**: 1) `preferred_username` — legível no histórico de movimentações e na timeline da nota sem consulta ao Keycloak; instável se renomeado; 2) `sub` — estável; exige resolver nome para exibir; 3) ambos — duas colunas por tabela.
- **Decisão**: (1), por legibilidade nas telas e no PDF; renomeação de usuário é evento raro e sem impacto funcional neste sistema. Já refletido em `modelo-de-dados.md` §2.5.
- **Consequências**: + exibição direta; − histórico mostra o nome antigo se o usuário for renomeado.
- **Y-statement**: No contexto da auditoria de operações, diante da escolha entre identificador estável e nome legível, decidimos armazenar `preferred_username` para exibir o autor sem chamadas ao Keycloak, aceitando inconsistência histórica em caso de renomeação.
- **Documentos afetados**: `modelo-de-dados.md` §2.5 (já reflete a proposta)
- **ADR**: —
- **Substitui**: —

### D-007 — Repositório público contém código, README e documentação de arquitetura; especificação e config do agente ficam locais

- **Data**: 2026-09-12
- **Status**: revogada em 2026-09-12 — `docs/` inteiro, `AGENTS.md` e `CLAUDE.md` passaram a ser versionados e públicos; apenas `.claude/` e `docs/escopo.pdf` permanecem fora do repositório (`.gitignore`, `requisitos.md` §8.3, RNF-DOC-01)
- **Decisor**: humano
- **Contexto**: o humano alterou o `.gitignore` para excluir `docs/`, `.claude/`, `AGENTS.md`, `CLAUDE.md` (e, por engano, o próprio `.gitignore`). `requisitos.md` §7.9 prevê ADRs, diagramas C4 e OpenAPI/AsyncAPI em `docs/` como entregáveis públicos — era preciso decidir o que o repositório público mostra.
- **Opções consideradas**: 1) versionar tudo, inclusive a config do agente e o registro de decisões — mostra o processo, mas expõe o desenvolvimento assistido; 2) versionar `docs/` inteiro e só a config do agente fora — especificação pública; 3) repositório público = código + README + documentação de arquitetura (`docs/adr/`, `docs/architecture/`, `docs/api/`); especificação (`requisitos.md`, `modelo-de-dados.md`), registro de decisões, guia de processo e toda a config do agente permanecem **locais**, ignorados arquivo a arquivo (não a pasta `docs/` inteira, para que os documentos de arquitetura sejam versionados quando forem criados).
- **Decisão**: (3). O `.gitignore` é versionado (removida a linha que o ignorava — sem ele, quem clonar o repositório não teria `target/`, `node_modules/` etc. ignorados). RNF-DOC-02 a 05 permanecem válidos; §8.3 de `requisitos.md` recebe nota de que os arquivos de especificação/processo não são versionados.
- **Consequências**: + o repositório se apresenta como trabalho autoral, com ADRs, C4 e contratos públicos; + os documentos de trabalho continuam sendo a fonte da verdade localmente. − quem avaliar não vê requisitos e modelo de dados completos (o README e os ADRs devem cobrir o essencial); − backup dos documentos locais é responsabilidade do humano (não estão no git).
- **Y-statement**: No contexto de um repositório público de portfólio, diante da escolha entre expor o processo de desenvolvimento assistido e apresentar apenas o resultado, decidimos versionar código, README e documentação de arquitetura, mantendo especificação e configuração do agente locais, para que o repositório seja lido como trabalho autoral, aceitando que os documentos de trabalho fiquem fora do controle de versão.
- **Documentos afetados**: `.gitignore` (atualizado) · `requisitos.md` §8.3 (nota aplicada em 2026-09-12 após confirmação) · `AGENTS.md` (Fluxo git — atualizado)
- **ADR**: —
- **Substitui**: —

### D-008 — Versões da plataforma: Spring Boot 4.1, Java 21, `groupId` `dev.eliascosta.invoice`

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: F1-01 (bootstrap do build) exige fixar as versões da plataforma (premissa P5 de `requisitos.md`: "última estável/LTS disponível compatível com as escolhas deste documento"). O documento (§1, §2.6 C1, §8.1, §14) dizia "Spring Boot 3.x", mas em 2026-09-13 o suporte OSS da linha 3.5 já havia terminado (2026-06-30; último patch 3.5.16). Linhas ativas: 4.0.8 (OSS até 2026-12-31) e 4.1.1 (lançada 2026-08-20, OSS até 2027-07-31); Spring Cloud 2025.1.3 (Oakwood) é compatível com Boot 4.0.x e 4.1.x. Também faltava definir o `groupId`/pacote base (`.claude/rules/backend-java.md` usa `dev.<autor>.invoice.<servico>` sem definir `<autor>`).
- **Opções consideradas**:
  1. **Spring Boot 4.1.1** — última estável, maior janela de suporte OSS, Spring Framework 7 / Jakarta EE 11 / Hibernate 7 / Jackson 3; custo: starters renomeados (`spring-boot-starter-webmvc`, `-flyway`, `-webmvc-test`…), `@MockitoBean` no lugar de `@MockBean`, menos exemplos na internet; exige atualizar §8.1, `AGENTS.md` e rules.
  2. Spring Boot 4.0.8 — mesma geração, mas OSS termina em 2026-12-31, forçando migração no meio da F2.
  3. Spring Boot 3.5.16 — mantém o documento como está e o ecossistema mais conhecido; custo: linha sem suporte OSS, sem correções de segurança — sinal ruim em portfólio.
  - Java: **21** (documentado em §8.1, RNF-RUN-02, plano F1-13; `JAVA_HOME` da máquina; LTS mais exigido em vagas) vs 25 (LTS mais recente; exigiria alterar documentos e imagens Docker, e avaliadores com JDK 21 não compilariam).
  - `groupId`: **`dev.eliascosta.invoice`** (convenção já escrita nas rules) vs `io.github.<usuário>.invoice` vs `br.com.eliascosta.invoice`.
- **Decisão**: (1) Spring Boot **4.1.1** com Spring Cloud **2025.1.3**; Java **21** (`maven.compiler.release=21`); Maven **3.9.16** via wrapper 3.3.4 (`only-script`); `groupId` **`dev.eliascosta.invoice`**, pacote base `dev.eliascosta.invoice.<servico>`. Demais versões de build fixadas no parent POM (Spotless 3.10.2 + google-java-format 1.36.1, JaCoCo 0.8.15, Surefire/Failsafe 3.6.0, MapStruct 1.6.3). Angular: major corrente (22) a fixar em F1-24; PostgreSQL 16, Kafka (KRaft) e Keycloak 26 permanecem como documentados, com tags de imagem fixadas em F1-05/F2-01. Detalhes em ADR-001.
- **Consequências**: + plataforma suportada durante toda a vida prevista do projeto (F1–F3); + demonstra domínio da geração atual do Spring; + `groupId` coerente com as rules. − `requisitos.md` (§1, §2.6 C1, §8.1, §14), `AGENTS.md` e `.claude/rules/backend-java.md` atualizados de "Spring Boot 3" para "Spring Boot 4"; − código e testes seguem as convenções do Boot 4 (starters modulares, `@MockitoBean`, Jackson 3 em `tools.jackson`), a observar nas tarefas seguintes.
- **Y-statement**: No contexto do bootstrap de um portfólio em 2026-09, diante do fim do suporte OSS do Spring Boot 3.5 e da premissa de fixar a última estável compatível, decidimos Spring Boot 4.1.1 + Java 21 + `dev.eliascosta.invoice` para maximizar suporte e sinal de atualidade, aceitando a atualização dos documentos e as diferenças de API da geração 4.
- **Documentos afetados**: `requisitos.md` §1 (tabela de capa), §2.6 C1, §8.1, §14 ADR-001 (atualizados em 2026-09-13 após confirmação) · `AGENTS.md` (descrição) · `.claude/rules/backend-java.md` (título) · `docs/adr/0001-versoes-da-plataforma.md` (criado)
- **ADR**: ADR-001
- **Substitui**: —

### D-009 — Subclasse genérica `InvalidRequestException` (400) na hierarquia de erros do `common`

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: F1-02 define a hierarquia `DomainException` + subclasses genéricas previstas na Entrega (`NotFoundException` 404, `ConflictException` 409, `InvalidStateException` 409, `BusinessRuleException` 422, `UpstreamUnavailableException` 503); o status HTTP é decidido pelo `@RestControllerAdvice` a partir da subclasse, e o domínio não conhece HTTP. O catálogo `requisitos.md` §8.8 tem dois erros **400** que nascem de regra de negócio, não de Bean Validation (`field-immutable` — tentativa de alterar `code`/`document`; `duplicate-invoice-item` — RN-08), e nenhuma subclasse prevista mapeia para 400.
- **Opções consideradas**: 1) sexta subclasse `InvalidRequestException` → 400 — catálogo coberto, status continua só no advice; custo: uma classe além da Entrega; 2) manter as cinco e tratar esses casos como `validation-error` via constraints customizadas — sem classe nova, mas os slugs `field-immutable`/`duplicate-invoice-item` deixariam de existir (revogar linhas do catálogo); 3) `DomainException` carrega o status HTTP (`int`) — qualquer serviço define qualquer status sem subclasse, mas conhecimento HTTP entra no domínio, contra a arquitetura hexagonal.
- **Decisão**: (1). Hierarquia fechada com seis subclasses genéricas; o mapa classe → status vive exclusivamente em `ProblemDetailExceptionHandler`. As exceções específicas dos serviços (`ProductCodeAlreadyExistsException`, `InvalidInvoiceStatusException`, …) estendem a subclasse genérica correspondente e informam o slug do catálogo; `field-immutable` (F1-09/F1-17) e `duplicate-invoice-item` (F1-19) estenderão `InvalidRequestException`.
- **Consequências**: + todas as linhas do catálogo §8.8 têm uma subclasse genérica correspondente; + domínio sem dependência de HTTP; − uma classe além da Entrega original de F1-02.
- **Y-statement**: No contexto do catálogo de erros RFC 9457, diante de erros 400 que não vêm de Bean Validation, decidimos adicionar `InvalidRequestException` à hierarquia genérica para manter o status HTTP fora do domínio, aceitando uma subclasse a mais.
- **Documentos afetados**: — (§8.8 já prevê os slugs) · plano F1-02 (Entrega ampliada, registrada no campo **Registro**)
- **ADR**: ADR-014 (a escrever na F2, deve incorporar esta decisão)
- **Substitui**: —

### D-010 — Origem do `traceId` no `ProblemDetail` antes do tracing distribuído

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` §8.8 e RNF-OBS-05 exigem `traceId` em todo `ProblemDetail` (RF-RES-01 CA2 o exibe na UI). Micrometer Tracing só entra em F2-08 e o gateway só passa a emitir `X-Trace-Id` em F1-22; F1-02 precisava definir de onde o advice obtém o valor nesta fase.
- **Opções consideradas**: 1) MDC `traceId` → header `X-Trace-Id` da requisição → omitir a propriedade — nunca inventa valor, F2-08 encaixa sem alterar o advice; 2) gerar um UUID por erro quando não houver — sempre presente, mas não correlaciona com log/trace nenhum (sinal falso); 3) omitir até F2-08 — mais simples, mas RF-RES-01 CA2 e RNF-OBS-05 sem base até a F2.
- **Decisão**: (1), via `TraceIdResolver` no módulo `common`: lê a chave `traceId` do MDC (que o Micrometer Tracing popula em F2-08), senão o header `X-Trace-Id` da requisição (F1-22), senão a propriedade `traceId` é omitida do `ProblemDetail`.
- **Consequências**: + F2-08 não altera o advice; + correlação via header já funciona a partir de F1-22; − até F1-22, respostas obtidas diretamente dos serviços (sem gateway) não trazem `traceId`.
- **Y-statement**: No contexto de um `ProblemDetail` que deve trazer `traceId` antes de existir tracing, diante do risco de inventar um valor sem correlação, decidimos resolver MDC → header → ausente para que a propriedade só apareça quando tiver significado, aceitando sua ausência temporária fora do gateway.
- **Documentos afetados**: —
- **ADR**: ADR-014
- **Substitui**: —

### D-011 — Erros fora do catálogo §8.8: violação de integridade e erros de protocolo HTTP

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a Entrega de F1-02 manda mapear `DataIntegrityViolationException` → 409/422, mas o catálogo §8.8 não tinha slug para violação de constraint de banco não tratada pela aplicação (é rede de segurança: unicidade deve ser detectada antes e virar a `ConflictException` específica — `product-code-already-exists`, `customer-document-already-exists`). Além disso, o `ResponseEntityExceptionHandler` do Spring já trata erros de protocolo HTTP (404 sem rota, 405, 406, 415) que também não estão no catálogo.
- **Opções consideradas** — integridade: 1) dois slugs novos discriminados por SQLState PostgreSQL (`unique-constraint-violation` 409 para `23505`; `data-integrity-violation` 422 para `23503`/`23514`/`23502` e demais); 2) um slug `data-integrity-violation` com dois status (contra a prática do RFC 9457); 3) sempre 409 sem inspecionar SQLState (violação de CHECK apareceria como conflito). Protocolo: a) manter o `ProblemDetail` padrão do Spring (`type` `about:blank`, permitido pelo RFC 9457) enriquecido com `traceId`/`timestamp`; b) slug genérico `http-error`; c) mapear tudo para `validation-error` (semântica incorreta para 405/415).
- **Decisão**: (1) + (a). Duas linhas adicionadas ao catálogo §8.8 (`unique-constraint-violation` 409, `data-integrity-violation` 422), com a observação de que são rede de segurança. Erros de protocolo mantêm o `ProblemDetail` do Spring com `about:blank`; erros 400 de **formato** tratados pelo `ResponseEntityExceptionHandler` (JSON ilegível, tipo de parâmetro errado, parâmetro obrigatório ausente) usam `validation-error`, como já previa a linha "Bean Validation / formato".
- **Consequências**: + todo erro de banco não previsto tem status coerente com a causa; + o catálogo continua sendo a lista dos erros de negócio; − duas linhas novas em §8.8; − 405/415 respondem `about:blank` (o frontend trata por status, RF-RES-01).
- **Y-statement**: No contexto de erros que escapam da aplicação (constraints de banco, protocolo HTTP), diante de um catálogo pensado para erros de negócio, decidimos discriminar violações de integridade por SQLState em dois slugs e manter o padrão do Spring para erros de protocolo, para dar status coerente sem inflar o catálogo, aceitando `about:blank` nesses casos raros.
- **Documentos afetados**: `requisitos.md` §8.8 (duas linhas adicionadas em 2026-09-13 após confirmação)
- **ADR**: ADR-014
- **Substitui**: —

### D-012 — Escopo da `Idempotency-Key`: rota é o path real da requisição, não o padrão do handler

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: agente (trivial, informado no plano de F1-03)
- **Contexto**: `modelo-de-dados.md` §6.3 define `idempotency_keys.route` como "método + rota (`POST /invoices`)" e o `request_hash` como hash **do corpo**. Para `POST /invoices/{id}/print` o corpo é vazio: se `route` fosse o padrão do handler (`POST /invoices/{id}/print`), a mesma chave reenviada para **outra** nota teria rota e hash iguais e receberia, como replay, a resposta da primeira nota.
- **Opções consideradas**: 1) `route` = método + path real da requisição (`POST /invoices/3fa8…/print`), hash só do corpo — path variables discriminam naturalmente; 2) `route` = padrão do handler e hash sobre path + corpo — contraria a definição de `request_hash` no modelo; 3) `route` = padrão e ignorar o problema — incorreto.
- **Decisão**: (1). `route` armazena `<MÉTODO> <requestURI sem query string>`; chave reutilizada em path diferente responde `422 idempotency-key-payload-mismatch`, como já previa §6.3 para "route diferente". O padrão de handler não é usado.
- **Consequências**: + correto para ações sem corpo (`/print`, `/cancel`, `/reactivate`); + nenhuma mudança de schema (`route varchar(200)` comporta o path com UUID). − reenvio da mesma chave para outro recurso é rejeitado com 422 em vez de executado — comportamento desejado.
- **Y-statement**: No contexto de ações `POST` sem corpo identificadas apenas pelo path, diante do risco de um replay devolver a resposta de outro recurso, decidimos escopar a chave pelo path real da requisição para que a idempotência seja por recurso, aceitando que a mesma chave nunca sirva para dois recursos.
- **Documentos afetados**: — (§6.3 já diz "método + rota"; o exemplo `POST /invoices` continua válido)
- **ADR**: ADR-009 (idempotência) deve incorporar esta decisão ao ser escrito
- **Substitui**: —

### D-013 — Chave de idempotência `IN_FLIGHT` órfã permanece 409 até expirar

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RN-16 e `modelo-de-dados.md` §6.3 dizem "chave em processamento → 409". Se o serviço cair (ou for reiniciado) entre o `INSERT (IN_FLIGHT)` e a conclusão, a linha fica `IN_FLIGHT` sem ninguém para completá-la ou removê-la; os documentos não dizem o que acontece com reenvios dessa chave.
- **Opções consideradas**: 1) nenhum tratamento especial: a chave responde 409 até o TTL de 24 h; o cliente gera nova chave para nova tentativa (o frontend só reutiliza a chave em retries automáticos, §8.9); 2) timeout de in-flight configurável (ex.: 60 s) após o qual um novo claim retoma a chave e reexecuta — recupera de crash sem nova chave, mas arrisca execução dupla quando a original só está lenta (Estoque com timeout + retry + circuit breaker pode passar de 10 s).
- **Decisão**: (1). Chave órfã fica `IN_FLIGHT` até `expires_at`; o claim atômico só reaproveita linhas expiradas. Zero risco de execução dupla; comportamento idêntico ao descrito nos documentos.
- **Consequências**: + semântica simples e segura; + nenhum parâmetro novo. − após um crash, aquela chave específica fica inutilizável por até 24 h (o usuário recarrega o formulário e recebe nova chave).
- **Y-statement**: No contexto de uma chave `IN_FLIGHT` deixada por um crash, diante da escolha entre recuperação automática e segurança contra execução dupla, decidimos manter o 409 até a expiração para nunca executar duas vezes, aceitando que a chave fique presa até 24 h.
- **Documentos afetados**: —
- **ADR**: ADR-009 (idempotência) deve incorporar esta decisão ao ser escrito
- **Substitui**: —

### D-014 — Resposta replayada pela idempotência é o mesmo documento JSON, não os mesmos bytes

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: agente (trivial, informado no relatório de F1-03)
- **Contexto**: RN-16 diz "mesma chave + mesmo payload → mesma resposta". `modelo-de-dados.md` §6.3 armazena o corpo em `response_body jsonb`. O `jsonb` do PostgreSQL guarda o documento decomposto e o devolve renormalizado: espaços após `:` e `,`, chaves reordenadas (por tamanho, depois bytes) e duplicatas removidas. O IT de F1-03 mostrou que o replay de `{"id":"…","name":"…"}` volta como `{"id": "…", "name": "…"}` e que um `ProblemDetail` volta com as chaves em outra ordem.
- **Opções consideradas**: 1) aceitar igualdade **semântica**: o replay é o mesmo documento JSON (mesmos campos e valores), sem garantir bytes, ordem de chaves ou espaçamento; 2) trocar a coluna para `json` (preserva o texto) — altera o schema documentado e perde indexação/validação do `jsonb`; 3) guardar o corpo como string JSON escapada dentro do `jsonb` — preserva bytes, mas anula o propósito do tipo e dobra o escape.
- **Decisão**: (1). Nenhum cliente JSON depende de ordem de chaves ou espaçamento; testes comparam árvores JSON, não texto. Sem mudança de schema.
- **Consequências**: + schema e semântica do modelo mantidos; + testes robustos a formatação. − uma comparação byte a byte de replay (ex.: `diff` de `curl` em script de demonstração) precisa passar por `jq -S` ou equivalente.
- **Y-statement**: No contexto do replay de respostas armazenadas em `jsonb`, diante da renormalização feita pelo PostgreSQL, decidimos definir "mesma resposta" como igualdade de documento JSON para manter o schema documentado, aceitando que bytes, ordem de chaves e espaçamento possam diferir.
- **Documentos afetados**: — (RN-16 permanece válida com esta leitura)
- **ADR**: ADR-009 (idempotência) deve registrar esta nuance
- **Substitui**: —

### D-015 — Evento da outbox é abandonado após N falhas definitivas de publicação (coluna `failed_at`)

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (contra a recomendação do agente na primeira opção; detalhamento proposto pelo agente)
- **Contexto**: F1-04 implementa o `OutboxPublisher` (RNF-ARC-05, §8.11). `modelo-de-dados.md` §6.1 prevê `attempts` e `last_error`, mas nenhum documento fixa limite de tentativas nem o que acontece com um evento cuja publicação falha repetidamente; o schema de A.3 não tinha estado terminal. Com o poller a 500 ms e o lote interrompido na primeira falha (para preservar a ordem por chave exigida em §8.7), uma linha que nunca publica bloquearia as seguintes indefinidamente; por outro lado, o cliente Kafka distingue erros transitórios (`RetriableException`, timeout de ack — broker fora, líder em eleição) de erros definitivos (`RecordTooLargeException`, `InvalidTopicException`, autorização), e abandonar eventos durante uma queda do broker anularia a garantia da outbox (RNF-RES-05).
- **Opções consideradas**: 1) retentar para sempre — sem mudança de schema, nenhum evento perdido, visibilidade por `attempts`/`last_error`/métrica (recomendação inicial do agente); custo: uma falha permanente bloqueia a fila até intervenção manual; 2) desistir após N falhas de qualquer natureza — simples, mas uma queda do Kafka de ≈ N × timeout descarta eventos; 3) desistir após N falhas **definitivas** (não-`RetriableException`, não-timeout), marcando a linha com coluna nova `failed_at`; falhas transitórias incrementam `attempts`/`last_error` mas nunca abandonam; 4) sem coluna, abandono implícito por `attempts >= max` — estado invisível e sensível a mudança de configuração.
- **Decisão**: (3). `outbox_events` ganha `failed_at timestamptz NULL` e `ck_outbox_events_terminal CHECK (published_at IS NULL OR failed_at IS NULL)`; o índice parcial passa a `WHERE published_at IS NULL AND failed_at IS NULL`. O poller só enxerga linhas com ambos nulos; após `invoice.outbox.publisher.max-attempts` (padrão **5**, simétrico a RNF-RES-02) falhas cuja última causa é definitiva, grava `failed_at` e segue para a próxima linha. Métricas: `outbox_pending_events` (ignora abandonados) e `outbox_failed_events`. Reprocessamento é manual: `UPDATE outbox_events SET failed_at = NULL, attempts = 0 WHERE id = …`. Linhas abandonadas não são removidas pelo job de limpeza (evidência para diagnóstico).
- **Consequências**: + a fila nunca fica presa por um evento impublicável; + indisponibilidade do broker continua sem perda; + estado explícito, consultável e observável. − uma coluna, uma constraint e um índice alterados em A.3 (F1-06/F1-14 usam a DDL atualizada); − classificação de erro depende da hierarquia do cliente Kafka (erro não classificado como `RetriableException` conta como definitivo); − reprocessar exige SQL manual (endpoint administrativo fica como evolução).
- **Y-statement**: No contexto do poller da outbox, diante de eventos que nunca poderão ser publicados versus quedas transitórias do broker, decidimos abandonar um evento apenas após N falhas definitivas, marcando-o em `failed_at`, para nunca travar a fila nem perder eventos numa indisponibilidade, aceitando uma coluna a mais e o reprocessamento manual.
- **Documentos afetados**: `modelo-de-dados.md` §6.1, §12, Apêndice A.3 · `requisitos.md` §8.11, RNF-OBS-03 (atualizados em 2026-09-13 após confirmação)
- **ADR**: ADR-007 (outbox com poller) deve incorporar esta decisão ao ser escrito
- **Substitui**: —

### D-016 — `BusinessRuleException` em listener Kafka: não-retentável, vai direto ao DLT

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` §8.11 diz que `BusinessRuleException` é marcada como *not retryable* no `DefaultErrorHandler` — no Spring Kafka isso significa pular o backoff e entregar imediatamente ao recoverer (DLT). RNF-RES-02 diz que "exceções de negócio não vão para DLT — geram evento `rejected`". As duas frases descrevem camadas diferentes: o consumidor (F1-11/F1-20) trata a regra de negócio e grava o evento `rejected` na outbox, então a exceção nunca deveria chegar ao error handler; faltava definir o que acontece se chegar.
- **Opções consideradas**: 1) `addNotRetryableExceptions(BusinessRuleException.class)` → uma tentativa → `<topic>.DLT` — leitura literal de §8.11, visibilidade em caso de bug; 2) recoverer customizado que só loga e confirma o offset — leitura literal de RNF-RES-02, mas um bug no consumidor perde o evento sem rastro além do log; 3) tratar como qualquer exceção (5 tentativas + DLT) — contraria §8.11 e desperdiça ≈ 15 s de backoff numa exceção determinística.
- **Decisão**: (1). Regra para os consumidores: capturar a regra de negócio dentro do use case e produzir `rejected`; `BusinessRuleException` que escapa é bug e aparece no DLT sem retentativas. RNF-RES-02 permanece válido nessa leitura.
- **Consequências**: + sem perda silenciosa; + sem backoff inútil. − o DLT pode receber eventos que, a rigor, deveriam ter virado `rejected` — sinal de bug a corrigir.
- **Y-statement**: No contexto do tratamento de erros dos consumidores, diante da ambiguidade entre "não-retentável" e "não vai para DLT", decidimos enviar exceções de negócio que escapem diretamente ao DLT para ter visibilidade de bugs, aceitando que o DLT sirva também como alarme de consumidor mal implementado.
- **Documentos afetados**: — (RNF-RES-02 e §8.11 permanecem válidos com esta leitura)
- **ADR**: ADR-006 (Kafka) deve registrar esta nuance
- **Substitui**: —

### D-017 — Tópicos Kafka criados pelo `KafkaAdmin` a partir de `invoice.kafka.topics`

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` §8.7 fixa 3 partições para `stock.debit-*` e 1 para `*.DLT`; sem criação explícita o broker auto-cria com 1 partição. A Entrega de F1-04 cita `KafkaAdmin` na configuração base, mas não dizia onde os tópicos são declarados.
- **Opções consideradas**: 1) lista em propriedades no `common` (`invoice.kafka.topics[].{name,partitions}` + `invoice.kafka.dead-letter.partitions`) convertida em bean `NewTopics`; cada serviço declara no `application.yml`; 2) beans `NewTopic` em cada serviço (F1-11/F1-20) — duplicação e sem teste em F1-04; 3) container de init no Compose — ITs e execução local sem os tópicos corretos.
- **Decisão**: (1). Para cada tópico listado o `KafkaAdmin` do Boot cria `<name>` com as partições indicadas e `<name>.DLT` com `dead-letter.partitions` (padrão 1); criação é idempotente (só cria o que falta), então ambos os serviços podem declarar a lista completa. Fator de replicação fica no padrão do broker (1 no Compose).
- **Consequências**: + declarativo e testado no IT do `common`; + F1-06/F1-14 só precisam de YAML. − mais uma propriedade a documentar; − `KafkaAdmin` com `fail-fast=false` (padrão) apenas avisa se o broker estiver fora na subida (RNF-RES-05) e cria os tópicos na próxima oportunidade.
- **Y-statement**: No contexto da criação dos tópicos com o número de partições do contrato, diante de auto-criação com 1 partição, decidimos declará-los por propriedade no `common` e deixar o `KafkaAdmin` criá-los para manter contrato e código no mesmo lugar, aceitando uma propriedade adicional.
- **Documentos afetados**: —
- **ADR**: ADR-006
- **Substitui**: —

### D-018 — Bancos e Kafka publicam portas apenas em loopback; sem perfil `dev` no Compose

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` §8.13 dizia que bancos e Kafka "não expõem portas por padrão exceto para desenvolvimento (`ports` sob perfil `dev`)". O Docker Compose não permite condicionar `ports` a um perfil (perfis selecionam serviços inteiros), e as tarefas F1-06/F1-09 precisam de acesso do host (`./mvnw spring-boot:run` com perfil `local`, clientes SQL, inspeção do Kafka). Era preciso escolher o mecanismo real.
- **Opções consideradas**: 1) um único `docker-compose.yml` publicando as portas ligadas a `127.0.0.1` (`127.0.0.1:5433`, `127.0.0.1:5434`, `127.0.0.1:9092`) — simples, um arquivo, nunca acessível fora da máquina; 2) arquivo base sem `ports` + `docker-compose.override.yml` versionado (carregado automaticamente) — idiomático, mas o padrão continua exposto e há um arquivo a mais; 3) `docker-compose.dev.yml` opt-in via `-f` ou `COMPOSE_FILE` no `.env` — cumpre a frase ao pé da letra, mas alonga os comandos ou depende do `.env`, menos óbvio para quem clona.
- **Decisão**: (1). A propriedade de segurança pretendida por §8.13 (não expor bancos e broker à rede) é obtida pelo *bind* em loopback; o Kafka ganha dois listeners (`INTERNAL://kafka:9092` na rede `internal`, `EXTERNAL://localhost:9092` publicado no host a partir da porta 9094 do container). §8.13 é reescrito para descrever isso.
- **Consequências**: + um arquivo, comportamento único para avaliador e desenvolvedor; + `spring-boot:run` e clientes SQL funcionam sem passos extras. − não existe forma "fechada" da stack (irrelevante: P1 diz que não há deploy público); − o texto de §8.13 muda.
- **Y-statement**: No contexto de uma stack local sem deploy público, diante da impossibilidade de condicionar `ports` a perfis do Compose, decidimos publicar as portas de bancos e Kafka apenas em `127.0.0.1` num único arquivo, para manter simplicidade e acesso de desenvolvimento, aceitando que a stack nunca rode "fechada".
- **Documentos afetados**: `requisitos.md` §8.13 (reescrito em 2026-09-13 após confirmação)
- **ADR**: —
- **Substitui**: —

### D-019 — Imagem `apache/kafka-native:4.2.1` com healthcheck por probe `ApiVersions` e volume inicializado por `kafka-init`

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: ADR-001 deixou a tag da imagem do Kafka para a infra (F1-05). Os ITs do `common` (F1-04) já usam `apache/kafka-native:4.2.1`. RNF-RES-06 exige `healthcheck` real (os serviços dependerão de `kafka` `healthy` para o `KafkaAdmin` criar os tópicos com as partições de §8.7 — D-017). Verificado empiricamente: a imagem nativa não traz os scripts CLI (`kafka-topics.sh`, `kafka-broker-api-versions.sh`), tem `bash`/`nc`/`wget`, fica `RUNNING` em < 1 s; um teste de porta (`nc -z`) dá falso positivo porque o broker abre o socket antes de habilitar o processamento de requisições; um probe de protocolo `ApiVersions` (api_key 18, v0) via `nc` só recebe resposta quando o broker está servindo. Também verificado: a imagem roda como `appuser` (uid 1000) e um volume nomeado montado em caminho inexistente na imagem nasce `root:root` → `Permission denied` ao formatar o storage.
- **Opções consideradas**: 1) `apache/kafka-native:4.2.1` — igual aos ITs, ~140 MB, ~150 MB de RAM, subida < 1 s; healthcheck por script `infra/kafka/healthcheck.sh` (probe `ApiVersions`); 2) `apache/kafka:4.2.1` (JVM) — CLI completa e healthcheck legível com `kafka-broker-api-versions.sh`; custo: ~400 MB de imagem, ~700 MB de RAM, 5–10 s de subida, cada probe sobe uma JVM. Para o volume: a) serviço `kafka-init` (mesma imagem, `user: "0:0"`, `chown 1000:1000`) com `depends_on: condition: service_completed_successfully`; b) rodar o broker como root — contraria o espírito de RNF-RUN-03; c) sem volume — perde mensagens em voo num `docker compose down` (exatamente o que outbox + persistência devem evitar).
- **Decisão**: (1) + (a). Healthcheck: `bash /healthcheck.sh` (montado de `infra/kafka/healthcheck.sh`, sem depender de bit de execução), intervalo 5 s. Dados em `KAFKA_LOG_DIRS=/var/lib/kafka/data` no volume `kafka-data`; `CLUSTER_ID` fixo para o `kafka-storage format` ser determinístico. Auto-criação de tópicos permanece no padrão do broker (habilitada), como pressupõem D-017 e o Registro de F1-04.
- **Consequências**: + mesma versão de broker em teste e execução; + subida quase instantânea (RNF-RUN-04); + healthcheck que reflete prontidão real. − inspeção de tópicos só pelo Kafka UI (perfil `tools`) ou por clientes externos; − um serviço auxiliar (`kafka-init`) e um script no repositório; − a imagem nativa é marcada como experimental pelo projeto Kafka (aceitável para ambiente local).
- **Y-statement**: No contexto da infra local, diante de uma imagem sem CLI mas muito mais leve e já usada nos testes, decidimos fixar `apache/kafka-native:4.2.1` com um probe de protocolo como healthcheck e um init de volume, para ter subida rápida e ordenação confiável de dependências, aceitando a ausência de ferramentas de linha de comando no container.
- **Documentos afetados**: `requisitos.md` §8.3 (árvore `infra/kafka/`, atualizada em 2026-09-13 após confirmação)
- **ADR**: ADR-001 (tag registrada aqui; o ADR permanece imutável)
- **Substitui**: —

### D-020 — Kafka UI: `ghcr.io/kafbat/kafka-ui:v1.5.0` (fork mantido)

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: §8.2 e RNF-RUN-01 preveem "Kafka UI" no perfil `tools` (8090) sem indicar a imagem. O projeto original `provectuslabs/kafka-ui` não recebe versões desde v0.7.2 (2024-04-10); o fork `kafbat/kafka-ui` é o mantido (v1.5.0 em 2026-04-20), com a mesma interface e as mesmas variáveis (`KAFKA_CLUSTERS_0_*`), e suporta Kafka 4.x/KRaft.
- **Opções consideradas**: 1) `ghcr.io/kafbat/kafka-ui:v1.5.0`; 2) `provectuslabs/kafka-ui:v0.7.2` — nome mais conhecido, no Docker Hub, sem manutenção e sem garantia com Kafka 4.x.
- **Decisão**: (1). Serviço `kafka-ui` no perfil `tools`, rede `internal`, `depends_on: kafka: service_healthy`, porta `127.0.0.1:8090:8080`.
- **Consequências**: + compatível com o broker escolhido; + segurança recebe correções. − imagem no GHCR (não no Docker Hub).
- **Y-statement**: No contexto de uma ferramenta opcional de inspeção, diante de um projeto abandonado e seu fork mantido, decidimos o fork para garantir compatibilidade com Kafka 4.x, aceitando o registro GHCR.
- **Documentos afetados**: —
- **ADR**: ADR-001 (tag registrada aqui)
- **Substitui**: —

### D-021 — Role de aplicação não-superusuário por banco, criada por script de init; variáveis `POSTGRES_*`

- **Data**: 2026-09-13
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a Entrega de F1-05 pede "scripts de init dos bancos (`CREATE DATABASE stock_db/billing_db` + usuários)" e `.env.example` com `POSTGRES_*` (§8.13, RNF-SEC-06), mas nenhum documento fixa nomes de usuários, quem é superusuário nem os nomes das variáveis — e esses nomes serão usados por `application.yml` (F1-06/F1-14) e pelos serviços no Compose (F1-13/F1-21).
- **Opções consideradas**: 1) superusuário `postgres` (`POSTGRES_PASSWORD`, só para administração e init) + roles de aplicação **não-superusuárias** `stock_user`/`billing_user`, owners de `stock_db`/`billing_db`, criadas por um único `infra/postgres/init-db.sh` parametrizado por `APP_DB_NAME/APP_DB_USER/APP_DB_PASSWORD` e montado nos dois containers; 2) usar `POSTGRES_USER`/`POSTGRES_DB` da imagem em cada container (a aplicação roda como superusuário; sem script) — menos peças, mas contraria a Entrega e o princípio de menor privilégio.
- **Decisão**: (1). Nomes dos bancos fixos (`stock_db`, `billing_db`, como no modelo de dados); variáveis: `POSTGRES_PASSWORD`, `POSTGRES_STOCK_USER`, `POSTGRES_STOCK_PASSWORD`, `POSTGRES_BILLING_USER`, `POSTGRES_BILLING_PASSWORD`, com valores de desenvolvimento em `.env.example` e os mesmos valores como default (`${VAR:-…}`) no `docker-compose.yml`, para que `docker compose up --build` funcione sem `.env` (RNF-RUN-01). `pg_trgm` é extensão *trusted* (PG ≥ 13), então o owner do banco executa `V1__extensions` sem ser superusuário — verificado na entrega. Migrações são executadas pelo mesmo usuário da aplicação (Flyway do serviço).
- **Consequências**: + aplicação com menor privilégio; + credenciais por serviço (C3: nenhum serviço acessa o banco do outro); + stack sobe sem passo manual. − um script no repositório; − o superusuário compartilha a senha entre os dois containers (aceitável localmente); − extensões não *trusted* (nenhuma prevista) exigiriam mudança no init.
- **Y-statement**: No contexto de dois bancos PostgreSQL em containers, diante da ausência de definição de usuários e variáveis, decidimos criar por script uma role de aplicação sem privilégios de superusuário por banco e padronizar `POSTGRES_*` com defaults no Compose, para menor privilégio e subida em um comando, aceitando um script e uma senha de superusuário compartilhada.
- **Documentos afetados**: `requisitos.md` §8.3 (árvore `infra/postgres/`, atualizada em 2026-09-13 após confirmação)
- **ADR**: ADR-004 (banco por serviço) pode registrar esta decisão ao ser escrito
- **Substitui**: —

### D-022 — Integração local do Codex ao fluxo de trabalho

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: o projeto já usa Claude Code com `AGENTS.md`, plano de implementação local e controle humano do Git (D-001 e D-004). O humano pediu uma configuração análoga para usar o Codex no mesmo repositório.
- **Opções consideradas**: 1) configuração específica local, como a do Claude Code, exige configuração em cada clone; 2) configuração específica versionada, disponível a cada clone.
- **Decisão**: (1). Abrir a tarefa F0-05 e manter a configuração em `.codex/` e `.agents/skills/`, ambas ignoradas pelo Git. Os agentes compartilham o plano, as instruções e o registro de decisões. O humano autorizou atualizar `requisitos.md` §8.3.
- **Consequências**: + os agentes acompanham o mesmo estado das tarefas e mantêm configurações próprias; − outro clone precisa receber a configuração local separadamente.
- **Y-statement**: No contexto do uso de dois agentes no mesmo repositório, diante do risco de duplicar regras e progresso, decidimos manter as configurações locais e compartilhar as instruções e o plano para facilitar a troca de agente, aceitando configurar cada clone.
- **Documentos afetados**: `AGENTS.md` · `guia-trabalho-com-agente.md` · `requisitos.md` §8.3 · `.gitignore`
- **ADR**: —
- **Substitui**: —

### D-023 — Uso alternado de Claude Code e Codex

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano
- **Contexto**: após a integração local do Codex (D-022), o humano esclareceu como os dois agentes serão usados no mesmo checkout.
- **Opções consideradas**: 1) uso alternado com passagem de contexto pelo plano; 2) trabalho simultâneo com coordenação de arquivos, como previa a primeira versão do guia.
- **Decisão**: (1). O humano usa um agente por vez no repositório. Se trocar de agente durante uma tarefa, o próximo retoma a partir do plano compartilhado e inspeciona as alterações existentes antes de editar.
- **Consequências**: + o plano precisa registrar a passagem de contexto entre sessões; − cada sessão de trabalho começa depois de encerrar a anterior.
- **Y-statement**: No contexto de um checkout compartilhado, diante da necessidade de trocar de agente entre sessões, decidimos usar Claude Code e Codex em sequência para preservar a continuidade da tarefa, aceitando concluir a sessão atual antes de iniciar a próxima.
- **Documentos afetados**: `AGENTS.md` · `guia-trabalho-com-agente.md` · plano local · skill local `$tarefa`
- **ADR**: —
- **Substitui**: —

### D-024 — Shell Angular antes da integração com o Gateway

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-24 agrupava o shell Angular e a integração com o Gateway. O humano quer visualizar a interface mais cedo e aprovou antecipar o shell, com nome e tema definidos.
- **Opções consideradas**: 1) antecipar o shell e manter a integração na posição original do roteiro; 2) entregar toda a F1-24 após o Gateway.
- **Decisão**: (1). Executar F1-24A antes de F1-06 com o workspace e o shell estático da F1-24. Usar “Notas Fiscais” na barra superior, tema claro em azul-petróleo e cinzas neutros, Angular, CLI, Material e CDK em 22.3.0 com Vitest, ESLint e Prettier. A F1-24 passa a depender também da F1-24A e entrega a integração com o Gateway e os componentes compartilhados de API.
- **Consequências**: + o shell pode ser executado e revisado antes dos microsserviços; − os dados e fluxos de negócio chegam nas tarefas de integração e de cada tela.
- **Y-statement**: No contexto do MVP, diante da necessidade de visualizar a interface mais cedo, decidimos antecipar o shell Angular para permitir revisão visual antes do Gateway, aceitando entregar as conexões com a API em etapas posteriores.
- **Documentos afetados**: plano local de implementação (reordenado). `requisitos.md` §9, §11 e `modelo-de-dados.md` permanecem aplicáveis.
- **ADR**: ADR-001 (versão exata registrada aqui, conforme D-008)
- **Substitui**: —

### D-025 — Dependências Maven do `stock-service` na F1-06

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-06 introduz as dependências do serviço para integrar `common`, expor Actuator, configurar `permitAll`, aplicar migrações PostgreSQL e verificar a arquitetura e a subida com o Maven Wrapper.
- **Opções consideradas**: 1) reutilizar o BOM do Spring Boot e centralizar a versão do ArchUnit no POM pai; 2) fixar as versões das dependências diretamente no POM do serviço.
- **Decisão**: usar `common`, `spring-boot-starter-actuator`, `spring-boot-starter-security`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` e `org.postgresql:postgresql` com escopo `runtime` para o driver. Usar as versões do BOM para as dependências externas gerenciadas. Nos testes, usar `spring-boot-starter-test`, `spring-boot-testcontainers` com `@ServiceConnection`, os módulos JUnit, PostgreSQL e Kafka do Testcontainers, e `com.tngtech.archunit:archunit` com escopo `test`. Gerenciar o ArchUnit em `dependencyManagement` do POM pai com `archunit.version=1.5.1`. Confirmar no relatório do Surefire que `StockArchitectureTest` foi executado. Na F2, trocar `spring-boot-starter-security` por `spring-boot-starter-oauth2-resource-server` para implementar RNF-SEC-02.
- **Consequências**: as versões do serviço seguem o BOM, os testes de arquitetura compartilham uma versão explícita do ArchUnit e o IT de subida usa as conexões fornecidas pelos containers. A F2 requer atualizar o POM e a configuração isolada de segurança.
- **Y-statement**: No contexto do bootstrap do Estoque, diante da necessidade de validar a arquitetura e a aplicação das migrações, decidimos reutilizar as dependências gerenciadas e centralizar a versão do ArchUnit para manter o build reproduzível, aceitando atualizar a dependência de segurança na F2.
- **Documentos afetados**: plano local de implementação (F1-06). `requisitos.md` §7.4, §8.4 e §8.10 permanecem aplicáveis.
- **ADR**: —
- **Substitui**: —

### D-026 — Entrada manual em produto inativo

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RN-03 proíbe baixar o saldo de produtos inativos. RF-STK-01 prevê entradas e saídas manuais e deixa em aberto a situação de uma entrada em produto inativo.
- **Opções consideradas**: 1) permitir entrada manual com o produto inativo; 2) exigir reativação antes de qualquer entrada.
- **Decisão**: permitir `INBOUND` em produto inativo. A entrada mantém o estado de inativação e registra o novo saldo no histórico. `OUTBOUND` e `INVOICE_DEBIT` exigem produto ativo, conforme RN-03.
- **Consequências**: o usuário pode repor o saldo sem reativar o produto. O saldo fica disponível para baixa após a reativação.
- **Y-statement**: No contexto da reposição de estoque, diante de um produto inativo, decidimos permitir a entrada manual para registrar o saldo recebido, mantendo a restrição de baixa de RN-03.
- **Documentos afetados**: plano local de implementação (F1-07). RF-STK-01 e RN-03 permanecem aplicáveis.
- **ADR**: —
- **Substitui**: —

### D-027 — Limite de saldo na entrada manual

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `modelo-de-dados.md` §2.3 e §4.2 adotam `integer` para o saldo. RF-STK-01 deixa em aberto como rejeitar uma entrada cujo saldo resultante exceda a capacidade desse tipo.
- **Opções consideradas**: 1) usar `validation-error` (400), já previsto no catálogo; 2) definir um erro de negócio específico (422).
- **Decisão**: rejeitar a entrada que elevaria o saldo acima de `2.147.483.647` com `validation-error`. O saldo permanece intacto e a operação não gera movimentação.
- **Consequências**: a operação informa que a quantidade ultrapassa o limite e preserva o saldo e o histórico. A F1-10 usa o mapeamento existente do `common` para 400.
- **Y-statement**: No contexto da entrada de estoque, diante do limite de `integer`, decidimos rejeitar a quantidade que exceda o saldo representável com `validation-error` para preservar a consistência do agregado e do histórico.
- **Documentos afetados**: plano local de implementação (F1-07). `modelo-de-dados.md` §2.3 e §4.2 e o catálogo de `requisitos.md` §8.8 permanecem aplicáveis.
- **ADR**: —
- **Substitui**: —

### D-028 — MapStruct e testes JPA na F1-08

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano
- **Contexto**: a F1-08 introduz os primeiros mapeamentos de persistência e os testes com `@DataJpaTest`. A partir do JDK 23, a descoberta de processadores no classpath exige configuração explícita para que eles sejam executados.
- **Opções consideradas**: 1) configurar o processador apenas no módulo de Estoque; 2) centralizar o caminho do processador e as opções de geração no plugin de compilação do POM pai.
- **Decisão**: declarar `org.mapstruct:mapstruct` no `stock-service` com a versão 1.6.3 gerenciada pelo POM pai. Configurar `org.mapstruct:mapstruct-processor` em `annotationProcessorPaths` do `maven-compiler-plugin` no POM pai, com versão explícita `${mapstruct.version}` e uso exclusivo como processador. Definir `-Amapstruct.defaultComponentModel=spring` e `-Amapstruct.unmappedTargetPolicy=ERROR` em `compilerArgs`. Usar `spring-boot-starter-data-jpa-test` em escopo `test` com a versão gerenciada pelo BOM. Incluir `org.mapstruct` na regra do `StockArchitectureTest` que protege o domínio de dependências de framework.
- **Consequências**: os módulos herdam o processamento explícito e as mesmas opções de geração. Os mapeadores gerados são componentes Spring e campos de destino esquecidos interrompem a compilação. ArchUnit verifica a independência do domínio em relação ao MapStruct.
- **Y-statement**: No contexto da persistência JPA, diante da necessidade de geração explícita e consistente dos mapeadores, decidimos configurar o processador no POM pai para reproduzir a compilação e detectar omissões nos mapeamentos, com as mesmas opções de geração para todos os módulos.
- **Documentos afetados**: plano local de implementação (F1-08).
- **ADR**: —
- **Substitui**: —

### D-029 — Encerramento da implementação na F1

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano
- **Contexto**: o plano de entrega em `requisitos.md` §11 previa três fases até a `v1.0.0`. A F1 já prevê uma demonstração ponta a ponta do fechamento de notas e da baixa de estoque, com cenários de concorrência, idempotência e recuperação após indisponibilidade do Estoque.
- **Opções consideradas**: 1) implementar F1, F2 e F3, ampliando as capacidades demonstradas e o esforço de implementação. 2) concluir a F1 e apresentar a `v0.1.0`, concentrando o esforço no fluxo principal e na demonstração local.
- **Decisão**: (2). Encerrar a implementação ao concluir a F1 e apresentar a `v0.1.0` como a entrega do portfólio. Para esse objetivo, o custo adicional de F2 e F3 supera o ganho esperado.
- **Consequências**: o encerramento acontece na F1, com a demonstração e o README previstos na F1-30. F2 e F3 ficam como evoluções possíveis fora do escopo da entrega. A `v0.1.0` é uma demonstração local sem autenticação e sem geração de PDF.
- **Y-statement**: no contexto de um projeto de portfólio, diante do custo de implementar as fases adicionais, decidimos encerrar a implementação na F1 e apresentar a `v0.1.0` para demonstrar o fluxo central de ponta a ponta, aceitando deixar as capacidades da F2 e da F3 para possíveis evoluções.
- **Documentos afetados**: plano local de implementação (§2, F1-30, §5 e §6) · `requisitos.md` §11 (alinhamento pendente de confirmação) · `README.md` (previsto na F1-30).
- **ADR**: —
- **Substitui**: —

### D-030 — Starter de testes MVC do Estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-09 exige testes `@WebMvcTest`. O `stock-service` já tem o starter base de testes, e o `common` declara o starter MVC no próprio escopo de teste.
- **Opções consideradas**: 1) usar o mesmo starter no Estoque para exercitar o slice MVC. 2) Revisar a estratégia de testes e usar somente o contexto completo.
- **Decisão**: declarar `spring-boot-starter-webmvc-test` no `stock-service`, com escopo `test` e versão gerenciada pelo BOM do Spring Boot.
- **Consequências**: os testes de contrato HTTP executam com um slice MVC. A dependência é usada durante o build de testes.
- **Y-statement**: No contexto dos testes da API de produtos, diante da necessidade de validar o contrato HTTP em um slice, decidimos usar o starter MVC de testes gerenciado pelo BOM para executar `@WebMvcTest`.
- **Documentos afetados**: plano local de implementação (F1-09).
- **ADR**: —
- **Substitui**: —

### D-031 — Contrato REST de produtos na F1-09

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `requisitos.md` §8.6 define o prefixo público e a estrutura de paginação. RF-PRD-03 remete ao histórico, e a F1-10 entrega sua rota paginada. Os campos da resposta individual, a lista de campos ordenáveis e o tratamento de `balance` no `PUT` precisavam de definição.
- **Opções consideradas**: 1) preservar o prefixo do Estoque em todo o percurso, usar o endpoint específico para o histórico e explicitar o contrato de produto. 2) Reescrever o prefixo no gateway ou incorporar o histórico no detalhe.
- **Decisão**: expor `/api/stock/products` no `stock-service` e preservar o prefixo no Gateway. Usar `id`, `code`, `description`, `unitPrice`, `balance`, `active` e `version` nas respostas de produto. No lote, usar os seis campos do RF-PRD-06. Consultar o histórico em `GET /api/stock/products/{id}/movements`, da F1-10. Permitir ordenação por `code`, `description`, `unitPrice`, `balance` e `active`. O padrão é `code,asc` e uma ordenação inválida retorna `400`. Se o `PUT` receber `balance`, responder `400 field-immutable`.
- **Consequências**: o `Location` do cadastro pode usar o mesmo caminho acessível pelo serviço e pelo gateway. O cliente faz uma consulta paginada ao abrir o histórico. Alterações de saldo passam pelos casos de uso de movimentação.
- **Y-statement**: No contexto da integração do Estoque com seus consumidores, diante dos detalhes de contrato ainda em aberto, decidimos fixar a rota, os campos e os critérios de edição e ordenação, para que a API direta e a integração usem as mesmas convenções.
- **Documentos afetados**: plano local de implementação (F1-09 e F1-22) · `requisitos.md` §3.2 e §8.6 (detalhamento registrado nesta decisão).
- **ADR**: —
- **Substitui**: —

### D-032 — Auditoria e idempotência HTTP de produtos na F1

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1 roda sem autenticação (D-029). `modelo-de-dados.md` §2.5 define a auditoria com usuário autenticado e operações internas. Em `requisitos.md`, RN-16 usa uma formulação geral para POSTs, §8.6 marca só o cadastro entre as rotas de produtos da F1-09 e §8.9 cita UUID v4. O `common` valida o formato UUID.
- **Opções consideradas**: 1) seguir a tabela de §8.6 e reutilizar `anonymous` e a validação existente. 2) Exigir a chave também na reativação ou restringir as chaves a UUID v4.
- **Decisão**: usar `anonymous` na auditoria de operações HTTP de produtos da F1. Entre as rotas da F1-09, exigir `Idempotency-Key` no cadastro, aceitando UUID conforme a validação já implementada no `common`.
- **Consequências**: o cadastro reutiliza o tratamento de repetição do `common`. A reativação pode ser chamada sem a chave. Os registros de auditoria das chamadas ao MVP ficam identificados como anônimos.
- **Y-statement**: No contexto do Estoque sem autenticação, diante da diferença entre as regras gerais e a tabela de endpoints, decidimos adotar a regra explícita da tabela, o formato de chave já validado e uma identidade anônima, para integrar a API ao `common` na F1.
- **Documentos afetados**: plano local de implementação (F1-09) · `requisitos.md` RN-16 e §8.9 · `modelo-de-dados.md` §2.5 (alinhamento textual pendente de confirmação).
- **ADR**: —
- **Substitui**: —

### D-033 — Alinhamento dos documentos ao contrato de produtos

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: D-031 e D-032 definiram os detalhes da API de produtos aplicados na F1-09. Os documentos de referência ainda continham formulações anteriores sobre histórico, erros de edição, chaves idempotentes e autoria.
- **Opções consideradas**: 1) alinhar ambos os documentos. 2) Manter os detalhes no registro de decisões.
- **Decisão**: atualizar `requisitos.md` e `modelo-de-dados.md` para refletir D-031 e D-032.
- **Consequências**: os documentos passam a descrever a rota de histórico, os campos e a ordenação, os `POST` que exigem chave, o formato UUID e a autoria `anonymous` no Estoque da F1.
- **Y-statement**: No contexto da entrega da API de produtos, diante da diferença entre a especificação anterior e o contrato aprovado, decidimos alinhar os documentos de referência para que os consumidores tenham a mesma descrição aplicada pelo serviço.
- **Documentos afetados**: `requisitos.md` §3.2, RN-16, §8.6, §8.8 e §8.9 · `modelo-de-dados.md` §2.5 (atualizados em 2026-11-19).
- **ADR**: —
- **Substitui**: —

### D-034 — Respostas REST de movimentações de estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-STK-01 e RF-STK-02 definem as rotas, os status e os dados do histórico. A F1-10 precisava definir os campos JSON de cada item, o corpo do cadastro e a aplicação da autoria da F1 às movimentações manuais.
- **Opções consideradas**: 1) usar a mesma representação no cadastro e no histórico. 2) definir respostas diferentes para as duas operações.
- **Decisão**: o `POST` retorna `201` com o mesmo objeto usado em cada item do `GET` paginado. Os campos são `id`, `productId`, `type`, `quantity`, `balanceAfter`, `reason`, `referenceId`, `referenceNumber`, `createdBy` e `createdAt`. O `POST` omite o header `Location`. Na F1, as movimentações manuais registram `anonymous`.
- **Consequências**: a resposta da gravação fornece o saldo resultante e pode ser exibida junto com os itens do histórico. A referência de nota usa campos nulos nas movimentações manuais.
- **Y-statement**: No contexto das movimentações manuais e do histórico, diante da necessidade de exibir o saldo após cada operação, decidimos compartilhar a representação REST para devolver os mesmos dados nas duas rotas, aceitando omitir o `Location` enquanto a consulta estiver disponível pela coleção.
- **Documentos afetados**: `requisitos.md` §3.3 e §8.6 · `modelo-de-dados.md` §2.5 e §4.3 (detalhamento registrado nesta decisão) · plano local de implementação (F1-10).
- **ADR**: —
- **Substitui**: —

### D-035 — Motivos e detalhes da rejeição de baixa por nota

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-STK-03, RN-13 e `requisitos.md` §8.7 descrevem a rejeição e seus detalhes. O evento tem um único `reason` e pode haver itens com falhas de tipos diferentes. Um produto inexistente também deixa dois campos sem valor conhecido.
- **Opções consideradas**: 1) usar uma ordem fixa de prioridade. 2) Usar o motivo do primeiro item com falha na ordem recebida. Ambas incluem todos os itens com falha nos detalhes.
- **Decisão**: usar `PRODUCT_NOT_FOUND` com prioridade sobre `PRODUCT_INACTIVE`, seguido de `INSUFFICIENT_STOCK`. Incluir todos os itens com falha em `details`. Para um produto inexistente, informar `productCode` e `available` como `null`.
- **Consequências**: o motivo geral é estável quando os mesmos itens são enviados em outra ordem. Os detalhes preservam a quantidade solicitada e os dados de estoque disponíveis para cada falha.
- **Y-statement**: no contexto da baixa atômica, diante de falhas simultâneas em uma nota, decidimos priorizar os motivos e registrar todos os itens com falha para oferecer um resultado determinístico e preservar os dados do diagnóstico.
- **Documentos afetados**: `requisitos.md` RF-STK-03, RN-13 e §8.7 · `modelo-de-dados.md` §4.4 (detalhamento registrado nesta decisão).
- **ADR**: —
- **Substitui**: —

### D-036 — Nova tentativa para nota com baixa confirmada

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RN-17 e D-005 permitem uma única baixa confirmada por nota e definem a reemissão para o mesmo `printRequestId`. O contrato não define um motivo de rejeição para um identificador de tentativa inédito em uma nota que já teve baixa confirmada.
- **Opções consideradas**: 1) tratar o evento como inconsistência técnica e encaminhá-lo ao DLT pela política existente. 2) Ignorar a solicitação e marcar o evento como processado.
- **Decisão**: tratar uma nova tentativa para uma nota com baixa confirmada como inconsistência técnica. Reverter a transação e deixar o Kafka encaminhar o evento ao DLT pela política existente.
- **Consequências**: a baixa confirmada permanece registrada, e a inconsistência fica disponível para diagnóstico no DLT. A reemissão do resultado para o mesmo `printRequestId` continua aplicável.
- **Y-statement**: no contexto da idempotência da Saga, diante de uma nova tentativa para uma nota com baixa confirmada, decidimos usar a política técnica do Kafka para preservar a baixa existente e tornar o evento inconsistente visível.
- **Documentos afetados**: `requisitos.md` RN-17 e §6.7 · `modelo-de-dados.md` §4.4 (detalhamento registrado nesta decisão).
- **ADR**: —
- **Substitui**: —

### D-037 — Execução local do Estoque no Compose

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-13 prevê acesso direto ao Estoque em `8081` antes da integração com o Gateway. A listagem padrão filtra produtos ativos (RF-PRD-02) e o seed inclui um produto inativo (`modelo-de-dados.md` §13.1).
- **Opções consideradas**: 1) publicar o serviço em loopback, aplicar os perfis da demonstração por padrão e verificar ativos e inativos com o filtro existente. 2) Revisar a configuração de acesso ao contêiner antes de implementar.
- **Decisão**: autorizar a alteração de `docker-compose.yml` para publicar o Estoque em `127.0.0.1:8081`, usar `docker,demo` como valor padrão de `SPRING_PROFILES_ACTIVE` e preservar o padrão de credenciais `POSTGRES_STOCK_*` da D-021. Verificar os 12 produtos com duas consultas, uma para os 11 ativos e outra com `active=false` para o inativo.
- **Consequências**: o serviço pode ser consultado pelo host durante a demonstração local sem autenticação. O Compose permite selecionar os perfis por variável de ambiente. A verificação cobre os dois estados do filtro da API.
- **Y-statement**: no contexto da demonstração em Docker, diante da necessidade de verificar o Estoque antes do Gateway, decidimos publicar o serviço em loopback e usar os perfis `docker,demo` por padrão para permitir que o avaliador consulte os dados de demonstração em uma subida local.
- **Documentos afetados**: plano local de implementação (F1-13) · `docker-compose.yml` · `.env.example`.
- **ADR**: —
- **Substitui**: —

### D-038 — Dependências e migrações iniciais do Faturamento

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-14 aplica ao Faturamento a estrutura entregue para o Estoque na F1-06. A D-025 autorizou as dependências do Estoque. O modelo de dados define o DDL inicial do Faturamento em §10.2 e nos apêndices A.2 e A.3.
- **Opções consideradas**: 1) reutilizar as dependências da F1-06 com as versões gerenciadas pelo projeto e aplicar o DDL documentado. 2) definir um conjunto próprio de dependências para a inicialização do Faturamento, com configuração e verificação específicas.
- **Decisão**: autorizar `common`, os starters Actuator, Security e Flyway, `flyway-database-postgresql` e o driver PostgreSQL. Para os testes, autorizar o starter de testes do Spring Boot, `spring-boot-testcontainers`, os módulos JUnit Jupiter, PostgreSQL e Kafka do Testcontainers e o ArchUnit. Usar as versões gerenciadas pelo POM pai. Criar as migrações iniciais V1–V6 conforme os apêndices A.2 e A.3.
- **Consequências**: o Faturamento segue a base técnica da F1-06. O teste de subida usa PostgreSQL e Kafka reais em contêineres e valida os mapeamentos JPA compartilhados com o schema gerado pelo Flyway.
- **Y-statement**: no contexto da inicialização do Faturamento, diante da necessidade de verificar a arquitetura e aplicar o schema inicial, decidimos reutilizar a base de dependências já validada e o DDL documentado para manter o build reproduzível e os dois serviços consistentes.
- **Documentos afetados**: plano local de implementação (F1-14).
- **ADR**: —
- **Substitui**: —

### D-039 — Histórico e dados da tentativa de impressão

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a máquina de estados de `requisitos.md` §5 representa a edição como `OPEN → OPEN`. RF-INV-06 descreve o histórico de mudanças de status. O modelo §5.3 define o identificador da última tentativa de impressão e o instante em que o processamento começou.
- **Opções consideradas**: registrar ou omitir a edição no histórico, incluir ou omitir `OPEN` em `allowedTransitions` e limpar ou manter `processingStartedAt` quando o processamento terminar.
- **Decisão**: registrar a criação e as mudanças de status no histórico. Editar itens mantém `OPEN` e atualiza a auditoria da nota. `allowedTransitions` inclui `OPEN` como destino da edição. Ao confirmar ou rejeitar o fechamento, limpar `processingStartedAt` e manter o `printRequestId` da última tentativa.
- **Consequências**: os consumidores da API veem a edição entre as opções de uma nota aberta. O histórico guarda a evolução do status. O instante de início acompanha o processamento em curso e o identificador da tentativa permanece disponível após o resultado.
- **Y-statement**: no contexto da máquina de estados e da Saga, diante da edição em `OPEN` e do término do processamento, decidimos registrar mudanças de status, preservar as opções de edição e limpar o instante de início para refletir o estado atual da nota.
- **Documentos afetados**: `requisitos.md` §3.5 e §5 · `modelo-de-dados.md` §5.3 e §5.5 (detalhamento registrado nesta decisão) · plano local de implementação (F1-15).
- **ADR**: —
- **Substitui**: —

### D-040 — Limite dos totais de item e de nota

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RN-19 determina o cálculo do total de cada item e da nota. `modelo-de-dados.md` §5.3 e §5.4 definem `numeric(15,2)` para ambos. Valores individualmente válidos podem exceder o limite após multiplicação ou soma.
- **Opções consideradas**: rejeitar no domínio com `validation-error` ou usar o mapeamento de falhas de integridade após a escrita no banco.
- **Decisão**: rejeitar no domínio um valor acima do limite representável com `validation-error` (400). Indicar `lineTotal` para o total de um item e `total` para o da nota. Se a falha ocorrer na substituição de itens, preservar os itens, o total e a auditoria anteriores.
- **Consequências**: a validação informa qual cálculo ultrapassou o limite antes de persistir alterações. O schema e a regra de arredondamento permanecem aplicáveis.
- **Y-statement**: no contexto do cálculo de totais, diante da possibilidade de exceder a capacidade de `numeric(15,2)`, decidimos validar cada resultado no domínio para informar o campo afetado ao usuário e preservar a nota.
- **Documentos afetados**: `requisitos.md` RN-19 e §8.8 · `modelo-de-dados.md` §5.3 e §5.4 (detalhamento registrado nesta decisão) · plano local de implementação (F1-15).
- **ADR**: —
- **Substitui**: —

### D-041 — MapStruct e testes JPA no Faturamento

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-16 prevê mapeadores e testes com `@DataJpaTest`. A D-028 autorizou as dependências do Estoque e configurou o processador do MapStruct no POM pai.
- **Opções consideradas**: reutilizar a configuração da F1-08 no Faturamento ou definir uma configuração própria para o módulo.
- **Decisão**: declarar `org.mapstruct:mapstruct` e `org.springframework.boot:spring-boot-starter-data-jpa-test` no `billing-service`, com as versões já gerenciadas e o starter em escopo `test`. Herdar o processador e as opções do MapStruct do POM pai.
- **Consequências**: o Faturamento usa o mesmo padrão de mapeamento e de teste JPA do Estoque. A dependência do slice é usada durante os testes.
- **Y-statement**: no contexto da persistência do Faturamento, diante da necessidade de mapear os agregados e testar o schema do Flyway, decidimos reutilizar a configuração de mapeamento e de teste da F1-08 para manter os dois serviços consistentes.
- **Documentos afetados**: plano local de implementação (F1-16).
- **ADR**: —
- **Substitui**: —

### D-042 — Limites do filtro por período das notas

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-INV-02 e `requisitos.md` §8.6 definem os filtros `from` e `to`, e `modelo-de-dados.md` §5.3 associa o período a `created_at`. A especificação não indicava como tratar os instantes exatamente nos limites do intervalo.
- **Opções consideradas**: incluir os dois limites ou incluir o limite inicial e excluir o final.
- **Decisão**: filtrar `createdAt` com `from` inclusivo e `to` exclusivo. Cada limite é opcional e pode ser usado independentemente.
- **Consequências**: períodos adjacentes podem compartilhar um limite sem duplicar as notas emitidas exatamente naquele instante.
- **Y-statement**: no contexto da listagem de notas por data de criação, diante da possibilidade de consultas por períodos adjacentes, decidimos incluir o início e excluir o fim para permitir combinar intervalos sem repetir registros no limite.
- **Documentos afetados**: `requisitos.md` RF-INV-02 e §8.6 · `modelo-de-dados.md` §5.3 (detalhamento registrado nesta decisão) · plano local de implementação (F1-16).
- **ADR**: —
- **Substitui**: —

### D-043 — Starter de testes MVC do Faturamento

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-17 prevê testes `@WebMvcTest`. O Faturamento tem o starter base de testes. A D-030 já define o starter MVC para o Estoque.
- **Opções consideradas**: reutilizar o starter da F1-09 no Faturamento ou rever a estratégia de testes para usar o contexto completo.
- **Decisão**: declarar `spring-boot-starter-webmvc-test` no `billing-service`, em escopo `test` e com a versão gerenciada pelo BOM do Spring Boot.
- **Consequências**: o módulo pode testar o contrato HTTP com um slice MVC e usar MockMvc nos testes de integração.
- **Y-statement**: no contexto da API de clientes, diante da necessidade de testar seus status HTTP em um slice, decidimos usar o starter MVC já adotado pelo Estoque para manter a estratégia de testes consistente entre os serviços.
- **Documentos afetados**: plano local de implementação (F1-17).
- **ADR**: —
- **Substitui**: —

### D-044 — Contrato REST e auditoria de clientes na F1

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-CUS-01 a RF-CUS-05 e `requisitos.md` §8.6 descrevem as rotas de clientes. A F1 roda sem autenticação (D-029). Os campos das respostas, o comportamento do `PUT`, a autoria HTTP e o resumo das notas recentes precisavam de definição.
- **Opções consideradas**: aplicar as convenções da API de produtos ao Faturamento e incluir um resumo limitado de notas no detalhe, ou especificar um formato próprio de rota e resposta.
- **Decisão**: expor `/api/billing/customers` no serviço com o prefixo preservado pelo Gateway. Usar `anonymous` na auditoria HTTP da F1. As respostas de cliente incluem `id`, `name`, `document`, `documentType`, `email`, `phone`, `active` e `version`. O `PUT` recebe nome, e-mail, telefone e versão, aceita `document` igual ao cadastrado e retorna `400 field-immutable` para alteração de documento ou envio de `active`. O detalhe inclui `recentInvoices` com até cinco notas de qualquer status, da mais recente à mais antiga por `createdAt`, cada uma com `id`, `number`, `status`, `total` e `createdAt`.
- **Consequências**: o consumidor recebe a versão atual e resumos suficientes para abrir as notas recentes. A inativação e a reativação usam as rotas dedicadas.
- **Y-statement**: no contexto da API de clientes da F1, diante das lacunas no contrato, decidimos manter as convenções de rota e auditoria da API de produtos e explicitar as respostas de cliente para viabilizar a integração com o Gateway e a UI.
- **Documentos afetados**: `requisitos.md` RF-CUS-03, RF-CUS-04 e §8.6 · `modelo-de-dados.md` §2.5 e §5.3 · plano local de implementação (F1-17 e F1-22).
- **ADR**: —
- **Substitui**: —

### D-045 — Busca e ordenação da lista de clientes

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-CUS-02 remete aos padrões de busca de RF-PRD-02. `modelo-de-dados.md` §5.2 descreve a busca por documento como busca por prefixo. A lista de campos ordenáveis também precisava de definição.
- **Opções consideradas**: buscar o documento por qualquer trecho, seguindo o padrão da busca de produtos, ou pelo prefixo definido no modelo de dados.
- **Decisão**: buscar por trecho do nome sem distinção de maiúsculas e minúsculas ou por prefixo do documento. Usar `active=true` e `name,asc` por padrão. Permitir ordenar por `name`, `document` ou `active`.
- **Consequências**: o consumidor pode digitar o início do CPF ou CNPJ, ou parte do nome, e paginar os resultados por estado de ativação.
- **Y-statement**: no contexto da lista de clientes, diante de descrições diferentes para a busca, decidimos manter a busca por trecho no nome e adotar o prefixo no documento para aplicar a convenção registrada no modelo de dados.
- **Documentos afetados**: `requisitos.md` RF-CUS-02 e §8.6 · `modelo-de-dados.md` §5.2 · plano local de implementação (F1-17).
- **ADR**: —
- **Substitui**: —

### D-046 — Alinhamento dos documentos ao contrato de clientes

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: D-044 e D-045 definiram a busca, as respostas e a autoria HTTP na F1-17. Os documentos de referência descreviam esses pontos em termos gerais.
- **Opções consideradas**: alinhar os dois documentos ou manter os detalhes no registro de decisões.
- **Decisão**: atualizar `requisitos.md` e `modelo-de-dados.md` para refletir D-044 e D-045.
- **Consequências**: a documentação da API inclui a busca, os campos das respostas e o comportamento da edição. O modelo de dados identifica a autoria das chamadas HTTP de clientes na F1.
- **Y-statement**: no contexto da entrega da API de clientes, diante das lacunas dos documentos de referência, decidimos alinhá-los às decisões da F1-17 para documentar o contrato usado pelos consumidores.
- **Documentos afetados**: `requisitos.md` RF-CUS-02 a RF-CUS-04 e §8.6 · `modelo-de-dados.md` §2.5.
- **ADR**: —
- **Substitui**: —

### D-047 — Dependências da integração HTTP com o Estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-18 usa as anotações Resilience4j do §8.11 e expõe as métricas exigidas por RF-RES-03 CA4. O plano permite `MockRestServiceServer` ou WireMock para testar o cliente.
- **Opções consideradas**: testar respostas simuladas com `MockRestServiceServer` ou usar WireMock para enviar requisições HTTP reais a um servidor de testes e simular latência no transporte.
- **Decisão**: adicionar `resilience4j-spring-boot4` 2.4.0, `spring-boot-starter-aspectj` e `micrometer-registry-prometheus` ao `billing-service`, com as versões gerenciadas pelo BOM do Spring Boot para AspectJ e Prometheus. Usar `org.wiremock:wiremock-standalone` 3.13.2 em escopo `test`.
- **Consequências**: o Faturamento usa os aspectos e os indicadores do Resilience4j e publica métricas Prometheus. Os testes exercitam falhas e timeouts em uma conexão HTTP real.
- **Y-statement**: no contexto da integração com o Estoque, diante da necessidade de testar timeout e retentativas, decidimos usar WireMock para exercitar o transporte HTTP, aceitando uma dependência adicional de teste.
- **Documentos afetados**: plano local de implementação (F1-18).
- **ADR**: —
- **Substitui**: —

### D-048 — Janela do circuito do Estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-RES-03 CA2 e requisitos §6.5 descrevem cinco falhas em dez chamadas. O YAML de §8.11 configura `minimumNumberOfCalls: 5`, permitindo abrir o circuito antes de completar a primeira janela.
- **Opções consideradas**: avaliar o limiar a partir de cinco chamadas ou esperar dez consultas e contabilizar o resultado de cada consulta após o retry.
- **Decisão**: configurar `minimumNumberOfCalls: 10`. O `CircuitBreaker` envolve o `Retry` e registra um resultado por consulta HTTP em lote após as retentativas. O `TimeLimiter` se aplica a cada tentativa. Cinco falhas entre as últimas dez consultas abrem o circuito por 10 s. Em meio-aberto, são permitidas três consultas de teste.
- **Consequências**: o circuito avalia a taxa com uma janela completa e oferece recuperação automática. Uma indisponibilidade imediatamente após a partida pode consumir dez consultas completas antes de abrir o circuito.
- **Y-statement**: no contexto das consultas síncronas ao Estoque, diante da divergência sobre a primeira janela, decidimos avaliar o resultado de dez consultas completas para que as retentativas de uma mesma consulta contem uma vez, aceitando coletar dez resultados antes de abrir o circuito.
- **Documentos afetados**: `requisitos.md` §6.5 e §8.11 (detalhamento registrado nesta decisão) · plano local de implementação (F1-18).
- **ADR**: —
- **Substitui**: —

### D-049 — Lotes e falhas HTTP na consulta ao Estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-PRD-06 e requisitos §8.6 limitam `/products/batch` a 100 IDs por requisição e omitem os IDs inexistentes. A porta do Faturamento precisa consultar listas maiores e tratar respostas HTTP 4xx do Estoque.
- **Opções consideradas**: dividir as consultas em lotes e preservar a semântica de omissão ou impor o limite de 100 IDs ao chamador. Para 4xx, registrar falha no circuito ou devolver o erro de integração sem afetar a janela.
- **Decisão**: dividir consultas maiores em lotes de até 100 IDs e devolver os produtos encontrados. Mapear 4xx para `503 stock-service-unavailable`, sem retentativa e sem contabilizar a resposta no circuito. Permitir até duas retentativas para timeout, falhas de rede e 5xx, com backoff exponencial conforme §8.11.
- **Consequências**: a consulta pode realizar mais de uma requisição HTTP. Uma falha em qualquer lote encerra a consulta, e o chamador recebe um erro de integração.
- **Y-statement**: no contexto da validação de produtos de uma nota, diante do limite do endpoint e de possíveis respostas 4xx, decidimos repartir a consulta e excluir o 4xx do circuito para respeitar o contrato do Estoque e reservar retentativas para falhas transitórias.
- **Documentos afetados**: `requisitos.md` RF-PRD-06, RF-RES-03 e §8.11 (detalhamento registrado nesta decisão) · plano local de implementação (F1-18).
- **ADR**: —
- **Substitui**: —

### D-050 — Alinhamento do exemplo de resiliência do Estoque

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano
- **Contexto**: o exemplo do §8.11 ainda permitia avaliar a taxa de falhas após cinco chamadas e omitia parte da política de retentativas aprovada na F1-18.
- **Opções consideradas**: atualizar o exemplo do §8.11 ou manter o detalhamento no registro de decisões.
- **Decisão**: atualizar o bloco YAML do §8.11 conforme D-048 e D-049, incluindo o mínimo de dez consultas, a ordem dos aspectos, as exceções que admitem retentativa e a exclusão de respostas 4xx da janela do circuito.
- **Consequências**: o exemplo documenta os valores do `billing-service` e permite conferir a política aplicada na F1-18.
- **Y-statement**: no contexto da consulta síncrona ao Estoque, diante da diferença entre o exemplo e a configuração em uso, decidimos alinhar o bloco YAML para que a especificação reflita a política aprovada.
- **Documentos afetados**: `requisitos.md` §8.11 · plano local de implementação (F1-18).
- **ADR**: —
- **Substitui**: —

### D-051 — Referências inexistentes na API de notas

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-INV-01 CA2–CA3 pede 422 ao validar um cliente ou produto inexistente. O catálogo de erros do §8.8 atribui 404 aos mesmos tipos em consultas por ID.
- **Opções consideradas**: manter 422 para referências da nota ou aplicar 404 também à validação dessas referências.
- **Decisão**: retornar 422 `customer-not-found` ou `product-not-found` na validação da nota, com `customerId` ou `productId` no `ProblemDetail`. A nota solicitada por ID, quando inexistente, retorna 404 `invoice-not-found`.
- **Consequências**: a API identifica o campo de referência que deve ser corrigido e permite distinguir a falha no formulário de uma nota ausente.
- **Y-statement**: no contexto da validação de uma nota, diante da diferença entre os critérios de aceite e o catálogo, decidimos aplicar 422 às referências inválidas para identificar os dados que o usuário deve corrigir.
- **Documentos afetados**: `requisitos.md` RF-INV-01 e §8.8 · plano local de implementação (F1-19).
- **ADR**: —
- **Substitui**: —

### D-052 — Respostas e ordenação da API de notas

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-INV-01 a RF-INV-05 e `requisitos.md` §8.6 descrevem as operações, com lacunas nos campos das respostas, nas ações disponíveis e nos campos de ordenação da listagem.
- **Opções consideradas**: usar um resumo paginado e compartilhar o formato de detalhe entre a consulta e as escritas ou definir uma resposta específica para cada operação.
- **Decisão**: listar `id`, `number`, `status`, `customerId`, `customerName`, `total` e `createdAt`. Permitir ordenar por `number`, `status`, `customerName`, `total` e `createdAt`, com padrão `number,desc`. A consulta individual, a criação, a edição e o cancelamento retornam `id`, `number`, `status`, `customer {id, name, document}`, `items [{id, productId, productCode, productDescription, unitPrice, quantity, lineTotal, lineOrder}]`, `total`, `createdAt`, `closedAt`, `canceledAt`, `lastFailureReason`, `cancelReason`, `version` e `allowedActions`. Na F1-19, `allowedActions` contém `EDIT_ITEMS` e `CANCEL` para `OPEN` e fica vazio nos demais status. A F1-20 acrescenta `PRINT` quando a rota de impressão estiver disponível.
- **Consequências**: listagem e detalhe usam os snapshots gravados na nota. Os consumidores podem reaproveitar a versão recebida após uma edição e habilitar as ações que a API oferece.
- **Y-statement**: no contexto da API de notas, diante da necessidade de integrar a listagem e o detalhe ao frontend, decidimos compartilhar a resposta de detalhe nas operações individuais e limitar a listagem a um resumo paginado.
- **Documentos afetados**: `requisitos.md` RF-INV-02, RF-INV-03 e §8.6 · plano local de implementação (F1-19 e F1-20).
- **ADR**: —
- **Substitui**: —

### D-053 — Edição, período e auditoria da API de notas

- **Data**: 2026-11-19
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-INV-04 protege o cliente da nota. O modelo §2.7 exige versão nos `PUT`, D-042 define os limites do período e D-044 registra a convenção de autoria HTTP usada em clientes na F1.
- **Opções consideradas**: rejeitar ou ignorar `customerId` na edição, retornar lista vazia ou erro para intervalo vazio e aplicar às notas a mesma autoria usada para clientes.
- **Decisão**: usar `anonymous` na auditoria HTTP das notas da F1. Aceitar `{items, version}` em `PUT /invoices/{id}/items` e rejeitar o envio de `customerId` com 400 `field-immutable`. Quando `from >= to`, devolver uma página vazia conforme os limites de D-042. Alinhar `requisitos.md` e `modelo-de-dados.md` às decisões D-051 a D-053.
- **Consequências**: a edição exige a versão lida pelo consumidor e informa explicitamente a tentativa de alterar o cliente. Um intervalo vazio usa o formato normal de paginação.
- **Y-statement**: no contexto da edição e da listagem de notas na F1, diante das lacunas na autoria e nos parâmetros, decidimos explicitar a imutabilidade do cliente, o controle de versão e a resposta de um período vazio para que o consumidor possa tratar esses casos.
- **Documentos afetados**: `requisitos.md` RF-INV-02, RF-INV-04 e §8.6–8.8 · `modelo-de-dados.md` §2.5, §2.7 e §5.3 · plano local de implementação (F1-19).
- **ADR**: —
- **Substitui**: —

### D-054 — Mensagem de rejeição do fechamento

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-PRT-04 exige um motivo legível. D-035 define um motivo geral e detalhes por item, e `modelo-de-dados.md` §5.3 e §5.5 limita cada texto persistido a 500 caracteres.
- **Opções consideradas**: apresentar os detalhes que couberem no limite, mostrar apenas o primeiro item ou apresentar apenas o motivo geral e o total de itens afetados.
- **Decisão**: traduzir o motivo geral e listar, na ordem do evento, código ou UUID e quantidades dos itens. Omitir os itens finais que excederiam 500 caracteres e indicar quantos ficaram de fora. Usar o mesmo texto em `lastFailureReason` e no histórico.
- **Consequências**: os detalhes cabem nas colunas existentes e os eventos Kafka preservam a lista completa.
- **Y-statement**: no contexto de uma rejeição com vários itens, diante do limite de armazenamento, decidimos priorizar os detalhes na ordem do evento e indicar as omissões para manter a mensagem legível.
- **Documentos afetados**: `requisitos.md` RF-PRT-04 · `modelo-de-dados.md` §5.3 e §5.5 (detalhamento registrado nesta decisão).
- **ADR**: —
- **Substitui**: —

### D-055 — Resultado de baixa para nota inexistente

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: D-005 define quando uma tentativa de impressão está obsoleta. O plano F1-20 e `requisitos.md` §6.7 não dizem como tratar um resultado bem-formado cujo `invoiceId` não existe no Faturamento.
- **Opções consideradas**: reverter a transação e usar a política de DLT do Kafka ou ignorar o resultado com registro em `processed_events`.
- **Decisão**: tratar a referência inexistente como inconsistência técnica. Reverter a transação, inclusive a marca de deduplicação, e deixar a política Kafka encaminhar a mensagem ao DLT.
- **Consequências**: a mensagem fica disponível para diagnóstico com os dados originais após as retentativas da política existente.
- **Y-statement**: no contexto dos resultados da Saga, diante de uma referência a uma nota inexistente, decidimos usar a política de falhas do Kafka para tornar a inconsistência visível.
- **Documentos afetados**: `requisitos.md` §6.7 e §8.7 (detalhamento registrado nesta decisão).
- **ADR**: —
- **Substitui**: —

### D-056 — Execução local do Faturamento no Compose

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-21 prevê executar o Faturamento no Compose, carregar os dados de `modelo-de-dados.md` §13.4–13.6 e verificar a API local antes da tarefa do Gateway. A D-037 definiu o padrão local do Estoque.
- **Opções consideradas**: 1) publicar o Faturamento em loopback, reutilizar os perfis do Estoque e adicionar o seed repetível. 2) manter o Compose e as migrações na configuração atual.
- **Decisão**: autorizar `docker-compose.yml` com o Faturamento em `127.0.0.1:8082`, os perfis Spring `docker,demo` por padrão, credenciais `POSTGRES_BILLING_*` conforme D-021 e URL interna `http://stock-service:8081`. Autorizar `R__seed_demo.sql` no perfil `demo`.
- **Consequências**: o Compose sobe o Faturamento com as dependências locais e a API permite consultar as cinco notas de demonstração pelo host.
- **Y-statement**: no contexto da demonstração anterior ao Gateway, decidimos publicar o Faturamento em loopback com dados de demonstração para permitir a verificação da API local.
- **Documentos afetados**: plano local de implementação (F1-21) · `docker-compose.yml`.
- **ADR**: —
- **Substitui**: —

### D-057 — Sequência de notas no seed do Faturamento

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: `modelo-de-dados.md` §13.5 e Apêndice A.4 preveem `setval('invoice_number_seq', 5)` para que a primeira nota após o seed seja a nº 6. Um ajuste incondicional pode fazer a sequência voltar ao reexecutar a migração repetível.
- **Opções consideradas**: 1) usar 5 como mínimo, manter a sequência se já estiver adiantada e falhar se uma nota alheia ao seed ocupar um número reservado. 2) aplicar `setval(..., 5)` em todas as execuções.
- **Decisão**: usar 5 como mínimo para a sequência e preservar numerações emitidas. Interromper o seed se um dos números de 1 a 5 estiver ocupado por uma nota diferente da prevista para aquele número.
- **Consequências**: a primeira nota de um banco recém-populado recebe o nº 6. Reexecutar o seed preserva o avanço da sequência. Um conflito com os números da demonstração precisa ser resolvido antes de ativar o perfil `demo` naquele banco.
- **Y-statement**: no contexto de um seed repetível com números fixos, decidimos limitar o ajuste da sequência a avanços e rejeitar conflitos nos números 1–5 para preservar as notas do banco.
- **Documentos afetados**: `modelo-de-dados.md` §13.5 e Apêndice A.4 · plano local de implementação (F1-21).
- **ADR**: —
- **Substitui**: —

### D-058 — Dependências e execução local do Gateway

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-22 prevê roteamento, Actuator, testes com `WebTestClient` e execução local na porta 8080. O `pom.xml` do módulo ainda é o esqueleto do bootstrap.
- **Opções consideradas**: usar o Spring Cloud Gateway Server WebFlux com os BOMs existentes e servidores HTTP simulados pelo JDK, ou adicionar uma biblioteca de simulação para os testes de roteamento.
- **Decisão**: adicionar `spring-cloud-starter-gateway-server-webflux`, `spring-boot-starter-actuator` e `spring-boot-starter-test`, nas versões gerenciadas pelos BOMs. Usar servidores HTTP do JDK nos testes. Autorizar `docker-compose.yml` com o Gateway publicado em `127.0.0.1:8080`, conectado a `edge` e `internal`, e iniciado quando Estoque e Faturamento estiverem saudáveis.
- **Consequências**: o frontend pode usar a rede `edge` para chegar ao Gateway, e o Gateway encontra os serviços de domínio na rede `internal`. Os testes do módulo podem rodar com Java 21 sem Docker.
- **Y-statement**: no contexto do MVP local, decidimos usar as dependências gerenciadas pela plataforma e executar o Gateway nas duas redes do Compose para conectar o ponto de entrada aos serviços de domínio.
- **Documentos afetados**: plano local de implementação (F1-22) · `docker-compose.yml`.
- **ADR**: —
- **Substitui**: —

### D-059 — Correlação HTTP e CORS no Gateway

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RNF-OBS-05 e D-010 exigem um identificador de correlação na resposta e no `ProblemDetail` dos serviços. RNF-SEC-05 exige que o Gateway restrinja CORS à origem do frontend.
- **Opções consideradas**: gerar o identificador no Gateway ou reaproveitar um valor enviado pelo cliente. Para CORS, autorizar os métodos e cabeçalhos da API ou aceitar qualquer método e cabeçalho para a origem local.
- **Decisão**: gerar um UUID por requisição no Gateway, substituir `X-Trace-Id` recebido e usar o mesmo valor no encaminhamento e na resposta. Aceitar CORS apenas de `http://localhost:4200`, para `GET`, `POST`, `PUT` e `DELETE`, com `Content-Type` e `Idempotency-Key`. Expor `Location` e `X-Trace-Id` ao navegador, sem credenciais.
- **Consequências**: os serviços usam o identificador do Gateway na resposta de erro. O frontend pode enviar a chave de idempotência, ler o endereço retornado pelas escritas e apresentar o identificador de erro.
- **Y-statement**: no contexto do acesso do frontend à API durante a F1, decidimos gerar a correlação no Gateway e restringir CORS à origem e aos recursos usados pelo navegador para manter um identificador único por requisição e permitir as operações previstas.
- **Documentos afetados**: plano local de implementação (F1-22), com detalhamento de RNF-SEC-05 e RNF-OBS-05.
- **ADR**: —
- **Substitui**: —

### D-060 — Origem da API e imagens do frontend

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-24 prevê a configuração da API e o Nginx de produção local. O plano não fixa a origem da API durante o desenvolvimento nem as imagens base.
- **Opções consideradas**: encaminhar `/api` pela mesma origem em ambos os ambientes ou acessar o Gateway diretamente durante o desenvolvimento.
- **Decisão**: usar caminhos de API na mesma origem. O `ng serve` encaminha `/api` para `127.0.0.1:8080`, e o Nginx encaminha para `api-gateway:8080`. Usar `node:22-alpine` para o build e `nginx:stable-alpine` no runtime, com Nginx executando como usuário não-root na porta interna 80.
- **Consequências**: o client Angular usa os mesmos caminhos nos dois ambientes. Cada proxy preserva o prefixo da API e depende do Gateway no endereço correspondente.
- **Y-statement**: no contexto da integração com o Gateway, decidimos usar a mesma origem nos dois ambientes para compartilhar os caminhos da API, aceitando configurar um proxy em cada servidor.
- **Documentos afetados**: plano local de implementação (F1-24).
- **ADR**: —
- **Substitui**: —

### D-061 — Feedback e repetição de requisições no frontend

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-RES-01 define as ações para erros HTTP. A F1 não inclui login, e o requisito não delimita quais escritas podem ser repetidas após um 503.
- **Opções consideradas**: permitir repetir qualquer requisição após um 503 ou limitar a ação a leituras, POSTs idempotentes e escritas autorizadas pela tela.
- **Decisão**: na F1, apresentar snackbar em caso de 401. Em 409, a ação “Recarregar” recarrega a página. Em 503, a ação “Tentar novamente” repete leituras e POSTs marcados, preservando a chave de idempotência. Outras escritas só são repetidas mediante autorização explícita da tela.
- **Consequências**: o login permanece na F2. A ação de recarregar aplica a navegação normal do navegador. Uma tela que permita repetir outra escrita precisa declarar essa escolha na requisição.
- **Y-statement**: no contexto do feedback do MVP, decidimos exigir ação explícita para repetir operações após um 503 e limitar as escritas elegíveis para reduzir o risco de duplicação.
- **Documentos afetados**: plano local de implementação (F1-24), com detalhamento de RF-RES-01.
- **ADR**: —
- **Substitui**: —

### D-062 — Rotas lazy antes das telas de domínio

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: a F1-24 prepara as rotas de produtos, clientes e notas, cujas telas serão entregues nas tarefas seguintes.
- **Opções consideradas**: manter os itens de navegação desabilitados ou ativá-los com páginas provisórias.
- **Decisão**: declarar as três rotas lazy com página simples de indisponibilidade temporária. Manter os itens do menu desabilitados até a entrega das respectivas telas.
- **Consequências**: é possível verificar a navegação direta e o carregamento por rota enquanto o menu indica as telas disponíveis.
- **Y-statement**: no contexto da entrega incremental do frontend, decidimos preparar as rotas e habilitar a navegação do menu quando cada tela for entregue.
- **Documentos afetados**: plano local de implementação (F1-24 a F1-27).
- **ADR**: —
- **Substitui**: —

### D-063 — Validação do código no cadastro de produtos

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: RF-PRD-01 prevê validação síncrona no formulário e retorno de `409 product-code-already-exists` pela API. O §9.4 cita um validador assíncrono, e a API oferece apenas busca parcial com filtros separados de situação.
- **Opções consideradas**: validar localmente o formato e exibir a duplicidade após o cadastro ou consultar a busca de ativos e inativos enquanto o usuário digita.
- **Decisão**: validar o formato no formulário e exibir o `409 product-code-already-exists` no campo Código. A API verifica a unicidade na gravação.
- **Consequências**: a duplicidade é informada no envio do formulário, inclusive para códigos inativos. A verificação usa o contrato de cadastro existente.
- **Y-statement**: no contexto do cadastro, diante da busca parcial e da necessidade de verificar o código entre produtos de ambas as situações, decidimos usar a verificação de unicidade do cadastro para exibir uma mensagem no campo correto.
- **Documentos afetados**: requisitos RF-PRD-01 e §9.4 (detalhamento registrado nesta decisão) · plano local de implementação (F1-25).
- **ADR**: —
- **Substitui**: —

### D-064 — Confirmação de saída dos formulários de produto

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: requisitos §9.3 prevê confirmação de navegação para formulários com alterações pendentes. O plano mencionava explicitamente o guard na entrega das notas fiscais.
- **Opções consideradas**: aplicar a confirmação aos formulários de produto na F1-25 ou deixar a proteção para os formulários de nota na F1-27.
- **Decisão**: incluir confirmação ao sair do cadastro ou da edição de produto com alterações não salvas, usando `canDeactivate`.
- **Consequências**: o usuário pode permanecer no formulário ou descartar as alterações. Após uma gravação bem-sucedida, a navegação prossegue sem diálogo.
- **Y-statement**: no contexto dos formulários de produto, diante do risco de perder alterações na navegação, decidimos aplicar o guard na F1-25 para que o usuário possa confirmar o descarte.
- **Documentos afetados**: requisitos §9.3 (detalhamento registrado nesta decisão) · plano local de implementação (F1-25).
- **ADR**: —
- **Substitui**: —

### D-065 — Máscara de telefone nos dados do cliente

- **Data**: 2026-11-20
- **Status**: aceita
- **Decisor**: humano (proposta: agente)
- **Contexto**: o modelo de dados §5.2 prevê máscara para o telefone do cliente na interface e armazenamento apenas dos dígitos com DDI e DDD. A F1-26 precisava definir o formato exibido no formulário e no detalhe.
- **Opções consideradas**: 1) máscara brasileira para números com DDI 55 e DDD, com formato internacional simples para os demais. 2) formato internacional simples para todos os números.
- **Decisão**: usar máscara brasileira para números com DDI 55 e DDD. Para os demais, exibir `+` seguido dos dígitos. A API continua recebendo até 20 dígitos.
- **Consequências**: o formulário aceita número de telefone formatado na interface e o detalhe usa a mesma apresentação. A API continua recebendo o telefone sem pontuação.
- **Y-statement**: no contexto da interface de clientes, decidimos formatar os números brasileiros com DDI e DDD para facilitar a leitura e exibir os demais em formato internacional simples, preservando o armazenamento dos dígitos.
- **Documentos afetados**: modelo de dados §5.2 (detalhamento registrado nesta decisão) · plano local de implementação (F1-26).
- **ADR**: —
- **Substitui**: —
