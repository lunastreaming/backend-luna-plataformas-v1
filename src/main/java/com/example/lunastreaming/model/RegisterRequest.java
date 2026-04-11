package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @Pattern(regexp = "^(seller|provider)$",
            message = "El rol debe ser 'seller' o 'provider'")
    public String role;

    public String referrerCode;

}
