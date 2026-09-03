// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize.converters;

import com.lark.channel.model.ResourceDescriptor;
import com.lark.channel.normalize.ChannelMessageConverter;
import com.lark.channel.normalize.ConvertContext;
import com.lark.channel.normalize.ConvertResult;
import com.lark.channel.normalize.NormalizeJsons;

import java.util.Collections;

public class FallbackMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        String text = NormalizeJsons.optString(NormalizeJsons.parseObject(rawContent), "text");
        return new ConvertResult(text != null ? text : "[unsupported message]", Collections.<ResourceDescriptor>emptyList());
    }
}
