// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.examples.echo;

import com.lark.channel.ChannelEventHandler;
import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.SendInput;
import com.lark.channel.model.SendOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EchoBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(EchoBot.class);

    private EchoBot() {
    }

    public static void main(String[] args) throws Exception {
        final LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(requiredEnv("APP_ID"), requiredEnv("APP_SECRET"))
                        .transport("websocket")
                        .build());

        channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                channel.send(
                        message.getChatId(),
                        SendInput.text("Received: " + message.getContent()),
                        SendOptions.newBuilder().replyTo(message.getMessageId()).build())
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                LOGGER.error("Failed to send echo reply", error);
                            }
                        });
            }
        });

        channel.start();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }
}
