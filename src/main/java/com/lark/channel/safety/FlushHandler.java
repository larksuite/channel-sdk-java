// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.safety;

interface FlushHandler {
    void flush(BatchedDispatch batch);
}
