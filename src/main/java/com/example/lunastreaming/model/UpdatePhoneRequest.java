package com.example.lunastreaming.model;

import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePhoneRequest {

    @NotBlank(message = "phone_required")
    private String newPhone;

}
