# Prompt Base — Case Modelo do Zero (Help Desk Inteligente com RAG)

Atue como um Arquiteto de Soluções Sênior e Especialista em Clean/Hexagonal Architecture, Java e Spring Boot.

Preciso que você desenhe e implemente o código-fonte completo de um **Case Modelo de Arquitetura de Referência** em **Spring Boot (Java 17+)** utilizando **PostgreSQL com pgvector**, focado no **Cenário de Negócio: Help Desk Inteligente com RAG**.

O sistema deve suportar upload de documentos (PDF/TXT), processá-los de forma transacional segura, indexá-los como vetores e expor uma interface web clássica e funcional para busca semântica. O projeto deve seguir rigorosamente **Arquitetura Hexagonal (Ports and Adapters)** e possuir **testes automatizados para tudo** (unitários e de integração).

---

### 1. Stack Tecnológica (decisões fechadas — não deixar em aberto)

- **Backend:** Spring Boot 3.3.x, Java 17+, Spring Web, Spring Data JPA, Spring Validation.
- **Banco:** PostgreSQL 16 com extensão `pgvector`. Flyway para migrations.
- **Extração de texto:** Apache Tika (cobre PDF e TXT com a mesma API, evita depender de PDFBox isolado).
- **Embeddings:** Spring AI + OpenAI (`text-embedding-3-small`, **1536 dimensões**). A porta de saída `EmbeddingServicePort` deve ser desacoplada do provedor:
  - Implementação real (`OpenAiEmbeddingAdapter`) usada em produção/dev, configurada via `application.yml` (API key por variável de ambiente).
  - Implementação determinística em memória (`FakeEmbeddingAdapter`, ex.: hash do texto normalizado em vetor de 1536 posições) usada nos testes automatizados — **os testes não devem depender de rede nem de API key**.
- **Frontend:** Thymeleaf + Bootstrap 5 + jQuery (AJAX para upload e busca).
- **Ambiente local:** `docker-compose.yml` com a imagem `pgvector/pgvector:pg16` para desenvolvimento fora dos testes.
- **Testes:** JUnit 5 + Mockito (unitários) e Spring Boot Test + Testcontainers (`pgvector/pgvector:pg16`) para integração.
- **Segurança:** fora de escopo deste case — não implementar Spring Security. Deixar isso explícito em um comentário no `README`/config para não ser confundido com omissão.

---

### 2. Estrutura de Pacotes (Arquitetura Hexagonal)

- `com.helpdesk.rag`
  - `domain` — entidades de negócio puras, value objects, enums (ex.: `DocumentStatus`).
  - `application`
    - `ports.in` — Use Cases (interfaces de entrada).
    - `ports.out` — interfaces de saída (persistência, IA, extração de texto).
    - `usecase` — implementações dos casos de uso.
  - `infrastructure`
    - `adapters.in.web` — Controllers Spring MVC, views Thymeleaf.
    - `adapters.out.persistence` — Spring Data JPA, entidades JPA, migrations Flyway.
    - `adapters.out.ai` — adapters de embedding (real e fake) e de extração de texto (Tika).
    - `config` — beans, `TaskExecutor` para processamento assíncrono, configuração de eventos transacionais.

---

### 3. Regras de Negócio Fechadas (para eliminar ambiguidade)

1. **Máquina de estados do documento** (`DocumentStatus`): `PENDING → PROCESSING → PROCESSED | ERROR`.
   - `PENDING`: metadados salvos, aguardando processamento pós-commit.
   - `PROCESSING`: extração/chunking/embedding em andamento.
   - `PROCESSED`: todos os chunks vetorizados com sucesso.
   - `ERROR`: falha em qualquer etapa; guardar mensagem de erro; documento fica pesquisável apenas por metadados, não por conteúdo.
2. **Processamento assíncrono real:** o listener pós-commit deve ser anotado com `@Async` (executor dedicado configurado em `config`, ex. `documentProcessingExecutor`) **além de** `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, para não bloquear a thread da requisição HTTP.
3. **Chunking:** por parágrafo quando possível, com fallback para chunks fixos de **1000 caracteres com overlap de 200 caracteres**.
4. **Score de similaridade:** `score = ROUND((1 - distancia_cosseno) * 100, 2)`, limitado ao intervalo `[0, 100]`. Exibido na UI como percentual.
5. **Exclusão:** apenas **lógica** (soft delete). Documento ganha `status = DELETED` e `deleted_at`; os chunks/vetores associados são removidos fisicamente do `pgvector` (não faz sentido manter vetores órfãos). Toda query de busca e de listagem deve excluir documentos com `status = DELETED` por padrão.
6. **Concorrência:** `Document` tem `@Version` (lock otimista). Update/Delete solicitados enquanto `status = PROCESSING` devem ser rejeitados com HTTP 409 e mensagem clara.
7. **Upload:** aceitar apenas `.pdf` e `.txt` (validar extensão **e** content-type), tamanho máximo de 10 MB, validado antes de qualquer persistência.
8. **Listagem:** paginada (`Pageable`, tamanho de página padrão 20), ordenada por data de upload decrescente.

---

### 4. Artefatos que devem ser gerados

1. **`pom.xml`:** Spring Web, Spring Data JPA, Thymeleaf, Validation, driver PostgreSQL, `pgvector-java`, Flyway, Spring AI (starter OpenAI), Apache Tika, Testcontainers (JUnit 5 + PostgreSQL module).
2. **Migration Flyway (`V1__init_schema.sql`):**
   - `CREATE EXTENSION IF NOT EXISTS vector;`
   - Tabela `documents` (id, nome, tipo, status, mensagem de erro, `deleted_at`, `version`, timestamps).
   - Tabela `document_chunks` (id, `document_id` FK, texto do trecho, `embedding VECTOR(1536)`).
   - Índice HNSW com operador de distância de cosseno (`vector_cosine_ops`) na coluna `embedding`.
3. **Domínio e Portas:**
   - Entidades de domínio puras (`Document`, `DocumentChunk`), enum `DocumentStatus`.
   - Portas de entrada: `UploadDocumentUseCase`, `SearchRagUseCase`, `ListDocumentsUseCase`, `UpdateDocumentUseCase`, `DeleteDocumentUseCase`.
   - Portas de saída: `DocumentRepositoryPort`, `EmbeddingServicePort`, `TextExtractionPort`.
4. **Casos de Uso:**
   - Upload: salva metadados (`status = PENDING`) dentro de `@Transactional`, publica `DocumentUploadedEvent`.
   - Listener pós-commit (`@Async` + `AFTER_COMMIT`): extrai texto (Tika), faz chunking, chama `EmbeddingServicePort`, persiste chunks/vetores, atualiza `status` para `PROCESSED` ou `ERROR`.
   - Busca semântica: converte a pergunta em vetor e delega ao repositório a query nativa de similaridade.
5. **Adaptadores:**
   - Adapter JPA implementando `DocumentRepositoryPort`, com native query usando `<=>` e o cálculo de score (regra da seção 3.4).
   - `OpenAiEmbeddingAdapter` e `FakeEmbeddingAdapter`.
   - `TikaTextExtractionAdapter`.
   - Controllers Spring MVC/REST para upload, listagem paginada, update, delete e busca (AJAX).
6. **Frontend (Thymeleaf + Bootstrap 5 + jQuery):**
   - Formulário de upload com feedback de status.
   - Tela de listagem paginada de documentos com status (badge colorido) e ações de editar/excluir.
   - Seção de busca semântica com campo de texto, resultados em cards Bootstrap mostrando documento de origem, trecho (chunk) e percentual de score.
7. **Testes automatizados:**
   - Unitários (JUnit 5 + Mockito) para os Use Cases, incluindo a máquina de estados e as regras de concorrência/soft delete.
   - Integração (Testcontainers com `pgvector/pgvector:pg16`) cobrindo: persistência dos chunks e vetores, execução completa do fluxo pós-commit com `FakeEmbeddingAdapter`, a query nativa de busca por similaridade e o cálculo de score.

Por favor, gere a arquitetura completa, código modular organizado por pastas, e comentários apenas onde a regra de negócio não for óbvia pelo nome (ex.: fórmula do score, motivo do `@Async` combinado com `AFTER_COMMIT`).

---

# Prompt de Evolução — CRUD Completo + Busca Semântica Avançada

Atue como um Arquiteto de Software Sênior e Especialista em Clean Architecture.

Este prompt evolui o projeto gerado pelo **Prompt Base** acima. Não redefina as decisões já fechadas ali (máquina de estados, score, soft delete, `@Async` + `AFTER_COMMIT`, paginação, lock otimista) — apenas estenda o comportamento a seguir, mantendo consistência total com o que já existe.

---

### Novas Funcionalidades

1. **Update de Documento:**
   - Permite reenviar um novo arquivo para o mesmo `Document` (o metadado é atualizado; o arquivo antigo é substituído).
   - Ao confirmar o update: dispara `DocumentUpdatedEvent`; o listener pós-commit (mesmo padrão `@Async` + `AFTER_COMMIT`) deve **primeiro apagar fisicamente** os `document_chunks`/vetores antigos e só então reprocessar os novos chunks, transicionando `status` por `PROCESSING → PROCESSED | ERROR`.
   - Se o documento já estiver em `status = PROCESSING`, o update é rejeitado com HTTP 409 (reaproveitando a regra de concorrência do Prompt Base).

2. **Busca Semântica na UI:**
   - Reaproveita a query nativa e a fórmula de score já definidas no Prompt Base — este prompt não altera o cálculo, apenas conecta a UI (jQuery AJAX) à busca já existente.
   - Cards Bootstrap exibindo documento de origem, chunk e percentual de match, ordenados por score decrescente.

3. **Consistência de Arquitetura e Testes:**
   - Mantém Arquitetura Hexagonal, `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` para toda escrita que impacta o pgvector, e cobertura de testes (unitários + Testcontainers) para os novos fluxos de update, incluindo o caso de update rejeitado por concorrência.

Por favor, apresente a evolução dos Use Cases, Ports, Adapters de Persistência (com a query já otimizada do Prompt Base) e a atualização Thymeleaf/jQuery para o painel de controle, deixando explícito onde cada novo comportamento se encaixa na estrutura de pacotes já definida.
