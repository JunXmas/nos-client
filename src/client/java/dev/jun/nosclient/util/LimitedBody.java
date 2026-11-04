package dev.jun.nosclient.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;

/** Huỷ subscription ngay khi phản hồi vượt trần, không đợi nạp hết vào bộ nhớ. */
public final class LimitedBody implements BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private volatile Subscription subscription;
    private final int maximum;

    public LimitedBody(int maximum) {
        this.maximum = maximum;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return this.result;
    }

    @Override
    public void onSubscribe(Subscription value) {
        this.subscription = value;
        value.request(1L);
    }

    public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            // search trả tới 20 bản kèm cả lời thường lẫn lời đồng bộ (~0,5–1 MB với bài nhiều
            // bản).
            if (this.output.size() + buffer.remaining() > maximum) {
                this.onError(new IOException("Lyrics response too large"));
                this.cancel();
                return;
            }

            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            this.output.writeBytes(bytes);
        }

        this.subscription.request(1L);
    }

    @Override
    public void onError(Throwable error) {
        this.result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
        this.result.complete(this.output.toByteArray());
    }

    public void cancel() {
        if (this.subscription != null) {
            this.subscription.cancel();
        }
    }
}
