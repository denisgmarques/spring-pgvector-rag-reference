# Confirmed Input — helpdesk-rag

## Summary
Implementar o case de referência "Help Desk Inteligente com RAG" em Spring Boot (Arquitetura Hexagonal) com PostgreSQL/pgvector — upload/CRUD de documentos com processamento assíncrono pós-commit, busca semântica por similaridade de cosseno com score, e cobertura de testes automatizados completa.

## Slug
helpdesk-rag

## Tier
complete (12 confirmed ACs ≥ 11; domain spans ≥ 2 bounded contexts — document lifecycle management and semantic search/AI — in a single repo)

## Full description source
Full requirements narrative (stack, package structure, closed business rules) is in `prompts.md` at repo root — read both sections: "Prompt Base — Case Modelo do Zero" and "Prompt de Evolução — CRUD Completo + Busca Semântica Avançada". Treat them as one combined feature scope.

## Confirmed Acceptance Criteria (source of truth — no issue tracker)

1. Upload de documento (PDF/TXT) salva metadados com `status=PENDING` dentro de transação e dispara evento pós-commit.
2. Listener pós-commit (`@Async` + `AFTER_COMMIT`) extrai texto (Tika), faz chunking (parágrafo, fallback 1000 chars/200 overlap), gera embeddings (1536 dim) e persiste chunks/vetores; status transiciona `PROCESSING → PROCESSED | ERROR`.
3. Listagem paginada de documentos (`Pageable`, page size 20, ordenado por data desc) exibindo status. (drafted)
4. Update de documento substitui o arquivo, apaga fisicamente chunks/vetores antigos e reprocessa; rejeitado com HTTP 409 se `status=PROCESSING`.
5. Delete lógico (soft delete): marca `status=DELETED`/`deleted_at`, remove fisicamente chunks/vetores associados; documentos deletados excluídos de buscas e listagens.
6. Busca semântica: converte pergunta em vetor, executa query nativa `<=>` (cosseno) via pgvector, retorna chunks com `score = ROUND((1-distância)*100,2)` clamped `[0,100]`, exibidos via AJAX/jQuery em cards Bootstrap.
7. Upload valida extensão e content-type (apenas pdf/txt) e tamanho máx. 10MB antes de persistir.
8. Lock otimista (`@Version`) em `Document`; update/delete concorrente durante `PROCESSING` retorna 409.
9. Arquitetura Hexagonal estrita (domain/application/infrastructure, ports in/out) conforme estrutura de pacotes definida no prompt (`com.helpdesk.rag`).
10. Cobertura de testes: unitários (JUnit5+Mockito) para use cases incl. máquina de estados e concorrência/soft delete; integração (Testcontainers `pgvector/pgvector:pg16`) cobrindo persistência de chunks/vetores, fluxo pós-commit completo com `FakeEmbeddingAdapter`, query de busca+score, e update rejeitado por concorrência.
11. Migration Flyway `V1__init_schema.sql` cria extensão `vector`, tabelas `documents`/`document_chunks` com `embedding VECTOR(1536)` e índice HNSW cosine.
12. `EmbeddingServicePort` com duas implementações — `OpenAiEmbeddingAdapter` (prod) e `FakeEmbeddingAdapter` determinístico (testes) — sem dependência de rede/API key nos testes.

## Architecture reference status
missing — no AGENTS.md / docs/agents/ / copilot-instructions.md found. Greenfield repo, no implemented code yet. Developer chose to proceed without architecture guidance. SPEC and PLAN must carry `architecture_reference_status: missing` and emit the explicit warning marker instead of silently planning. Use the package layout and layering rules stated in `prompts.md` ("Estrutura de Pacotes (Arquitetura Hexagonal)") as the only available convention source.

## Init chain
None of `.spec/init/project-description.md`, `user-stories.md`, `database-schema.md`, `project-phases.md` exist. Do not reference them.
