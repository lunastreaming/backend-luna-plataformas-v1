package com.example.lunastreaming.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketRequestDTO {

    private Long stockId;       // ID de la compra/stock
    private String issueType;   // Tipo de problema
    private String description; // Detalle opcional

}
