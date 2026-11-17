package com.axelor.ragassistant.chat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    @WithMockUser
    void returnsTheGeneratedAnswerAndSourceReferences() throws Exception {
        when(chatService.chatWithContext("Hello")).thenReturn(new ChatResult(
                "Hello there",
                List.of(new SourceReference("doc-1", "guide.txt", 0))));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello there"))
                .andExpect(jsonPath("$.sources[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.sources[0].documentName").value("guide.txt"))
                .andExpect(jsonPath("$.sources[0].chunkIndex").value(0));

        verify(chatService).chatWithContext("Hello");
    }

    @Test
    @WithMockUser
    void rejectsBlankMessages() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @WithMockUser
    void reportsRetrievalAndProviderFailuresWithServiceErrors() throws Exception {
        when(chatService.chatWithContext("Hello"))
                .thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Document storage or retrieval is unavailable. Verify PostgreSQL, pgvector, and embedding configuration."));

        doThrow(new ChatGenerationException("provider unavailable", null))
                .when(chatService).chatWithContext("Hello");

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "The AI provider could not generate a response. Please try again."));
    }
}
