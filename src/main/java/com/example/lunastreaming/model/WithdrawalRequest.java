package com.example.lunastreaming.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class WithdrawalRequest {

    private BigDecimal amount;
    /**
     * true si el cliente envía monto en soles; false si envía en USD.
     */
    private Boolean soles;

}
