# Security scanning

Defense-in-depth tooling covers source, dependencies, code, images, and supply-chain provenance.
All layers have local scripts. The current GitLab pipeline does not yet invoke those security
scripts, so a clean local run is additional evidence rather than CI parity.

## Authentication hiện tại và reset password

Xác thực dùng JWT stateless với access token và refresh token, trả trong JSON body.
API được bảo vệ nhận `Authorization: Bearer <accessToken>`; gateway và downstream xác minh
chữ ký, issuer, audience, loại token và thời hạn tại chỗ. Không lưu session đăng nhập,
không dùng cookie xác thực hoặc Redis projection. Gateway không gọi IAM introspection
trên mỗi request.

`POST /api/v1/auth/refresh` nhận `{ "refreshToken": "..." }`, hoạt động cả khi access token
đã hết hạn. IAM kiểm tra tài khoản và quyền hiện tại trước khi cấp cặp token mới. Thời hạn
mặc định: access 15 phút, refresh 7 ngày; refresh giữ thời điểm hết hạn tuyệt đối ban đầu.
Token refresh cũ còn hiệu lực đến hết hạn; không có cơ chế lưu trạng thái rotation/reuse.

Logout ghi audit, trả 204 và client xoá cả hai token. JWT đã cấp vẫn hợp lệ đến hết hạn;
không cam kết thu hồi tức thời. Tenant context lấy từ JWT đã xác minh; đổi tenant qua
`POST /api/v1/auth/switch-tenant` với Bearer access token và `{tenantId,refreshToken}`,
rồi dùng cặp token mới. Header `X-Tenant-Id` từ client không cấp hoặc đổi quyền tenant.

Owner-issued temporary password và `mustChangePassword` là đặc tả từ backend nguồn,
chưa được triển khai trong Java IAM hiện tại. Không dùng luồng Better Auth/NestJS hoặc
cam kết revoke phiên làm hướng dẫn vận hành. Trước khi triển khai reset password, cần
định nghĩa riêng quy tắc đổi mật khẩu bắt buộc và hiệu lực của JWT đã cấp.

Các công cụ scanning bên dưới là hướng dẫn nguồn; kiểm tra script tồn tại trong checkout
trước khi chạy, không coi chúng là kiểm tra CI đã được áp dụng cho Java.

## Layers

| #   | Layer          | Tool          | Local script                      | Current GitLab CI |
| --- | -------------- | ------------- | --------------------------------- | ----------------- |
| 1   | Dependencies   | OSV-Scanner   | `scripts/security/scan-deps.sh`   | not wired         |
| 2   | SAST           | Semgrep       | `scripts/security/scan-sast.sh`   | not wired         |
| 3   | Container CVEs | Trivy         | `scripts/security/scan-images.sh` | not wired         |
| 4   | Supply chain   | Cosign + SLSA | `scripts/security/sign-images.sh` | not wired         |

There is no dedicated secret scanner. Semgrep's `p/secrets` ruleset is the only
thing looking for committed credentials, and it works on the current tree — not
on history. Treat any credential that ever reached a commit as compromised and
rotate it; nothing in this repo will tell you it is there.

Run the whole stack locally: `./scripts/security/scan-all.sh`.

## What each tool catches

### OSV-Scanner — dependency CVEs

Reads `pnpm-lock.yaml` directly so transitive resolutions match what's
installed. Uses the OSV.dev advisory feed which aggregates GitHub Security
Advisories, GHSA, NVD, language ecosystems (npm, PyPI, Maven, …) and OS
package advisories. Complements `pnpm audit` (npm registry only) and Trivy
(image-level).

```bash
./scripts/security/scan-deps.sh
```

### Semgrep — SAST

Pattern-based static analysis tuned for this stack:

- `p/typescript`, `p/nodejs`, `p/javascript`
- `p/owasp-top-ten`, `p/jwt`, `p/sql-injection`, `p/xss`, `p/command-injection`
- `p/secrets` (AST-level secret heuristics — the only secret detection there is)
- `p/dockerfile`, `p/ci`

```bash
./scripts/security/scan-sast.sh           # gate on ERROR
./scripts/security/scan-sast.sh --audit   # show WARNING/INFO too
```

### Trivy — image CVEs

Scope: OS packages (Alpine APK), language packages (npm), and known misconfigurations.

```bash
./scripts/security/scan-images.sh                # all four images
./scripts/security/scan-images.sh api            # subset
TRIVY_EXIT_CODE=0 ./scripts/security/scan-images.sh   # warn-only
```

Image scanning is not enforced by `.gitlab-ci.yml` yet. The local `build-*.sh` scripts skip it to
keep the inner loop fast, so run `scripts/security/scan-images.sh` manually before promotion.

### Cosign — supply-chain signatures

The repository contains a Cosign helper, but the current GitLab pipeline does not sign published
images. Verification is meaningful only after an operator or future CI job has signed a digest.

Verify a published tag locally:

```bash
COSIGN_IDENTITY="<approved-oidc-signing-identity>" \
COSIGN_OIDC_ISSUER="<approved-oidc-issuer>" \
IMAGE_REGISTRY="<registry>" IMAGE_NAMESPACE="<namespace>" IMAGE_TAG="<tag>" \
./scripts/security/sign-images.sh verify
```

Do not claim SLSA provenance or signed-image guarantees until those artifacts are actually
published and verified for the deployed digest.

## Local developer workflow

```bash
# One-shot full audit (no images required)
./scripts/security/scan-all.sh

# Just the fast check before pushing
./scripts/security/scan-deps.sh

# Build production images locally (fast — no scan)
./scripts/build-prod.sh

# Required manual pre-promotion image scan until CI owns the gate
./scripts/security/scan-images.sh
```

Lefthook runs `lint-staged` + `typecheck` on `pre-commit` and commitlint on
`commit-msg`. There is no `pre-push` hook. Full tests and builds remain CI gates
so normal commits stay fast. To run hooks manually:

```bash
pnpm exec lefthook run pre-commit
pnpm exec lefthook run pre-push
```

## CI flow

`.gitlab-ci.yml` currently gates lint, typecheck, coverage tests, production build, migration drift,
and API e2e. It builds images for release merge requests and publishes commit-SHA images from
`main`; the manual deploy job is a placeholder. OSV, Semgrep, Trivy, Cosign, SBOM and provenance
must be added as explicit jobs before this document may describe them as CI gates.

## Tuning

| Variable          | Purpose                                | Default          |
| ----------------- | -------------------------------------- | ---------------- |
| `TRIVY_VERSION`   | Pin Trivy image                        | `0.62.0`         |
| `TRIVY_SEVERITY`  | Severities Trivy gates on              | `HIGH,CRITICAL`  |
| `TRIVY_EXIT_CODE` | `0` = warn-only, `1` = gate            | `1`              |
| `TRIVY_CACHE`     | Trivy DB cache directory               | `~/.cache/trivy` |
| `SEMGREP_VERSION` | Pin Semgrep image                      | `1.99.0`         |
| `OSV_VERSION`     | Pin OSV-Scanner image                  | `v2.0.2`         |
| `COSIGN_VERSION`  | Pin Cosign image                       | `v2.4.1`         |
| `COSIGN_IDENTITY` | Expected signing identity for `verify` | (required)       |

## Container hardening reference

Per-service Dockerfile properties relied on by the scanners above:

- Pinned base image by SHA256 digest (rebuilds reproducible, immune to
  tag mutation).
- Non-root user (UID 1001), `STOPSIGNAL SIGTERM`, tini PID 1.
- Multi-stage build — only pruned production node_modules + compiled JS in
  the final layer; no `node_modules` ever copied from the host
  (`.dockerignore` enforces this).
- npm CLI vendored by the Node Alpine base is removed in the production
  stage — strips the upstream picomatch CVE that `pnpm.overrides` cannot
  reach.
- HEALTHCHECK uses `node`'s built-in `http` module instead of `wget`,
  removing one binary and its CVE surface.
- OCI labels (`org.opencontainers.image.*`) populated for registry, scanner,
  and policy-engine consumption.
