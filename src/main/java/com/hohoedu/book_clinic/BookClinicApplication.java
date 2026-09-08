package com.hohoedu.book_clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BookClinicApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookClinicApplication.class, args);
	}

}
