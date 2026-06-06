package com.frostwane.paperagent.parse;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionPipelineCatalog {

    public static final String PIPELINE_NAME = "pdf-ingestion-v1";
    public static final String NODE_TYPE = "INGESTION";

    private final List<IngestionPipelineNodeDefinition> nodes = List.of(
        new IngestionPipelineNodeDefinition(NODE_TYPE, "prepare", "准备", "标记文献进入解析状态，并清理旧片段。", 10, true),
        new IngestionPipelineNodeDefinition(NODE_TYPE, "fetch-pdf", "读取 PDF", "从对象存储读取 PDF 原始字节。", 20, true),
        new IngestionPipelineNodeDefinition(NODE_TYPE, "parse-pdf", "抽取文本", "使用 PDFBox 按页抽取文本，并按重叠窗口切分片段。", 30, true),
        new IngestionPipelineNodeDefinition(NODE_TYPE, "persist-chunks", "写入片段", "把解析片段写入数据库，进入索引阶段。", 40, true),
        new IngestionPipelineNodeDefinition(NODE_TYPE, "index-embeddings", "向量索引", "为片段生成向量并写入 pgvector 索引。", 50, true),
        new IngestionPipelineNodeDefinition(NODE_TYPE, "finalize", "完成", "标记文献解析完成，可进入 RAG 检索。", 60, true)
    );

    public List<IngestionPipelineNodeDefinition> nodes() {
        return nodes;
    }

    public record IngestionPipelineNodeDefinition(
        String type,
        String name,
        String label,
        String description,
        int order,
        boolean enabled
    ) {
    }
}
