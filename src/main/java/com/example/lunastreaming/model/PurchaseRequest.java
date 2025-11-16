package com.example.lunastreaming.model;

import lombok.*;

@Getter
@Setter
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseRequest {

    private String clientName;
    private String clientPhone;
    private String password;
}
