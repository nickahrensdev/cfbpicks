package com.nickspicks.api;

import com.nickspicks.api.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class NicksPicksApplication {

    public static void main(String[] args) {
        SpringApplication.run(NicksPicksApplication.class, args);
    }
}
