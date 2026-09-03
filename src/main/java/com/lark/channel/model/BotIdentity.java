// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.model;

public class BotIdentity {
    private final String openId;
    private final String name;

    public BotIdentity(String openId, String name) {
        this.openId = openId;
        this.name = name;
    }

    public String getOpenId() {
        return openId;
    }

    public String getName() {
        return name;
    }
}
