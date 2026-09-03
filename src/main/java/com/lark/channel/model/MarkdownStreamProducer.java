// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.model;

/**
 * User callback that writes incremental Markdown content.
 */
public interface MarkdownStreamProducer {
    /**
     * Produce streaming chunks using the provided controller.
     */
    void produce(MarkdownStreamController controller) throws Exception;
}
