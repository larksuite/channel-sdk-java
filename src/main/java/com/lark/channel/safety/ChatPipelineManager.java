// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.safety;

import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.NormalizedMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ChatPipelineManager {
    private final LarkChannelOptions.BatchTextConfig config;
    private final ConcurrentHashMap<String, ChatPipeline> pipelines = new ConcurrentHashMap<>();

    ChatPipelineManager(LarkChannelOptions.BatchTextConfig config) {
        this.config = config == null ? new LarkChannelOptions.BatchTextConfig() : config;
    }

    void push(String scope, NormalizedMessage message, FlushHandler handler) {
        getOrCreate(scope, false).push(message, handler);
    }

    void run(String scope, Runnable task) {
        getOrCreate(scope, true).run(task);
    }

    void flushAll() {
        for (Map.Entry<String, ChatPipeline> entry : pipelines.entrySet()) {
            entry.getValue().flushNow();
        }
    }

    void dispose() {
        flushAll();
        for (Map.Entry<String, ChatPipeline> entry : pipelines.entrySet()) {
            entry.getValue().dispose();
        }
        pipelines.clear();
    }

    private ChatPipeline getOrCreate(String scope, boolean serialOnly) {
        String key = scope == null || scope.isEmpty() ? "__default__" : scope;
        ChatPipeline existing = pipelines.get(key);
        if (existing != null) {
            return existing;
        }
        ChatPipeline created = new ChatPipeline(config, serialOnly);
        ChatPipeline previous = pipelines.putIfAbsent(key, created);
        if (previous != null) {
            created.dispose();
            return previous;
        }
        return created;
    }
}
