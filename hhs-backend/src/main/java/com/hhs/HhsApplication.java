package com.hhs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HHS Health Monitoring System Main Application
 */
@EnableScheduling
@SpringBootApplication
public class HhsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HhsApplication.class, args);
    }
}
