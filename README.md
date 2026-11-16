# Enterprise RAG Document Assistant

An AI-powered document assistant with a Spring Boot API, Spring AI, PostgreSQL/pgvector, and a React/Vite frontend.

## Architecture

- `POST /api/documents` accepts one PDF or TXT file, extracts its text in memory, splits it with Spring AI's `TokenTextSplitter`, and stores chunks and metadata in the pgvector store.
- `POST /api/chat` retrieves up to five relevant chunks, asks the configured chat model to answer only from those excerpts, and returns source references with the answer.
- The application does not retain uploaded files or expose them through a public path; the multipart runtime may use private temporary storage while processing a request. Extracted text, metadata, and embeddings are stored through the configured vector store.
- PDF files containing only scanned images are not OCR-processed.
- Upload processing is limited to 10 MiB per file, 100 PDF pages, 5 MiB of extracted text, 100 indexed chunks per document, and two concurrent uploads per application instance.

The backend uses Java 17, Spring Boot 3.4.4, and Spring AI 1.0.0. The frontend is React 19 with Vite.

## Backend configuration

Install Java 17 or newer, Maven, PostgreSQL with the pgvector extension, and configure the environment variables below. The root `.env.example` contains placeholders only; Spring Boot does not load that file automatically, so set the variables in your shell, IDE, or deployment environment.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/enterprise_rag` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | empty | Database password |
| `AI_PROVIDER` | `openai` | Chat provider (`openai` or `ollama`) |
| `AI_EMBEDDING_PROVIDER` | `none` | Set to `openai` to enable embeddings |
| `OPENAI_API_KEY` | empty | Required for OpenAI chat and embeddings |
| `OPENAI_MODEL` | `gpt-4o-mini` | OpenAI chat model |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `OPENAI_EMBEDDING_DIMENSIONS` | `1536` | Embedding output dimensions |
| `PGVECTOR_DIMENSIONS` | `1536` | Vector column dimensions; must match the embedding model |
| `PGVECTOR_SCHEMA_NAME` | `public` | pgvector schema used by Spring AI |
| `PGVECTOR_TABLE_NAME` | `vector_store` | pgvector table used by Spring AI |
| `PGVECTOR_INITIALIZE_SCHEMA` | `false` | Allow Spring AI to initialize the vector schema |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | Single permitted browser origin for `/api/**` |
| `RAG_RETRIEVAL_TOP_K` | `5` | Maximum retrieved chunks per chat request |
| `RAG_SIMILARITY_THRESHOLD` | `0.70` | Minimum Spring AI similarity score (0.0-1.0) |

Set a database password appropriate for your environment. Do not commit `.env` files or real credentials. The database role needs permission to use pgvector; if `PGVECTOR_INITIALIZE_SCHEMA=true`, it also needs permission to create the required vector schema objects. Otherwise provision the schema separately.

The current application has no user identity, authentication, authorization, or tenant filtering. All callers of the API share the same vector store: a caller who can reach the API can upload documents and ask questions that may retrieve any indexed document. CORS only controls browser cross-origin access; it does not authenticate callers. This project is for trusted local development and is not production-ready.

Before multi-user or public deployment, integrate the team's selected identity provider with Spring Security, require authenticated principals on upload and chat, store an owner/tenant identifier on every chunk, and scope similarity search to that identity. Migrate or explicitly quarantine existing unowned vectors before enabling access. Replacement and deletion must also be scoped to the authenticated owner; do not infer ownership from a filename.

To enable grounded chat and document ingestion, configure OpenAI embeddings as well as chat:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/enterprise_rag"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-database-password>"
$env:AI_EMBEDDING_PROVIDER = "openai"
$env:OPENAI_API_KEY = "<your-openai-api-key>"
mvn spring-boot:run
```

The Spring AI pgvector store uses cosine distance and defaults to 1536 dimensions. Keep `PGVECTOR_DIMENSIONS` consistent with the embedding model. The OpenAI Maven profile is active by default.

For Ollama chat with OpenAI embeddings, start Ollama and activate both model profiles:

```powershell
$env:AI_PROVIDER = "ollama"
$env:AI_EMBEDDING_PROVIDER = "openai"
mvn -Popenai,ollama spring-boot:run
```

PDF and TXT content is processed in memory; unsupported, empty, malformed, non-UTF-8, and control-character-containing TXT files are rejected. PDF processing rejects files above 100 pages and extraction above 5 MiB of text; documents producing more than 100 chunks are rejected, and at most two uploads are processed concurrently by one application instance. There is no hard parser time limit, so retain the API size/page/chunk limits and restrict network access.

Uploading the same filename and exact bytes again reuses stable document/chunk IDs and checks the vector table before embedding, avoiding repeat embedding work when the chunks are already indexed on the same application instance or in a later sequential request. Simultaneous identical uploads handled by separate application instances can still race and repeat embedding work. Uploads are versioned by filename and content: changed bytes receive a new document ID, and the previous version remains searchable. The API does not currently offer replacement or deletion because it has no authenticated owner identity with which to safely scope those operations. Add owner-scoped replacement only alongside the identity and retrieval-filtering work described above.

## API

### Upload a document

```http
POST /api/documents
Content-Type: multipart/form-data
```

The multipart form field must be named `file`:

```powershell
curl.exe -F "file=@.\handbook.pdf" http://localhost:8080/api/documents
```

Success response:

```json
{
  "documentId": "generated-uuid",
  "documentName": "handbook.pdf",
  "chunkCount": 8,
  "message": "Document uploaded and indexed successfully."
}
```

### Ask a question

The existing request contract is unchanged:

```http
POST /api/chat
Content-Type: application/json

{"message":"How many days of leave does the policy provide?"}
```

The `answer` field remains available to existing clients; `sources` is an additive response field:

```json
{
  "answer": "The policy provides 20 days of annual leave [1].",
  "sources": [
    {
      "documentId": "generated-uuid",
      "documentName": "handbook.pdf",
      "chunkIndex": 2
    }
  ]
}
```

When retrieval finds no sufficiently similar chunks, chat returns a not-found-in-indexed-documents response without asking the LLM to invent an answer. Database or embedding configuration failures are returned as service errors; AI provider failures are returned as gateway errors.

## Frontend

Configure `frontend/.env` from `frontend/.env.example` if the API is not served from the same origin. The default example points to `http://localhost:8080`; the backend permits the Vite origin `http://localhost:5173` by default. Change `CORS_ALLOWED_ORIGIN` when using a different browser origin.

```powershell
cd frontend
npm install
npm run dev
```

The chat UI displays source filenames and chunk numbers for retrieved answers and provides PDF/TXT upload feedback, including byte-transfer progress and an indexing state. The browser validates file extension, emptiness, and size; the backend performs authoritative validation.

## Tests

```powershell
mvn test
```

The backend tests cover ingestion metadata and retrieval thresholds, duplicate upload avoidance, PDF/TXT validation and extraction, PDF page and upload concurrency limits, upload responses, grounded prompt construction, citation validation, source references, and no-context/provider-error behavior.

```powershell
cd frontend
npm run lint
npm run build
```
