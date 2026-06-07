package com.frostwane.paperagent.agent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseFromTraceRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationCaseRequest;
import com.frostwane.paperagent.admin.dto.AdminDtos.EvaluationDatasetRequest;
import com.frostwane.paperagent.agent.ChatRecord;
import com.frostwane.paperagent.agent.ChatRecordRepository;
import com.frostwane.paperagent.agent.RagTrace;
import com.frostwane.paperagent.agent.RagTraceRepository;
import com.frostwane.paperagent.common.BusinessException;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.paper.PaperRepository;
import com.frostwane.paperagent.user.User;
import com.frostwane.paperagent.user.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEvaluationServiceTest {

    private final EvaluationDatasetRepository datasetRepository = mock(EvaluationDatasetRepository.class);
    private final EvaluationCaseRepository caseRepository = mock(EvaluationCaseRepository.class);
    private final PaperRepository paperRepository = mock(PaperRepository.class);
    private final ChatRecordRepository chatRecordRepository = mock(ChatRecordRepository.class);
    private final RagTraceRepository ragTraceRepository = mock(RagTraceRepository.class);
    private final AgentEvaluationService service = new AgentEvaluationService(
        datasetRepository,
        caseRepository,
        paperRepository,
        chatRecordRepository,
        ragTraceRepository,
        new ObjectMapper()
    );

    @Test
    void createsDatasetWithNormalizedCode() {
        User admin = admin();
        when(datasetRepository.findByCodeIgnoreCase("PAPER_QA_REGRESSION")).thenReturn(Optional.empty());
        when(datasetRepository.save(any(EvaluationDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDataset(new EvaluationDatasetRequest(
            "paper qa regression",
            "论文问答回归集",
            "沉淀高价值问答样本",
            "paper",
            true
        ), admin);

        ArgumentCaptor<EvaluationDataset> captor = ArgumentCaptor.forClass(EvaluationDataset.class);
        verify(datasetRepository).save(captor.capture());
        EvaluationDataset saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("PAPER_QA_REGRESSION");
        assertThat(saved.getName()).isEqualTo("论文问答回归集");
        assertThat(saved.getScope()).isEqualTo("PAPER");
        assertThat(saved.getCreatedBy()).isSameAs(admin);
    }

    @Test
    void createsCaseFromTraceUsingSavedChatAnswerAndSources() {
        User owner = new User();
        owner.setUsername("researcher");
        owner.setEmail("researcher@example.local");
        owner.setPasswordHash("hash");
        owner.setRole(UserRole.USER);

        Paper paper = new Paper();
        paper.setOwner(owner);
        paper.setTitle("RAG Survey");

        ChatRecord chat = new ChatRecord();
        chat.setOwner(owner);
        chat.setPaper(paper);
        chat.setQuestion("这篇论文的贡献是什么？");
        chat.setAnswer("主要贡献是提出了一个可观测 RAG 框架。");
        chat.setSourcesJson("[{\"paperId\":1,\"page\":3}]");

        RagTrace trace = new RagTrace();
        trace.setOwner(owner);
        trace.setPaper(paper);
        trace.setChatRecord(chat);
        trace.setScope("PAPER");
        trace.setQuestion("这篇论文的贡献是什么？");
        trace.setStatus("SUCCESS");

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setCode("PAPER_QA");
        dataset.setName("论文问答");
        dataset.setScope("PAPER");

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset));
        when(ragTraceRepository.findById(9L)).thenReturn(Optional.of(trace));
        when(caseRepository.save(any(EvaluationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createCaseFromTrace(new EvaluationCaseFromTraceRequest(
            1L,
            9L,
            null,
            null,
            "贡献,引用",
            "hard",
            true
        ), admin());

        ArgumentCaptor<EvaluationCase> captor = ArgumentCaptor.forClass(EvaluationCase.class);
        verify(caseRepository).save(captor.capture());
        EvaluationCase saved = captor.getValue();
        assertThat(saved.getDataset()).isSameAs(dataset);
        assertThat(saved.getSourceOwner()).isSameAs(owner);
        assertThat(saved.getPaper()).isSameAs(paper);
        assertThat(saved.getChatRecord()).isSameAs(chat);
        assertThat(saved.getRagTrace()).isSameAs(trace);
        assertThat(saved.getQuestion()).isEqualTo("这篇论文的贡献是什么？");
        assertThat(saved.getExpectedAnswer()).isEqualTo("主要贡献是提出了一个可观测 RAG 框架。");
        assertThat(saved.getExpectedSourcesJson()).isEqualTo("[{\"paperId\":1,\"page\":3}]");
        assertThat(saved.getDifficulty()).isEqualTo("HARD");
        assertThat(saved.getTags()).isEqualTo("贡献,引用");
    }

    @Test
    void rejectsInvalidExpectedSourcesJson() {
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setCode("MANUAL");
        dataset.setName("手工样本");
        dataset.setScope("LIBRARY");
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset));

        EvaluationCaseRequest request = new EvaluationCaseRequest(
            1L,
            "LIBRARY",
            null,
            null,
            null,
            "请比较这些论文的方法差异",
            "应按方法、实验和局限进行比较。",
            "not-json",
            "比较",
            "MEDIUM",
            true
        );

        assertThatThrownBy(() -> service.createCase(request, admin()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("期望来源 JSON 格式不正确");
        verify(caseRepository, never()).save(any(EvaluationCase.class));
    }

    private User admin() {
        User user = new User();
        user.setUsername("admin");
        user.setEmail("admin@example.local");
        user.setPasswordHash("hash");
        user.setRole(UserRole.ADMIN);
        return user;
    }
}
