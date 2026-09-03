// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.examples.raw;

import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.oapi.core.response.RawResponse;
import com.lark.oapi.core.token.AccessTokenType;

import java.nio.charset.StandardCharsets;

public final class RawClient {
    private RawClient() {
    }

    public static void main(String[] args) throws Exception {
        LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(requiredEnv("APP_ID"), requiredEnv("APP_SECRET"))
                        .build());

        channel.connectSync();
        try {
            RawResponse response = channel.getRawClient().get(
                    "/open-apis/bot/v3/info",
                    null,
                    AccessTokenType.Tenant);
            System.out.println(new String(response.getBody(), StandardCharsets.UTF_8));
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
