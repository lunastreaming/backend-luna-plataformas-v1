package com.example.lunastreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LunastreamingApplication {

	public static void main(String[] args) {
		SpringApplication.run(LunastreamingApplication.class, args);
	}

}
