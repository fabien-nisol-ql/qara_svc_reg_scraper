package com.qaralink.regscraper.svc.workload;

import jakarta.inject.Singleton;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provider-agnostic workload plumbing shared by every {@link WorkloadOrchestrator}
 * implementation: preparing a work directory on a shared mount and resolving the
 * {@code ${var}}-templated command/args. None of this depends on any particular
 * execution backend (Kubernetes, Docker, ...).
 * <p>
 * Adapted from opc_svc_ai's {@code WorkEnvironmentService} — ported the command/
 * args templating verbatim (it's exactly what qara_cli_reg_scraper's own flags
 * need), dropped the file-based input/output parameter handling (JSON_FILE/FILE/
 * DIRECTORY types) since this workload type has no file I/O — every parameter is
 * a plain string (a source list, a number, a flag value).
 */
@Singleton
public class WorkEnvironmentService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkEnvironmentService.class);

    public Path getWorkdir(Path sharedDir, Workload workload) {
        return sharedDir.resolve(workload.getType()).resolve(workload.getId());
    }

    public WorkEnvironment prepare(Path sharedDir, Workload workload) throws IOException {
        Path workdir = getWorkdir(sharedDir, workload);
        Files.createDirectories(workdir);
        WorkEnvironment.WorkEnvironmentBuilder envBuilder = WorkEnvironment.builder().workdir(workdir);
        if (workload.getParameters() != null) {
            workload.getParameters().forEach(envBuilder::parameter);
        }
        return envBuilder.build();
    }

    /**
     * Resolves {@link Workload#getCommand()} into a list of tokens with {@code ${var}}
     * placeholders substituted, or {@code null} if the workload declares no command.
     */
    public List<String> resolveCommand(Workload workload, WorkEnvironment workEnvironment) {
        return resolve(workload.getCommand(), workEnvironment);
    }

    /**
     * Resolves {@link Workload#getArgs()} into a list of tokens with {@code ${var}}
     * placeholders substituted, or {@code null} if the workload declares no args.
     */
    public List<String> resolveArgs(Workload workload, WorkEnvironment workEnvironment) {
        return resolve(workload.getArgs(), workEnvironment);
    }

    private List<String> resolve(String raw, WorkEnvironment workEnvironment) {
        if (raw == null) {
            return null;
        }
        StringSubstitutor substitutor = new StringSubstitutor(workEnvironment.getParameters());
        CommandLine cmdLine = CommandLine.parse(singleLine(raw));
        List<String> resolved = Stream.concat(Stream.of(cmdLine.getExecutable()), Arrays.stream(cmdLine.getArguments()))
                .map(substitutor::replace)
                .peek(this::hasBeenSubstituted)
                .toList();
        return resolved.isEmpty() ? null : resolved;
    }

    private void hasBeenSubstituted(String s) {
        if (s.contains("${")) throw new IllegalStateException("Variable " + s + " has not been substituted");
    }

    public static String singleLine(String s) {
        return s.replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public WorkloadResult buildResult(
            Workload workload,
            String workloadProvider,
            short exitCode,
            String diagnosticMessage
    ) {
        return GenericWorkloadResult.builder()
                .workload(workload)
                .workloadProvider(workloadProvider)
                .displayMessage("Workload finished with code " + exitCode)
                .diagnosticMessage(diagnosticMessage)
                .exitCode(exitCode)
                .build();
    }

    public void cleanupWorkdir(WorkEnvironment workEnvironment) {
        try {
            FileUtils.deleteDirectory(workEnvironment.getWorkdir().toFile());
        } catch (IOException e) {
            LOG.warn("Could not clean directory {}", workEnvironment.getWorkdir(), e);
        }
    }
}
