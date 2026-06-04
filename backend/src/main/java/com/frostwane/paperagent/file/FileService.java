package com.frostwane.paperagent.file;

import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.file.dto.FileDtos.FileResponse;
import com.frostwane.paperagent.user.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileService {

    private static final long MAX_SIZE = 50L * 1024 * 1024;

    private final PaperFileRepository fileRepository;
    private final ObjectStorageService storageService;

    public FileService(PaperFileRepository fileRepository, ObjectStorageService storageService) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
    }

    @Transactional
    public FileResponse uploadPaperFile(MultipartFile file, User owner) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 PDF 文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("PDF 文件不能超过 50MB");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException("仅支持上传 PDF 文件");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("PDF 文件读取失败");
        }
        if (!hasPdfHeader(bytes)) {
            throw new BusinessException("文件头不是有效 PDF");
        }

        int pageCount = readPageCount(bytes);
        String objectKey = buildObjectKey(owner, originalName);
        String contentType = MediaType.APPLICATION_PDF_VALUE;
        storageService.put(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);

        PaperFile record = new PaperFile();
        record.setOwner(owner);
        record.setOriginalName(originalName);
        record.setObjectKey(objectKey);
        record.setContentType(contentType);
        record.setSize(bytes.length);
        record.setSha256(sha256(bytes));
        record.setPageCount(pageCount);
        PaperFile saved = fileRepository.save(record);
        return toResponse(saved);
    }

    public PaperFile requireOwnedFile(Long fileId, Long ownerId) {
        return fileRepository.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> new BusinessException("文件不存在或无权访问"));
    }

    public InputStream openPdf(PaperFile file) {
        return storageService.get(file.getObjectKey());
    }

    public FileResponse toResponse(PaperFile file) {
        return new FileResponse(
            file.getId(),
            file.getOriginalName(),
            file.getSize(),
            file.getContentType(),
            file.getPageCount(),
            file.getCreatedAt()
        );
    }

    private boolean hasPdfHeader(byte[] bytes) {
        return bytes.length >= 4
            && bytes[0] == '%'
            && bytes[1] == 'P'
            && bytes[2] == 'D'
            && bytes[3] == 'F';
    }

    private int readPageCount(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new BusinessException("PDF 文件无法解析页数");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 计算失败", ex);
        }
    }

    private String buildObjectKey(User owner, String originalName) {
        String suffix = originalName.toLowerCase(Locale.ROOT).endsWith(".pdf") ? ".pdf" : "";
        return "papers/%d/%s/%s%s".formatted(
            owner.getId(),
            LocalDate.now(),
            UUID.randomUUID(),
            suffix
        );
    }

    private String safeOriginalName(String originalName) {
        String value = originalName == null || originalName.isBlank() ? "paper.pdf" : originalName.trim();
        return value.replace("\\", "_").replace("/", "_");
    }
}
