package com.axelor.ragassistant.document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.axelor.ragassistant.config.RagRetrievalProperties;
import com.axelor.ragassistant.exception.RequestValidationException;

@Service
public class DocumentIngestionService {

    public static final int MAX_DOCUMENT_CHUNKS = 100;
    private static final String EMBEDDING_CONFIGURATION_MESSAGE =
            "Vector operations require the OpenAI embedding model. Enable the OpenAI Maven profile, set "
                    + "AI_EMBEDDING_PROVIDER=openai, and configure OPENAI_API_KEY.";

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;
    private final String vectorTable;
    private final String vectorSchema;
    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withMinChunkLengthToEmbed(1)
            .build();

    @Autowired
    public DocumentIngestionService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String vectorSchema,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String vectorTable) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorSchema = requireSqlIdentifier(vectorSchema, "pgvector schema");
        this.vectorTable = requireSqlIdentifier(vectorTable, "pgvector table");
    }

    public DocumentIngestionService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate) {
        this(vectorStoreProvider, jdbcTemplate, "public", "vector_store");
    }

    public int ingest(String documentName, String content) {
        return ingest(UUID.randomUUID(), documentName, content, null);
    }

    public int ingest(UUID documentId, String documentName, String content, String contentSha256) {
        if (documentId == null) {
            throw new RequestValidationException("Document ID must not be null.");
        }
        String normalizedName = requireText(documentName, "Document name");
        String documentContent = requireText(content, "Document content");
        List<Document> splitDocuments = this.textSplitter.split(
                new Document(documentContent, Map.of("document_name", normalizedName)));

        if (splitDocuments.isEmpty()) {
            throw new RequestValidationException("Document content did not produce any indexable chunks.");
        }
        if (splitDocuments.size() > MAX_DOCUMENT_CHUNKS) {
            throw new RequestValidationException(
                    "A document must produce no more than " + MAX_DOCUMENT_CHUNKS + " chunks.");
        }

        VectorStore vectorStore = requireVectorStore();
        validateDatabaseConnection();

        List<Document> chunks = new ArrayList<>(splitDocuments.size());
        for (int index = 0; index < splitDocuments.size(); index++) {
            Document splitDocument = splitDocuments.get(index);
            Map<String, Object> metadata = new HashMap<>(splitDocument.getMetadata());
            metadata.put("document_name", normalizedName);
            metadata.put("document_id", documentId.toString());
            metadata.put("chunk_index", index);
            metadata.put("chunk_count", splitDocuments.size());
            if (contentSha256 != null) {
                metadata.put("content_sha256", contentSha256);
            }
            String chunkId = UUID.nameUUIDFromBytes(
                    (documentId + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
            chunks.add(new Document(chunkId, splitDocument.getText(), metadata));
        }

        vectorStore.add(chunks);
        return chunks.size();
    }

    public ExistingDocumentStatus existingDocumentStatus(UUID documentId, String contentSha256) {
        if (documentId == null || contentSha256 == null || contentSha256.isBlank()) {
            throw new RequestValidationException("Document ID and content hash must not be blank.");
        }
        validateDatabaseConnection();
        String query = "SELECT COUNT(*) AS stored_count, "
                + "COALESCE(MAX((metadata ->> 'chunk_count')::integer), 0) AS expected_count "
                + "FROM \"" + this.vectorSchema + "\".\"" + this.vectorTable
                + "\" WHERE metadata ->> 'document_id' = ? AND metadata ->> 'content_sha256' = ?";
        return this.jdbcTemplate.queryForObject(
                query,
                (resultSet, rowNumber) -> new ExistingDocumentStatus(
                        resultSet.getInt("stored_count"),
                        resultSet.getInt("expected_count")),
                documentId.toString(),
                contentSha256);
    }

    public record ExistingDocumentStatus(int storedChunkCount, int expectedChunkCount) {

        public boolean isComplete() {
            return this.expectedChunkCount > 0 && this.storedChunkCount == this.expectedChunkCount;
        }
    }

    public List<Document> similaritySearch(String query, int topK) {
        return similaritySearch(query, topK, null);
    }

    public List<Document> similaritySearch(String query, int topK, double similarityThreshold) {
        return similaritySearch(query, topK, Double.valueOf(similarityThreshold));
    }

    private List<Document> similaritySearch(String query, int topK, Double similarityThreshold) {
        String normalizedQuery = requireText(query, "Search query");
        if (topK < 1 || topK > RagRetrievalProperties.MAX_TOP_K) {
            throw new RequestValidationException(
                    "Search result count must be between 1 and " + RagRetrievalProperties.MAX_TOP_K + ".");
        }
        if (similarityThreshold != null
                && (similarityThreshold < 0.0 || similarityThreshold > 1.0
                        || !Double.isFinite(similarityThreshold))) {
            throw new RequestValidationException("Similarity threshold must be between 0.0 and 1.0.");
        }

        VectorStore vectorStore = requireVectorStore();
        validateDatabaseConnection();
        SearchRequest.Builder request = SearchRequest.builder().query(normalizedQuery).topK(topK);
        if (similarityThreshold != null) {
            request.similarityThreshold(similarityThreshold);
        }
        return vectorStore.similaritySearch(request.build());
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException(EMBEDDING_CONFIGURATION_MESSAGE);
        }
        return vectorStore;
    }

    private void validateDatabaseConnection() {
        try {
            Integer result = this.jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (!Integer.valueOf(1).equals(result)) {
                throw new IllegalStateException("PostgreSQL connection validation returned an unexpected result.");
            }
        }
        catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Unable to connect to PostgreSQL. Verify DB_URL, DB_USERNAME, and DB_PASSWORD.",
                    exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new RequestValidationException(fieldName + " must not be blank.");
        }
        return value.trim();
    }

    private static String requireSqlIdentifier(String identifier, String fieldName) {
        if (identifier == null || !identifier.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("The configured " + fieldName + " name is invalid.");
        }
        return identifier;
    }
}
