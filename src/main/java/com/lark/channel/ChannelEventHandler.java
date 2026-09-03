// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel;

public interface ChannelEventHandler<T> {
    void handle(T event);
}
