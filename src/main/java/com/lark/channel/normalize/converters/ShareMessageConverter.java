// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize.converters;

import com.google.gson.JsonObject;
import com.lark.channel.model.ResourceDescriptor;
import com.lark.channel.normalize.ChannelMessageConverter;
import com.lark.channel.normalize.ConvertContext;
import com.lark.channel.normalize.ConvertResult;
import com.lark.channel.normalize.NormalizeJsons;

import java.util.Collections;

public class ShareMessageConverter implements ChannelMessageConverter {
    private final String messageType;

    public ShareMessageConverter(String messageType) {
        this.messageType = messageType;
    }

    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        if ("share_user".equals(messageType)) {
            return new ConvertResult(
                    "<contact_card id=\"" + safe(NormalizeJsons.optString(parsed, "user_id")) + "\"/>",
                    Collections.<ResourceDescriptor>emptyList());
        }
        return new ConvertResult(
                "<group_card id=\"" + safe(NormalizeJsons.optString(parsed, "chat_id")) + "\"/>",
                Collections.<ResourceDescriptor>emptyList());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
