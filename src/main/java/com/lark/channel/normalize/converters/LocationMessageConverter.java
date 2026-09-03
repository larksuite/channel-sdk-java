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

public class LocationMessageConverter implements ChannelMessageConverter {
    @Override
    public ConvertResult convert(String rawContent, ConvertContext context) {
        JsonObject parsed = NormalizeJsons.parseObject(rawContent);
        String name = NormalizeJsons.optString(parsed, "name");
        String lat = NormalizeJsons.optString(parsed, "latitude");
        String lng = NormalizeJsons.optString(parsed, "longitude");
        String nameAttr = name == null || name.isEmpty() ? "" : " name=\"" + NormalizeTexts.escapeAttr(name) + "\"";
        String coordsAttr = lat != null && !lat.isEmpty() && lng != null && !lng.isEmpty() ? " coords=\"lat:" + lat + ",lng:" + lng + "\"" : "";
        return new ConvertResult("<location" + nameAttr + coordsAttr + "/>", Collections.<ResourceDescriptor>emptyList());
    }
}
