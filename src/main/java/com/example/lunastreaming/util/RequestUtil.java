package com.example.lunastreaming.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class RequestUtil {

    /**
     * Convierte una cadena de sort (ej: "soldAt,desc;productName,asc") en un Spring Data Sort.
     * Si sort es null o inválido, devuelve Sort.by(defaultField).descending() si defaultField no es null,
     * si defaultField es null devuelve Sort.unsorted().
     *
     * Formato soportado:
     *   "field"                -> field desc (por compatibilidad con implementación previa)
     *   "field,asc"            -> field asc
     *   "field,desc"           -> field desc
     *   "f1,asc;f2,desc"       -> múltiples reglas separadas por ';'
     */
    public static Sort parseSort(String sort, String defaultField) {
        if (sort == null || sort.trim().isEmpty()) {
            if (defaultField == null || defaultField.trim().isEmpty()) return Sort.unsorted();
            return Sort.by(Sort.Direction.DESC, defaultField);
        }

        try {
            String[] parts = sort.split(";");
            List<Sort.Order> orders = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (p == null || p.trim().isEmpty()) continue;
                String[] pieces = p.split(",");
                String field = pieces[0].trim();
                if (field.isEmpty()) continue;
                Sort.Direction dir = Sort.Direction.DESC;
                if (pieces.length > 1 && "asc".equalsIgnoreCase(pieces[1].trim())) dir = Sort.Direction.ASC;
                orders.add(new Sort.Order(dir, field));
            }
            if (orders.isEmpty()) {
                if (defaultField == null || defaultField.trim().isEmpty()) return Sort.unsorted();
                return Sort.by(Sort.Direction.DESC, defaultField);
            }
            return Sort.by(orders);
        } catch (Exception ex) {
            if (defaultField == null || defaultField.trim().isEmpty()) return Sort.unsorted();
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
    }

    /**
     * Crea un Pageable seguro aplicando un límite máximo a size.
     *
     * @param page  número de página (0-based)
     * @param size  tamaño solicitado
     * @param sortParam  parámetro sort recibido del request
     * @param defaultSortField  campo por defecto si sortParam es nulo o inválido
     * @param maxSize  tope máximo de size (por ejemplo 100)
     * @return Pageable listo para pasar a repositorios
     */
    public static Pageable createPageable(int page, int size, String sortParam, String defaultSortField, int maxSize) {
        int safeSize = safeSize(size, maxSize);
        Sort sort = parseSort(sortParam, defaultSortField);
        return PageRequest.of(Math.max(0, page), safeSize, sort);
    }

    /**
     * Normaliza size asegurando rango razonable y aplicando tope.
     */
    public static int safeSize(int size, int maxSize) {
        int s = (size <= 0) ? 20 : size;
        if (maxSize <= 0) return s;
        return Math.min(s, maxSize);
    }

    /**
     * Convierte una cadena vacía o nula a null, y trim si aplica.
     */
    public static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}
