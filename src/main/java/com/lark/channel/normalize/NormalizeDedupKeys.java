// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize;

import com.lark.channel.model.CardActionEvent;
import com.lark.channel.model.CommentEvent;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.ReactionEvent;
import com.lark.oapi.core.utils.Jsons;

public final class NormalizeDedupKeys {
    private NormalizeDedupKeys() {
    }

    public static String cardAction(CardActionEvent event) {
        String valueJson = Jsons.DEFAULT.toJson(event.getActionValue());
        return "card:" + safe(event.getMessageId()) + ":" + safe(event.getOperatorId()) + ":" + safe(event.getActionTag())
                + ":" + safe(event.getActionName()) + ":" + safe(event.getActionOption()) + ":" + truncate(safe(valueJson), 128);
    }

    public static String reaction(ReactionEvent event) {
        return "rx:" + safe(event.getMessageId()) + ":" + safe(event.getOperatorId()) + ":" + safe(event.getEmojiType())
                + ":" + safe(event.getAction()) + ":" + event.getActionTime();
    }

    public static String message(NormalizedMessage event) {
        return "message:" + safe(event.getMessageId());
    }

    public static String comment(CommentEvent event) {
        return "comment:" + safe(event.getFileToken()) + ":" + safe(event.getCommentId());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
