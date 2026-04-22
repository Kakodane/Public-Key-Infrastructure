package com.ib;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IbApplication {

	public static void main(String[] args) {
		SpringApplication.run(IbApplication.class, args);
	}

}
