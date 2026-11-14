# ENTERPRISE-RAG-DOCUMENT-ASSISTANT

An AI-powered Enterprise RAG Document Assistant built using Java, Spring Boot, Spring AI, PostgreSQL with pgvector, and React. It enables users to upload PDF documents, perform semantic searches, and get accurate, context-aware answers using Generative AI with document citations and structured responses.

## Backend starter

The backend is a Java 17 / Spring Boot 3.4 application. Spring AI's OpenAI and Ollama starters are available as Maven profiles, and `ChatClient` is exposed through `POST /api/chat`.

### Run with OpenAI

Set an OpenAI API key, then start the application:

```powershell
$env:OPENAI_API_KEY = "your-api-key"
mvn spring-boot:run
```

OpenAI is the default provider. Set `OPENAI_MODEL` to override the default `gpt-4o-mini` model.

### Run with Ollama

Start Ollama locally and pull a chat model, then select the provider:

```powershell
ollama pull llama3.2
$env:AI_PROVIDER = "ollama"
mvn -Pollama spring-boot:run
```

Set `OLLAMA_BASE_URL` or `OLLAMA_MODEL` to use a different Ollama endpoint or model. The defaults are `http://localhost:11434` and `llama3.2`.

### Configure PostgreSQL and pgvector

The backend includes PostgreSQL/pgvector support for storing and searching document embeddings. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for your PostgreSQL database; the local defaults target `jdbc:postgresql://localhost:5432/enterprise_rag` with the `postgres` user and password. The database must have the pgvector extension available.

Embeddings are disabled by default (`AI_EMBEDDING_PROVIDER=none`). To enable OpenAI embeddings, set `AI_EMBEDDING_PROVIDER=openai` and `OPENAI_API_KEY`. The default embedding model is `text-embedding-3-small` with 1536 dimensions; `OPENAI_EMBEDDING_MODEL` and `OPENAI_EMBEDDING_DIMENSIONS` can override these defaults. The pgvector store is configured for 1536 dimensions and cosine distance. Set `PGVECTOR_DIMENSIONS` if using a different embedding size, ensuring it matches the embedding model output.

Schema initialization is disabled by default. To let the application create its pgvector schema, set `PGVECTOR_INITIALIZE_SCHEMA=true`; the database user will need permission to create the required schema objects. Otherwise, provision the schema separately.

There is currently no HTTP endpoint for uploading or ingesting documents, so this configuration alone does not populate the vector store.

When using Ollama for chat with OpenAI embeddings, activate both Maven profiles so both model integrations are available:

```powershell
$env:AI_PROVIDER = "ollama"
$env:AI_EMBEDDING_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your-api-key"
mvn -Popenai,ollama spring-boot:run
```

### Chat API

```http
POST /api/chat
Content-Type: application/json

{"message":"Summarize the purpose of a RAG assistant."}
```

The response contains the generated answer:

```json
{"answer":"..."}
```
