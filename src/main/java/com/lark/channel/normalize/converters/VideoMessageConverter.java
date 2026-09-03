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

public class VideoMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        String fileKey = NormalizeJsons.optString(parsed, "file_key");
        if (fileKey == null || fileKey.isEmpty()) {
            return new ConvertResult("[video]", Collections.<ResourceDescriptor>emptyList());
        }
        String fileName = NormalizeJsons.optString(parsed, "file_name");
        Long duration = NormalizeTexts.parseLongObject(NormalizeJsons.optString(parsed, "duration"));
        String coverImageKey = NormalizeJsons.optString(parsed, "image_key");
        String nameAttr = fileName == null || fileName.isEmpty() ? "" : " name=\"" + NormalizeTexts.escapeAttr(fileName) + "\"";
        String durationValue = NormalizeTexts.formatDuration(duration);
        String durationAttr = durationValue == null ? "" : " duration=\"" + durationValue + "\"";
        return new ConvertResult("<video key=\"" + fileKey + "\"" + nameAttr + durationAttr + "/>",
                Collections.singletonList(new ResourceDescriptor("video", fileKey, fileName, duration, coverImageKey)));
    }
}
