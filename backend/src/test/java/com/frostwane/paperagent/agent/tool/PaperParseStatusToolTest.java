package com.frostwane.paperagent.agent.tool;

import com.frostwane.paperagent.agent.dto.AgentDtos.ChatRequest;
import com.frostwane.paperagent.agent.pipeline.AgentPipelineContext;
import com.frostwane.paperagent.file.PaperFile;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.ProcessStatus;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import com.frostwane.paperagent.parse.ParseJob;
import com.frostwane.paperagent.parse.ParseJobRepository;
import com.frostwane.paperagent.user.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperParseStatusToolTest {

    @Test
    void supportsSinglePaperParseStatusQuestions() {
        PaperParseStatusTool tool = new PaperParseStatusTool(mock(PaperChunkRepository.class), mock(ParseJobRepository.class));
        AgentPipelineContext paperContext = context(42L, "这篇论文为什么解析失败，chunk 和向量索引状态如何？");
        paperContext.paper(paper(ProcessStatus.FAILED));

        AgentPipelineContext libraryContext = context(null, "文献库解析状态统计一下");

        assertThat(tool.supports(paperContext)).isTrue();
        assertThat(tool.supports(libraryContext)).isFalse();
    }

    @Test
    void executeReportsLatestParseJobAndChunkCoverage() {
        PaperChunkRepository chunkRepository = mock(PaperChunkRepository.class);
        ParseJobRepository parseJobRepository = mock(ParseJobRepository.class);
        PaperParseStatusTool tool = new PaperParseStatusTool(chunkRepository, parseJobRepository);
        AgentPipelineContext context = context(42L, "这篇论文是否可检索？");
        context.paper(paper(ProcessStatus.FAILED));

        when(chunkRepository.countByPaperId(42L)).thenReturn(8L);
        when(chunkRepository.countByPaperIdAndEnabledTrue(42L)).thenReturn(6L);
        when(chunkRepository.countByPaperIdAndEmbeddingIdIsNotNull(42L)).thenReturn(5L);
        when(chunkRepository.countByPaperIdAndEnabledTrueAndEmbeddingIdIsNotNull(42L)).thenReturn(4L);
        when(parseJobRepository.findTopByOwnerIdAndPaperIdOrderByStartedAtDesc(7L, 42L)).thenReturn(Optional.of(failedJob()));

        ToolExecutionOutput output = tool.execute(context);

        assertThat(output.summary()).contains("解析失败", "暂不可稳定参与检索", "启用 6 个", "已向量化 4 个");
        assertThat(output.details()).contains("失败信息", "PDF 文本为空", "停用 2", "启用且已向量化 4");
    }

    private AgentPipelineContext context(Long paperId, String question) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);
        return new AgentPipelineContext(new ChatRequest(null, paperId, question, true), owner);
    }

    private Paper paper(ProcessStatus status) {
        Paper paper = mock(Paper.class);
        PaperFile file = mock(PaperFile.class);
        when(paper.getId()).thenReturn(42L);
        when(paper.getTitle()).thenReturn("Test Paper");
        when(paper.getProcessStatus()).thenReturn(status);
        when(paper.getFile()).thenReturn(file);
        when(file.getOriginalName()).thenReturn("test.pdf");
        when(file.getPageCount()).thenReturn(12);
        when(file.getSize()).thenReturn(2048L);
        when(file.getSha256()).thenReturn("0123456789abcdef0123456789abcdef");
        return paper;
    }

    private ParseJob failedJob() {
        ParseJob job = new ParseJob();
        job.setStatus("FAILED");
        job.setPageCount(12);
        job.setChunkCount(0);
        job.setDurationMs(2300);
        job.setStartedAt(OffsetDateTime.parse("2026-06-07T10:00:00+08:00"));
        job.setFinishedAt(OffsetDateTime.parse("2026-06-07T10:00:03+08:00"));
        job.setErrorMessage("PDF 文本为空，无法生成知识片段");
        return job;
    }
}
