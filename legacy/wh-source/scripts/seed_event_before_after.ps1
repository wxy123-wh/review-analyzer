param(
  [string]$BaseUrl = 'http://localhost:8080',
  [string]$Username = 'pm',
  [string]$Password = '123456',
  [string]$PlatformName = 'JD',
  [string]$ProductName = 'XX蓝牙耳机',
  [int]$Pages = 1,
  [string]$EventName = '双12活动',
  [ValidateSet('activity', 'version')]
  [string]$EventType = 'activity',
  [string]$StartDate = '2025-12-06',
  [string]$EndDate = '2025-12-10'
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Net.Http

function Invoke-ApiPostJson {
  param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][hashtable]$Body,
    [string]$Token
  )
  $json = ($Body | ConvertTo-Json -Depth 20)

  $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $Url)
  $req.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
  if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
  }

  $client = [System.Net.Http.HttpClient]::new()
  try {
    $resp = $client.SendAsync($req).Result
    $bytes = $resp.Content.ReadAsByteArrayAsync().Result
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if (-not $resp.IsSuccessStatusCode) {
      throw "HTTP $([int]$resp.StatusCode): $text"
    }
    return ($text | ConvertFrom-Json)
  } finally {
    $client.Dispose()
  }
}

function Invoke-ApiGet {
  param(
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Token
  )

  $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $Url)
  if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
  }

  $client = [System.Net.Http.HttpClient]::new()
  try {
    $resp = $client.SendAsync($req).Result
    $bytes = $resp.Content.ReadAsByteArrayAsync().Result
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if (-not $resp.IsSuccessStatusCode) {
      throw "HTTP $([int]$resp.StatusCode): $text"
    }
    return ($text | ConvertFrom-Json)
  } finally {
    $client.Dispose()
  }
}

function Assert-Ok {
  param([Parameter(Mandatory = $true)]$Resp)
  if ($null -eq $Resp) {
    throw 'Empty response'
  }
  if ($Resp.code -ne 0) {
    throw ("API error: code={0}, msg={1}" -f $Resp.code, $Resp.msg)
  }
  return $Resp.data
}

$login = Assert-Ok (Invoke-ApiPostJson -Url "$BaseUrl/api/auth/login" -Body @{ username = $Username; password = $Password })
$token = $login.token
if ([string]::IsNullOrWhiteSpace($token)) {
  throw 'Login succeeded but token is empty'
}

$crawl = Assert-Ok (Invoke-ApiPostJson -Url "$BaseUrl/api/crawl/run" -Token $token -Body @{
    platformName = $PlatformName
    productName  = $ProductName
    pages        = $Pages
  })

$products = Assert-Ok (Invoke-ApiGet -Url "$BaseUrl/api/meta/products" -Token $token)
$product = $products | Where-Object { $_.name -eq $ProductName } | Select-Object -First 1
if ($null -eq $product) {
  throw "Product not found after crawl/import: $ProductName"
}

$event = Assert-Ok (Invoke-ApiPostJson -Url "$BaseUrl/api/events" -Token $token -Body @{
    productId = [long]$product.id
    name      = $EventName
    type      = $EventType
    startDate = $StartDate
    endDate   = $EndDate
  })
$eventId = $event.id
if (-not $eventId) {
  throw 'Create event succeeded but eventId is empty'
}

$report = Assert-Ok (Invoke-ApiGet -Url "$BaseUrl/api/evaluate/before-after?eventId=$eventId" -Token $token)

Write-Host "OK: crawlRun inserted=$($crawl.inserted) skipped=$($crawl.skipped) errors=$($crawl.errors) batchId=$($crawl.batchId)"
Write-Host "OK: productId=$($product.id) name=$($product.name)"
Write-Host "OK: eventId=$eventId name=$($report.event.name) type=$($report.event.type) $($report.event.startDate)~$($report.event.endDate)"
Write-Host "OK: before reviewCount=$($report.before.reviewCount) negRate=$($report.before.negRate)"
Write-Host "OK: after  reviewCount=$($report.after.reviewCount)  negRate=$($report.after.negRate)"
Write-Host "OK: keywordChanges count=$(@($report.keywordChanges).Count)"
