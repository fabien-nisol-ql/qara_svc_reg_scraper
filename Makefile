# Adapted from opc_svc_ai/opc_svc_scrp_orch's Makefiles (same Gradle/
# shadowJar/Docker shape), trimmed of what doesn't apply here:
#   - no test_data/document_svc cloning (AI-specific fixtures)
#   - no raw-kubectl job-run/worker-build/kind targets: this service's own
#     Java code (svc/workload/**) talks to Docker/Kubernetes directly to
#     launch scrape jobs - triggering one is `POST /v1/jobs/scrape`
#     against the running service, not a Make/kubectl step.

PROJECT_NAME := $(shell grep rootProject.name settings.gradle.kts | cut -d'"' -f2)
PROJECT_DIR  := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
DOCKER_DIR   := src/main/docker
BUILD_DIR    := $(PROJECT_DIR)build
DOCKER_BUILD_DIR := $(BUILD_DIR)/docker
IMAGE_NAME   := com.qaralink/reg-scraper-svc
LOCAL_VER    := dev
DEBUG       ?= false

# Composite-build path to ../qara_lib_mn - settings.gradle.kts already
# defaults this to "../" on its own; only pass it explicitly if you're
# running make from somewhere qara_lib_mn isn't a sibling of.
export LIB_BASE_DIR ?= $(realpath ../)
VERSION := $(shell ./gradlew -DLIB_BASE_DIR="$(LIB_BASE_DIR)" -q printVersion)
TARBALL := $(DOCKER_BUILD_DIR)/$(PROJECT_NAME)-$(VERSION).tar

ifeq ($(DEBUG),true)
MICRONAUT_EXTRA_ENVIRONMENTS := debug
endif

DOCKER_COMPOSE := docker compose -f $(DOCKER_DIR)/docker-compose.yml

.PHONY: help jar test docker run stop logs logs-app psql clean \
        docker_save docker_checksum docker_manifest docker_package

.DEFAULT_GOAL := help

help: ## Show help (default target)
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@grep -h -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | \
		sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo ""

jar: ## Rebuild the shadowJar file (build/libs/service.jar)
	./gradlew --no-daemon -DLIB_BASE_DIR="$(LIB_BASE_DIR)" shadowJar

test: ## Run tests
	./gradlew --no-daemon -DLIB_BASE_DIR="$(LIB_BASE_DIR)" \
		-Dservice.baseUrl=$${SERVICE_BASE_URL:-http://localhost:8080} \
		test

docker: jar ## Build the Docker image (tags :$(VERSION) and :dev)
	docker build \
		--load \
		-t $(IMAGE_NAME):$(VERSION) \
		-t $(IMAGE_NAME):$(LOCAL_VER) \
		-f $(DOCKER_DIR)/Dockerfile .

run: docker ## Run the service + Postgres + NATS via docker compose (local dev loop)
	MICRONAUT_EXTRA_ENVIRONMENTS=$(MICRONAUT_EXTRA_ENVIRONMENTS) \
		$(DOCKER_COMPOSE) up --build -d
	@$(MAKE) logs-app

stop: ## Stop the docker compose stack
	$(DOCKER_COMPOSE) down

logs: ## Follow every docker compose service's logs
	$(DOCKER_COMPOSE) logs -f

logs-app: ## Follow just the app container's logs
	$(DOCKER_COMPOSE) logs -f app

psql: ## Open a psql shell inside the compose Postgres
	$(DOCKER_COMPOSE) exec db psql -U myuser -d test_db

clean: ## Clean Gradle build artifacts and compose volumes/images
	./gradlew -DLIB_BASE_DIR="$(LIB_BASE_DIR)" clean
	rm -rf "$(DOCKER_BUILD_DIR)"
	$(DOCKER_COMPOSE) down -v
	@docker rmi $(IMAGE_NAME):$(VERSION)   2>/dev/null || echo "Image $(IMAGE_NAME):$(VERSION) does not exist"
	@docker rmi $(IMAGE_NAME):$(LOCAL_VER) 2>/dev/null || echo "Image $(IMAGE_NAME):$(LOCAL_VER) does not exist"

##########################################################################################
# CI/registry packaging (optional - produces a portable tarball + checksum, not a push)
##########################################################################################

$(DOCKER_BUILD_DIR):
	mkdir -p $(DOCKER_BUILD_DIR)

docker_save: $(DOCKER_BUILD_DIR) docker ## Save the built image to a tarball under build/docker/
	docker save $(IMAGE_NAME):$(VERSION) -o $(TARBALL)

docker_checksum: $(DOCKER_BUILD_DIR) docker_save ## docker_save + a .sha256 alongside it
	shasum -a 256 $(TARBALL) > $(TARBALL).sha256

docker_manifest: $(DOCKER_BUILD_DIR) ## Write build/docker/version.txt
	echo "$(VERSION)" > $(DOCKER_BUILD_DIR)/version.txt

docker_package: docker_checksum docker_manifest ## Tarball + checksum + manifest, one step
