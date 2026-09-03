// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.examples.streaming;

import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.MarkdownStreamController;
import com.lark.channel.model.MarkdownStreamProducer;
import com.lark.channel.model.StreamInput;

public final class StreamingBot {
    private StreamingBot() {
    }

    public static void main(String[] args) throws Exception {
        LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(requiredEnv("APP_ID"), requiredEnv("APP_SECRET"))
                        .build());

        channel.connectSync();
        try {
            channel.streamSync(requiredEnv("CHANNEL_CHAT_ID"), StreamInput.markdown(
                    new MarkdownStreamProducer() {
                        @Override
                        public void produce(MarkdownStreamController controller) throws Exception {
                            controller.append("Thinking...\n\n");
                            controller.append("This is a streaming response.\n\n");
                            controller.append("Done.");
                        }
                    }));
        } finally {
            channel.disconnectSync();
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }
}
