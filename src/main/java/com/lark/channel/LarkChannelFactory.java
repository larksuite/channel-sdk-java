// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel;

import com.lark.channel.config.LarkChannelOptions;

public final class LarkChannelFactory {
    private LarkChannelFactory() {
    }

    public static LarkChannel createLarkChannel(LarkChannelOptions options) {
        return new LarkChannel(options);
    }
}
