package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    public String username;

    public String phone;

    @NotBlank @Size(min = 8)
    public String password;

    public String role; // optional: "seller" or "provider" etc.

    public String referrerCode;

}
