package com.axelor.ragassistant.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void returnsTheGeneratedAnswer() throws Exception {
        when(chatService.chat("Hello")).thenReturn("Hello there");

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello there"));

        verify(chatService).chat("Hello");
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
}
