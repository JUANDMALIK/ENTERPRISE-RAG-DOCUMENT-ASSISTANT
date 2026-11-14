package com.axelor.ragassistant.document;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    private final ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        this.service = new DocumentIngestionService(this.vectorStoreProvider, this.jdbcTemplate);
        when(this.vectorStoreProvider.getIfAvailable()).thenReturn(this.vectorStore);
        when(this.jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    }

    @Test
    void splitsContentAndPreservesDocumentAndChunkMetadata() {
        AtomicReference<List<Document>> storedChunks = new AtomicReference<>();
        doAnswer(invocation -> {
            storedChunks.set(invocation.getArgument(0));
            return null;
        }).when(this.vectorStore).add(anyList());

        String content = String.join(" ", java.util.Collections.nCopies(2_500, "policy"));
        int chunkCount = this.service.ingest("handbook.pdf", content);
        List<Document> chunks = storedChunks.get();

        assertTrue(chunkCount > 1);
        assertEquals(chunkCount, chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            assertEquals("handbook.pdf", chunks.get(index).getMetadata().get("document_name"));
            assertEquals(index, chunks.get(index).getMetadata().get("chunk_index"));
            assertEquals(chunkCount, chunks.get(index).getMetadata().get("chunk_count"));
        }
        verify(this.vectorStore).add(anyList());
        verify(this.jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void rejectsBlankDocumentInputsBeforeAccessingServices() {
        assertThrows(IllegalArgumentException.class, () -> this.service.ingest(" ", "some text"));
        assertThrows(IllegalArgumentException.class, () -> this.service.ingest("guide.txt", "  "));

        verifyNoInteractions(this.vectorStoreProvider, this.vectorStore, this.jdbcTemplate);
    }

    @Test
    void delegatesSimilaritySearchAfterValidatingTheDatabase() {
        List<Document> expected = List.of(new Document("Relevant content"));
        when(this.vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expected);

        List<Document> results = this.service.similaritySearch("benefits", 5);

        assertSame(expected, results);
        verify(this.vectorStore).similaritySearch(any(SearchRequest.class));
        verify(this.jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void rejectsOutOfRangeSearchResultCountsBeforeAccessingServices() {
        assertThrows(IllegalArgumentException.class, () -> this.service.similaritySearch("benefits", 0));
        assertThrows(IllegalArgumentException.class, () -> this.service.similaritySearch("benefits", 101));

        verifyNoInteractions(this.vectorStoreProvider, this.vectorStore, this.jdbcTemplate);
    }

    @Test
    void explainsMissingEmbeddingConfiguration() {
        when(this.vectorStoreProvider.getIfAvailable()).thenReturn(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> this.service.similaritySearch("benefits", 5));

        assertTrue(exception.getMessage().contains("AI_EMBEDDING_PROVIDER=openai"));
        verify(this.jdbcTemplate, never()).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void reportsDatabaseConnectionFailuresWithoutCallingTheVectorStore() {
        when(this.jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> this.service.ingest("handbook.pdf", "Some document content."));

        assertTrue(exception.getMessage().contains("Verify DB_URL, DB_USERNAME, and DB_PASSWORD"));
        verify(this.vectorStore, never()).add(anyList());
    }
}
