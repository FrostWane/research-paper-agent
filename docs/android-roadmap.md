# Android Roadmap

第三阶段新增 `android/` 原生 Android 简化端，技术路线为 Kotlin + REST API。

## Scope

Android 第一版只做阅读闭环：

- 登录 / 退出登录。
- 文献列表、搜索、状态筛选。
- 文献详情。
- PDF 简化预览。
- 当前文献提问。
- 问答历史查看。

## Out of Scope

- 手机端 PDF 上传。
- 文献编辑复杂表单。
- 多文献综述。
- 管理员能力。
- 离线全文缓存。

## API Reuse

Android 直接复用 Spring Boot REST API：

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/papers`
- `GET /api/files/papers/{fileId}/preview`
- `POST /api/agent/chat`
- `GET /api/papers/{paperId}/chats`

移动端需要本地安全保存 JWT，并在 401 时清理登录态回到登录页。
