package com.biorad.csrag.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.biorad.csrag")
public class CsRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsRagApplication.class, args);
    }
}
