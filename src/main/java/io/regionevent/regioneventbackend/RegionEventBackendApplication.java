package io.regionevent.regioneventbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RegionEventBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegionEventBackendApplication.class, args);
    }

}
