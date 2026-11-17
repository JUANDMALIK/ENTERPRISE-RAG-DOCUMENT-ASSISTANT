package com.axelor.ragassistant.chat;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatResult result = this.chatService.chatWithContext(request.message());
        return new ChatResponse(result.answer(), result.sources());
    }

    public record ChatRequest(@NotBlank String message) {
    }

    public record ChatResponse(String answer, List<SourceReference> sources) {
    }
}
