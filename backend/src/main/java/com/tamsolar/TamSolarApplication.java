package com.tamsolar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TamSolarApplication {

    public static void main(String[] args) {
        SpringApplication.run(TamSolarApplication.class, args);
    }
}
