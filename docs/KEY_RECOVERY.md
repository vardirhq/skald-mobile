# Signing key recovery

The release keystore is an application identity, not a disposable CI credential. Back up the JKS file and its alias/passwords somewhere independent of GitHub.

If GitHub secrets are lost but the offline key remains, recreate the four release secrets and continue releasing normally. If the signing key itself is lost, existing direct-installed APKs cannot be upgraded by a newly signed APK with the same application ID.
