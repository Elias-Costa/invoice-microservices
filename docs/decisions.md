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
