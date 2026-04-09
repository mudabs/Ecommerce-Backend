package com.psd.smartcart_ecommerce.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserChatMemoryProvider implements ChatMemoryProvider {

    private static final int MAX_MESSAGES = 10;

    private final ConcurrentHashMap<Long, ChatMemory> memories = new ConcurrentHashMap<>();

    @Override
    public ChatMemory get(Object memoryId) {
        Long userId = ((Number) memoryId).longValue();
        return memories.computeIfAbsent(userId,
                id -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES));
    }

    public void clear(Long userId) {
        ChatMemory removed = memories.remove(userId);
        if (removed != null) {
            removed.clear();
        }
    }
}
