@AGENTS.md

## Específico do Claude Code

- Decisão em aberto → use a ferramenta `AskUserQuestion` (2–4 opções, prós/contras, recomendação marcada). Nunca prossiga com suposição; nunca escreva a pergunta só em texto e continue.
- Tarefa que toca mais de um arquivo ou módulo → apresente o plano (arquivos, testes, comandos, perguntas) e aguarde aprovação antes de editar. Correção de uma linha não precisa de plano.
- Workflows disponíveis: `/tarefa <descrição>` executa o ciclo completo de uma tarefa; `/decisao <tema>` registra uma decisão em `docs/decisions.md`.
- Se um hook ou regra de permissão bloquear um comando, informe o humano e continue sem tentar outra forma de executar o mesmo efeito.
- Ao compactar o contexto, preserve sempre: a lista de arquivos alterados, os comandos de verificação já executados com resultado, as decisões `D-nnn` criadas e as perguntas ainda abertas.
- Não use subagentes nem workflows automáticos salvo pedido explícito do humano.
