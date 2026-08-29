package com.qaralink.regscraper.svc.workload.docker;

import jakarta.inject.Singleton;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shells out to the {@code docker} CLI via commons-exec rather than pulling in a
 * Docker Engine API client library.
 */
@Singleton
public class DefaultCommandRunner implements CommandRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCommandRunner.class);
    private static final long DEFAULT_TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();

    @Override
    public CommandResult run(CommandLine commandLine) throws IOException {
        return run(commandLine, DEFAULT_TIMEOUT_MILLIS);
    }

    @Override
    public CommandResult run(CommandLine commandLine, long timeoutMillis) throws IOException {
        LOG.debug("Executing: {}", commandLine);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        DefaultExecutor executor = DefaultExecutor.builder().get();
        // We interpret exit codes ourselves (e.g. `docker inspect` returning non-zero just means
        // "not found") instead of having commons-exec throw ExecuteException on non-zero exit.
        executor.setExitValues(null);
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        if (timeoutMillis > 0) {
            executor.setWatchdog(ExecuteWatchdog.builder()
                    .setTimeout(Duration.ofMillis(timeoutMillis))
                    .get());
        }

        int exitCode = executor.execute(commandLine);
        return CommandResult.builder()
                .exitCode(exitCode)
                .stdout(stdout.toString(StandardCharsets.UTF_8))
                .stderr(stderr.toString(StandardCharsets.UTF_8))
                .build();
    }
}
