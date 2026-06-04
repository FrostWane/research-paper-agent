package com.frostwane.paperagent.paper;

import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.common.PageResponse;
import com.frostwane.paperagent.file.FileService;
import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.dto.PaperDtos.PaperRequest;
import com.frostwane.paperagent.paper.dto.PaperDtos.PaperResponse;
import com.frostwane.paperagent.paper.dto.PaperDtos.ParseStatusResponse;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import com.frostwane.paperagent.user.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PaperService {

    private final PaperRepository paperRepository;
    private final FileService fileService;
    private final PaperChunkRepository chunkRepository;

    public PaperService(PaperRepository paperRepository, FileService fileService, PaperChunkRepository chunkRepository) {
        this.paperRepository = paperRepository;
        this.fileService = fileService;
        this.chunkRepository = chunkRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaperResponse> list(User owner, String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1) - 1;
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Paper> result = paperRepository.findAll(specification(owner, keyword, status),
            PageRequest.of(safePage, safePageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new PageResponse<>(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getTotalElements(),
            safePage + 1,
            safePageSize,
            result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PaperResponse get(Long id, User owner) {
        return toResponse(requireOwnedPaper(id, owner.getId()));
    }

    @Transactional
    public PaperResponse create(PaperRequest request, User owner) {
        Paper paper = new Paper();
        paper.setOwner(owner);
        applyRequest(paper, request, owner);
        return toResponse(paperRepository.save(paper));
    }

    @Transactional
    public PaperResponse update(Long id, PaperRequest request, User owner) {
        Paper paper = requireOwnedPaper(id, owner.getId());
        applyRequest(paper, request, owner);
        return toResponse(paperRepository.save(paper));
    }

    @Transactional
    public PaperResponse updateStatus(Long id, String status, User owner) {
        Paper paper = requireOwnedPaper(id, owner.getId());
        paper.setStatus(parseStatus(status));
        return toResponse(paperRepository.save(paper));
    }

    @Transactional
    public void delete(Long id, User owner) {
        Paper paper = requireOwnedPaper(id, owner.getId());
        paperRepository.delete(paper);
    }

    @Transactional(readOnly = true)
    public ParseStatusResponse parseStatus(Long id, User owner) {
        Paper paper = requireOwnedPaper(id, owner.getId());
        long chunks = chunkRepository.countByPaperId(paper.getId());
        int progress = switch (paper.getProcessStatus()) {
            case PENDING -> 0;
            case PARSING -> 35;
            case INDEXING -> 75;
            case INDEXED -> 100;
            case FAILED -> 0;
        };
        String message = switch (paper.getProcessStatus()) {
            case PENDING -> "PDF 尚未解析";
            case PARSING -> "PDF 正在解析正文";
            case INDEXING -> "正在准备向量索引";
            case INDEXED -> "PDF 已解析，可用于来源片段检索";
            case FAILED -> "PDF 解析失败，请重试";
        };
        return new ParseStatusResponse(paper.getId(), paper.getProcessStatus().name(), message, progress, chunks);
    }

    public Paper requireOwnedPaper(Long id, Long ownerId) {
        return paperRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new BusinessException("文献不存在或无权访问"));
    }

    public PaperResponse toResponse(Paper paper) {
        PaperFile file = paper.getFile();
        return new PaperResponse(
            paper.getId(),
            paper.getTitle(),
            paper.getAuthors(),
            paper.getVenue(),
            paper.getYear(),
            paper.getKeywords(),
            paper.getAbstractText(),
            paper.getNote(),
            paper.getStatus().name(),
            paper.getProcessStatus().name(),
            file == null ? null : file.getId(),
            file == null ? null : file.getOriginalName(),
            file == null ? null : file.getSize(),
            file == null ? null : file.getPageCount(),
            paper.getCreatedAt(),
            paper.getUpdatedAt()
        );
    }

    private void applyRequest(Paper paper, PaperRequest request, User owner) {
        paper.setTitle(clean(request.title()));
        paper.setAuthors(clean(request.authors()));
        paper.setVenue(clean(request.venue()));
        paper.setYear(request.year());
        paper.setKeywords(clean(request.keywords()));
        paper.setAbstractText(clean(request.abstractText()));
        paper.setNote(clean(request.note()));
        if (request.fileId() != null) {
            paper.setFile(fileService.requireOwnedFile(request.fileId(), owner.getId()));
        }
    }

    private Specification<Paper> specification(User owner, String keyword, String status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("owner").get("id"), owner.getId()));

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), like),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("authors")), like),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("keywords")), like),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("abstractText")), like)
                ));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), parseStatus(status)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PaperStatus parseStatus(String value) {
        String normalized = value.trim();
        if ("已精读".equals(normalized) || "INTENSIVE_READ".equalsIgnoreCase(normalized)) {
            return PaperStatus.INTENSIVE_READ;
        }
        if ("待阅读".equals(normalized) || "TO_READ".equalsIgnoreCase(normalized)) {
            return PaperStatus.TO_READ;
        }
        throw new BusinessException("不支持的阅读状态");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
