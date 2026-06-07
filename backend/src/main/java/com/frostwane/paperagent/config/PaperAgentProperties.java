package com.frostwane.paperagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "paper-agent")
public class PaperAgentProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private Ai ai = new Ai();
    private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public void setBootstrapAdmin(BootstrapAdmin bootstrapAdmin) {
        this.bootstrapAdmin = bootstrapAdmin;
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:15173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Jwt {
        private String secret = "change-this-secret-to-at-least-32-characters";
        private long expirationMinutes = 10080;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "paperagent";
        private String secretKey = "paperagent_password";
        private String bucket = "paper-pdfs";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Ai {
        private String provider = "fallback";
        private String embeddingProvider = "local";
        private String chatModel = "gpt-4o-mini";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEmbeddingProvider() {
            return embeddingProvider;
        }

        public void setEmbeddingProvider(String embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }
    }

    public static class BootstrapAdmin {
        private boolean enabled = true;
        private String username = "admin";
        private String email = "admin@example.local";
        private String password = "Admin@123456";
        private boolean resetPassword = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isResetPassword() {
            return resetPassword;
        }

        public void setResetPassword(boolean resetPassword) {
            this.resetPassword = resetPassword;
        }
    }
}
