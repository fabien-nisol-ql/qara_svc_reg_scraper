package com.qaralink.regscraper.svc.workload;

import lombok.*;

import java.util.Map;

/**
 * Describes one container invocation to launch — the "run
 * qara_cli_reg_scraper for these sources with these flags" request, in a
 * form neither {@link WorkloadOrchestrator} implementation (Docker,
 * Kubernetes) needs to know the specifics of.
 * <p>
 * Adapted from opc_svc_ai's {@code Workload} (same name/shape, ported and
 * simplified): dropped {@code QaraContext} (this domain has no such
 * concept — a workload here is about sources, not a QARA project/product)
 * and the file-based input/output parameter machinery (this workload type
 * has no file I/O — {@code command}/{@code args} substitution against
 * plain string {@code parameters} is all it needs).
 */
@Data
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Workload {
    @NonNull
    private String type;
    @NonNull
    @ToString.Include
    private String id;
    private String command;
    private String args;
    /** Values substituted into {@code ${var}} placeholders in command/args. */
    @Singular
    private Map<String, String> parameters;
    /**
     * Free-form audit metadata (who triggered this job, when, and why) applied as k8s
     * annotations on both the Job and its pod template.
     */
    @Singular
    private Map<String, String> annotations;
    /**
     * Extra container environment variables specific to THIS job, on top of whatever the
     * orchestrator always sets (service base URL, storage paths, ...) — e.g.
     * QARA_REG_SCRAPER_RETRY_BUDGET_MINUTES for a {@link com.qaralink.regscraper.svc.ScrapeJobService}
     * retry-scheduler-triggered job. Deliberately NOT {@code @Singular} (unlike parameters/
     * annotations above) — every caller sets this wholesale in one go, if at all; null/empty
     * (the default) means "nothing extra," same as before this field existed.
     */
    private Map<String, String> env;
}
