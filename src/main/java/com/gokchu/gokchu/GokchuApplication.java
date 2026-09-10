package com.gokchu.gokchu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GokchuApplication {

	public static void main(String[] args) {
		SpringApplication.run(GokchuApplication.class, args);
	}

}
