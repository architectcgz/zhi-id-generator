# PowerShell 环境规则

> 此文件定义了在 Windows PowerShell 环境中执行命令和脚本的重要规则。

## 执行环境

- **操作系统**: Windows
- **Shell**: PowerShell (不是 cmd.exe)
- **编码**: UTF-8
- **用户语言**: 中文

---

## ⚠️ 重要规则

### 1. HTTP 请求工具选择

| 必须使用 | 禁止使用 |
|----------|----------|
| `Invoke-WebRequest` | `curl` |
| `Invoke-RestMethod` | `wget` |
| 项目定义的 `Invoke-ApiRequest` 函数 | 任何需要额外安装的 HTTP 客户端 |

**原因**: 在 PowerShell 中 `curl` 是 `Invoke-WebRequest` 的别名，但行为可能不一致，容易导致错误。

### 2. 标准 API 请求函数

项目中已定义标准的 API 请求函数，应优先使用：

```powershell
function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json"; Headers = $Headers; ErrorAction = "Stop" }
        if ($Body) { $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) }
        $Response = Invoke-WebRequest @RequestParams
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $Result.Body = $StreamReader.ReadToEnd() | ConvertFrom-Json
                $StreamReader.Close()
            } catch { $Result.Error = $_.Exception.Message }
        } else { $Result.Error = $_.Exception.Message }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}
```

### 3. 字符编码注意事项

- URL 参数中的特殊字符必须使用 `[System.Uri]::EscapeDataString()` 编码
- JSON 请求体使用 `ConvertTo-Json -Depth 10` 确保嵌套对象正确序列化
- 响应内容使用 `ConvertFrom-Json` 解析

### 4. 禁止在 PowerShell 脚本中使用 Emoji

**严禁在 PowerShell 脚本中使用 Emoji 字符！**

| 禁止使用 | 应该使用 |
|----------|----------|
| `✅` | `[PASS]` 或 `"PASS"` |
| `❌` | `[FAIL]` 或 `"FAIL"` |
| `⏭️` | `[SKIP]` 或 `"SKIP"` |
| `❓` | `[?]` 或 `"UNKNOWN"` |
| 任何 Emoji 字符 | ASCII 文本替代 |

**原因**: Windows PowerShell 的默认编码可能无法正确处理 Emoji 字符，会导致脚本解析错误：
```
表达式或语句中包含意外的标记"FAIL" { "鉂?"。
```

**正确示例**:
```powershell
# 错误 - 使用 Emoji
$StatusEmoji = switch ($Result.Status) {
    "PASS" { "✅" }
    "FAIL" { "❌" }
}

# 正确 - 使用 ASCII 文本
$StatusMark = switch ($Result.Status) {
    "PASS" { "[PASS]" }
    "FAIL" { "[FAIL]" }
    "SKIP" { "[SKIP]" }
    default { "[?]" }
}
```

### 5. 路径处理

- 使用 `Join-Path` 拼接路径，避免手动拼接字符串
- 使用 `Split-Path -Parent $MyInvocation.MyCommand.Path` 获取脚本所在目录
- 相对路径基于脚本位置计算，不是当前工作目录

### 6. 变量作用域

- 全局变量使用 `$Global:` 前缀
- 脚本级变量使用 `$script:` 前缀
- 函数内修改外部变量需要正确的作用域前缀

### 7. 错误处理

- 使用 `try-catch` 块捕获异常
- HTTP 错误响应需要从 `$_.Exception.Response` 中提取
- 使用 `ErrorAction = "Stop"` 确保错误被捕获

### 8. 输出格式

- 使用 `Write-Host` 配合 `-ForegroundColor` 输出彩色信息
- 避免使用 `echo` (在 PowerShell 中是 Write-Output 的别名)
- 测试结果使用 `[PSCustomObject]` 存储

### 9. 执行脚本

- 脚本执行: `.\script-name.ps1`
- 确保在正确的目录下执行
- 如果遇到执行策略问题: `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process`

---

## 常见错误对照表

| 错误做法 | 正确做法 |
|------------|------------|
| `curl -X POST ...` | `Invoke-ApiRequest -Method "POST" ...` |
| `curl -H "Authorization: Bearer $token"` | `Invoke-WebRequest -Headers @{ "Authorization" = "Bearer $token" }` |
| `$url + "/" + $id` | `"$ServiceUrl/api/v1/resource/$id"` |
| `echo "message"` | `Write-Host "message"` |
| `$result.data.count` | `if ($result.data) { $result.data.Count }` |
| 直接访问可能为 null 的属性 | 先检查是否存在再访问 |
| `cd some/path && command` | 使用 `Push-Location` / `Pop-Location` 或指定完整路径 |
| 在脚本中使用 Emoji (如 ✅❌) | 使用 ASCII 文本 (如 [PASS] [FAIL]) |

---

## PowerShell vs Bash 命令对照

| Bash | PowerShell |
|------|------------|
| `cat file.txt` | `Get-Content file.txt` |
| `ls -la` | `Get-ChildItem` |
| `rm -rf dir` | `Remove-Item -Recurse -Force dir` |
| `mkdir -p path` | `New-Item -ItemType Directory -Path path -Force` |
| `cp -r src dest` | `Copy-Item -Recurse src dest` |
| `mv src dest` | `Move-Item src dest` |
| `grep pattern file` | `Select-String -Pattern pattern -Path file` |
| `export VAR=value` | `$env:VAR = "value"` |
| `command1 && command2` | `command1; if ($?) { command2 }` |
| `command1 \| command2` | `command1 \| command2` (管道相同) |

---

## 注意事项

1. **不要假设 Linux 命令可用** - Windows 环境下很多 Linux 命令不存在或行为不同
2. **路径分隔符** - Windows 使用 `\`，但 PowerShell 也接受 `/`
3. **大小写** - Windows 文件系统默认不区分大小写，但变量名区分
4. **换行符** - Windows 使用 `\r\n`，注意文件处理时的差异
5. **环境变量** - 使用 `$env:VAR_NAME` 访问环境变量
