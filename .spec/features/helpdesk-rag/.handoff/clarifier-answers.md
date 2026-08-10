# Developer Answers — helpdesk-rag clarifier resolve pass

## Q-01 [Marker] Architecture guidance source missing
Confirmed: `prompts.md` §2 ("Estrutura de Pacotes (Arquitetura Hexagonal)") remains the sole convention source for this feature. No `AGENTS.md` will be created as part of this pass. Resolve the marker with this statement — no further action needed.

## Q-02 [Marker] AC-3 "(drafted)" — paginated listing
Changed, not just confirmed: replace paginated listing (Pageable/page-number UI) with **infinite scroll**.
- Batch size: 20 documents per batch (same as original page size).
- Mechanism: automatic load-more triggered on scroll proximity to the end of the list (no "load more" button).
- Underlying API may still be offset/limit or keyset-based (implementation detail, FLEXIBLE) — the RIGID requirement is the UX behavior (auto-load on scroll, 20/batch) and that it still excludes soft-deleted documents (see Q-04) and sorts by upload date descending.
- Update RF-05/CT-02 accordingly; remove "(drafted)" annotation, this is now a fully confirmed, changed requirement.

## Q-03 [Contradiction] RF-12 optimistic-lock (@Version) test coverage
Move to integration test coverage under RNF-06 (Testcontainers), in addition to/instead of unit-only mock coverage in RNF-05. Add an explicit Testcontainers scenario: stale `@Version` on concurrent update/delete → real `OptimisticLockException` surfaced as HTTP 409. Keep this distinct from the existing RF-07 PROCESSING-state conflict scenario already in RNF-06 — both are integration-tested, covering different conflict sources.

## Q-04 [Gap] RF-09 soft-delete "por padrão" wording
Reword RF-09 to remove the override implication: soft-deleted documents are **always** excluded from listing and search (no override endpoint/parameter, no admin view of deleted documents — out of scope). Replace "por padrão" with "sempre" (always) in the RF text.

## Q-05 [Gap] RF-02 zero-chunk extraction
Explicitly define: zero chunks extracted (empty/whitespace-only PDF/TXT, or chunking that yields nothing) is a terminal `ERROR` state, with a defined error message (e.g. "Nenhum conteúdo extraído do documento"). Add this as an explicit state-machine transition and an explicit AC under RF-02/RF-04, plus RNF-05 unit-test coverage for this transition.

## Q-06 [Gap] RF-11 content-type validation strictness
Soften RF-11: accept upload when extension is valid (`.pdf`/`.txt`) even if content-type is generic or absent (`application/octet-stream`, empty). Only reject when extension itself is invalid, or when content-type is present and explicitly contradicts a *different* known type (e.g. `.txt` file declared as `image/png`). Reword RF-11 accordingly — extension is the primary signal, content-type is a secondary sanity check with an explicit octet-stream/absent fallback.

## Q-07 [Gap] CT-06 text normalization for embeddings
Move "normalização de texto para embedding (regras exatas)" to the **FLEXIBLE** section, implementer-defined, with the single constraint that it must be deterministic (same input text always yields the same output in `FakeEmbeddingAdapter`, so integration tests relying on repeatable embeddings/scores stay stable). Do not pin exact normalization rules (trim/case/whitespace) in RIGID.
