package com.phillippitts.speaktomack;

import org.springframework.boot.SpringApplication;

public class TestSpeakToMackApplication {

    public static void main(String[] args) {
        SpringApplication.from(SpeakToMackApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
