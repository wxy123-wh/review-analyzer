package com.wh.reputation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
public class ReputationMvpApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReputationMvpApplication.class, args);
    }
}
