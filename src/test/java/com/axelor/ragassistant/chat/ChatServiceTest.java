package com.axelor.ragassistant.chat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.retry.TransientAiException;

import com.axelor.ragassistant.config.RagRetrievalProperties;
import com.axelor.ragassistant.document.DocumentIngestionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec callResponse = mock(ChatClient.CallResponseSpec.class);
    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);

    private ChatService service;

    @BeforeEach
    void setUp() {
        this.service = new ChatService(
                this.chatClient, this.ingestionService, new RagRetrievalProperties(5, 0.70));
        when(this.chatClient.prompt()).thenReturn(this.requestSpec);
        when(this.requestSpec.system(anyString())).thenReturn(this.requestSpec);
        when(this.requestSpec.user(anyString())).thenReturn(this.requestSpec);
        when(this.requestSpec.call()).thenReturn(this.callResponse);
    }

    @Test
    void retrievesContextAndReturnsReferencesWithTheGeneratedAnswer() {
        Document chunk = new Document(
                "chunk-1",
                "Employees receive 20 days of annual leave.",
                Map.of("document_id", "doc-1", "document_name", "leave-policy.pdf", "chunk_index", 2));
        when(this.ingestionService.similaritySearch("How much leave?", 5, 0.70)).thenReturn(List.of(chunk));
        when(this.callResponse.content()).thenReturn("The policy grants 20 days [1].");

        ChatResult result = this.service.chatWithContext("How much leave?");

        assertEquals("The policy grants 20 days [1].", result.answer());
        assertEquals(List.of(new SourceReference("doc-1", "leave-policy.pdf", 2)), result.sources());
        org.mockito.ArgumentCaptor<String> prompt =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(this.requestSpec).user(prompt.capture());
        assertTrue(prompt.getValue().contains("How much leave?"));
        assertTrue(prompt.getValue().contains("Employees receive 20 days of annual leave."));
        verify(this.requestSpec).system(org.mockito.ArgumentMatchers.contains("only the supplied document excerpts"));
    }

    @Test
    void returnsGroundedFallbackWithoutCallingTheLlmWhenNoContextIsFound() {
        when(this.ingestionService.similaritySearch("What is the policy?", 5, 0.70)).thenReturn(List.of());

        ChatResult result = this.service.chatWithContext("What is the policy?");

        assertTrue(result.answer().contains("couldn't find relevant information"));
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(this.chatClient);
    }

    @Test
    void doesNotHideRetrievalFailures() {
        when(this.ingestionService.similaritySearch("What is the policy?", 5, 0.70))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> this.service.chatWithContext("What is the policy?"));
        verifyNoInteractions(this.chatClient);
    }

    @Test
    void reportsLlmFailuresWithoutReturningAnUnverifiedAnswer() {
        when(this.ingestionService.similaritySearch("What is the policy?", 5, 0.70))
                .thenReturn(List.of(new Document("Relevant policy text.")));
        when(this.callResponse.content()).thenThrow(new TransientAiException("provider unavailable"));

        assertThrows(ChatGenerationException.class, () -> this.service.chatWithContext("What is the policy?"));
    }

    @Test
    void rejectsCitationsThatDoNotReferToRetrievedSources() {
        Document chunk = new Document("The policy grants 20 days.");
        when(this.ingestionService.similaritySearch("How much leave?", 5, 0.70)).thenReturn(List.of(chunk));
        when(this.callResponse.content()).thenReturn(
                "The policy grants 20 days [1], see [1, 1], not 40 [2] or [999999999999] "
                        + "or [source:fake].");

        assertThrows(ChatGenerationException.class, () -> this.service.chatWithContext("How much leave?"));
    }

    @Test
    void rejectsAnswersContainingOnlyInvalidCitations() {
        when(this.ingestionService.similaritySearch("Question?", 5, 0.70))
                .thenReturn(List.of(new Document("Relevant content.")));
        when(this.callResponse.content()).thenReturn("[0] [8]");

        assertThrows(ChatGenerationException.class, () -> this.service.chatWithContext("Question?"));
    }

    @Test
    void retrievalSettingsRejectInvalidRangesAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new RagRetrievalProperties(0, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RagRetrievalProperties(101, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RagRetrievalProperties(5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new RagRetrievalProperties(5, 1.01));
    }
}
