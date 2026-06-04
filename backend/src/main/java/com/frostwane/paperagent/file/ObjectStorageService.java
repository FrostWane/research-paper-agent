package com.frostwane.paperagent.file;

import com.frostwane.paperagent.config.PaperAgentProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ObjectStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public ObjectStorageService(MinioClient minioClient, PaperAgentProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.getMinio().getBucket();
    }

    public void put(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 文件保存失败", ex);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 文件读取失败", ex);
        }
    }
}
