// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize;

public interface ChannelMessageConverter {
    ConvertResult convert(String rawContent, ConvertContext context);
}
