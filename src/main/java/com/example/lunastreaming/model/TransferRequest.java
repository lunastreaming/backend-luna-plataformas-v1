package com.example.lunastreaming.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    private UUID sellerId;
    private BigDecimal amount;

}
