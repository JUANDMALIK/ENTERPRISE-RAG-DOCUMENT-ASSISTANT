package com.axelor.ragassistant.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final int MAX_SEARCH_RESULTS = 100;
    private static final String EMBEDDING_CONFIGURATION_MESSAGE =
            "Vector operations require the OpenAI embedding model. Enable the OpenAI Maven profile, set "
                    + "AI_EMBEDDING_PROVIDER=openai, and configure OPENAI_API_KEY.";

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withMinChunkLengthToEmbed(1)
            .build();

    public DocumentIngestionService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int ingest(String documentName, String content) {
        String normalizedName = requireText(documentName, "Document name");
        String documentContent = requireText(content, "Document content");
        List<Document> splitDocuments = this.textSplitter.split(
                new Document(documentContent, Map.of("document_name", normalizedName)));

        if (splitDocuments.isEmpty()) {
            throw new IllegalArgumentException("Document content did not produce any indexable chunks.");
        }

        VectorStore vectorStore = requireVectorStore();
        validateDatabaseConnection();

        List<Document> chunks = new ArrayList<>(splitDocuments.size());
        for (int index = 0; index < splitDocuments.size(); index++) {
            Document splitDocument = splitDocuments.get(index);
            Map<String, Object> metadata = new HashMap<>(splitDocument.getMetadata());
            metadata.put("document_name", normalizedName);
            metadata.put("chunk_index", index);
            metadata.put("chunk_count", splitDocuments.size());
            chunks.add(new Document(splitDocument.getText(), metadata));
        }

        vectorStore.add(chunks);
        return chunks.size();
    }

    public List<Document> similaritySearch(String query, int topK) {
        String normalizedQuery = requireText(query, "Search query");
        if (topK < 1 || topK > MAX_SEARCH_RESULTS) {
            throw new IllegalArgumentException(
                    "Search result count must be between 1 and " + MAX_SEARCH_RESULTS + ".");
        }

        VectorStore vectorStore = requireVectorStore();
        validateDatabaseConnection();
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(normalizedQuery).topK(topK).build());
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
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value.trim();
    }
}
