package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PaymentMethodReportDTO {

    private String methodName;
    private String color;
    private Long transactionCount;
    private BigDecimal totalAmount;

}
