package com.phillippitts.speaktomack.service.stt.whisper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Handles concurrent consumption of process stdout and stderr streams.
 *
 * <p>This class prevents deadlocks by draining both output streams concurrently using
 * dedicated background threads ("stream gobblers"). Without this, a process can block
 * waiting for its stdout buffer to be read while the parent waits for the process to exit.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Starting background threads to consume process output streams</li>
 *   <li>Accumulating stream content into StringBuilders with capacity limits</li>
 *   <li>Preventing deadlocks by continuously draining streams</li>
 *   <li>Gracefully handling thread cleanup with timeouts</li>
 * </ul>
 *
 * <p><b>Capacity Limits:</b> To prevent pathological memory usage, gobblers enforce
 * byte limits on accumulated output. Once the limit is reached, they continue draining
 * the stream (to prevent deadlock) but discard additional content.
 *
 * <p><b>Thread Safety:</b> Individual StreamHandlers are not thread-safe, but multiple
 * handlers can be used concurrently for different processes.
 *
 * @since 1.2
 */
final class ProcessStreamHandler {

    private static final Logger LOG = LogManager.getLogger(ProcessStreamHandler.class);

    /**
     * Represents a running stream gobbler with its output buffer and thread.
     */
    static final class StreamGobbler {
        private final Thread thread;
        private final StringBuilder output;

        StreamGobbler(Thread thread, StringBuilder output) {
            this.thread = thread;
            this.output = output;
        }

        /**
         * Returns the accumulated output from this stream.
         *
         * @return captured stream content (may be truncated if capacity limit was reached)
         */
        String getOutput() {
            return output.toString();
        }

        /**
         * Waits for the gobbler thread to finish reading the stream.
         *
         * @param timeout maximum time to wait
         */
        void join(Duration timeout) {
            if (thread == null) {
                return;
            }
            try {
                thread.join(timeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Starts a background thread to consume an input stream.
     *
     * <p>The thread will read lines from the stream until EOF, accumulating them into
     * a StringBuilder. If the accumulated content exceeds maxBytes, the thread continues
     * draining (to prevent deadlock) but discards additional content.
     *
     * @param inputStream the stream to consume (stdout or stderr)
     * @param streamName descriptive name for logging ("stdout", "stderr", etc.)
     * @param maxBytes maximum bytes to accumulate before truncating
     * @return StreamGobbler containing the background thread and output buffer
     */
    static StreamGobbler startGobbler(InputStream inputStream, String streamName, int maxBytes) {
        StringBuilder output = new StringBuilder();
        Runnable gobbler = new StreamGobblerRunnable(inputStream, output, streamName, maxBytes);
        Thread thread = new Thread(gobbler, streamName);
        thread.setDaemon(true);
        thread.start();
        return new StreamGobbler(thread, output);
    }

    /**
     * Runnable that consumes a stream into a StringBuilder with capacity limits.
     *
     * <p>Reads lines from an input stream until EOF. Once the capacity limit is reached,
     * continues draining the stream without accumulating to prevent deadlock, but logs
     * a warning to indicate data loss.
     */
    private static final class StreamGobblerRunnable implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder sink;
        private final String name;
        private final int maxBytes;

        StreamGobblerRunnable(InputStream inputStream, StringBuilder sink, String name, int maxBytes) {
            this.inputStream = inputStream;
            this.sink = sink;
            this.name = name;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean capReached = false;
                while ((line = br.readLine()) != null) {
                    // Stop accumulating once we hit the cap (but keep reading to drain the stream)
                    if (sink.length() >= maxBytes) {
                        if (!capReached) {
                            LOG.warn("Stream '{}' reached {}B cap; discarding further output", name, maxBytes);
                            capReached = true;
                        }
                        continue; // Drain stream without accumulating
                    }
                    if (!sink.isEmpty()) {
                        sink.append('\n');
                    }
                    // Truncate line if it would exceed the cap
                    int available = maxBytes - sink.length();
                    if (line.length() > available) {
                        sink.append(line, 0, available);
                        LOG.warn("Stream '{}' reached {}B cap (truncated line)", name, maxBytes);
                        capReached = true;
                    } else {
                        sink.append(line);
                    }
                }
            } catch (IOException e) {
                LOG.debug("Stream gobbler '{}' stopped: {}", name, e.toString());
            }
        }
    }
}
