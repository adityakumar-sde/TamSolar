
package com.tamsolar.inquiry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InquiryRateLimiter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(10).toMillis();

    private static final int MAX_SUBMISSIONS = 8;
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Map<String, Window> windows = new HashMap<>();

    public synchronized boolean allow(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        Window current = windows.get(remoteAddress);

        if (current != null
                && now - current.startedAt() >= WINDOW_MILLIS) {
            windows.remove(remoteAddress);
            current = null;
        }

        if (current == null) {
            if (windows.size() >= MAX_TRACKED_ADDRESSES) {
                Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();

                while (iterator.hasNext()) {
                    Window window = iterator.next().getValue();

                    if (now - window.startedAt() >= WINDOW_MILLIS) {
                        iterator.remove();
                    }
                }
            }

            if (windows.size() >= MAX_TRACKED_ADDRESSES) {
                return false;
            }

            // Record the first submission.
            windows.put(remoteAddress, new Window(now, 1));
            return true;
        }

        if (current.submissions() >= MAX_SUBMISSIONS) {
            return false;
        }

        windows.put(
                remoteAddress,
                new Window(current.startedAt(), current.submissions() + 1));

        return true;
    }

    private record Window(long startedAt, int submissions) {
    }
}
