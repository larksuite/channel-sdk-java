// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.outbound;

import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.exception.LarkChannelException;

public final class OutboundRetry {
    private OutboundRetry() {
    }

    public static <T> T retry(RetryableOperation<T> operation, LarkChannelOptions.RetryConfig config) {
        int maxAttempts = config == null ? 3 : config.getMaxAttempts();
        long baseDelayMs = config == null ? 500L : config.getBaseDelayMs();
        LarkChannelException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.run(attempt);
            } catch (Exception error) {
                last = OutboundErrors.classifyError(error);
                if (attempt >= maxAttempts || !OutboundErrors.isRetryable(last)) {
                    throw last;
                }
                sleep(baseDelayMs * (long) Math.pow(3, attempt - 1));
            }
        }
        throw last == null ? new IllegalStateException("retry finished without result") : last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public interface RetryableOperation<T> {
        T run(int attempt) throws Exception;
    }
}
