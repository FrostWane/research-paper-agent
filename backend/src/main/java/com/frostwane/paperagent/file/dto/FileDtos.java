package com.frostwane.paperagent.file.dto;

import java.time.OffsetDateTime;

public final class FileDtos {
    private FileDtos() {
    }

    public record FileResponse(
        Long fileId,
        String originalName,
        long size,
        String contentType,
        Integer pageCount,
        OffsetDateTime createdAt
    ) {
    }
}
