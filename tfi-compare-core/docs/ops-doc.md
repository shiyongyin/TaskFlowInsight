# TFI Compare Core Operations Boundary

> Status: `IMPLEMENTED`

Core is an in-process synchronous library. It owns no database, queue, thread pool, scheduler, network client,
cache service, or shutdown lifecycle. Host applications own deployment, scaling, process health, and selection
of exactly one comparison artifact.

Operational failures are returned through typed comparison outcomes and bounded limitation/problem codes. Logs
must not contain paths, keys, business values, caller rules, exception messages, or implicit object
stringification. Diagnosis uses fixed event codes, bounded counts, outcome/completion values, and artifact
identity evidence.

Applications using `tfi-compare-core` must not also include `tfi-compare`. Existing applications that continue to
use `tfi-compare` require no Core dependency or migration.
