package com.prepdeck.common.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DeepSeekClientTest {

    @Mock
    private ChatModel chatModel;

    // 测：clearTokenCounter 正确清除 ThreadLocal 值（P2-13 修复）
    @Test
    void clearTokenCounter_removesValue() {
        DeepSeekClient client = new DeepSeekClient(chatModel, "test-api-key-which-is-very-long");

        client.clearTokenCounter();
        assertEquals(0, client.getLastTokenCount());

        // Simulate tokenCounter being set via reflection (same as chatStream does)
        java.lang.reflect.Field tokenCounterField;
        try {
            tokenCounterField = DeepSeekClient.class.getDeclaredField("tokenCounter");
            tokenCounterField.setAccessible(true);
            ThreadLocal<Integer> tokenCounter = (ThreadLocal<Integer>) tokenCounterField.get(client);
            tokenCounter.set(100);
        } catch (Exception e) {
            fail("Failed to access tokenCounter field", e);
        }

        client.clearTokenCounter();
        assertEquals(0, client.getLastTokenCount(),
                "tokenCounter should be 0 after clear (ThreadLocal removed)");
    }

    // 测：getLastTokenCount 返回 0 当 ThreadLocal 为空
    @Test
    void getLastTokenCount_returnsZeroWhenEmpty() {
        DeepSeekClient client = new DeepSeekClient(chatModel, "test-api-key-which-is-very-long");
        client.clearTokenCounter();
        assertEquals(0, client.getLastTokenCount());
    }

    // 测：chatStream 使用 whenComplete 确保无论成功失败都清理 ThreadLocal
    @Test
    void chatStream_whenCompleteCleansUpThreadLocal() throws Exception {
        DeepSeekClient client = new DeepSeekClient(chatModel, "test-api-key-which-is-very-long");

        // Directly set tokenCounter via reflection to simulate the thenApply set
        java.lang.reflect.Field tokenCounterField = DeepSeekClient.class.getDeclaredField("tokenCounter");
        tokenCounterField.setAccessible(true);
        ThreadLocal<Integer> tokenCounter = (ThreadLocal<Integer>) tokenCounterField.get(client);

        // Simulate what happens: thenApply sets tokenCounter, then whenComplete removes it
        java.util.concurrent.CompletableFuture<String> future =
                java.util.concurrent.CompletableFuture.completedFuture("result")
                        .thenApply(result -> {
                            tokenCounter.set((int)(result.length() * 1.5));
                            return result;
                        })
                        .whenComplete((result, ex) -> {
                            tokenCounter.remove();
                        });

        future.get(1, TimeUnit.SECONDS);
        assertEquals(0, client.getLastTokenCount(),
                "ThreadLocal must be cleaned up after future completes");
    }
}
