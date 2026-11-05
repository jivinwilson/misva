$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$envFile = Join-Path $root '.env'
$dbName = 'misva'
$pgHost = 'localhost'
$pgPort = '5432'

function New-Secret { -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ }) }

function Invoke-Psql([string]$db, [string]$sql, [switch]$Scalar) {
    $args = @('-h', $pgHost, '-p', $pgPort, '-U', 'postgres', '-d', $db, '-X', '-v', 'ON_ERROR_STOP=1')
    if ($Scalar) { $args += @('-tA') }
    $out = $sql | & $psql @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $out" }
    return ($out | Out-String).Trim()
}

$secure = Read-Host 'PostgreSQL "postgres" admin password' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

try {
    Write-Host "`n== Inspection =="
    Write-Host (Invoke-Psql 'postgres' 'SELECT version();' -Scalar)
    $exists = Invoke-Psql 'postgres' "SELECT 1 FROM pg_database WHERE datname='$dbName';" -Scalar
    $owner = Invoke-Psql 'postgres' "SELECT 1 FROM pg_roles WHERE rolname='misva_owner';" -Scalar
    $app = Invoke-Psql 'postgres' "SELECT 1 FROM pg_roles WHERE rolname='misva_app';" -Scalar
    Write-Host "database '$dbName' exists: $([bool]$exists)"
    Write-Host "role misva_owner exists: $([bool]$owner)"
    Write-Host "role misva_app exists: $([bool]$app)"

    if ($exists) {
        Write-Host "`nDatabase already exists. Contents:"
        Write-Host (Invoke-Psql $dbName "SELECT n.nspname AS schema, count(c.oid) AS relations FROM pg_namespace n LEFT JOIN pg_class c ON c.relnamespace=n.oid WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname <> 'information_schema' GROUP BY 1 ORDER BY 1;")
        Write-Host "`nNothing was changed. Share this output with Claude before proceeding."
        return
    }
    if ($owner -or $app) {
        Write-Host "`nOne of the roles already exists. Nothing was changed; ask Claude how to proceed."
        return
    }
    if (Test-Path $envFile) {
        Write-Host "`n.env already exists. Nothing was changed; refusing to overwrite it."
        return
    }

    $answer = Read-Host "`nCreate database '$dbName' with roles misva_owner and misva_app? (yes/no)"
    if ($answer -ne 'yes') { Write-Host 'Aborted.'; return }

    $ownerPw = New-Secret
    $appPw = New-Secret

    Invoke-Psql 'postgres' @"
CREATE ROLE misva_owner LOGIN PASSWORD '$ownerPw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE misva_app LOGIN PASSWORD '$appPw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE DATABASE $dbName OWNER misva_owner ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE $dbName FROM PUBLIC;
GRANT CONNECT ON DATABASE $dbName TO misva_owner, misva_app;
"@ | Out-Null

    Invoke-Psql $dbName @"
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO misva_owner;
GRANT USAGE ON SCHEMA public TO misva_app;
ALTER DEFAULT PRIVILEGES FOR ROLE misva_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO misva_app;
ALTER DEFAULT PRIVILEGES FOR ROLE misva_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO misva_app;
"@ | Out-Null

    $jwt = (New-Secret) + (New-Secret)
    @"
APP_ENV=local

DB_HOST=$pgHost
DB_PORT=$pgPort
DB_NAME=$dbName
DB_OWNER_USER=misva_owner
DB_OWNER_PASSWORD=$ownerPw
DB_APP_USER=misva_app
DB_APP_PASSWORD=$appPw

JWT_SECRET=$jwt
REDIS_URL=
STORAGE_LOCAL_PATH=./storage
"@ | Set-Content $envFile -Encoding ascii

    Write-Host "`n== Verification =="
    foreach ($u in @(@('misva_owner', $ownerPw), @('misva_app', $appPw))) {
        $env:PGPASSWORD = $u[1]
        $r = & $psql -h $pgHost -p $pgPort -U $u[0] -d $dbName -X -tA -c "SELECT current_user, pg_encoding_to_char(encoding) FROM pg_database WHERE datname=current_database();" 2>&1
        Write-Host "$($u[0]): $r"
    }
    Write-Host "`nDone. Credentials were written to .env (git-ignored). They were not printed."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
