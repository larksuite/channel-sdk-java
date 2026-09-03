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

public class StickerMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        String fileKey = NormalizeJsons.optString(parsed, "file_key");
        if (fileKey == null || fileKey.isEmpty()) {
            return new ConvertResult("[sticker]", Collections.<ResourceDescriptor>emptyList());
        }
        return new ConvertResult("<sticker key=\"" + fileKey + "\"/>",
                Collections.singletonList(new ResourceDescriptor("sticker", fileKey, null, null)));
    }
}
