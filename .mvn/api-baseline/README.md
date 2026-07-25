# API Compatibility Baseline

This directory is the repository-owned Maven input used only by the `api-compat` profile.
It contains the exact 3.0.0 POM/JAR payloads required by the Core and all-in-one japicmp gates.

## Provenance

The payloads were copied on 2026-07-12 from the local Maven artifacts that had already been
approved as the 3.0.0 compatibility input. Keeping those bytes in this repository makes CI
resolution reproducible. These files are not evidence that version 3.0.0 was published to any
external Maven repository.

Only the parent POM and the Core, Spring Starter, Compare, Ops, and all-in-one POM/JAR payloads
are retained. Maven metadata, examples, source JARs, Javadoc JARs, local update markers, and
repository state are deliberately excluded.

## Verification

From this directory, verify every payload before use:

```bash
shasum -a 256 -c SHA256SUMS
```

Every payload also has an adjacent `.sha256` authority sidecar and a `.sha1` transport sidecar
required by Maven 3.9 repository resolution. Any baseline replacement requires explicit approval,
replacement of the payload bytes, regeneration of all checksum forms, and a passing
`ApiBaselineRepositoryContractTests` run.
