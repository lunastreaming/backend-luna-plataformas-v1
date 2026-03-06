package com.example.lunastreaming.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("tipo") // Esto mapea "tipo" del JSON a "type" en Java
    private String type;

    @JsonProperty("numeroPerfil") // Esto mapea "numeroPerfil" a "numberProfile"
    private Integer numberProfile;
    private String status;
    private String pin;
    private String clientName;
    private String clientPhone;

    private String resolutionNote; // nota de resolución

}
