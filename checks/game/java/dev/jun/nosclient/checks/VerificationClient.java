package dev.jun.nosclient.checks;

import net.fabricmc.api.ClientModInitializer;

/** Chọn phép đo mà không nạp lớp mới của Nos khi chạy jar đối chứng 0.3.1. */
public final class VerificationClient implements ClientModInitializer {
    public void onInitializeClient() {
        if ("performance".equals(System.getProperty("nos.verify.mode")))
            new PerformanceVerification().onInitializeClient();
        else new GameVerification().onInitializeClient();
    }
}
