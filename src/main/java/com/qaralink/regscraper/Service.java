package com.qaralink.regscraper;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@OpenAPIDefinition(
        info = @Info(
                title = "QARALink regulatory scraper service",
                version = "1.0",
                description = "Owns the Postgres index qara_cli_reg_scraper scrapes into, and triggers "
                        + "scrape runs (Docker or Kubernetes Job) on demand or on a schedule.",
                license = @License(name = "(C)opyright QARAlink S.A.")
        )
)
public class Service {

    public static void main(String[] args) {
        Micronaut.run(Service.class, args);
    }
}
