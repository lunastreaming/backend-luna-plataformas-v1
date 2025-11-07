package com.example.lunastreaming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {

    public UUID id;
    public String username;
    public String phone;
    public String role;
    public BigDecimal balance;
    public Integer salesCount;
    public String status;
    public Integer referralsCount;

}
