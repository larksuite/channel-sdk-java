// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.exception;

public enum LarkChannelErrorCode {
    FORMAT_ERROR("format_error"),
    TARGET_REVOKED("target_revoked"),
    RATE_LIMITED("rate_limited"),
    PERMISSION_DENIED("permission_denied"),
    UPLOAD_FAILED("upload_failed"),
    SSRF_BLOCKED("ssrf_blocked"),
    SEND_TIMEOUT("send_timeout"),
    NOT_CONNECTED("not_connected"),
    UNKNOWN("unknown");

    private final String value;

    LarkChannelErrorCode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
