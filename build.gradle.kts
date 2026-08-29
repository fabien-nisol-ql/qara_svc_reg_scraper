plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.3.6"
    id("io.micronaut.aot") version "4.3.6"
    id("com.gorylenko.gradle-git-properties") version "2.3.2"
}

apply(from = "versioning.gradle.kts")

version = extra["versionFromGit"] as String
group = "com.qaralink.svc"

configure<com.gorylenko.GitPropertiesPluginExtension> {
    dateFormat = "yyyy-MM-dd'T'HH:mmZ"
    customProperty("service.name", project.name)
}

repositories {
    mavenCentral()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("service")
    archiveClassifier.set("")
    archiveVersion.set("")
    // The kubernetes-client dependencies alone push this fat jar's entry
    // count past the plain zip 65535-entry limit.
    isZip64 = true
}

tasks.withType<Zip>().configureEach {
    isZip64 = true
}

dependencies {
    // N.B.: Lombok has to be first because all other annotation processors will rely on it
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    implementation("io.micronaut.openapi:micronaut-openapi-annotations")

    implementation("io.micronaut.security:micronaut-security")
    implementation("io.micronaut.security:micronaut-security-jwt")

    annotationProcessor("io.micronaut:micronaut-http-validation")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    runtimeOnly("com.h2database:h2")

    // json/yaml/jackson
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.0")

    implementation(platform("io.micronaut:micronaut-core-bom:4.3.6"))
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("org.flywaydb:flyway-core:11.7.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.7.2")

    runtimeOnly("org.yaml:snakeyaml")
    // Micronaut Data JPA
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.data:micronaut-data-tx-hibernate")
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")

    runtimeOnly("ch.qos.logback:logback-classic")

    // Required, not optional: qara_lib_mn's own com.qaralink.nats.NatsService
    // is an unconditional @Context bean with a non-nullable
    // io.nats.client.Connection constructor param and no @Requires gate —
    // any service depending on qara_lib_mn needs a real, reachable NATS
    // broker just to start, whether or not it publishes/subscribes to
    // anything itself (confirmed the hard way: removing this dependency, or
    // setting a nats.enabled: false that doesn't actually exist as a
    // recognized property, both fail startup the same way). This service
    // doesn't use NATS for anything yet (see the plan's "out of scope"
    // list) — the dependency is here purely because qara_lib_mn requires it.
    implementation("io.micronaut.nats:micronaut-nats") {
        exclude(group = "io.micronaut.serde", module = "micronaut-serde-api")
        exclude(group = "io.micronaut.serde", module = "micronaut-serde-jackson")
    }

    implementation("org.postgresql:postgresql:42.7.2")

    // Micronaut validation support
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut.validation:micronaut-validation")
    annotationProcessor("io.micronaut:micronaut-inject-java")

    // workload orchestration (Docker + Kubernetes job execution — see
    // svc/workload/**, ported and adapted from opc_svc_ai)
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("org.apache.commons:commons-exec:1.6.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.14.0")
    implementation("io.micronaut.kubernetes:micronaut-kubernetes-client:7.2.0")
    implementation("io.micronaut.kubernetes:micronaut-kubernetes-informer:7.2.0")
    val k8sClientVersion = "25.0.0"
    implementation("io.kubernetes:client-java:$k8sClientVersion")
    implementation("io.kubernetes:client-java-api:$k8sClientVersion")
    implementation("io.kubernetes:client-java-proto:$k8sClientVersion")
    implementation("io.kubernetes:client-java-extended:$k8sClientVersion")

    // test
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")

    // qaralink shared library (composite build if ../qara_lib_mn exists, see settings.gradle.kts)
    implementation("com.qaralink.libs:qara_lib_mn:0.1.0")
}

application {
    mainClass = "com.qaralink.regscraper.Service"
}
java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(false)
        annotations("com.qaralink.regscraper.**")
    }
    aot {
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "21"
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version for Makefile and CI"
    doLast {
        println(project.version)
    }
}
tasks.test {
    systemProperty("service.baseUrl", System.getProperty("service.baseUrl", "http://localhost:8080"))
}
