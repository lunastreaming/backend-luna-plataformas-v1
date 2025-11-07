package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    public String token;
    public String tokenType = "Bearer";
    public UUID userId;
    public String username;
    public String role;
    public String refreshToken;

}
