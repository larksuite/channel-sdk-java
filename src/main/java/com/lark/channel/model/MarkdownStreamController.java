// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.model;

/**
 * Controller exposed to Markdown streaming producers.
 */
public interface MarkdownStreamController {
    /**
     * Append one generated chunk to the current streaming message.
     */
    void append(String chunk);

    /**
     * Replace the full accumulated content. Useful when a model returns a
     * corrected full answer instead of incremental deltas.
     */
    void setContent(String full);

    /**
     * Message id of the streaming message once it has been created.
     */
    String getMessageId();
}
