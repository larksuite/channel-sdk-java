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

public class ImageMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        String imageKey = NormalizeJsons.optString(parsed, "image_key");
        if (imageKey == null || imageKey.isEmpty()) {
            return new ConvertResult("[image]", Collections.<ResourceDescriptor>emptyList());
        }
        return new ConvertResult("![image](" + imageKey + ")",
                Collections.singletonList(new ResourceDescriptor("image", imageKey, null, null)));
    }
}
