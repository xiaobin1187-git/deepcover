---
name: Pull Request
about: 在此模板中填写 PR 信息，帮助维护者快速理解和审查您的更改。
title: '<type>(<scope>): <简短描述>'

---

## 📋 变更描述

**清晰描述本次 PR 的主要内容和目的：**

> 例如：移除内部依赖，使用 OpenTelemetry 替代 trace 功能

---

### 🎯 关联 Issue

**本 PR 关联的 Issue（如有）:**

- Closes #[issue-number]
- Related to #[issue-number]
- Fixes #[issue-number]

> 例如：
> Closes #123
> Related to #124

### 📊 变更类型

- [ ] `feat`: 新功能
- [ ] `fix`: Bug 修复
- [ ] `docs`: 文档更新
- [ `style`: 代码格式调整
- [ `refactor`: 代码重构
- `test`: 添加或修改测试
- `chore`: 构建或辅助工具的变动

### 🔍 变更范围

**主要影响的模块或文件：**

- [ ] 核心代码（entity, config, util）
- [ ] 扩展代码（ext）
- [ ] 配置文件（properties, xml）
- [ ] 文档（md）

---

## 📝 详细说明

### 主要更改

**1. 第一个重要更改**

> 例如：移除了 `io.deepcover.cat-toolkit-trace` 依赖，改用 OpenTelemetry API

**2. 第二个重要更改**

> 例如：创建了 `TraceContext` 工具类，提供 ThreadLocal 的 traceId 管理

**3. 第三个重要更改（如有）**

> 例如：更新了 README.md，添加了 OpenTelemetry 使用说明

### 技术细节

**实现方式或关键技术点：**

> 例如：使用 ThreadLocal 保持与原有行为一致，确保线程安全

### 兼容性说明

**是否存在 breaking change 或需要特殊配置：**

> 例如：配置文件格式变更，需要用户更新 deepcover.properties

---

## ✅ 测试和验证

### 功能测试

- [ ] 代码编译通过：`mvn clean package`
- [ ] 单元测试通过（如适用）
- [ ] 集成测试通过（如适用）
- [ ] 本地运行成功

### 手动测试

- [ ] 按照 README 中的快速开始步骤验证
- [ ] 验证配置文件正确加载
- [ ] 检查日志无错误或警告
- [ ] 验证数据发送功能正常

### 环境兼容性

- [ ] 在 Java 8 环境测试通过
- [ ] 在 Java 11+ 环境测试通过
- [ ] 在不同操作系统（Windows/Linux/macOS）测试通过

---

## 📸 截图或演示

**如有相关截图或演示，请附上：**

> 例如：新的配置界面截图或功能演示 GIF

---

## 📋 检查清单

### 代码质量

- [ ] 代码遵循项目规范（见 [CONTRIBUTING.md](CONTRIBUTING.md)）
- [ ] 没有引入不必要的依赖
- [ ] 没有硬编码的敏感信息
- [ ] 方法职责单一、易于理解
- [ ] 有适当的错误处理和日志记录
- [ ] 没有引入编译警告（或警告可解释）

### 文档更新

- [ ] README.md 已更新（如需要）
- [ ] API 变更已记录（如适用）
- [ ] 配置示例正确
- [ ] Javadoc 注释完整（如适用）
- [ ] CHANGELOG.md 已更新（如适用）

### 提交信息规范

- [ ] 提交信息格式正确：`<type>(<scope>): <subject>`
- [ ] PR 标题清晰且有意义
- [ ] Body 部分详细说明更改
- [ ] 关联相关 Issues（如有）

### 许可证

- [ ] 添加了 Apache 2.0 License 头到新增文件
- [ ] 第三方依赖的许可证兼容

### 合并准备

- [ ] 可以安全合并到目标分支
- [ ] 没有冲突或冲突已解决
- [ ] CI/CD 检查通过（如适用）
- [ ] 相关文档已同步

---

## 🔒 安全检查

- [ ] 没有引入新的安全漏洞
- [ ] 敏感信息已移除或替换为占位符
- [ ] 配置文件不会被意外提交到版本控制
- [ ] 依赖来自可信的 Maven Central

---

**💡 提示**：
- 更多信息请查看 [CONTRIBUTING.md](CONTRIBUTING.md)
- 如有问题，请在 PR 中 `@` 维护者进行讨论

---

感谢您的贡献！🙏‍♂️
