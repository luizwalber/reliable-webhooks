package com.reliablewebhooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReliableWebhooksApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReliableWebhooksApplication.class, args);
    }
}
