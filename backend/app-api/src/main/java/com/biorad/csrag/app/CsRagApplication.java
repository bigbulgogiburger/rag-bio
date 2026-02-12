package com.biorad.csrag.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.biorad.csrag")
@EntityScan(basePackages = "com.biorad.csrag")
@EnableJpaRepositories(basePackages = "com.biorad.csrag")
public class CsRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsRagApplication.class, args);
    }
}
