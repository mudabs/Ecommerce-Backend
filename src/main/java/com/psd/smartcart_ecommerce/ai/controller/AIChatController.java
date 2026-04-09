package com.psd.smartcart_ecommerce.ai.controller;

import com.psd.smartcart_ecommerce.ai.agent.SmartCartAgent;
import com.psd.smartcart_ecommerce.ai.dto.ChatRequest;
import com.psd.smartcart_ecommerce.ai.dto.ChatResponse;
import com.psd.smartcart_ecommerce.ai.memory.ConversationMemory;
import com.psd.smartcart_ecommerce.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AIChatController {

    @Autowired
    private SmartCartAgent agent;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ConversationMemory conversationMemory;

    @PostMapping("/ai/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = authUtil.loggedInUserId();
        ChatResponse response = agent.processMessage(userId, request.getMessage());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/ai/chat/history")
    public ResponseEntity<Void> clearHistory() {
        Long userId = authUtil.loggedInUserId();
        conversationMemory.clear(userId);
        return ResponseEntity.noContent().build();
    }
}
