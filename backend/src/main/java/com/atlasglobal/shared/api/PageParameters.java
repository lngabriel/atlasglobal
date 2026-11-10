package com.atlasglobal.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageParameters {
    @Min(0) @Max(10000)
    @Schema(defaultValue = "0", description = "Zero-based page index")
    private int page = 0;
    @Min(1) @Max(100)
    @Schema(defaultValue = "20", description = "Records per page, from 1 to 100")
    private int size = 20;
    @Size(max = 40)
    @Schema(description = "Allowed sort field; the default depends on the resource")
    private String sort;
    @Pattern(regexp = "asc|desc")
    @Schema(defaultValue = "asc", allowableValues = {"asc", "desc"})
    private String direction = "asc";

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Pageable pageable(String defaultField, Set<String> allowedFields, String tieBreaker) {
        String field = sort == null ? defaultField : sort;
        if (!allowedFields.contains(field)) throw ApiException.badRequest("Campo de ordenação inválido.");
        Sort order = Sort.by(Sort.Direction.fromString(direction), field);
        if (!field.equals(tieBreaker)) order = order.and(Sort.by(tieBreaker));
        return PageRequest.of(page, size, order);
    }
}
