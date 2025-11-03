package com.project.autocrud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class AutoCrudApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoCrudApplication.class, args);
    }

}
