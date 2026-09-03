// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.outbound;

public class RawSendArgs {
    public final String to;
    public final OutboundRouting.ReceiveIdType idType;
    public final String msgType;
    public final Object content;
    public final String replyTo;
    public final Boolean replyInThread;

    public RawSendArgs(String to, OutboundRouting.ReceiveIdType idType, String msgType, Object content,
                       String replyTo, Boolean replyInThread) {
        this.to = to;
        this.idType = idType;
        this.msgType = msgType;
        this.content = content;
        this.replyTo = replyTo;
        this.replyInThread = replyInThread;
    }
}
