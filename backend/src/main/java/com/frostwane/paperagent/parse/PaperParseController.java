package com.frostwane.paperagent.parse;

import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.paper.dto.PaperDtos.ParseStatusResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers")
public class PaperParseController {

    private final PaperParseService parseService;
    private final CurrentUserService currentUserService;

    public PaperParseController(PaperParseService parseService, CurrentUserService currentUserService) {
        this.parseService = parseService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<ParseStatusResponse> parse(@PathVariable Long id) {
        return ApiResponse.ok(parseService.parse(id, currentUserService.getRequiredUser()));
    }
}
