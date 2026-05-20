package com.example.lunastreaming.model;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StockSpecification {

    public static Specification<StockEntity> getPurchasesSpec(
            UUID buyerId,
            List<String> allowedStatuses,
            List<Long> excludedStockIds,
            Instant limitDate,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filtrar siempre por el comprador actual (comparando el UUID con el id de la relación buyer)
            predicates.add(cb.equal(root.get("buyer").get("id"), buyerId));

            // 2. Filtrar por estados permitidos (sold, RENEWED)
            predicates.add(root.get("status").in(allowedStatuses));

            // 3. Excluir IDs con tickets activos
            if (excludedStockIds != null && !excludedStockIds.isEmpty()) {
                predicates.add(cb.not(root.get("id").in(excludedStockIds)));
            }

            // 4. Filtro por fecha límite (days)
            if (limitDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endAt"), limitDate));
            }

            // 5. Filtro global 'q' adaptado a tus entidades reales
            if (q != null && !q.strip().isEmpty()) {
                String likePattern = "%" + q.toLowerCase() + "%";
                List<Predicate> qPredicates = new ArrayList<>();

                // Intento de búsqueda exacta por ID numérico
                try {
                    Long searchId = Long.parseLong(q.strip());
                    qPredicates.add(cb.equal(root.get("id"), searchId));
                } catch (NumberFormatException e) {
                    // Si no es número, no se agrega el predicado de ID
                }

                // Búsqueda en campos de texto directos de StockEntity
                if (root.get("username") != null) {
                    qPredicates.add(cb.like(cb.lower(root.get("username")), likePattern));
                }
                if (root.get("clientName") != null) {
                    qPredicates.add(cb.like(cb.lower(root.get("clientName")), likePattern));
                }

                // Búsqueda cruzada en el Producto relacionado (product.name)
                // Nota: Asegúrate de que ProductEntity tenga el atributo "name" o cámbialo por su campo de texto (ej. "title")
                qPredicates.add(cb.like(cb.lower(root.get("product").get("name")), likePattern));

                // Búsqueda por el Proveedor
                // Como en tu servicio original tienes product.getProviderId(), asumimos que en ProductEntity
                // existe una relación mapeada hacia el proveedor o un campo indexado.
                // Si tienes una relación @ManyToOne con UserEntity llamada "provider" en tu ProductEntity, usa esta línea:
                qPredicates.add(cb.like(cb.lower(root.get("product").get("provider").get("username")), likePattern));

                predicates.add(cb.or(qPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
