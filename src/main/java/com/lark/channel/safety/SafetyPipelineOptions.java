// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.safety;

import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.BotIdentity;
import com.lark.oapi.core.cache.ICache;

public class SafetyPipelineOptions {
    private final LarkChannelOptions.SafetyConfig config;
    private final LarkChannelOptions.PolicyConfig policy;
    private final ICache cache;
    private final BotIdentity botIdentity;
    private final OnReject onReject;
    private final OnMessageDispatch onMessage;

    public SafetyPipelineOptions(LarkChannelOptions.SafetyConfig config,
                                 LarkChannelOptions.PolicyConfig policy,
                                 ICache cache,
                                 OnReject onReject,
                                 OnMessageDispatch onMessage) {
        this(config, policy, cache, null, onReject, onMessage);
    }

    public SafetyPipelineOptions(LarkChannelOptions.SafetyConfig config,
                                 LarkChannelOptions.PolicyConfig policy,
                                 ICache cache,
                                 BotIdentity botIdentity,
                                 OnReject onReject,
                                 OnMessageDispatch onMessage) {
        this.config = config == null ? new LarkChannelOptions.SafetyConfig() : config;
        this.policy = policy;
        this.cache = cache;
        this.botIdentity = botIdentity;
        this.onReject = onReject;
        this.onMessage = onMessage;
    }

    LarkChannelOptions.SafetyConfig getConfig() {
        return config;
    }

    LarkChannelOptions.PolicyConfig getPolicy() {
        return policy;
    }

    ICache getCache() {
        return cache;
    }

    BotIdentity getBotIdentity() {
        return botIdentity;
    }

    OnReject getOnReject() {
        return onReject;
    }

    OnMessageDispatch getOnMessage() {
        return onMessage;
    }
}
