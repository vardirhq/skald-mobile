# Checksums

Every GitHub Release contains `SHA256SUMS.txt`. After downloading the release files into one directory, they can be verified on Linux with:

```bash
sha256sum -c SHA256SUMS.txt
```

Checksums detect corrupted or substituted downloads; Android signing separately proves that the APK was signed with the Skald Mobile release identity.
