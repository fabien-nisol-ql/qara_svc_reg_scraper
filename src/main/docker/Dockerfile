FROM eclipse-temurin:21-jre-alpine
ARG JARFILE=build/libs/service.jar
ARG JAVA_OPTS=""
WORKDIR /app
# The `docker` CLI, not the whole daemon: DockerWorkloadOrchestrator (see
# svc/workload/docker/DefaultCommandRunner) shells out to it to launch
# sibling scrape-job containers when qaralink.execution.provider=docker.
# It talks to the HOST's Docker daemon over /var/run/docker.sock (mounted
# in by whichever compose file deploys this image), not a daemon inside
# this container — no dockerd/dind here, just the client.
RUN apk add --no-cache docker-cli
COPY $JARFILE /app/service.jar
EXPOSE 8080
EXPOSE 8081
ENV JAVA_OPTS=${JAVA_OPTS}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/service.jar"]
