package com.qaralink.regscraper.svc.workload.docker;

import com.qaralink.regscraper.exceptions.ContainerAlreadyRunningException;
import jakarta.inject.Singleton;
import org.apache.commons.exec.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal service to (re)start a Docker container by name, the Docker-provider sibling of
 * {@code k8s.K8sJobManagementService}.
 * <p>
 * Behavior:
 * - if no container with that name exists: create and start it
 * - if a container with that name is still running: throw ContainerAlreadyRunningException
 * - if a container with that name has finished: remove it, then create and start a new one
 */
@Singleton
public class DockerContainerManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerManagementService.class);

    private final CommandRunner commandRunner;

    public DockerContainerManagementService(CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    /**
     * Fails fast and loud if the {@code docker} binary/daemon isn't reachable, rather than
     * surfacing an opaque failure the first time a workload is submitted.
     */
    public void checkDaemonAvailable() {
        try {
            CommandResult result = commandRunner.run(dockerCommand("info"));
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Docker daemon is not reachable: " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not run the 'docker' CLI - is it installed and on PATH?", e);
        }
    }

    public String startContainerIfNotRunning(DockerRunSpec spec) throws IOException {
        ContainerState state = inspectState(spec.getName());
        switch (state) {
            case RUNNING -> throw new ContainerAlreadyRunningException("Container '" + spec.getName() + "' is still running");
            case FINISHED -> removeContainer(spec.getName());
            case ABSENT -> {
                // nothing to clean up
            }
        }
        ensureImageAvailable(spec.getImage(), spec.getImagePullPolicy());
        return createAndStart(spec);
    }

    public short waitForExit(String containerId, Duration timeout) throws IOException {
        CommandResult result = commandRunner.run(dockerCommand("wait", containerId), timeout.toMillis());
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("'docker wait' failed for container '" + containerId + "': " + result.getStderr());
        }
        try {
            return Short.parseShort(result.getStdout().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Unexpected 'docker wait' output for container '" + containerId + "': " + result.getStdout(), e);
        }
    }

    public String tailLogs(String containerId, int lines) {
        try {
            CommandResult result = commandRunner.run(dockerCommand("logs", "--tail", String.valueOf(lines), containerId));
            return result.getExitCode() == 0 ? (result.getStdout() + result.getStderr()) : null;
        } catch (IOException e) {
            LOG.warn("Could not fetch logs for container '{}'", containerId, e);
            return null;
        }
    }

    public void removeQuietly(String nameOrId) {
        try {
            removeContainer(nameOrId);
        } catch (IOException e) {
            LOG.warn("Could not remove container '{}'", nameOrId, e);
        }
    }

    private void removeContainer(String nameOrId) throws IOException {
        CommandResult result = commandRunner.run(dockerCommand("rm", "-f", nameOrId));
        if (result.getExitCode() != 0 && !isMissingContainer(result)) {
            throw new IllegalStateException("Could not remove container '" + nameOrId + "': " + result.getStderr());
        }
    }

    private void ensureImageAvailable(String image, String pullPolicy) throws IOException {
        if ("Never".equalsIgnoreCase(pullPolicy)) {
            return;
        }
        boolean present = imagePresent(image);
        if ("Always".equalsIgnoreCase(pullPolicy) || !present) {
            LOG.info("Pulling image '{}'", image);
            CommandResult result = commandRunner.run(dockerCommand("pull", image));
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Could not pull image '" + image + "': " + result.getStderr());
            }
        }
    }

    private boolean imagePresent(String image) throws IOException {
        CommandResult result = commandRunner.run(dockerCommand("image", "inspect", image));
        return result.getExitCode() == 0;
    }

    private String createAndStart(DockerRunSpec spec) throws IOException {
        CommandResult result = commandRunner.run(buildRunCommand(spec));
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Could not start container '" + spec.getName() + "': " + result.getStderr());
        }
        return result.getStdout().trim();
    }

    private CommandLine buildRunCommand(DockerRunSpec spec) {
        CommandLine cmd = new CommandLine("docker");
        cmd.addArgument("run", false);
        cmd.addArgument("-d", false);
        cmd.addArgument("--name", false);
        cmd.addArgument(spec.getName(), false);
        if (spec.getSharedVolume() != null && !spec.getSharedVolume().isBlank() && spec.getContainerSharedDir() != null) {
            cmd.addArgument("-v", false);
            cmd.addArgument(spec.getSharedVolume() + ":" + spec.getContainerSharedDir(), false);
        }
        if (spec.getNetwork() != null && !spec.getNetwork().isBlank()) {
            cmd.addArgument("--network", false);
            cmd.addArgument(spec.getNetwork(), false);
        }
        if (spec.getEnvFile() != null && !spec.getEnvFile().isBlank()) {
            cmd.addArgument("--env-file", false);
            cmd.addArgument(spec.getEnvFile(), false);
        }
        if (spec.getEnv() != null) {
            for (Map.Entry<String, String> entry : spec.getEnv().entrySet()) {
                // Blank values are unset/not-configured-yet passthroughs (see
                // qaralink.docker.jobs.env in application.yml) - passing them
                // through as `-e KEY=` would shadow any default the workload
                // image itself falls back to, rather than just being absent.
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                cmd.addArgument("-e", false);
                cmd.addArgument(entry.getKey() + "=" + entry.getValue(), false);
            }
        }
        cmd.addArgument(spec.getImage(), false);
        if (spec.getCommand() != null) {
            spec.getCommand().forEach(token -> cmd.addArgument(token, false));
        }
        if (spec.getArgs() != null) {
            spec.getArgs().forEach(token -> cmd.addArgument(token, false));
        }
        return cmd;
    }

    private ContainerState inspectState(String name) throws IOException {
        CommandResult result = commandRunner.run(dockerCommand("inspect", "-f", "{{.State.Status}}", name));
        if (result.getExitCode() != 0) {
            return ContainerState.ABSENT;
        }
        String status = result.getStdout().trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "running", "restarting" -> ContainerState.RUNNING;
            default -> ContainerState.FINISHED;
        };
    }

    private boolean isMissingContainer(CommandResult result) {
        String stderr = result.getStderr() == null ? "" : result.getStderr();
        return stderr.contains("No such container");
    }

    private CommandLine dockerCommand(String... args) {
        CommandLine cmd = new CommandLine("docker");
        for (String arg : args) {
            cmd.addArgument(arg, false);
        }
        return cmd;
    }

    private enum ContainerState { ABSENT, RUNNING, FINISHED }
}
