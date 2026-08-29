package com.qaralink.regscraper.svc.workload.docker;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DockerRunSpec {
    private String name;
    private String image;
    private List<String> command;
    private List<String> args;
    // A Docker volume NAME or an absolute HOST filesystem path - NOT a path inside this
    // service's own container - see the comment on
    // DockerWorkloadOrchestratorConfiguration.sharedVolume for why: this service runs under
    // docker-outside-of-docker (a mounted /var/run/docker.sock), so `docker run -v <SRC>:...`
    // here executes against the HOST's real daemon, which resolves SRC by asking the daemon
    // for it directly - a path inside THIS container would be invisible to the spawned sibling
    // container, but a named volume or an absolute host path both resolve correctly either way.
    // QARA_IAC_LOCAL_DOCKER currently passes an absolute host path (see its docker-compose.yml).
    private String sharedVolume;
    private String containerSharedDir;
    private String network;
    private Map<String, String> env;
    private String envFile;
    private String imagePullPolicy;
}
