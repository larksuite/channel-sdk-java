// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize;

import com.lark.channel.model.ResourceDescriptor;

import java.util.Collections;
import java.util.List;

public class ConvertResult {
    private final String content;
    private final List<ResourceDescriptor> resources;

    public ConvertResult(String content, List<ResourceDescriptor> resources) {
        this.content = content == null ? "" : content;
        this.resources = resources == null ? Collections.<ResourceDescriptor>emptyList() : resources;
    }

    public String getContent() {
        return content;
    }

    public List<ResourceDescriptor> getResources() {
        return resources;
    }
}
