# Contributing to DeepCover

感谢您对 DeepCover 精准分析采集工具的兴趣！我们欢迎任何形式的贡献。

## 行为准则

- 保持友好和专业的沟通
- 尊重不同的观点和经验水平
- 在 GitHub Issues 和 Pull Requests 中保持透明的讨论

## 如何贡献

### 报告 Bug

1. 在 Issues 中搜索是否已有相关 issue
2. 如果没有，创建新 issue，包含：
   - 清晰的标题
   - 重现步骤
   - 预期行为和实际行为
   - 环境信息（Java 版本、DeepCover 版本、操作系统、JVM Sandbox 版本）

### 提交代码

#### 开发环境设置

1. Fork 本仓库
2. 创建特性分支: `git checkout -b feature/AmazingFeature`
3. 进行修改

#### 代码规范

- 使用 4 空格缩进
- 类名 PascalCase (如 `CodeCollecter`)
- 方法名 camelCase (如 `loadCompleted`)
- 常量 UPPER_SNAKE_CASE (如 `PATTERN_CACHE`)
- 类和方法需要 Javadoc 注释
- 复杂逻辑需要行内注释

#### 提交信息格式

```
<type>(<scope>): <subject>

[body]

类型: feat / fix / docs / style / refactor / test / chore
```

示例:
```
fix(collecter): 修复线程池未优雅关闭的问题

添加 awaitTermination(5s) + shutdownNow() 兜底机制，
防止模块卸载时采集数据丢失。
```

#### 测试要求

项目使用 JUnit 4.13.2 + Mockito 3.12.4 作为测试框架。

```bash
# 运行全部测试
mvn clean test -Dmaven.javadoc.skip=true

# 跳过测试编译打包
mvn clean package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```

当前测试覆盖 52 个用例，新增功能建议同步添加测试用例。测试文件位于 `src/test/java/io/deepcover/agent/` 下。

#### Pull Request 流程

1. 确保代码通过编译和测试: `mvn clean test`
2. 更新相关文档（如果需要）
3. 提交信息清晰
4. 在 PR 描述中引用相关 issue 并描述变更内容

## Pull Request 审查标准

### 代码审查

- [ ] 代码遵循项目规范
- [ ] 没有引入不必要的依赖
- [ ] 没有硬编码的敏感信息
- [ ] 有适当的错误处理和日志记录
- [ ] 日志级别使用正确（error/warn/info/debug）

### 文档审查

- [ ] README 已更新（如果需要）
- [ ] 配置示例正确
- [ ] CHANGELOG 已更新

### 功能审查

- [ ] 功能按预期工作
- [ ] 没有引入破坏性更改
- [ ] 性能影响已评估
- [ ] 边界情况已考虑

## 发布流程

DeepCover 遵循语义化版本: `MAJOR.MINOR.PATCH`

发布检查清单:

- [ ] 更新 CHANGELOG.md
- [ ] 版本号已更新
- [ ] 所有测试通过
- [ ] 已打标签 (git tag)
