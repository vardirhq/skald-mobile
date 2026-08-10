# Hotfixes

Normal releases are prepared from `main`. For a hotfix, make the fix on an appropriate branch, ensure the final release commit is merged or otherwise made reachable from `main`, add the user-visible fix to `[Unreleased]`, then use the normal release preparation flow. The signed release workflow deliberately rejects release commits that are not ancestors of `main`.
