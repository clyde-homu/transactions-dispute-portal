package com.clydehomu.disputes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DisputePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisputePortalApplication.class, args);
    }
}
