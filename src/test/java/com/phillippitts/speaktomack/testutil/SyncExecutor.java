package com.phillippitts.speaktomack.testutil;

import java.util.concurrent.Executor;

/**
 * Synchronous executor for predictable test execution.
 *
 * <p>Runs tasks immediately on the calling thread instead of submitting
 * to a thread pool, making tests deterministic and easier to debug.
 */
public class SyncExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
