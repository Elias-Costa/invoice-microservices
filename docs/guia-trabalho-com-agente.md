# Guia de trabalho com agente de código

| Campo | Valor |
|---|---|
| Projeto | `invoice-microservices` |
| Versão | 1.0 |
| Data | 2026-09-12 |
| Finalidade | Registrar as práticas de desenvolvimento com Claude Code e Codex, as decisões de processo adotadas e a função de cada arquivo de configuração do repositório |

---

## 1. Modelo de trabalho adotado

| Papel | Responsabilidade |
|---|---|
| **Humano** (autor do projeto) | Define requisitos, decide trade-offs, revisa cada entrega, faz `git add`/`commit`/`push`, gerencia branches e releases |
| **Agente** (Claude Code ou Codex) | Lê a fonte da verdade, planeja, implementa, testa, apresenta evidências, registra decisões, **para e pergunta** quando algo não está explícito |

**Fonte da verdade**, em ordem de precedência:

1. Instruções do humano na conversa (sempre prevalecem).
2. [`docs/decisions.md`](decisions.md) — decisões tomadas durante a implementação (as mais recentes prevalecem).
3. [`docs/requisitos.md`](requisitos.md) e [`docs/modelo-de-dados.md`](modelo-de-dados.md) — especificação.
4. Código existente — padrões já estabelecidos no repositório.

Se código e documento divergirem, o documento está certo até que uma decisão registrada diga o contrário.

### 1.1 Protocolo "parar e perguntar"

Quando, durante uma tarefa, o agente precisa de uma informação que **não está explícita** nos documentos (ou encontra contradição entre eles):

1. **Não supõe.** Interrompe a implementação daquele ponto.
2. **Pergunta** na ferramenta disponível no chat. No Claude Code, use `AskUserQuestion`. Explique o contexto em 2–3 linhas, apresente as opções com prós e contras e indique a recomendação. Aguarde a resposta.
3. **Registra** a resposta em `docs/decisions.md` (entrada `D-nnn`) antes de continuar.
4. **Propõe a alteração** em `requisitos.md`/`modelo-de-dados.md` se a decisão os afeta; aplica após confirmação.
5. **Continua** a implementação com a decisão tomada.

Perguntas devem ser **agrupadas na fase de planejamento** sempre que possível (uma rodada de 2–4 perguntas), em vez de interromper várias vezes no meio da implementação.

O que **não** exige pergunta: escolhas triviais e reversíveis que seguem padrão já existente no código ou convenção da linguagem (nome de variável, ordem de métodos, posição de um arquivo dentro do pacote correto). Critério: *"um revisor questionaria isso?"* ou *"é caro reverter?"* ou *"afeta API, schema, eventos, UX ou segurança?"* → se qualquer resposta for sim, pergunta.

### 1.2 Ciclo de uma tarefa

```
Explorar → Planejar → (Perguntar) → Implementar → Verificar → Relatar → Revisão humana → Commit humano
```

| Etapa | O que acontece |
|---|---|
| Explorar | Agente lê as seções relevantes de `requisitos.md`, `modelo-de-dados.md`, `decisions.md` e o código existente |
| Planejar | Lista arquivos a criar/alterar, testes a escrever, comandos de verificação; identifica lacunas → perguntas |
| Implementar | Código + testes, seguindo as convenções de `AGENTS.md` e `.claude/rules/` |
| Verificar | Roda build, lint e testes; corrige até passar; nunca desabilita teste para "passar" |
| Relatar | Entrega resumo com: arquivos alterados, comandos executados e saída, decisões registradas, pendências, o que revisar primeiro |
| Revisão | Humano lê o diff e o relatório; pede ajustes ou aprova |
| Commit | Humano faz `git add`/`commit` (Conventional Commits) |

Use um agente por vez e uma tarefa por sessão (D-023). Claude Code e Codex consultam e atualizam o mesmo `.claude/plano-de-implementacao.md`. Ao trocar de agente durante uma tarefa, o humano indica a passagem e o agente atual anota no **Registro** os arquivos em andamento, as verificações executadas e as decisões pendentes. O próximo agente lê o registro e inspeciona a árvore de trabalho antes de editar.

---

## 2. Síntese da pesquisa

### 2.1 Como o agente funciona e o que limita sua qualidade

A documentação oficial do Claude Code parte de uma restrição central: *"Claude's context window fills up fast, and performance degrades as it fills"*. Praticamente todas as práticas derivam disso — instruções curtas, uma tarefa por sessão, exploração via subagentes, `/clear` entre tarefas.

Práticas recomendadas pela Anthropic que adotamos:

| Prática | Como aplicamos |
|---|---|
| **Dar ao agente uma forma de verificar o trabalho** (testes, build, lint) — *"Without a check it can run, 'looks done' is the only signal available"* | Comandos de verificação em `AGENTS.md`; Definition of Done exige rodá-los; relatório mostra a saída |
| **Explorar → planejar → codificar** — plan mode para mudanças multi-arquivo | Regra em `CLAUDE.md`; skill `/tarefa` implementa o ciclo |
| **Ser específico no prompt**: arquivos, restrições, padrões de referência | Tarefas referenciam seções dos documentos (`RF-INV-01`, `§5.3`) |
| **Deixar o agente entrevistar antes de grandes features e escrever a spec** | Feito: `requisitos.md` e `modelo-de-dados.md` nasceram de entrevista |
| **Corrigir cedo; após duas correções no mesmo ponto, `/clear` e prompt melhor** | Orientação ao humano em [§4](#4-orientações-para-o-revisor-humano) |
| **Hooks para o que deve acontecer sempre, sem exceção** — *"Unlike CLAUDE.md instructions which are advisory, hooks are deterministic"* | Hook + regras `deny` bloqueiam `git commit/push/...` |
| **Revisão adversarial em contexto novo** antes de considerar pronto | Humano pode pedir `/code-review`; o agente que escreveu não é quem avalia |
| **Mostrar evidência, não afirmar sucesso** | Formato de relatório obrigatório em `AGENTS.md` |

### 2.2 CLAUDE.md — o que a documentação oficial diz

- Arquivo lido no início de toda sessão; deve ser **curto e de alto sinal** — meta de **menos de 200 linhas**; *"Bloated CLAUDE.md files cause Claude to ignore your actual instructions!"*
- Teste para cada linha: *"Would removing this cause Claude to make mistakes?"* Se não, cortar.
- **Incluir**: comandos que o agente não adivinharia, regras de estilo que diferem do padrão, instruções de teste, etiqueta de repositório, decisões arquiteturais específicas, pegadinhas.
- **Excluir**: o que o agente descobre lendo o código, convenções padrão da linguagem, documentação de API (linkar), descrições arquivo a arquivo, "escreva código limpo".
- Instruções são **contexto, não configuração imposta** — *"CLAUDE.md content is delivered as a user message after the system prompt"*. Para bloquear uma ação de fato: hook `PreToolUse` ou regra `permissions.deny`.
- Locais: `./CLAUDE.md` ou `./.claude/CLAUDE.md` (projeto, versionado); `CLAUDE.local.md` (pessoal, no `.gitignore`); `~/.claude/CLAUDE.md` (usuário); subdiretórios carregam sob demanda.
- **`.claude/rules/*.md`** com frontmatter `paths:` carregam **só quando o agente toca arquivos que casam com o glob** — a forma recomendada de manter o arquivo raiz enxuto em monorepos.
- Importação com `@caminho` expande o arquivo no contexto (não economiza tokens; serve para organização).
- **AGENTS.md**: *"Claude Code reads CLAUDE.md, not AGENTS.md. If your repository already uses AGENTS.md for other coding agents, create a CLAUDE.md that imports it"*. No Windows, symlink exige privilégios — usar `@AGENTS.md`.
- Sobrevivência à compactação: o `CLAUDE.md` raiz é reinjetado após `/compact`; instruções dadas só na conversa se perdem. Instruções de compactação podem ser escritas no próprio arquivo (*"When compacting, always preserve…"*).
- Ferramentas: `/init` gera um rascunho; `/context` confirma quais arquivos carregaram; `/doctor` propõe cortes.

### 2.3 AGENTS.md — formato aberto

- Especificação mantida pela Agentic AI Foundation (Linux Foundation); lida por Codex, Copilot, Cursor, Gemini CLI, Aider, Jules e outros; Claude Code via import.
- *"No required fields"* — Markdown livre; seções populares: visão geral, comandos de build/teste, estilo, testes, segurança, convenções de commit/PR.
- Em monorepos, o arquivo **mais próximo do arquivo editado prevalece**; instruções explícitas do usuário no chat prevalecem sobre tudo.
- Limite técnico de 32 KiB; recomendação prática de **≤ 150 linhas**.

### 2.4 O que muda o comportamento do agente (evidência)

A análise do GitHub sobre **2.500+ repositórios** com `agents.md` encontrou seis áreas nos arquivos eficazes: **comandos, testes, estrutura do projeto, estilo de código, fluxo git e limites**. Conclusões-chave:

- *"One real code snippet showing your style beats three paragraphs describing it."*
- Comandos **copiáveis** com flags, no início do arquivo; personas vagas ("assistente útil") não funcionam.
- Sistema de limites em **três níveis**: ✅ sempre fazer · ⚠️ perguntar antes · 🚫 nunca fazer.
- **Retornos decrescentes acima de ~150 linhas** (custo de inferência +20–23 % sem ganho mensurável).
- Os melhores arquivos **crescem por iteração** (a cada erro do agente, uma linha), não por planejamento antecipado.

Padrões complementares (Crosley): instruções **orientadas a comando** (*"Commands are unambiguous… can verify success by checking the exit code"*); **definição de pronto** explícita (*"eliminates the most common failure mode: the agent reports 'done' without verifying"*); **regras de escalação** com uma lista "Nunca" de contornos destrutivos; seções organizadas por tarefa; *"instructions without verification commands are suggestions, not rules"*. Pesquisa citada (ICLR 2026): agentes instruídos a **perguntar** melhoram até 74 % em tarefas subespecificadas — base do protocolo "parar e perguntar".

### 2.5 Registro de decisões (decisions.md, ADR, AgDR)

- **ADR** (Nygard/MADR): título, status, contexto, opções consideradas, decisão, consequências. Arquivos `NNNN-titulo.md` em `docs/adr/` ou `docs/decisions/`; **imutáveis** — decisão revista gera novo registro que *supersede* o anterior. Status: `proposed`, `accepted`, `rejected`, `deprecated`, `superseded by`.
- **Por que importa com agentes**: *"the reasoning is the part nothing stores"* — o modelo não tem memória entre sessões; sem registro, um agente futuro *"cheerfully overturn[s] settled decisions"*. Instruir o agente a **ler a pasta de decisões antes de mudanças arquiteturais** e a **escrever** registros quando faz escolhas relevantes.
- **Seletividade**: registrar apenas decisões contestáveis, restrições não óbvias ou caras de reverter (estado, cache, autenticação, formato de resposta). Não registrar trivialidades — *"stale, irrelevant logs degrade faster than none"*.
- **AgDR** (Agent Decision Record) acrescenta metadados (agente, modelo, gatilho, status `proposed/executed/superseded`) e o **Y-statement** obrigatório: *"In the context of [situação], facing [preocupação], I decided [escolha] to achieve [objetivo], accepting [trade-off]"*. Revisão de PR como porta de aprovação.

**Decisão adotada** (D-001): `docs/decisions.md` é o **registro corrente** — leve, cronológico, uma entrada por decisão com Y-statement; decisões caras de reverter são **promovidas** a ADR formal em `docs/adr/` e a entrada aponta para ele.

### 2.6 Spec-driven development — o que funciona e o que não

Avaliação de Kiro, Spec-kit e Tessl (Martin Fowler, artigo de Birgitta Böckeler): a spec como contrato entre humano e agente tem mérito real, mas há armadilhas — excesso de Markdown para revisar, agentes ignorando instruções longas, workflows "tamanho único" que viram *"sledgehammer for small bugs"*. Recomendações incorporadas:

- **Pacotes de trabalho pequenos e iterativos**, não uma spec gigante executada de uma vez → nosso plano em fases e tarefas.
- **Humano verifica em cada fase** (*"your role isn't just to steer. It's to verify"*) → revisão obrigatória antes do commit.
- **Clarificações rastreadas** (Spec-kit usa checklists `[NEEDS CLARIFICATION]`) → `decisions.md` com status `aberta`.
- Ajustar o peso do processo ao tamanho da tarefa: correção de uma linha não precisa de plano.

### 2.7 Hooks e permissões

- `PreToolUse` com matcher `Bash|PowerShell` recebe JSON em stdin com `tool_input.command`; **exit 2 + mensagem em stderr bloqueia** e o motivo volta ao agente como feedback. No Windows os hooks rodam em Git Bash.
- Regras `permissions.deny` em `.claude/settings.json`: `Bash(git push *)` bloqueia comandos que **começam** com o prefixo; `git -C . push` não casa — por isso o hook (regex sobre o comando inteiro) complementa as regras. Regras `PowerShell(...)` têm a mesma forma; aliases são canonicalizados.
- Regras `ask` forçam confirmação mesmo em modo automático → usadas para os documentos fonte da verdade.
- Quando vários hooks casam, a decisão **mais restritiva** vence (`deny` > `ask` > `allow`).

---

## 3. Arquivos de configuração e sua função

| Arquivo | Função | Carregado |
|---|---|---|
| `AGENTS.md` | Instruções canônicas para qualquer agente: papéis, fonte da verdade, comandos, convenções, limites, definição de pronto, formato de relatório | Via import em `CLAUDE.md` (Claude) ou nativamente (outros agentes) |
| `CLAUDE.md` | `@AGENTS.md` + instruções específicas do Claude Code (plan mode, `AskUserQuestion`, compactação, hooks) | Toda sessão |
| `.claude/rules/backend-java.md` | Convenções Java/Spring detalhadas | Só ao tocar `backend/**` |
| `.claude/rules/frontend-angular.md` | Convenções Angular detalhadas | Só ao tocar `frontend/**` |
| `.claude/rules/docs.md` | Regras para editar documentação | Só ao tocar `docs/**` |
| `.claude/settings.json` | Permissões (`deny` git/gh, `ask` docs fonte da verdade) e registro dos hooks | Toda sessão |
| `.claude/hooks/block-git-write.sh` | Bloqueio determinístico de comandos git/gh que alteram índice, histórico, branches ou remoto | A cada `Bash`/`PowerShell` |
| `.claude/skills/tarefa/SKILL.md` | Workflow `/tarefa <descrição>`: ciclo completo de uma tarefa | Sob demanda |
| `.claude/skills/decisao/SKILL.md` | Workflow `/decisao <tema>`: registra uma decisão em `decisions.md` | Sob demanda |
| `.codex/config.toml` | Ativa hooks e define `GIT_OPTIONAL_LOCKS=0` nos comandos do Codex para evitar gravações opcionais do Git durante leituras | Projeto confiável no Codex |
| `.codex/rules/git.rules` | Bloqueia prefixos comuns de escrita Git e GitHub pela política de execução do Codex | Projeto confiável no Codex |
| `.codex/hooks.json` | Registra o hook `PreToolUse` do Codex para a ferramenta de shell | Após revisar e confiar no hook |
| `.codex/hooks/block-git-write.cjs` | Confere os comandos de shell e cobre formas como `git -C`, `git.exe` e chamadas compostas | A cada comando de shell atendido pelo hook |
| `.agents/skills/tarefa/SKILL.md` | Executa uma tarefa do plano compartilhado com `$tarefa <ID>` no Codex | Sob demanda |
| `.agents/skills/decisao/SKILL.md` | Registra uma decisão com `$decisao <tema>` no Codex | Sob demanda |
| `docs/decisions.md` | Registro corrente de decisões | Lido pelo agente no início de cada tarefa |
| `CLAUDE.local.md` (opcional, ignorado pelo git) | Preferências pessoais da máquina | Toda sessão |

Manutenção: `AGENTS.md` cresce **uma linha por erro recorrente** do agente; revisar a cada fase; remover o que o agente já faz certo sem instrução.

### 3.1 Ativação local do Codex

Abra este checkout como projeto local confiável no Codex, com Git e Node.js no `PATH`. Inicie uma sessão nova após alterações na configuração. Revise e autorize o hook do projeto em `/hooks` no Codex CLI. Um hook novo ou alterado fica pendente até o aceite. O hook analisa comandos de shell visíveis ao Codex. Chamadas escondidas em aliases, scripts externos ou outros conectores continuam sujeitas às instruções de `AGENTS.md`. Use as skills no seletor `$` do Codex e confira o plano ao trocar de agente. [Documentação do Codex sobre hooks](https://learn.chatgpt.com/docs/hooks#review-and-trust-hooks) e [política de execução](https://learn.chatgpt.com/docs/agent-configuration/rules).

---

## 4. Orientações para o revisor humano

- **Uma tarefa por sessão.** Enunciar a tarefa referenciando IDs dos documentos (`RF-PRD-01`, `RN-16`, `§4.2`).
- **Pedir plano antes** em tarefas multi-arquivo; aprovar ou corrigir o plano — corrigir plano é mais barato que corrigir código.
- **Ler o relatório antes do diff**: a seção "o que revisar primeiro" aponta os riscos.
- **Cobrar evidência**: se o relatório não mostra a saída dos testes, pedir.
- **Duas correções no mesmo ponto → `/clear`** e um prompt melhor com o que foi aprendido (a documentação oficial é explícita sobre isso).
- **Revisão independente** para mudanças grandes: `/code-review` roda em contexto novo.
- **Commit**: revisar `git diff`, `git add -p` se necessário, mensagem em Conventional Commits (`feat(stock): …`, `fix(billing): …`, `docs: …`, `chore(infra): …`).
- **Quando o agente perguntar**: responder com a decisão; conferir que a entrada em `decisions.md` reflete o que foi decidido antes de aprovar o diff.

---

## 5. Fontes

- Anthropic — *Best practices for Claude Code*: https://code.claude.com/docs/en/best-practices
- Anthropic — *How Claude remembers your project* (CLAUDE.md, rules, auto memory, AGENTS.md): https://code.claude.com/docs/en/memory
- Anthropic — *Automate actions with hooks*: https://code.claude.com/docs/en/hooks-guide
- Anthropic — *Configure permissions*: https://code.claude.com/docs/en/permissions
- Anthropic — *Set up Claude Code in a monorepo or large codebase*: https://code.claude.com/docs/en/large-codebases
- AGENTS.md — especificação e FAQ: https://agents.md/
- GitHub Blog — *How to write a great agents.md: lessons from over 2,500 repositories*: https://github.blog/ai-and-ml/github-copilot/how-to-write-a-great-agents-md-lessons-from-over-2500-repositories/
- Blake Crosley — *AGENTS.md Patterns: What Actually Changes Agent Behavior*: https://blakecrosley.com/blog/agents-md-patterns
- Tembo — *What Is AGENTS.md? How to Write One in 2026*: https://www.tembo.io/blog/agents-md
- MADR — *Markdown Architectural Decision Records*: https://adr.github.io/madr/
- Braingrid — *Architecture Decision Records for AI Coding Agents*: https://www.braingrid.ai/blog/architecture-decision-records-for-ai-coding-agents
- me2resh — *Agent Decision Records (AgDR)*: https://github.com/me2resh/agent-decision-record
- Martin Fowler / Birgitta Böckeler — *Understanding Spec-Driven Development: Kiro, spec-kit, and Tessl*: https://martinfowler.com/articles/exploring-gen-ai/sdd-3-tools.html
- Augment Code — *Spec-Driven Development & AI Agents Explained*: https://www.augmentcode.com/guides/spec-driven-development-ai-agents-explained
