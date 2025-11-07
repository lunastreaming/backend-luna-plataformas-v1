package com.example.lunastreaming.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RechargeRequest {

    private BigDecimal amount;
    @JsonProperty("isSoles")
    private boolean isSoles;

}
