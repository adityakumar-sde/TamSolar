# ==============================================================================
# TAM Solar Solutions - Local Network (LAN) Startup Script
# ==============================================================================

$FrontendPort = 5174
$BackendPort = 7811
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $PSScriptRoot "frontend"
$BackendDir = Join-Path $PSScriptRoot "backend"

# Detect active LAN IP (defaults to 192.168.5.14)
$detectedIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Wi-Fi*', 'Ethernet*' -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '169.254*' -and $_.IPAddress -notlike '127*' } |
    Select-Object -ExpandProperty IPAddress -First 1)

$LanIp = if ($detectedIp) { $detectedIp } else { "192.168.5.14" }

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "         TAM SOLAR SOLUTIONS - LOCAL NETWORK (LAN)" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Development Host LAN IP : $LanIp" -ForegroundColor Yellow
Write-Host ""
Write-Host "  >> OPEN ON MOBILE / OTHER PC (Connected to same Wi-Fi):" -ForegroundColor Green
Write-Host "     TAM Solar Website : http://${LanIp}:${FrontendPort}" -ForegroundColor White
Write-Host "     Admin Panel       : http://${LanIp}:${FrontendPort}/admin.html" -ForegroundColor White
Write-Host ""
Write-Host "  >> OPEN ON THIS COMPUTER (Localhost):" -ForegroundColor DarkCyan
Write-Host "     TAM Solar Website : http://localhost:${FrontendPort}" -ForegroundColor Gray
Write-Host "     Backend API       : http://localhost:${BackendPort}/api" -ForegroundColor Gray
Write-Host "     Health Check      : http://localhost:${BackendPort}/api/health" -ForegroundColor Gray
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# Verify directories exist
if (-not (Test-Path $BackendDir)) {
    Write-Error "Backend directory not found at $BackendDir"
    exit 1
}
if (-not (Test-Path $FrontendDir)) {
    Write-Error "Frontend directory not found at $FrontendDir"
    exit 1
}

# Start Backend in dedicated terminal window
Write-Host ">> Launching Spring Boot Backend on port $BackendPort..." -ForegroundColor Yellow
Start-Process powershell.exe -WorkingDirectory $BackendDir -ArgumentList "-NoExit", "-Command", "`$host.ui.RawUI.WindowTitle = 'TAM Solar Backend (Port $BackendPort)'; Write-Host 'Starting TAM Solar Spring Boot Backend...' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run"

# Start Frontend in dedicated terminal window
Write-Host ">> Launching Vite Frontend on port $FrontendPort (0.0.0.0)..." -ForegroundColor Yellow
Start-Process powershell.exe -WorkingDirectory $FrontendDir -ArgumentList "-NoExit", "-Command", "`$host.ui.RawUI.WindowTitle = 'TAM Solar Frontend (Port $FrontendPort)'; Write-Host 'Starting TAM Solar Frontend (Vite 0.0.0.0:$FrontendPort)...' -ForegroundColor Cyan; npm run dev"

Write-Host ""
Write-Host "Servers are launching in separate windows." -ForegroundColor Green
Write-Host "To stop the servers at any time, simply close their terminal windows." -ForegroundColor Gray
Write-Host ""
Write-Host "-----------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "FIREWALL NOTICE:" -ForegroundColor Magenta
Write-Host "If your mobile device fails to connect, Windows Firewall may be" -ForegroundColor Gray
Write-Host "blocking inbound ports $FrontendPort / $BackendPort on your Private Wi-Fi." -ForegroundColor Gray
Write-Host "You can allow them by running PowerShell as Administrator:" -ForegroundColor Gray
Write-Host "  New-NetFirewallRule -DisplayName 'TAM Solar Dev LAN' -Direction Inbound -LocalPort $FrontendPort,$BackendPort -Protocol TCP -Action Allow -Profile Private" -ForegroundColor DarkYellow
Write-Host "-----------------------------------------------------------" -ForegroundColor DarkGray
Write-Host ""

