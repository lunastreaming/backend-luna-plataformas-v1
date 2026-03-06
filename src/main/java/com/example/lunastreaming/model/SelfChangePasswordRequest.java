package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SelfChangePasswordRequest {

    @NotBlank
    private String currentPassword;
    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
