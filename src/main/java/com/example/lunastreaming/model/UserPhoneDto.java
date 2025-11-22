package com.example.lunastreaming.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPhoneDto {

    private UUID id;
    private String username;
    private String phone;

}
