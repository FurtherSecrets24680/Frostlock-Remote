# Builds the current working tree (including uncommitted + untracked changes) on GitHub Actions
# without committing anything, by pushing a throwaway snapshot commit to a scratch branch.
#
# The local repo state is never modified: a temporary Git index is used, so the real index,
# worktree and stash list are left exactly as they were.
#
#   .\tools\ci-snapshot.ps1                 # debug build of current WIP
#   .\tools\ci-snapshot.ps1 -Release        # signed release APKs + AABs of current WIP
#   .\tools\ci-snapshot.ps1 -KeepBranch     # leave the snapshot branch on the remote
#
# WARNING: the snapshot branch is pushed to origin, so this WIP becomes visible to anyone
# who can read the repository while the run is in progress.

[CmdletBinding()]
param(
    [switch]$Release,
    [switch]$KeepBranch,
    [string]$Branch = 'ci-snapshot',
    [string]$Workflow = 'android.yml',
    [string]$Target
)

$ErrorActionPreference = 'Stop'
$root = git rev-parse --show-toplevel
if (-not $root) { throw 'Not inside a git repository' }
Set-Location $root

if ([string]::IsNullOrWhiteSpace($Target)) {
    $Target = if ($Release) { 'build\release' } else { 'build\debug' }
}

# Actions only runs a workflow that exists at the pushed commit, so HEAD must already have it.
git cat-file -e "HEAD:.github/workflows/$Workflow" 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "HEAD does not contain .github/workflows/$Workflow. Merge the workflow into the current branch (or run this from ci/github-actions) first."
}

if (-not (git diff --quiet HEAD --) -or (git ls-files --others --exclude-standard)) {
    Write-Host 'Snapshotting working tree (uncommitted + untracked)...'
} else {
    Write-Host 'Working tree is clean; snapshotting HEAD as-is.'
}

# Build a commit from the working tree with a temp index so nothing local is touched.
$tmpIndex = Join-Path ([System.IO.Path]::GetTempPath()) ("ci-index-" + [guid]::NewGuid().ToString('N'))
$env:GIT_INDEX_FILE = $tmpIndex
try {
    git read-tree HEAD
    git add -A
    $tree = (git write-tree).Trim()
    $message = "ci snapshot $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $sha = (git commit-tree $tree -p HEAD -m $message).Trim()
} finally {
    Remove-Item -Force $tmpIndex -ErrorAction SilentlyContinue
    Remove-Item Env:\GIT_INDEX_FILE -ErrorAction SilentlyContinue
}

Write-Host "Snapshot commit: $sha"
git push --force origin "${sha}:refs/heads/$Branch"
if ($LASTEXITCODE -ne 0) { throw "Failed to push snapshot branch '$Branch'." }

if ($Release) {
    gh workflow run $Workflow --ref $Branch -f build_release=true | Out-Null
} else {
    gh workflow run $Workflow --ref $Branch | Out-Null
}
if ($LASTEXITCODE -ne 0) { throw 'Failed to dispatch the GitHub Actions workflow.' }

# Snapshot branches are excluded from automatic push builds; dispatch exactly one run.
$run = $null
foreach ($attempt in 1..20) {
    $runs = gh run list --branch $Branch --workflow $Workflow --event workflow_dispatch --limit 10 `
        --json databaseId,headSha,event,createdAt | ConvertFrom-Json |
        Where-Object { $_.headSha -eq $sha -and $_.event -eq 'workflow_dispatch' }
    if ($runs) { $run = $runs | Select-Object -First 1; break }
    Start-Sleep -Seconds 3
}
if (-not $run) { throw "No workflow run found for snapshot $sha" }

Write-Host "Watching run $($run.databaseId)..."
gh run watch $run.databaseId --interval 15
$conclusion = gh run view $run.databaseId --json conclusion -q '.conclusion'

Remove-Item -Recurse -Force $Target -ErrorAction SilentlyContinue
$artifacts = gh api "repos/{owner}/{repo}/actions/runs/$($run.databaseId)/artifacts" -q '.artifacts[].name'
foreach ($name in ($artifacts -split "`n" | Where-Object { $_ })) {
    gh run download $run.databaseId -n $name -D $Target
}
Get-ChildItem -Recurse $Target -Include *.apk, *.aab | ForEach-Object {
    "{0,8:N2} MB  {1}" -f ($_.Length / 1MB), $_.FullName.Substring($root.Length + 1)
}

if (-not $KeepBranch) {
    $nativeErrorPreference = $PSNativeCommandUseErrorActionPreference
    $PSNativeCommandUseErrorActionPreference = $false
    try {
        git push origin --delete $Branch 2>&1 | Out-Null
        $deleteExitCode = $LASTEXITCODE
    } finally {
        $PSNativeCommandUseErrorActionPreference = $nativeErrorPreference
    }
    if ($deleteExitCode -ne 0) { throw "Failed to delete remote snapshot branch '$Branch'." }
    Write-Host "Deleted remote branch $Branch"
}

if ($conclusion -ne 'success') { throw "Build finished with status: $conclusion" }
Write-Host "Snapshot build OK: $conclusion"
