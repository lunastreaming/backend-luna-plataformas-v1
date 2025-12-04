package com.example.lunastreaming.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockResolveRequest {

    private String username;
    private String password;
    private String url;
    private String type;
    private Integer numberProfile;
    private String status;
    private String pin;
    private String clientName;
    private String clientPhone;

    private String resolutionNote; // nota de resolución

}
