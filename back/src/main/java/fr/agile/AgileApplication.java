package fr.agile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
//@ComponentScan(basePackages = {"fr.agile"})
public class AgileApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgileApplication.class, args);
	}

}
