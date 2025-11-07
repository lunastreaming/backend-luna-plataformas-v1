package com.example.lunastreaming.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

@Configuration
public class SecurityBean {

    @Bean
    public Argon2PasswordEncoder passwordEncoder() {
        // params can be tuned for your infra
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

}
