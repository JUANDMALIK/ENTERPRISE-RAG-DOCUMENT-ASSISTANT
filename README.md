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
