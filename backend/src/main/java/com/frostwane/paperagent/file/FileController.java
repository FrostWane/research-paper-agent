package com.frostwane.paperagent.file;

import com.frostwane.paperagent.auth.CurrentUserService;
import com.frostwane.paperagent.common.ApiResponse;
import com.frostwane.paperagent.file.dto.FileDtos.FileResponse;
import com.frostwane.paperagent.user.User;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files/papers")
public class FileController {

    private final FileService fileService;
    private final CurrentUserService currentUserService;

    public FileController(FileService fileService, CurrentUserService currentUserService) {
        this.fileService = fileService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ApiResponse<FileResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(fileService.uploadPaperFile(file, currentUserService.getRequiredUser()));
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long fileId) {
        User user = currentUserService.getRequiredUser();
        PaperFile file = fileService.requireOwnedFile(fileId, user.getId());
        InputStream stream = fileService.openPdf(file);
        ContentDisposition disposition = ContentDisposition.inline()
            .filename(file.getOriginalName(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(file.getSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(new InputStreamResource(stream));
    }
}
