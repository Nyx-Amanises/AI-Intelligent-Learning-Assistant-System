package com.aiassistant.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.aiassistant.learning.mapper")
@SpringBootApplication
public class AiLearningAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiLearningAssistantApplication.class, args);
    }
}
