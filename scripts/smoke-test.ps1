# ============================================================
# AI Mall V1 smoke test: register -> login -> browse -> cart -> order -> AI chat
# Usage: pwsh -File scripts/smoke-test.ps1  (ASCII only, PS5.1 safe)
# ============================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'

function Json($obj) { $obj | ConvertTo-Json -Compress -Depth 5 }

# 1. register
$username = 'smoke_' + (Get-Random -Maximum 999999)
$reg = Invoke-RestMethod -Uri "$base/auth/register" -Method Post -ContentType 'application/json' -Body (Json @{ username = $username; password = '123456'; nickname = 'SmokeTester' })
Write-Host "[1] register: code=$($reg.code) user=$($reg.data.username)"

# 2. login
$login = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body (Json @{ username = $username; password = '123456' })
$token = $login.data.token
Write-Host "[2] login: code=$($login.code) token=$($token.Substring(0, [Math]::Min(12, $token.Length)))..."

$headers = @{ Authorization = $token }

# 3. product page
$list = Invoke-RestMethod -Uri "$base/products?page=1&size=10" -Headers $headers
Write-Host "[3] products: total=$($list.data.total) first=$($list.data.records[0].spuName)"

# 4. product detail
$detail = Invoke-RestMethod -Uri "$base/products/1" -Headers $headers
Write-Host "[4] detail: $($detail.data.spuName) skus=$($detail.data.skus.Count)"

# 5. add to cart sku=2 x2
$add = Invoke-RestMethod -Uri "$base/cart" -Method Post -Headers $headers -ContentType 'application/json' -Body (Json @{ skuId = 2; quantity = 2 })
Write-Host "[5] addCart: code=$($add.code) $($add.data.productName) x$($add.data.quantity)"

# 6. cart list
$cart = Invoke-RestMethod -Uri "$base/cart" -Headers $headers
$sum = ($cart.data | ForEach-Object { [decimal]$_.subtotal } | Measure-Object -Sum).Sum
Write-Host "[6] cart: items=$($cart.data.Count) total=$sum"

# 7. create order
$orderBody = Json @{
    items            = @(@{ skuId = 1; quantity = 1 }, @{ skuId = 2; quantity = 2 })
    receiverName    = 'Zhang San'
    receiverPhone   = '13800138000'
    receiverAddress = 'Shanghai Pudong xx Road 100'
}
$order = Invoke-RestMethod -Uri "$base/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body $orderBody
Write-Host "[7] order: no=$($order.data.orderNo) total=$($order.data.totalAmount) status=$($order.data.status)"

# 8. my orders
$orders = Invoke-RestMethod -Uri "$base/orders?page=1&size=10" -Headers $headers
Write-Host "[8] orders: total=$($orders.data.total) firstStatus=$($orders.data.records[0].status)"

# 9. AI chat (non-stream, verify DeepSeek key + preset product knowledge)
$chatBody = Json @{ message = 'Does AirSound Pro support bluetooth 5.3? How much is it?' }
try {
    $chat = Invoke-RestMethod -Uri "$base/chat" -Method Post -Headers $headers -ContentType 'application/json' -Body $chatBody -TimeoutSec 90
    Write-Host "[9] AI chat: code=$($chat.code)"
    Write-Host "    AI answer: $($chat.data)"
}
catch {
    Write-Host "[9] AI chat error: $($_.Exception.Message)"
}

Write-Host "`n==== SMOKE TEST DONE ===="