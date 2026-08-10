# Help Desk Inteligente com RAG

Case de referência: upload de documentos (PDF/TXT), processamento assíncrono pós-commit
(extração de texto, chunking, geração de embeddings, persistência vetorial), CRUD completo
de documentos com soft delete e lock otimista, e busca semântica por similaridade de
cosseno via PostgreSQL/pgvector, exposta em uma UI Thymeleaf + Bootstrap 5 + jQuery.

Construído seguindo Arquitetura Hexagonal (Ports and Adapters) sob o pacote `com.helpdesk.rag`.

## Segurança — fora de escopo (leia antes de usar)

> **Autenticação e autorização NÃO são implementadas neste projeto.** Spring Security foi
> deliberadamente omitido do escopo deste case de referência (foco em RAG, arquitetura
> hexagonal e persistência vetorial). Todos os endpoints são publicamente acessíveis sem
> qualquer controle de identidade. Esta é uma decisão de escopo explícita, não um descuido —
> não utilize esta base como está em um ambiente exposto à internet sem adicionar uma camada
> de autenticação/autorização.

## Stack

- Spring Boot 3.3.x / Java 17
- Spring Web, Spring Data JPA, Bean Validation, Thymeleaf
- PostgreSQL 16 + extensão `pgvector` (driver `pgvector-java`)
- Flyway (migrations)
- Spring AI (OpenAI `text-embedding-3-small`, 1536 dimensões)
- Apache Tika (extração de texto de PDF/TXT)
- Bootstrap 5 + jQuery (frontend, via CDN)
- JUnit 5 + Mockito (testes unitários), Testcontainers `pgvector/pgvector:pg16` (testes de integração)

## Rodando localmente

1. Suba o banco local com pgvector:

   ```bash
   docker compose up -d
   ```

2. Rode a aplicação (perfil default, sem `OPENAI_API_KEY`, contexto sobe normalmente —
   as chamadas de embedding real só falham se você efetivamente usar a busca/upload com o
   adapter `OpenAiEmbeddingAdapter` ativo):

   ```bash
   ./mvnw spring-boot:run
   ```

3. Para embeddings reais (produção/dev com OpenAI), defina a variável de ambiente e use o
   perfil `prod`:

   ```bash
   export OPENAI_API_KEY=sk-...
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
   ```

## Testes

A suíte completa (`mvn test`) roda sem acesso à rede externa e sem `OPENAI_API_KEY`
definido: os testes usam um `FakeEmbeddingAdapter` determinístico em memória (sem chamadas
HTTP), e os testes de integração usam Testcontainers (`pgvector/pgvector:pg16`) rodando
localmente via Docker.

```bash
mvn test
```

## Arquitetura

```
com.helpdesk.rag
├── domain                          # entidades puras, VOs, enums — zero dependência de framework
├── application
│   ├── ports.in                    # interfaces de use case
│   ├── ports.out                   # interfaces de persistência/IA/extração de texto
│   └── usecase                     # implementações dos use cases
└── infrastructure
    ├── adapters.in.web             # controllers Spring MVC, views Thymeleaf
    ├── adapters.in.event           # listener de eventos pós-commit (@Async + AFTER_COMMIT)
    ├── adapters.out.persistence    # Spring Data JPA, entidades JPA
    ├── adapters.out.ai             # adapters de embedding (OpenAI/Fake) e extração (Tika)
    └── config                      # beans, TaskExecutor, configuração de eventos transacionais
```
