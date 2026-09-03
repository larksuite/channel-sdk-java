// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.safety;

import com.lark.channel.model.RejectEvent;

public interface OnReject {
    void onReject(RejectEvent event);
}
