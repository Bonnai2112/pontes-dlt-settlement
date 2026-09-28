package com.dl3s.pontes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Pontes POC: connects the TARGET core (RTGS) to a DLT ecosystem through APIs.
 * Each direct sub-package is a bounded context verified by Spring Modulith.
 */
@EnableAsync
@Modulithic(systemName = "Pontes")
@SpringBootApplication
public class PontesApplication {

    public static void main(String[] args) {
        SpringApplication.run(PontesApplication.class, args);
    }
}
