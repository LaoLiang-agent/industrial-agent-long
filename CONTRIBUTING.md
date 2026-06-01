# Contributing to Industrial Agent Long

感谢你的关注！这个项目致力于用 AI Agent 解决工业场景的真实问题。

## 如何贡献

### 提 Bug

1. 先在 [Issues](../../issues) 里搜索，确认没人报过
2. 使用 Bug Report 模板创建 Issue
3. 描述清晰：复现步骤、期望行为、实际行为

### 提新功能

1. 先在 [Issues](../../issues) 里搜一下是否已被提议
2. 使用 Feature Request 模板，说清楚场景和动机
3. 如果是大功能，建议先开 Issue 讨论再动手写代码

### 提交代码

1. Fork 本仓库
2. 从 `main` 创建分支：`git checkout -b feat/your-feature`
3. 开发和测试
4. 提交：`git commit -m "feat: description"`
5. 推送到你的 Fork：`git push origin feat/your-feature`
6. 提交 Pull Request 到本仓库 `main` 分支

## Commit Message 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

- `feat:` 新功能
- `fix:` 修复 Bug
- `docs:` 文档变更
- `refactor:` 代码重构（不改功能）
- `chore:` 构建、依赖等杂项
- `test:` 测试相关

示例：
```
feat: add RAG-based knowledge retrieval for device diagnosis
fix: resolve alarm deduplication in high-frequency scenarios
docs: add deployment guide for Docker Compose
```

## 开发环境

参考 [README.md](./README.md) 的快速开始章节。

## 代码风格

- Java 17，Spring Boot 3.3
- 保持与现有代码风格一致
- 新功能尽量包含可运行的 demo 或测试

## Pull Request 规范

- 一个 PR 做一件事
- 标题用中文或英文都可以，保持清晰
- 描述里写清楚：做了什么、为什么做、怎么验证
- 关联相关 Issue（`Closes #123`）

## 行为准则

保持专业和友善。工业 AI Agent 是一个需要耐心和严谨的领域。
