package com.ib.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H2ConsoleConfig {

	@Bean
	public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServletRegistration() {
		ServletRegistrationBean<JakartaWebServlet> registration = new ServletRegistrationBean<>(new JakartaWebServlet());
		registration.addUrlMappings("/h2-console/*");
		return registration;
	}
}

