package com.psd.smartcart_ecommerce.ai.memory;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory short-term conversation history.
 * Stores last N messages per user for multi-turn context.
 */
@Component
public class ConversationMemory {

    private static final int MAX_MESSAGES = 10;

    private final Map<Long, LinkedList<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public void addMessage(Long userId, String role, String content) {
        conversations.computeIfAbsent(userId, k -> new LinkedList<>());
        LinkedList<Map<String, String>> history = conversations.get(userId);

        history.addLast(Map.of("role", role, "content", content));

        while (history.size() > MAX_MESSAGES) {
            history.removeFirst();
        }
    }

    public List<Map<String, String>> getHistory(Long userId) {
        return conversations.getOrDefault(userId, new LinkedList<>());
    }

    public void clear(Long userId) {
        conversations.remove(userId);
    }
}
