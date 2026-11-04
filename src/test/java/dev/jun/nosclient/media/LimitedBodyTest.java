package dev.jun.nosclient.media;

import static org.junit.jupiter.api.Assertions.*;

import dev.jun.nosclient.util.LimitedBody;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow.Subscription;

class LimitedBodyTest {
    private static class Feed implements Subscription {
        boolean cancelled;

        public void request(long count) {}

        public void cancel() {
            cancelled = true;
        }
    }

    @Test
    void responseStopsAtTheByteLimitEvenAcrossMultipleChunks() {
        LimitedBody body = new LimitedBody(4);
        Feed feed = new Feed();
        body.onSubscribe(feed);
        body.onNext(
                List.of(ByteBuffer.wrap(new byte[] {1, 2}), ByteBuffer.wrap(new byte[] {3, 4})));
        body.onComplete();
        assertArrayEquals(new byte[] {1, 2, 3, 4}, body.getBody().toCompletableFuture().join());
        assertFalse(feed.cancelled);
        LimitedBody overflow = new LimitedBody(4);
        Feed second = new Feed();
        overflow.onSubscribe(second);
        overflow.onNext(
                List.of(ByteBuffer.wrap(new byte[] {1, 2, 3}), ByteBuffer.wrap(new byte[] {4, 5})));
        assertTrue(second.cancelled);
        assertThrows(
                CompletionException.class, () -> overflow.getBody().toCompletableFuture().join());
    }
}
