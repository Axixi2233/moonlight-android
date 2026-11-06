package com.limelight.nvstream.http;

import java.io.IOException;

/** An explicit unsupported-endpoint response, allowing display controls to fall back safely. */
public final class HostDisplayApiUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;

    private HostDisplayApiUnavailableException() {
        super("此主机不支持显示器管理接口");
    }

    /** Classify either an HTTP status or a status parsed from a GameStream XML error body. */
    public static IOException forStatus(int status) {
        if (status == 404 || status == 405 || status == 501) {
            return new HostDisplayApiUnavailableException();
        }
        return new HostHttpResponseException(status, "显示器管理请求失败");
    }
}
