package com.sep.comiverse.dto.pagination;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorResponseDTO<T> {
    private List<T> data;
    private String nextCursor;
    private UUID nextReferenceId;
    private boolean hasMore;
}
