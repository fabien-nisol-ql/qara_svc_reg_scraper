package com.qaralink.regscraper.svc.workload.k8s;

import com.qaralink.regscraper.svc.workload.Workload;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class K8sWorkloadLabels {

    public static final String QARALINK_WORKLOAD_TYPE = "qaralink.reg-scraper/job-type";
    public static final String QARALINK_WORKLOAD_ID = "qaralink.reg-scraper/job-id";

    @NonNull
    private String type;
    @NonNull
    private String id;

    public static K8sWorkloadLabels from(Workload workload) {
        return new K8sWorkloadLabels(workload.getType(), workload.getId());
    }

    public Map<String, String> toMap() {
        Map<String, String> labels = new HashMap<>();
        labels.put(QARALINK_WORKLOAD_TYPE, type);
        labels.put(QARALINK_WORKLOAD_ID, id);
        return labels;
    }

    public static K8sWorkloadLabels from(V1Pod pod) {
        if (pod == null || pod.getMetadata() == null) {
            return null;
        }
        return from(pod.getMetadata().getLabels());
    }

    public static K8sWorkloadLabels from(V1Job job) {
        if (job == null || job.getMetadata() == null) {
            return null;
        }
        return from(job.getMetadata().getLabels());
    }

    public static K8sWorkloadLabels from(Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        String jobType = labels.get(QARALINK_WORKLOAD_TYPE);
        String jobId = labels.get(QARALINK_WORKLOAD_ID);
        if (jobType == null || jobId == null) {
            return null;
        }
        return new K8sWorkloadLabels(jobType, jobId);
    }

    public void applyTo(V1Job job) {
        Objects.requireNonNull(job);
        Objects.requireNonNull(job.getMetadata());
        Objects.requireNonNull(job.getSpec());
        Objects.requireNonNull(job.getSpec().getTemplate());

        Map<String, String> labels = toMap();

        if (job.getMetadata().getLabels() == null) {
            job.getMetadata().setLabels(new HashMap<>());
        }
        job.getMetadata().getLabels().putAll(labels);

        if (job.getSpec().getTemplate().getMetadata() == null) {
            job.getSpec().getTemplate().setMetadata(new V1ObjectMeta());
        }
        if (job.getSpec().getTemplate().getMetadata().getLabels() == null) {
            job.getSpec().getTemplate().getMetadata().setLabels(new HashMap<>());
        }
        job.getSpec().getTemplate().getMetadata().getLabels().putAll(labels);
    }
}
