// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.model;

public class BotAddedEvent {
    private final String chatId;
    private final String operatorId;
    private final String botName;
    private final Object raw;

    public BotAddedEvent(String chatId, String operatorId, String botName, Object raw) {
        this.chatId = chatId;
        this.operatorId = operatorId;
        this.botName = botName;
        this.raw = raw;
    }

    public String getChatId() {
        return chatId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getBotName() {
        return botName;
    }

    public String getChatName() {
        return botName;
    }

    public Object getRaw() {
        return raw;
    }
}
