// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize.converters;

import com.google.gson.JsonObject;
import com.lark.channel.model.ResourceDescriptor;
import com.lark.channel.normalize.ChannelMessageConverter;
import com.lark.channel.normalize.ConvertContext;
import com.lark.channel.normalize.ConvertResult;
import com.lark.channel.normalize.NormalizeJsons;
import com.lark.channel.normalize.NormalizeTexts;

import java.util.Collections;

public class FolderMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        String fileKey = NormalizeJsons.optString(parsed, "file_key");
        if (fileKey == null || fileKey.isEmpty()) {
            return new ConvertResult("[folder]", Collections.<ResourceDescriptor>emptyList());
        }
        String fileName = NormalizeJsons.optString(parsed, "file_name");
        String nameAttr = fileName == null || fileName.isEmpty() ? "" : " name=\"" + NormalizeTexts.escapeAttr(fileName) + "\"";
        return new ConvertResult("<folder key=\"" + fileKey + "\"" + nameAttr + "/>", Collections.<ResourceDescriptor>emptyList());
    }
}
