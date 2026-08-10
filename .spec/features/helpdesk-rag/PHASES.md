# Phases: helpdesk-rag

Gerado por /plan a partir de PLAN.md — view executável para `./ralph.sh .spec/features/helpdesk-rag/PHASES.md`.

## Phase 1: Scaffolding, schema, domain core, async config

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos

- [ ] T01 — Project scaffolding
      Arquivos: `pom.xml`, `src/main/resources/application.yml`, `src/main/resources/application-prod.yml`, `src/test/resources/application-test.yml`, `docker-compose.yml`, `README.md`, `src/test/java/com/helpdesk/rag/HelpdeskRagApplicationTests.java`
      Mudança: Spring Boot 3.3.x/Java 17 Maven project (Spring Web, Spring Data JPA, Validation, Thymeleaf, PostgreSQL driver, pgvector-java, Flyway, Spring AI OpenAI starter, Apache Tika, Testcontainers); docker-compose for local pgvector/pgvector:pg16; README documents Spring Security is intentionally out of scope.
      Cobre: RNF-08
      Acceptance criteria: Spring context loads with no OPENAI_API_KEY env var and no network access; README/config contains an explicit statement that authN/authZ is intentionally out of scope.
      Testes: `src/test/java/com/helpdesk/rag/HelpdeskRagApplicationTests.java` — context loads without OPENAI_API_KEY set.
- [ ] T02 — Flyway migration `V1__init_schema.sql`
      Arquivos: `src/main/resources/db/migration/V1__init_schema.sql`
      Mudança: `CREATE EXTENSION IF NOT EXISTS vector;`; tabela `documents` (id UUID PK, file_name, content_type, file_size, file_data BYTEA, status VARCHAR CHECK, error_message, uploaded_at, updated_at, deleted_at, version BIGINT); tabela `document_chunks` (id UUID PK, document_id FK, chunk_index, chunk_text, embedding VECTOR(1536), created_at); índice `documents(status, uploaded_at DESC, id DESC)`; índice HNSW `vector_cosine_ops` (m=16, ef_construction=64) sobre `document_chunks.embedding`.
      Cobre: RNF-03
      Acceptance criteria: em banco limpo, após `flyway migrate`, `pg_extension` contém `vector`; ambas as tabelas existem com as colunas especificadas; existe índice `hnsw` com `vector_cosine_ops` sobre `document_chunks.embedding` (verificado em T26).
      Testes: verificado por `SchemaMigrationIT` (T26) — não aplicável teste unitário para migration SQL pura.
- [ ] T03 — Domain model
      Arquivos: `src/main/java/com/helpdesk/rag/domain/DocumentStatus.java`, `Document.java`, `DocumentChunk.java`
      Mudança: classes de domínio puras (zero dependências de framework); `DocumentStatus` enum `PENDING, PROCESSING, PROCESSED, ERROR, DELETED`; `Document` com métodos de transição de estado (`markProcessing`, `markProcessed`, `markError`, `markDeleted`) que rejeitam transições inválidas.
      Cobre: RNF-01
      Acceptance criteria: transições válidas (`PENDING→PROCESSING→PROCESSED`, `PROCESSING→ERROR`) sucedem; transição inválida (`PENDING→PROCESSED` direto) lança `IllegalStateException`; `markDeleted` define `status=DELETED` e `deletedAt` não nulo.
      Testes: `src/test/java/com/helpdesk/rag/domain/DocumentTest.java` — transições válidas/inválidas e soft-delete.
- [ ] T20 — `AsyncConfig`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/config/AsyncConfig.java`
      Mudança: `@Configuration` + `@EnableAsync` declarando o bean `TaskExecutor` `documentProcessingExecutor` usado pelo `DocumentEventListener` (T10).
      Cobre: RNF-02
      Acceptance criteria: bean `documentProcessingExecutor` existe e é injetado por `@Async("documentProcessingExecutor")` no listener; comportamento non-blocking verificado end-to-end em T30.
      Testes: verificado por `AsyncNonBlockingIT` (T30) — sem teste unitário dedicado para uma classe de configuração pura.

## Phase 2: Events, ports, support services, JPA mapping

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos

- [ ] T04 — Domain events
      Arquivos: `src/main/java/com/helpdesk/rag/domain/event/DocumentUploadedEvent.java`, `DocumentUpdatedEvent.java`
      Mudança: POJOs imutáveis carregando `documentId` (UUID) e `occurredAt` (Instant), publicados via `ApplicationEventPublisher` pelos use cases.
      Cobre: RF-01, RF-06
      Acceptance criteria: eventos são publicados pelo Upload/Update use case (T09/T12) somente após persistência bem-sucedida; sem estado mutável.
      Testes: cobertos transitivamente pelos testes de T09/T12 (evento publicado ao salvar).
- [ ] T05 — Application ports in
      Arquivos: `src/main/java/com/helpdesk/rag/application/ports/in/UploadDocumentUseCase.java`, `ListDocumentsUseCase.java`, `UpdateDocumentUseCase.java`, `DeleteDocumentUseCase.java`, `SearchRagUseCase.java` (+ Command/Result records)
      Mudança: interfaces de entrada por caso de uso conforme `prompts.md` §4.
      Cobre: RNF-01
      Acceptance criteria: cada interface possui exatamente um método de entrada com Command/Result tipados; nenhuma dependência de infraestrutura nas assinaturas.
      Testes: nenhum (apenas interfaces; comportamento testado nas implementações).
- [ ] T06 — Application ports out
      Arquivos: `src/main/java/com/helpdesk/rag/application/ports/out/DocumentRepositoryPort.java`, `EmbeddingServicePort.java`, `TextExtractionPort.java`
      Mudança: `DocumentRepositoryPort` (save, findById, findBatch keyset, deleteChunksByDocumentId, saveChunks, findSimilarChunks); `EmbeddingServicePort.embed(String): float[1536]`; `TextExtractionPort.extractText(byte[], String): String`.
      Cobre: CT-06, RNF-01
      Acceptance criteria: nenhuma classe de `application`/`domain` importa `jakarta.persistence`/Tika/Spring AI diretamente — todo acesso passa por estas portas.
      Testes: nenhum (apenas interfaces).
- [ ] T07 — `FileValidationService`
      Arquivos: `src/main/java/com/helpdesk/rag/domain/service/FileValidationService.java`, `src/main/java/com/helpdesk/rag/domain/exception/DocumentValidationException.java`
      Mudança: valida extensão (.pdf/.txt), tamanho (≤10.485.760 bytes) e content-type (sinal secundário, fallback aceito para ausente/`application/octet-stream`, rejeição apenas se contraditório explícito); lança `DocumentValidationException`.
      Cobre: RF-11
      Acceptance criteria: arquivo válido aceito; extensão inválida rejeitada; tamanho exatamente 10.485.760 bytes aceito e 10.485.761 rejeitado; content-type ausente/octet-stream aceito; `.txt`+`image/png` rejeitado.
      Testes: `src/test/java/com/helpdesk/rag/domain/service/FileValidationServiceTest.java` — matriz completa de casos RF-11/RNF-07.
- [ ] T08 — `ChunkingService`
      Arquivos: `src/main/java/com/helpdesk/rag/domain/service/ChunkingService.java`
      Mudança: split por parágrafo quando possível; fallback para blocos fixos de 1000 caracteres com overlap de 200; input vazio/whitespace retorna lista vazia.
      Cobre: RF-02, RNF-07
      Acceptance criteria: texto multi-parágrafo divide por parágrafo; texto de bloco único de 2500 chars produz chunks fixos de exatamente 1000 chars com overlap exato de 200; input vazio/whitespace retorna lista vazia.
      Testes: `src/test/java/com/helpdesk/rag/domain/service/ChunkingServiceTest.java` — casos de parágrafo, fallback fixo com limites exatos, e vazio.
- [ ] T15 — JPA entities, Spring Data repositories, e mapper
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/out/persistence/DocumentJpaEntity.java`, `DocumentChunkJpaEntity.java`, `SpringDataDocumentJpaRepository.java`, `SpringDataDocumentChunkJpaRepository.java`, `DocumentPersistenceMapper.java`
      Mudança: entidades JPA mapeando o schema de T02, incluindo `@Version` em `DocumentJpaEntity.version` e conversor `float[1536]` ↔ pgvector `VECTOR(1536)`; mapper domínio↔entidade.
      Cobre: RNF-01, RNF-03, RF-12
      Acceptance criteria: round-trip domínio→entidade→domínio preserva todos os campos incluindo `version`, `deletedAt`, `errorMessage` nullable.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/persistence/DocumentPersistenceMapperTest.java` — round-trip completo.

## Phase 3: Use cases, listener, and out-adapters

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos

- [ ] T09 — `UploadDocumentUseCaseImpl`
      Arquivos: `src/main/java/com/helpdesk/rag/application/usecase/UploadDocumentUseCaseImpl.java`
      Mudança: `@Transactional`: valida via `FileValidationService` antes de persistir; persiste `Document status=PENDING`; publica `DocumentUploadedEvent`.
      Cobre: RF-01, RF-11
      Acceptance criteria: upload válido persiste `status=PENDING` e publica `DocumentUploadedEvent` exatamente uma vez; upload inválido não persiste nada; fallback/contradição de content-type tratados por RF-11.
      Testes: `src/test/java/com/helpdesk/rag/application/usecase/UploadDocumentUseCaseImplTest.java` — casos válido, extensão/tamanho inválidos, content-type fallback/contraditório.
- [ ] T10 — `DocumentEventListener`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/in/event/DocumentEventListener.java`
      Mudança: `@Async("documentProcessingExecutor")` + `@TransactionalEventListener(phase=AFTER_COMMIT)`; upload: PROCESSING→extrai→chunk→embed→persiste chunks→PROCESSED, ou ERROR com mensagem fixa se zero chunks; update: apaga chunks antigos antes de reprocessar; qualquer exceção→ERROR sem chunks órfãos persistidos.
      Cobre: RF-02, RF-03, RF-04, RF-06, RNF-02
      Acceptance criteria: sucesso produz ≥1 chunk com vetor 1536-dim e `status=PROCESSED`; conteúdo vazio produz `status=ERROR` com mensagem "Nenhum conteúdo extraído do documento" e zero chunks; exceção durante processamento produz `status=ERROR` sem chunks órfãos; update apaga chunks antigos antes de salvar novos (ordem verificada).
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/in/event/DocumentEventListenerTest.java` — sucesso, zero-chunk, exceção, ordem de update.
- [ ] T11 — `DeleteDocumentUseCaseImpl`
      Arquivos: `src/main/java/com/helpdesk/rag/application/usecase/DeleteDocumentUseCaseImpl.java`, `src/main/java/com/helpdesk/rag/domain/exception/DocumentProcessingConflictException.java`
      Mudança: rejeita com `DocumentProcessingConflictException` se `status=PROCESSING`; caso contrário soft-delete (`status=DELETED`+`deletedAt`) e apaga chunks fisicamente.
      Cobre: RF-07, RF-08, RF-09
      Acceptance criteria: documento fora de PROCESSING é soft-deletado e chunks removidos; documento PROCESSING rejeitado sem nenhuma chamada mutante ao repositório.
      Testes: `src/test/java/com/helpdesk/rag/application/usecase/DeleteDocumentUseCaseImplTest.java`.
- [ ] T12 — `UpdateDocumentUseCaseImpl`
      Arquivos: `src/main/java/com/helpdesk/rag/application/usecase/UpdateDocumentUseCaseImpl.java`
      Mudança: rejeita se `status=PROCESSING`; caso contrário valida novo arquivo (T07), substitui metadados/arquivo, define `status=PROCESSING`, salva e publica `DocumentUpdatedEvent`.
      Cobre: RF-06, RF-07, RF-11
      Acceptance criteria: update válido em documento fora de PROCESSING define `status=PROCESSING`, persiste e publica evento; documento PROCESSING rejeitado sem mutação; arquivo inválido rejeitado sem evento publicado.
      Testes: `src/test/java/com/helpdesk/rag/application/usecase/UpdateDocumentUseCaseImplTest.java`.
- [ ] T13 — `ListDocumentsUseCaseImpl`
      Arquivos: `src/main/java/com/helpdesk/rag/application/usecase/ListDocumentsUseCaseImpl.java`
      Mudança: delega a `DocumentRepositoryPort.findBatch(cursor, 20)`; mapeia para `DocumentPageResult` (items + `nextCursor` opcional).
      Cobre: RF-05
      Acceptance criteria: porta retornando 20 itens mapeia para exatamente 20 resultados com cursor não nulo; porta retornando <20 itens mapeia para `nextCursor=null`; cursor de entrada repassado sem alteração à porta.
      Testes: `src/test/java/com/helpdesk/rag/application/usecase/ListDocumentsUseCaseImplTest.java`.
- [ ] T14 — `SearchRagUseCaseImpl`
      Arquivos: `src/main/java/com/helpdesk/rag/application/usecase/SearchRagUseCaseImpl.java`
      Mudança: embeda a pergunta via `EmbeddingServicePort`; delega a `DocumentRepositoryPort.findSimilarChunks`; mapeia resultados já ordenados/score-calculados sem reordenar.
      Cobre: RF-10
      Acceptance criteria: pergunta é embedada exatamente uma vez; resultados da porta (pré-ordenados/score) passam sem reordenação; resultado vazio da porta mapeia para lista vazia.
      Testes: `src/test/java/com/helpdesk/rag/application/usecase/SearchRagUseCaseImplTest.java`.
- [ ] T16 — `JpaDocumentRepositoryAdapter`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/out/persistence/JpaDocumentRepositoryAdapter.java`, `@Query` nativas em `SpringDataDocumentChunkJpaRepository`/`SpringDataDocumentJpaRepository`
      Mudança: query de listagem keyset (`uploaded_at DESC, id DESC`, exclui DELETED); query nativa de similaridade `<=>` com `score = ROUND(LEAST(GREATEST((1-distância)*100,0),100)::numeric,2)` filtrando `status='PROCESSED'`; delete físico de chunks; propagação de `ObjectOptimisticLockingFailureException` em versão desatualizada.
      Cobre: RF-05, RF-08, RF-09, RF-10, RF-12
      Acceptance criteria: listagem exclui `DELETED` e ordena por data desc sem repetição entre lotes; busca retorna apenas chunks de documentos `PROCESSED`, score em `[0,100]` com 2 casas; delete de chunks é físico; save com versão desatualizada propaga exceção de lock otimista.
      Testes: coberto por testes de integração T27/T28/T29 (query nativa não é testável de forma significativa sem pgvector real).
- [ ] T17 — `TikaTextExtractionAdapter`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/out/ai/TikaTextExtractionAdapter.java`
      Mudança: implementa `TextExtractionPort` via Apache Tika `AutoDetectParser` para PDF e TXT.
      Cobre: RF-02
      Acceptance criteria: extrai texto não vazio de fixtures `.pdf`/`.txt`; retorna string vazia/whitespace para fixture `.txt` em branco.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/ai/TikaTextExtractionAdapterTest.java` com fixtures em `src/test/resources/fixtures/`.
- [ ] T18 — `OpenAiEmbeddingAdapter`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/out/ai/OpenAiEmbeddingAdapter.java`
      Mudança: implementa `EmbeddingServicePort` via Spring AI `EmbeddingModel` para `text-embedding-3-small` (1536 dim), API key via `OPENAI_API_KEY`; ativo apenas fora do profile de teste.
      Cobre: CT-06
      Acceptance criteria: com `EmbeddingModel` mockado, adapter delega o texto de entrada e retorna `float[1536]`; nenhuma chamada de rede real ocorre no teste.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/ai/OpenAiEmbeddingAdapterTest.java`.
- [ ] T19 — `FakeEmbeddingAdapter` (test-only)
      Arquivos: `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/ai/FakeEmbeddingAdapter.java`, `src/test/java/com/helpdesk/rag/infrastructure/config/TestEmbeddingConfig.java`
      Mudança: implementação determinística de `EmbeddingServicePort` (hash do texto normalizado expandido a 1536 floats), sem rede/API key, vivendo exclusivamente em `src/test/java`, wired via `@TestConfiguration` no profile `test`.
      Cobre: CT-06, RNF-04
      Acceptance criteria: mesmo texto de entrada produz vetor idêntico em chamadas repetidas; vetor de saída tem exatamente 1536 elementos.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/ai/FakeEmbeddingAdapterTest.java`.
- [ ] T26 — Testcontainers base infrastructure + schema verification
      Arquivos: `src/test/java/com/helpdesk/rag/support/AbstractIntegrationTest.java`, `src/test/java/com/helpdesk/rag/infrastructure/adapters/out/persistence/SchemaMigrationIT.java`
      Mudança: `AbstractIntegrationTest` com container singleton `pgvector/pgvector:pg16`, Flyway automático, profile `test` ativo (usa `FakeEmbeddingAdapter` de T19); `SchemaMigrationIT` valida o resultado da migration.
      Cobre: RNF-03, RNF-04, RNF-06
      Acceptance criteria: após `flyway migrate` no container, `pg_extension` contém `vector`; tabelas `documents`/`document_chunks` existem com as colunas especificadas; índice `hnsw`/`vector_cosine_ops` existe sobre `document_chunks.embedding`.
      Testes: `SchemaMigrationIT` (arquivo próprio desta task).

## Phase 4: Error handling

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos
3. `.spec/features/helpdesk-rag/openapi.yaml` — formato dos corpos de erro 400/404/409

- [ ] T21 — `GlobalExceptionHandler`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/in/web/GlobalExceptionHandler.java`
      Mudança: `@RestControllerAdvice` mapeando `DocumentValidationException`→400, `DocumentProcessingConflictException`→409, `ObjectOptimisticLockingFailureException`→409 (mensagem distinta), documento inexistente→404.
      Cobre: RF-04, RF-07, RF-11, RF-12, CT-01, CT-03, CT-04
      Acceptance criteria: cada tipo de exceção mapeia para o status HTTP e formato de corpo documentados em `openapi.yaml` (`ValidationErrorResponse`/`ConflictErrorResponse`/`NotFoundErrorResponse`); mensagem de conflito distingue PROCESSING de versão desatualizada.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/in/web/GlobalExceptionHandlerTest.java` — MockMvc simulando cada exceção.

## Phase 5: Web controllers

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos
3. `.spec/features/helpdesk-rag/openapi.yaml` — contrato exato dos endpoints desta fase

- [ ] T22 — `DocumentController`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/in/web/DocumentController.java`, `dto/UploadDocumentResponse.java`, `dto/DocumentSummaryResponse.java`, `dto/DocumentListResponse.java`, `dto/UpdateDocumentResponse.java`
      Mudança: implementa `POST /api/documents`, `GET /api/documents?cursor=`, `PUT /api/documents/{id}`, `DELETE /api/documents/{id}` exatamente conforme `openapi.yaml` (CT-01..CT-04).
      Cobre: CT-01, CT-02, CT-03, CT-04, RF-01, RF-05, RF-06, RF-07, RF-08, RF-11, RF-12
      Acceptance criteria: upload válido→201; upload inválido→400; listagem retorna até 20 itens + `nextCursor`; update válido→200 `status=PROCESSING`; update/delete em PROCESSING→409; delete válido→204; respostas batem byte-a-byte com os schemas de `openapi.yaml`.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/in/web/DocumentControllerTest.java` — MockMvc com use cases mockados cobrindo todos os status codes acima.
- [ ] T23 — `SearchController`
      Arquivos: `src/main/java/com/helpdesk/rag/infrastructure/adapters/in/web/SearchController.java`, `dto/SearchRequest.java`, `dto/SearchResultResponse.java`
      Mudança: `POST /api/search` com corpo `{"question": "..."}`; retorna array JSON de resultados na ordem produzida pelo use case.
      Cobre: CT-05, RF-10
      Acceptance criteria: pergunta válida retorna array JSON na ordem/forma exata do use case; pergunta em branco→400.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/in/web/SearchControllerTest.java`.

## Phase 6: Views and integration tests

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos

- [ ] T24 — Thymeleaf views + `PageViewController`
      Arquivos: `src/main/resources/templates/upload.html`, `documents.html`, `search.html`, `fragments/layout.html`, `src/main/java/com/helpdesk/rag/infrastructure/adapters/in/web/PageViewController.java`
      Mudança: `PageViewController` serve `GET /`, `/documents`, `/search`, `/upload` (shells server-side); `documents.html` inclui badges de status coloridos e ações editar/excluir; `search.html` inclui campo de pergunta e container de resultados; `upload.html` inclui formulário de upload; layout Bootstrap 5 compartilhado.
      Cobre: UI-01, UI-02, UI-03
      Acceptance criteria: `GET /documents`, `/search`, `/upload` retornam HTTP 200 e resolvem a view esperada; `documents.html` contém elemento de badge por status e controles de editar/excluir.
      Testes: `src/test/java/com/helpdesk/rag/infrastructure/adapters/in/web/PageViewControllerTest.java`.
- [ ] T27 — Integration test: fluxo de upload/pós-commit
      Arquivos: `src/test/java/com/helpdesk/rag/UploadDocumentFlowIT.java`
      Mudança: teste full-stack (MockMvc + Testcontainers + `FakeEmbeddingAdapter`) cobrindo upload válido→PROCESSED com chunks/vetores 1536-dim, e upload de conteúdo vazio→ERROR com mensagem fixa e zero chunks.
      Cobre: RF-01, RF-02, RF-03, RF-04
      Acceptance criteria: documento `.txt` válido termina `PROCESSED` com ≥1 `document_chunks` de vetor 1536-dim não nulo; documento vazio termina `ERROR` com mensagem "Nenhum conteúdo extraído do documento" e zero chunks; asserts via Awaitility (sem sleep fixo).
      Testes: este próprio arquivo é o teste.
- [ ] T28 — Integration test: busca semântica + cálculo de score
      Arquivos: `src/test/java/com/helpdesk/rag/SemanticSearchIT.java`
      Mudança: semeia documentos `PROCESSED` + um `DELETED`; chama `POST /api/search`; valida score em `[0,100]` com 2 casas, ordenação desc, ausência do documento `DELETED`.
      Cobre: RF-09, RF-10
      Acceptance criteria: todo score retornado está em `[0,100]` com exatamente 2 casas decimais; resultados ordenados desc; nenhum resultado referencia o documento `DELETED`.
      Testes: este próprio arquivo é o teste.
- [ ] T29 — Integration test: conflitos de concorrência
      Arquivos: `src/test/java/com/helpdesk/rag/ConcurrencyConflictIT.java`
      Mudança: Cenário A — update/delete em documento `PROCESSING`→409 sem mutação; Cenário B (distinto) — update/delete com `@Version` desatualizado→409 via `ObjectOptimisticLockingFailureException` real.
      Cobre: RF-07, RF-12
      Acceptance criteria: Cenário A retorna 409 e estado persistido inalterado; Cenário B retorna 409 por lock otimista real, comprovadamente distinto do Cenário A (não passa pelo mesmo código de rejeição por PROCESSING).
      Testes: este próprio arquivo é o teste.
- [ ] T30 — Integration test: verificação non-blocking assíncrona
      Arquivos: `src/test/java/com/helpdesk/rag/AsyncNonBlockingIT.java`
      Mudança: decorator com atraso artificial sobre `FakeEmbeddingAdapter`; upload via `POST /api/documents`; assert que a resposta HTTP retorna antes da conclusão do mock atrasado (comparação de timestamps, sem race por sleep).
      Cobre: RNF-02
      Acceptance criteria: timestamp de resposta HTTP do upload é anterior ao timestamp de conclusão da chamada de embedding atrasada.
      Testes: este próprio arquivo é o teste.

## Phase 7: Client-side scripting

Antes de implementar, leia:
1. `.spec/features/helpdesk-rag/SPEC.md` — requisitos RIGID que esta fase cobre
2. `.spec/features/helpdesk-rag/PLAN.md` — decomposição completa, dependências e riscos

- [ ] T25 — Static JS assets (jQuery AJAX)
      Arquivos: `src/main/resources/static/js/upload.js`, `documents.js`, `search.js`
      Mudança: `upload.js` submete via AJAX e exibe feedback sem reload; `documents.js` carrega o primeiro lote no load, auto-carrega o próximo lote ao aproximar do fim da lista (sem botão), e liga ações editar/excluir a `PUT`/`DELETE`; `search.js` submete pergunta via AJAX e renderiza cards Bootstrap ordenados por score desc.
      Cobre: UI-01, UI-02, UI-03
      Acceptance criteria: submissão de upload não navega para outra URL e exibe status do novo documento; scroll próximo do fim da lista dispara requisição automática do próximo lote sem botão "carregar mais"; submissão de busca renderiza cards com nome do documento, trecho e percentual de score, ordenados desc.
      Testes: nenhum test runner de JS declarado no stack (JUnit5+Mockito+Testcontainers apenas); comportamento observável via servidor coberto pelos testes de T22/T23/T24; cobertura de JS client-side é lacuna conhecida e não-RIGID.
