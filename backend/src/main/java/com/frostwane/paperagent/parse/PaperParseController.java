package com.frostwane.paperagent.parse;

import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.common.IdempotencyService;
import com.frostwane.paperagent.paper.dto.PaperDtos.ParseStatusResponse;
import com.frostwane.paperagent.user.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/papers")
public class PaperParseController {

    private final PaperParseService parseService;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;

    public PaperParseController(
        PaperParseService parseService,
        CurrentUserService currentUserService,
        IdempotencyService idempotencyService
    ) {
        this.parseService = parseService;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<ParseStatusResponse> parse(
        @PathVariable Long id,
        @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserService.getRequiredUser();
        return ApiResponse.ok(idempotencyService.run(
            user,
            "POST /api/papers/{id}/parse",
            idempotencyKey,
            Map.of("paperId", id),
            ParseStatusResponse.class,
            () -> parseService.parse(id, user)
        ));
    }

    @DeleteMapping("/{id}/parse")
    public ApiResponse<ParseStatusResponse> unparse(
        @PathVariable Long id,
        @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey
    ) {
        User user = currentUserService.getRequiredUser();
        return ApiResponse.ok(idempotencyService.run(
            user,
            "DELETE /api/papers/{id}/parse",
            idempotencyKey,
            Map.of("paperId", id),
            ParseStatusResponse.class,
            () -> parseService.unparse(id, user)
        ));
    }
}
