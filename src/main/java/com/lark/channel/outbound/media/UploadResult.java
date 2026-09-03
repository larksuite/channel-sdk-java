// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.outbound.media;

public class UploadResult {
    private final String kind;
    private final String fileKey;
    private final Integer durationMs;

    public UploadResult(String kind, String fileKey, Integer durationMs) {
        this.kind = kind;
        this.fileKey = fileKey;
        this.durationMs = durationMs;
    }

    public String getKind() {
        return kind;
    }

    public String getFileKey() {
        return fileKey;
    }

    public Integer getDurationMs() {
        return durationMs;
    }
}
