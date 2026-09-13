# ADR-001 — Versões da plataforma

- **Status**: aceito
- **Data**: 2026-09-13
- **Decisores**: autor do projeto
- **Registro de origem**: [D-008](../decisions.md#d-008--versões-da-plataforma-spring-boot-41-java-21-groupid-develiascostainvoice)

## Contexto e problema

O bootstrap do build exige fixar as versões da plataforma antes de qualquer código. A premissa P5 de [`requisitos.md`](../requisitos.md) determina "última estável/LTS disponível compatível com as escolhas deste documento", e o risco R6 (mudanças de versão durante o projeto) é mitigado fixando versões no bootstrap e atualizando-as apenas ao fim de cada fase.

Em 2026-09-13, a situação das linhas relevantes era:

| Tecnologia | Linhas disponíveis | Observação |
|---|---|---|
| Spring Boot | 3.5.16 (suporte OSS encerrado em 2026-06-30) · 4.0.8 (OSS até 2026-12-31) · **4.1.1** (lançada 2026-08-20, OSS até 2027-07-31) | 4.x = Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3 |
| Spring Cloud | **2025.1.3** (Oakwood) | compatível com Boot 4.0.x e 4.1.x (desde 2025.1.2) |
| Java | **21** (LTS) · 25 (LTS mais recente) | Boot 4 suporta 17–25 |
| Maven | **3.9.16** · 4.0.0 ainda em RC | wrapper 3.3.4 (`only-script`) |
| Angular | 20 (OSS até 2026-11) · 21 · **22** | fixado no bootstrap do frontend |
| PostgreSQL | 16 · 17 · 18 | 16 é o documentado; tag da imagem fixada na infra |
| Apache Kafka | 4.x (KRaft) | tag da imagem fixada na infra |
| Keycloak | 26.x | tag da imagem fixada na infra |

## Opções consideradas

### Spring Boot

1. **4.1.1** — última estável, maior janela de suporte OSS, geração atual do ecossistema.
2. 4.0.8 — mesma geração, mas suporte OSS termina em 2026-12-31, forçando migração durante a Fase 2.
3. 3.5.16 — ecossistema mais conhecido (Jackson 2, starters clássicos), mas sem suporte OSS nem correções de segurança.

### Java

1. **21** — versão documentada (§8.1, RNF-RUN-02), LTS mais exigida em vagas, disponível na máquina de desenvolvimento e nas imagens base; nenhum documento a alterar.
2. 25 — LTS mais recente; exigiria alterar documentos e imagens Docker, e avaliadores com JDK 21 não compilariam o projeto.

### `groupId` / pacote base

1. **`dev.eliascosta.invoice`** — convenção já adotada nas regras do projeto (`dev.<autor>.invoice.<servico>`).
2. `io.github.<usuário>.invoice` — convenção do Maven Central para projetos GitHub.
3. `br.com.eliascosta.invoice` — reverse-domain; mais longo, mesmo efeito.

## Decisão

| Item | Versão fixada |
|---|---|
| Java | **21** (`maven.compiler.release=21`; `-parameters`) |
| Spring Boot | **4.1.1** (BOM `spring-boot-dependencies` importado no parent) |
| Spring Cloud | **2025.1.3** (BOM `spring-cloud-dependencies`) |
| Maven | **3.9.16** via Maven Wrapper 3.3.4 (`only-script`) |
| `groupId` / pacote base | **`dev.eliascosta.invoice`** / `dev.eliascosta.invoice.<servico>` |
| Formatação | Spotless 3.10.2 + google-java-format 1.36.1 (estilo Google) |
| Cobertura | JaCoCo 0.8.15, mínimo 80 % de linhas em `domain` + `application` |
| Testes | Surefire 3.6.0 (`*Test`) e Failsafe 3.6.0 (`*IT`) |
| Mapeamento | MapStruct 1.6.3 |
| Angular | major corrente (22) — versão exata fixada no bootstrap do frontend |
| PostgreSQL / Kafka / Keycloak | 16 / 4.x (KRaft) / 26 — tags de imagem fixadas ao entrarem no Compose |

O parent POM (`backend/pom.xml`) importa os BOMs em vez de herdar de `spring-boot-starter-parent`, para manter o controle explícito das versões de plugins e a estrutura multi-módulo independente.

Atualização de versões: apenas ao fim de cada fase (R6), com nova entrada em `decisions.md` e, se a geração mudar, novo ADR que substitua este.

## Consequências

**Positivas**

- Plataforma com suporte OSS durante toda a vida prevista do projeto (Fases 1–3).
- Demonstra a geração atual do Spring (Framework 7, Jakarta EE 11, Hibernate 7).
- Versões de build reproduzíveis: qualquer clone compila com `./mvnw` sem Maven instalado.

**Negativas / riscos aceitos**

- Spring Boot 4 muda nomes de starters (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-webmvc-test`…), substitui `@MockBean` por `@MockitoBean` e move o Jackson para `tools.jackson`; há menos exemplos na internet do que para a geração 3.
- Documentos de requisitos, `AGENTS.md` e regras do agente precisaram ser atualizados de "Spring Boot 3" para "Spring Boot 4" (feito em 2026-09-13).
- Java 21 em vez de 25 abre mão de recursos mais recentes da linguagem em troca de compatibilidade com o ambiente típico de avaliação.
