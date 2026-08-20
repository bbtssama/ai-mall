# ============================================================
# One-time style alignment script (V1 dev phase)
# Align with legacy projects (order-system-v2 / blog-system):
#   1. entity package -> bean
#   2. BizException -> BusinessException
#   3. Controller classes -> *RestController
#   4. API prefix -> /api/v1
#   5. Per-interface @Mapper registration (drop MapperScan)
#   6. getSize() -> getPageSize()
# Usage: pwsh -File scripts/align-style.ps1
# ============================================================
$root = 'C:\Users\user\Desktop\note\Projects\ai-mall'
$java = Join-Path $root 'backend\src\main\java\com\aimall'
$xmlDir = Join-Path $root 'backend\src\main\resources\mapper'
$feApi = Join-Path $root 'frontend\src\api\index.js'

function Replace-ContentIn([string]$path, [string]$old, [string]$new) {
    $content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $content = $content.Replace($old, $new)
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

# ---------- 1. move entities entity -> bean ----------
$domainDirs = @('user', 'goods', 'order', 'ai')
foreach ($dom in $domainDirs) {
    $fromDir = Join-Path $java "$dom\entity"
    $toDir = Join-Path $java "$dom\bean"
    if (Test-Path $fromDir) {
        New-Item -ItemType Directory -Force -Path $toDir | Out-Null
        Get-ChildItem $fromDir -Filter '*.java' | ForEach-Object {
            Move-Item $_.FullName (Join-Path $toDir $_.Name) -Force
        }
        Remove-Item $fromDir -Recurse -Force
    }
}

# rewrite package/import/resultType references
Get-ChildItem $java -Recurse -Filter '*.java' | ForEach-Object {
    Replace-ContentIn $_.FullName '.entity.' '.bean.'
}
Get-ChildItem $xmlDir -Filter '*.xml' | ForEach-Object {
    Replace-ContentIn $_.FullName '.entity.' '.bean.'
}

# ---------- 2. BizException -> BusinessException ----------
$bizFile = Join-Path $java 'common\exception\BizException.java'
if (Test-Path $bizFile) {
    Move-Item $bizFile (Join-Path $java 'common\exception\BusinessException.java') -Force
}
Get-ChildItem $java -Recurse -Filter '*.java' | ForEach-Object {
    Replace-ContentIn $_.FullName 'BizException' 'BusinessException'
}

# ---------- 3. rename controllers ----------
$renames = @{
    'AuthController'    = 'AuthRestController'
    'ProductController' = 'ProductRestController'
    'CartController'    = 'CartRestController'
    'OrderController'   = 'OrderRestController'
    'ChatController'    = 'ChatRestController'
}
Get-ChildItem $java -Recurse -Filter '*.java' | Where-Object { $renames.ContainsKey($_.BaseName) } | ForEach-Object {
    $newName = $renames[$_.BaseName] + '.java'
    $newPath = Join-Path $_.DirectoryName $newName
    Replace-ContentIn $_.FullName "class $($_.BaseName)" "class $newName"
    Move-Item $_.FullName $newPath -Force
}

# ---------- 4. API prefix /api/v1 ----------
Get-ChildItem $java -Recurse -Filter '*.java' | ForEach-Object {
    Replace-ContentIn $_.FullName '@RequestMapping("/api/' '@RequestMapping("/api/v1/'
}
# frontend api paths gain /v1
Replace-ContentIn $feApi "'/auth/" "'/v1/auth/"
Replace-ContentIn $feApi "'/products" "'/v1/products"
Replace-ContentIn $feApi "'/cart" "'/v1/cart"
Replace-ContentIn $feApi "'/orders" "'/v1/orders"
Replace-ContentIn $feApi "'/chat" "'/v1/chat"

# ---------- 5. per-interface @Mapper ----------
Get-ChildItem $java -Recurse -Filter '*Mapper.java' | ForEach-Object {
    Replace-ContentIn $_.FullName 'package com.aimall.' "package com.aimall.`nimport org.apache.ibatis.annotations.Mapper;`n"
    Replace-ContentIn $_.FullName 'public interface ' "@Mapper`npublic interface "
}

# ---------- 6. getSize() -> getPageSize() ----------
Get-ChildItem $java -Recurse -Filter '*.java' | ForEach-Object {
    Replace-ContentIn $_.FullName 'getSize()' 'getPageSize()'
}

Write-Host 'Style alignment done.'
$count = (Get-ChildItem $java -Recurse -Filter '*.java').Count
Write-Host "Java files: $count"