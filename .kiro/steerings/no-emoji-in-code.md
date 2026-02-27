# 代码注释禁用 Emoji 规范

## 核心规则

**严禁在所有代码文件的注释中使用 Emoji 字符！**

## 适用范围

此规则适用于所有代码文件，包括但不限于：
- Java 源代码 (`.java`)
- PowerShell 脚本 (`.ps1`)
- JavaScript/TypeScript (`.js`, `.ts`)
- Python (`.py`)
- SQL 脚本 (`.sql`)
- 配置文件中的注释
- 任何包含代码注释的文件

## 原因

1. **编码兼容性问题**: Windows PowerShell 和某些 IDE 的默认编码可能无法正确处理 Emoji 字符
2. **脚本解析错误**: Emoji 会导致脚本解析失败，出现类似错误：
   ```
   表达式或语句中包含意外的标记"FAIL" { "鉂?"。
   ```
3. **跨平台兼容性**: 不同操作系统和终端对 Emoji 的支持不一致
4. **代码可读性**: ASCII 文本在所有环境下都能正确显示

## 替代方案

使用 ASCII 文本替代 Emoji：

| 禁止使用 | 应该使用 |
|----------|----------|
| ✅ | `[PASS]` 或 `"PASS"` 或 `SUCCESS` |
| ❌ | `[FAIL]` 或 `"FAIL"` 或 `ERROR` |
| ⏭️ | `[SKIP]` 或 `"SKIP"` 或 `SKIPPED` |
| ⚠️ | `[WARN]` 或 `"WARNING"` |
| ℹ️ | `[INFO]` 或 `"INFO"` |
| ❓ | `[?]` 或 `"UNKNOWN"` |
| 🔧 | `[FIX]` 或 `"FIXED"` |
| 🚀 | `[DEPLOY]` 或 `"DEPLOYED"` |
| 📝 | `[NOTE]` 或 `"NOTE"` |
| 🐛 | `[BUG]` 或 `"BUG"` |
| 任何其他 Emoji | 对应的英文文本 |

## 错误示例

```java
// 错误 - 使用 Emoji
/**
 * ✅ 用户注册成功
 * ❌ 用户名已存在
 */
public void registerUser() {
    // ...
}

// 错误 - 在日志中使用 Emoji
log.info("✅ 消息发送成功");
log.error("❌ 消息发送失败");
```

```powershell
# 错误 - 使用 Emoji
$StatusEmoji = switch ($Result.Status) {
    "PASS" { "✅" }
    "FAIL" { "❌" }
    "SKIP" { "⏭️" }
}
Write-Host "$StatusEmoji Test completed"
```

## 正确示例

```java
// 正确 - 使用 ASCII 文本
/**
 * [SUCCESS] 用户注册成功
 * [ERROR] 用户名已存在
 */
public void registerUser() {
    // ...
}

// 正确 - 在日志中使用文本
log.info("[SUCCESS] 消息发送成功");
log.error("[ERROR] 消息发送失败");
```

```powershell
# 正确 - 使用 ASCII 文本
$StatusMark = switch ($Result.Status) {
    "PASS" { "[PASS]" }
    "FAIL" { "[FAIL]" }
    "SKIP" { "[SKIP]" }
    default { "[?]" }
}
Write-Host "$StatusMark Test completed"
```

```java
// 正确 - 测试结果标记
public enum TestStatus {
    PASS,    // 测试通过
    FAIL,    // 测试失败
    SKIP,    // 测试跳过
    ERROR    // 测试错误
}
```

## 文档中的 Emoji

**注意**: 此规则仅适用于代码文件。在以下文件中可以使用 Emoji：

- Markdown 文档 (`.md`)
- README 文件
- 设计文档
- 用户手册
- 项目说明

但即使在文档中，也建议谨慎使用 Emoji，确保在所有环境下都能正确显示。

## 检查清单

在提交代码前，检查以下内容：

- [ ] 所有 Java 注释中没有 Emoji
- [ ] 所有 PowerShell 脚本注释中没有 Emoji
- [ ] 所有日志输出中没有 Emoji
- [ ] 所有测试代码注释中没有 Emoji
- [ ] 所有配置文件注释中没有 Emoji
- [ ] 使用了清晰的 ASCII 文本替代

## 违规处理

如果发现代码中使用了 Emoji：

1. **立即修复**: 将 Emoji 替换为对应的 ASCII 文本
2. **测试验证**: 确保修改后代码能正常运行
3. **代码审查**: 在 Code Review 中标记此类问题

## 总结

**记住：代码注释中永远不要使用 Emoji，始终使用 ASCII 文本！**

这不仅是编码规范，更是确保代码在所有环境下都能正确运行的必要措施。
