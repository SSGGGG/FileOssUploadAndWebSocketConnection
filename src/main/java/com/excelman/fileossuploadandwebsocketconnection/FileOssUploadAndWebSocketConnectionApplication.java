package com.excelman.fileossuploadandwebsocketconnection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FileOssUploadAndWebSocketConnectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileOssUploadAndWebSocketConnectionApplication.class, args);
    }

}
