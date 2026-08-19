package com.resume_parser;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResumeParserApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		if (dotenv.get("GROQ_API_KEY") == null) {
			dotenv = Dotenv.configure().directory("./resume-parser").ignoreIfMissing().load();
		}
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(ResumeParserApplication.class, args);
	}

}
