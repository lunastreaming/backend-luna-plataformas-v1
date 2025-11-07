package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank
    public String username; // username or phone
    @NotBlank public String password;

}
