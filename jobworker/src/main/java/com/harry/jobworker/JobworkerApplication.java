package com.harry.jobworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // Enable scheduling support in the application
@SpringBootApplication
public class JobworkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobworkerApplication.class, args);
	}

}
