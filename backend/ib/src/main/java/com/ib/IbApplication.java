package com.ib;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Security;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class IbApplication {

	public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        SpringApplication.run(IbApplication.class, args);
	}

}
