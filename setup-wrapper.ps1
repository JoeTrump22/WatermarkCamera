# 设置 Gradle Wrapper 脚本
# 在 Windows PowerShell 中运行此脚本以生成 gradle-wrapper.jar
# 这是 GitHub Actions 在线编译所必须的文件

Write-Host "=== 正在设置 Gradle Wrapper ===" -ForegroundColor Cyan

# 检查 Java 是否安装
try {
    $javaVersion = java -version 2>&1
    Write-Host "Java 已安装: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "错误: 未检测到 Java，请先安装 JDK 17" -ForegroundColor Red
    Write-Host "下载地址: https://adoptium.net/"
    exit 1
}

# 检查 Gradle 是否安装
try {
    $gradleVersion = gradle --version 2>&1
    Write-Host "Gradle 已安装" -ForegroundColor Green
} catch {
    Write-Host "下载 Gradle 8.5..." -ForegroundColor Yellow
    $url = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
    $output = "$env:USERPROFILE\gradle-8.5-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $output
    Expand-Archive -Path $output -DestinationPath "$env:USERPROFILE\gradle-8.5"
    $env:PATH = "$env:USERPROFILE\gradle-8.5\gradle-8.5\bin;$env:PATH"
}

# 生成 wrapper
Write-Host "生成 Gradle Wrapper..." -ForegroundColor Yellow
gradle wrapper --gradle-version 8.5

Write-Host "完成！现在可以上传到 GitHub 进行在线编译了。" -ForegroundColor Green
Write-Host ""
Write-Host "后续步骤:" -ForegroundColor Cyan
Write-Host "1. 将整个项目文件夹上传到 GitHub"
Write-Host "2. 在 GitHub 仓库的 Actions 页面查看编译进度"
Write-Host "3. 编译完成后在 Artifacts 中下载 APK 文件"