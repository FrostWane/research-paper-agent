package com.frostwane.paperagent.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaperAgentProperties.class)
public class AppConfig {

    @Bean
    public MinioClient minioClient(PaperAgentProperties properties) {
        PaperAgentProperties.Minio minio = properties.getMinio();
        return MinioClient.builder()
            .endpoint(minio.getEndpoint())
            .credentials(minio.getAccessKey(), minio.getSecretKey())
            .build();
    }

    @Bean
    public MinioBucketInitializer minioBucketInitializer(MinioClient client, PaperAgentProperties properties) {
        return new MinioBucketInitializer(client, properties.getMinio().getBucket());
    }

    public record MinioBucketInitializer(MinioClient client, String bucket) {
        public MinioBucketInitializer {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("MinIO bucket 初始化失败", ex);
            }
        }
    }
}
