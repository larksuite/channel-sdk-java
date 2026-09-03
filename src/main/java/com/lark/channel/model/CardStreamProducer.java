// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.model;

/**
 * User callback that updates an interactive card over time.
 */
public interface CardStreamProducer {
    /**
     * Produce card updates using the provided controller.
     */
    void produce(CardStreamController controller) throws Exception;
}
