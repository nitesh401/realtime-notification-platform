package com.example.notify.api.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageDto<T> from(Page<T> p) {
        return new PageDto<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
