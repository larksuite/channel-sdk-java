// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.outbound.streaming;

public class UpdateQueue {
    public synchronized void enqueue(QueueTask task) throws Exception {
        task.run();
    }

    public void drain() {
    }

    public interface QueueTask {
        void run() throws Exception;
    }
}
