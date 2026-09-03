// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.examples.webhook;

import com.lark.channel.ChannelEventHandler;
import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.NormalizedMessage;
import com.lark.oapi.event.EventDispatcher;

public final class WebhookBot {
    private WebhookBot() {
    }

    public static void main(String[] args) {
        LarkChannelOptions.WebhookOptions webhook = new LarkChannelOptions.WebhookOptions();
        webhook.setVerificationToken(requiredEnv("VERIFICATION_TOKEN"));
        webhook.setEncryptKey(requiredEnv("ENCRYPT_KEY"));

        LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(requiredEnv("APP_ID"), requiredEnv("APP_SECRET"))
                        .transport("webhook")
                        .webhook(webhook)
                        .build());

        channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                System.out.println("message received: " + message.getMessageId());
            }
        });

        EventDispatcher dispatcher = channel.createWebhookDispatcher();
        System.out.println("Attach this dispatcher to the host application's HTTP event endpoint: "
                + dispatcher.getClass().getName());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }
}
