// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.normalize;

import com.lark.channel.model.BotAddedEvent;
import com.lark.channel.model.CardActionEvent;
import com.lark.channel.model.CommentEvent;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.ReactionEvent;
import com.lark.oapi.service.im.v1.model.P2ChatMemberBotAddedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionDeletedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;

/**
 * Facade for channel normalization.
 * <p>
 * Responsibilities:
 * - normalizeMessage delegates to the message pipeline
 * - normalizeCardAction / normalizeReaction / normalizeBotAdded / normalizeComment stay as event normalizers
 * - build*DedupKey delegates to the dedicated dedup helper
 */
public class ChannelNormalizer {
    private final MessageNormalizer messageNormalizer = new MessageNormalizer();
    private final CardActionNormalizer cardActionNormalizer = new CardActionNormalizer();
    private final ReactionNormalizer reactionNormalizer = new ReactionNormalizer();
    private final BotAddedNormalizer botAddedNormalizer = new BotAddedNormalizer();
    private final CommentNormalizer commentNormalizer = new CommentNormalizer();

    public NormalizedMessage normalizeMessage(P2MessageReceiveV1 event, NormalizeOptions options) {
        return messageNormalizer.normalize(event, options);
    }

    public ReactionEvent normalizeReaction(P2MessageReactionCreatedV1 event, String action) {
        return reactionNormalizer.normalize(event, action);
    }

    public ReactionEvent normalizeReaction(P2MessageReactionDeletedV1 event, String action) {
        return reactionNormalizer.normalize(event, action);
    }

    public CardActionEvent normalizeCardAction(com.lark.oapi.event.cardcallback.model.P2CardActionTrigger event) {
        return cardActionNormalizer.normalize(event);
    }

    public BotAddedEvent normalizeBotAdded(P2ChatMemberBotAddedV1 event) {
        return botAddedNormalizer.normalize(event);
    }

    public CommentEvent normalizeComment(com.google.gson.JsonObject payload, Object raw) {
        return commentNormalizer.normalize(payload, raw);
    }

    public String buildCardActionDedupKey(CardActionEvent event) {
        return NormalizeDedupKeys.cardAction(event);
    }

    public String buildReactionDedupKey(ReactionEvent event) {
        return NormalizeDedupKeys.reaction(event);
    }

    public String buildMessageDedupKey(NormalizedMessage event) {
        return NormalizeDedupKeys.message(event);
    }

    public String buildCommentDedupKey(CommentEvent event) {
        return NormalizeDedupKeys.comment(event);
    }
}
