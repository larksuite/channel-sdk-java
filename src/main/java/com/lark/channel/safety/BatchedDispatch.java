// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.safety;

import com.lark.channel.model.NormalizedMessage;

import java.util.List;

class BatchedDispatch {
    private final NormalizedMessage message;
    private final List<String> sourceIds;

    BatchedDispatch(NormalizedMessage message, List<String> sourceIds) {
        this.message = message;
        this.sourceIds = sourceIds;
    }

    NormalizedMessage getMessage() {
        return message;
    }

    List<String> getSourceIds() {
        return sourceIds;
    }
}
