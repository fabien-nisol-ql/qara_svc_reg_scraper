package com.qaralink.regscraper.svc.workload.docker;

import org.apache.commons.exec.CommandLine;

import java.io.IOException;

/**
 * Seam around external process execution so {@link DockerContainerManagementService} is
 * unit-testable (with a fake/mock runner) without ever invoking a real {@code docker} binary.
 */
public interface CommandRunner {

    CommandResult run(CommandLine commandLine) throws IOException;

    CommandResult run(CommandLine commandLine, long timeoutMillis) throws IOException;
}
