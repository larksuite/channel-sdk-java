// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize.converters.interactive;

import com.google.gson.JsonElement;
import com.lark.channel.model.ResourceDescriptor;
import com.lark.channel.normalize.ChannelMessageConverter;
import com.lark.channel.normalize.ConvertContext;
import com.lark.channel.normalize.ConvertResult;
import com.lark.channel.normalize.NormalizeJsons;
import com.lark.channel.normalize.NormalizeTexts;

import java.util.Collections;
import java.util.List;

public class InteractiveMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonElement parsed = NormalizeJsons.parseElement(rawContent);
        if (parsed == null || !parsed.isJsonObject()) {
            return new ConvertResult("[interactive card]", Collections.<ResourceDescriptor>emptyList());
        }
        List<String> output = CardWalker.collectVisibleTexts(parsed);
        return new ConvertResult(output.isEmpty() ? "[interactive card]" : NormalizeTexts.joinLines(output),
                Collections.<ResourceDescriptor>emptyList());
    }
}
