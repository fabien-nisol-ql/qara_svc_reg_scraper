package com.qaralink.regscraper.svc.workload.k8s;

import com.qaralink.regscraper.exceptions.JobAlreadyExistsException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Minimal service to restart a Kubernetes Job by name.
 * <p>
 * Behavior:
 * - if the Job does not exist: create it
 * - if the Job exists and is still running: throw JobAlreadyExistsException("Still running")
 * - if the Job exists and is finished: delete it, wait until it is gone, recreate it
 * <p>
 * "Finished" here is intentionally simple: completionTime != null, or succeeded > 0, or
 * failed > 0. Everything else is considered still running.
 */
@Singleton
public class K8sJobManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(K8sJobManagementService.class);
    private static final int DELETE_POLL_INTERVAL_MS = 500;
    private static final int DELETE_TIMEOUT_MS = 30_000;

    private final BatchV1Api batchApi;

    public K8sJobManagementService(BatchV1Api batchApi) {
        this.batchApi = batchApi;
    }

    public V1Job startJobIfNotRunning(String namespace, V1Job job) throws ApiException {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(job.getMetadata(), "job.metadata");

        String jobName = job.getMetadata().getName();
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("job.metadata.name must be set");
        }

        V1Job existing = readJobOrNull(namespace, jobName);
        if (existing == null) {
            return createJob(namespace, job);
        }
        if (isFinished(existing)) {
            LOG.info("Job '{}' has finished — deleting before recreating", jobName);
            deleteAndWait(namespace, jobName);
            return createJob(namespace, job);
        }
        throw new JobAlreadyExistsException("Job '" + jobName + "' is still running");
    }

    private boolean isFinished(V1Job job) {
        V1JobStatus status = job.getStatus();
        if (status == null) return false;
        return status.getCompletionTime() != null
                || (status.getSucceeded() != null && status.getSucceeded() > 0)
                || (status.getFailed() != null && status.getFailed() > 0);
    }

    private void deleteAndWait(String namespace, String jobName) throws ApiException {
        batchApi.deleteNamespacedJob(jobName, namespace)
                .propagationPolicy("Foreground")
                .execute();

        long deadline = System.currentTimeMillis() + DELETE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (readJobOrNull(namespace, jobName) == null) return;
            try {
                Thread.sleep(DELETE_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for job '" + jobName + "' to be deleted", e);
            }
        }
        throw new IllegalStateException("Timed out waiting for job '" + jobName + "' to be deleted");
    }

    private V1Job readJobOrNull(String namespace, String jobName) throws ApiException {
        try {
            return batchApi.readNamespacedJob(jobName, namespace).execute();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private V1Job createJob(String namespace, V1Job job) throws ApiException {
        return batchApi.createNamespacedJob(namespace, job).execute();
    }
}
