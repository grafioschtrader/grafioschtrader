# GTNet Security Concept — Central Server, Instance Identity and Price Verification

**Status**: Concept only — nothing in this document is implemented yet.

**Positioning**: The first release of this concept is an **identity, accountability and
evidence-collection system**. Automatic network-wide punishment (quarantine, blacklisting) is
enabled only after evidence semantics, false-positive behavior and collusion resistance have been
validated with production-like data. Sections that describe sanctions therefore distinguish
between *observation-only* and *enforcing* operation.

## Design Decisions

Decisions that shape the whole concept, fixed up front:

| # | Question | Decision |
|---|----------|----------|
| D1 | Is price-origin authenticity required? | **Hop accountability**: the authenticated immediate supplier is liable for all data it forwards; the origin UUID is a claimed, unverified attribute. Origin signatures are optional future work (see Optional Enhancements). |
| D2 | What identity material does an instance hold? | **Keypair + issuer-signed certificate**: locally generated keypair (private key never leaves the instance), certificate issued by the central server binding issuer ID, network ID, instance UUID, domain hash, public key, validity period and serial number. |
| D3 | Who may join the public secure network? | **Manual approval** by a central-server administrator, plus a **maturation period** (default 30 days) before a new identity's queries and reports influence any reputation, plus registration rate limits. |
| D4 | Where does central security data live? | **Dedicated schema `gtnet_central`** with its own datasource/transaction manager; only this schema is Galera-replicated. Central servers remain normal GT instances with local portfolios. |
| D5 | Who is liable for forwarded bad data? | The **immediate authenticated supplier** (see D1). Trust penalties are never assigned to a claimed origin. |
| D6 | Which authority is exchanged during handshake? | Issuer ID + network ID from the registration certificate. Instances registered with unrelated issuers cannot connect, even if both are "secure". |
| D7 | How stale may positive security data become? | Positive verification/trust results have a configurable maximum age (default 14 days); cached blacklist/quarantine states remain enforced until an authoritative response removes them. |
| D8 | When do reports change reputation? | Only after the observation-only calibration phase; and only reports from ACTIVE, matured, independent reporters count. |

---

## Problem Statement

### Current Situation

GTNet enables peer-to-peer exchange of price data (intraday and historical) between
Grafioschtrader instances. The following protections **already exist**:

| Mechanism | Where | What it provides |
|-----------|-------|------------------|
| Mutual GUID token authentication | `GTNetConfig.tokenThis/tokenRemote` (lib), exchanged in first handshake, refreshable via `GT_NET_TOKEN_REFRESH_*` | Peers cannot impersonate each other on an established connection |
| Admission control | `GTNet.allowServerCreation`, `GTNetMessageAnswer` auto-answer rules | Unknown peers can be rejected (`GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S`) |
| Rate limiting | `GTNet.dailyRequestLimit`, counters in `GTNetConfig`, `requestViolationCount` | Misbehaving peers are throttled and their violations counted |
| Exchange statistics | `gt_net_exchange_log`, `gt_net_supplier_detail*` | Per-peer sent/updated counts and instrument coverage |
| Local supplier quality score | `SupplierScoreCalculator` (`coverageCount × successRate`) | Better suppliers are preferred for AC_OPEN requests |

What is still **missing**:

1. **No portable identity**: A peer is identified only by its `domainRemoteName`. Identity cannot
   be verified against any authority, and a blocked actor can reappear under a new domain.
2. **No per-price attribution**: `gt_net_lastprice` and `gt_net_historyquote` are shared pools
   keyed by instrument only — a stored price does not record which instance supplied it, and once
   a price is forwarded through an intermediate instance, even the immediate source is lost.
3. **No cross-instance reputation**: The supplier score is purely local. An instance that delivers
   bad data to peer A looks pristine to peer B.
4. **No verification against reference sources**: Received prices are never compared with the
   instance's own connectors, so wrong prices (accidental or malicious) go undetected.
5. **No network-wide sanctions**: Even an identified bad actor can only be blocked locally, one
   instance at a time.

### Threat Scenarios

| Threat | Description | Current Defense | Defense after this concept |
|--------|-------------|-----------------|----------------------------|
| **Accidental Bad Data** | Misconfigured connector provides wrong prices | None (success rate only measures delivery) | Sampled verification, anomaly evidence, quarantine |
| **Malicious Manipulation** | Actor intentionally provides false prices | None | Hop accountability: the authenticated sender is liable |
| **Identity Recreation** | Blocked actor re-registers under a new domain | Only local `allowServerCreation` | Manual approval, maturation period, rate limits (reduced, not eliminated — see Attack Scenario Analysis) |
| **Data Poisoning** | Gradual injection of slightly wrong prices | None | Deviation thresholds + anomaly-rate statistics over rolling windows |
| **Forged Attribution** | Forwarder stamps someone else's UUID on manipulated data | N/A | Claimed origin is never trusted; liability stays with the authenticated sender |
| **Colluding Reporters** | Cluster of identities files false anomaly reports | N/A | Reporter independence, maturation, dedup, false-reporting penalties, manual review before blacklist |

### Privacy Requirement

Price recipients should not be able to identify the **source domain URL** of prices that were
forwarded through intermediate instances. A centrally-issued UUID provides **pseudonymous**
attribution: it hides the domain from ordinary peers, but it is not anonymity — a stable UUID
allows correlation of an origin's instruments and activity over time, and the central authority
can map it to a domain. See the [Privacy](#privacy) section for the resulting obligations.

---

## Solution: Two Separate Networks

The solution divides GTNet into two **completely separate networks** that do not communicate with
each other:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ┌───────────────────────────────┐                     │
│                    │      CENTRAL SERVER CLUSTER   │                     │
│                    │         (Galera Sync)         │                     │
│                    │                               │                     │
│                    │  grafioschtrader.info ◄─────► grafioschtrader.com  │
│                    │                               │                     │
│                    │  • Issues certificates/UUIDs  │                     │
│                    │  • Verifies instances         │                     │
│                    │  • Manages sanction states    │                     │
│                    │  • Stores reputation evidence │                     │
│                    │  • Receives anomaly reports   │                     │
│                    │  • Provides thresholds        │                     │
│                    └───────────────┬───────────────┘                     │
│                                    │                                     │
│         ┌──────────────────────────┼──────────────────────────┐          │
│         ▼                          ▼                          ▼          │
│   ┌───────────┐             ┌───────────┐             ┌───────────┐      │
│   │Instance A │◄───────────►│Instance B │◄───────────►│Instance C │      │
│   │UUID: xxx  │             │UUID: yyy  │             │UUID: zzz  │      │
│   └───────────┘             └───────────┘             └───────────┘      │
│                                                                          │
│                         SECURE GTNET                                     │
│              (issuer-signed certificates, one network ID)                │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│   ┌───────────┐             ┌───────────┐             ┌───────────┐      │
│   │Instance X │◄───────────►│Instance Y │◄───────────►│Instance Z │      │
│   │(no UUID)  │             │(no UUID)  │             │(no UUID)  │      │
│   └───────────┘             └───────────┘             └───────────┘      │
│                                                                          │
│                        UNSECURE GTNET                                    │
│                      (No central server)                                 │
└─────────────────────────────────────────────────────────────────────────┘

              ╔═══════════════════════════════════════════╗
              ║  SECURE AND UNSECURE NETWORKS DO NOT      ║
              ║  COMMUNICATE WITH EACH OTHER              ║
              ╚═══════════════════════════════════════════╝
```

### Network Comparison

| Aspect | Secure GTNet | Unsecure GTNet |
|--------|--------------|----------------|
| **Identity** | Keypair + issuer-signed certificate + UUID | None (domain name only) |
| **Registration** | Required, manually approved by central admin | Not required |
| **Verification** | Central server validates certificates/UUIDs | None |
| **Sanctions** | Centrally managed, staged (quarantine/blacklist) | None |
| **Price Attribution** | Authenticated immediate sender + claimed origin | None |
| **Communication** | Only instances of the same issuer/network | Only with other unsecure instances |
| **Identity recreation** | Reduced by manual approval + maturation | Trivially possible |
| **Price Verification** | Decentralized sampling, anomalies reported | None |
| **Peer authentication** | Mutual GUID tokens (existing) + certificate | Mutual GUID tokens (existing) |

The existing token handshake remains the transport-level authentication in **both** networks. The
secure network adds a portable, centrally-verified, cryptographic identity on top of it.

---

## Layering: GNet Core vs Grafioschtrader

GTNet is already split into a reusable library layer (modules `grafiosch-base` /
`grafiosch-server-base`, internally "GNet core", message codes 0–54 in `GNetCoreMessageCode`) and
the application layer (`grafioschtrader-common` / `grafioschtrader-server`, message codes 60–95 in
`GTNetMessageCodeType`). The security concept follows the same seam. **Library code must never
reference grafioschtrader classes** (e.g. `AssetclassType`, connectors), which dictates the split:

### GNet core (grafiosch library) — domain-agnostic

| Concern | Artifacts |
|---------|-----------|
| Instance identity, keypair, certificate, network mode | Entity `GTNetInstanceIdentity` (`gt_net_instance_identity`) |
| Registration lifecycle with central server | Message codes 40–42, `SecureRegisterRequestHandler` (central side), hardened callback verification |
| Certificate renewal / key rotation | Message code 49 |
| UUID/certificate verification | Message codes 43–44 |
| Daily query (trust, sanctions, app payload) | Message codes 45–46, `GTNetTrustCache` (`gt_net_trust_cache`) |
| Reputation evidence & sanction states | Central schema: `gt_net_registered_instance`, `gt_net_partner_relation`, `gt_net_anomaly_report` (generic core), `gt_net_sanction_audit` |
| Anomaly report transport | Message codes 47–48 (generic envelope; detail payload is app-defined JSON) |
| Central outbox (offline queue, failover) | Entity `GTNetCentralOutbox` (`gt_net_central_outbox`) |
| Handshake gating (issuer/network compatibility) | Extension of `FirstHandshakeMsg` + `FirstHandshakeRequestHandler` |
| Config keys | `g.gnet.central.servers`, `g.gnet.central.issuer.*`, `g.gnet.central.role.enabled`, `g.gnet.trust.cache.max.age.days` |

### Grafioschtrader application — price-specific

| Concern | Artifacts |
|---------|-----------|
| What an anomaly *is* | Canonical comparison of received prices against own connectors (see Canonical Price Comparison) |
| Anomaly detail persistence | `gt_net_anomaly_report_lastprice`, `gt_net_anomaly_report_historical` (central schema, 1:1 to generic core rows) |
| Thresholds per asset class | `gt_net_threshold_config` (central), `gt_net_threshold_cache` (instance), keyed on `AssetclassType` (+ optional `SpecialInvestmentInstruments`, crypto rule) |
| Threshold distribution | App-specific JSON payload inside the generic daily-query response (`MessageEnvelope.payload`) |
| Price attribution | `received_from_id_gt_net` + `claimed_source_instance_uuid` columns; propagation in `InstrumentPriceDTO` / `HistoryquoteRecordDTO` |
| Config keys | `gt.gtnet.verification.sampling.rate` (globalparameter with `input_rule`) |

The wiring uses the extension points that already carry the price-exchange codes:
`GTNetMessageCodeRegistry` (core codes auto-registered, app codes registered in
`GTStartUp.registerGTNetMessageCodes()`), `GTNetModelHelper.registerModel(...)` for payload models,
and Spring auto-discovery of `GTNetMessageHandler` beans by `GTNetMessageHandlerRegistry`.

---

## Central Server Architecture

### Central Server = Normal Grafioschtrader Instance

The central server is **not a dedicated server** but a normal Grafioschtrader instance with the
central-server role enabled (`g.gnet.central.role.enabled=true`). Users can track their portfolios
on these instances while they also serve the secure GTNet.

**Initial Central Servers**:
- `https://grafioschtrader.info`
- `https://grafioschtrader.com`

### Central Persistence: Dedicated Schema + Second Datasource

This decision is made **before any central entity is implemented**, because it determines entity
placement, Flyway setup and transaction boundaries:

- Central security data lives in a dedicated schema **`gtnet_central`**, accessed through a
  **second datasource** with its own `EntityManagerFactory` and transaction manager. Only this
  schema is Galera-replicated; the instance's normal `grafioschtrader` database stays local.
- **Flyway ownership**: A second Flyway instance (own migration location, e.g.
  `db/migration-central/`, own history table) manages `gtnet_central`. It runs only when
  `g.gnet.central.role.enabled=true`.
- **Entity/repository boundaries**: Central entities live in dedicated packages
  (lib: `grafiosch.gtnet.central.entities`, app: `grafioschtrader.gtnet.central.entities`) so the
  persistence-unit package scan can separate them from normal entities.
- **Atomicity**: A generic anomaly core row and its app-specific detail row are written in **one
  transaction of the central datasource** (both tables live in `gtnet_central`). No transaction
  ever spans both datasources; cross-datasource consistency is not required because instance-side
  and central-side data have independent lifecycles.

Tables replicated via Galera (all in `gtnet_central`):

| Table | Layer | Purpose |
|-------|-------|---------|
| `gt_net_registered_instance` | lib | Identity, lifecycle state, certificate data, sanction state |
| `gt_net_partner_relation` | lib | Requester→partner observation edges (social proof) |
| `gt_net_anomaly_report` | lib | Generic anomaly core (reporter, accused, event key, dedup) |
| `gt_net_sanction_audit` | lib | Immutable audit trail of sanction decisions |
| `gt_net_anomaly_report_lastprice` | app | Last-price anomaly detail |
| `gt_net_anomaly_report_historical` | app | Historical-price anomaly detail |
| `gt_net_threshold_config` | app | Thresholds per asset class |
| `gt_net_registration_challenge` | lib | Pending registration challenges (short-lived) |

### Central Server Role

| Function | Description |
|----------|-------------|
| **Certificate/UUID Issuance** | Signs a registration certificate binding UUID, domain hash and public key |
| **Instance Verification** | Validates certificates/UUIDs (never reveals the domain) |
| **Reputation Evidence** | Persists relationship edges and deduplicated anomaly evidence |
| **Sanction Management** | Maintains staged sanction states with audit trail |
| **Threshold Configuration** | Provides thresholds per asset class (app payload) |
| **Policy Distribution** | Publishes policy version and configuration epoch |

---

## Issuer and Network Identity

A "secure" flag alone cannot distinguish trust networks: two instances registered with unrelated
central servers would both claim to be secure without sharing any authority. Therefore:

- **Issuer ID**: A stable identifier of the certificate-issuing authority (e.g.
  `gtnet-public-01`). The issuer holds a signing keypair; its public key fingerprint is pinned in
  every member's configuration (`g.gnet.central.issuer.fingerprint`).
- **Network ID**: A stable identifier of the trust network (e.g. `gtnet-public`). One issuer
  serves exactly one network in the first release.
- **Registration certificate** (issued at registration, stored by the instance):

```json
{
  "issuerId": "gtnet-public-01",
  "networkId": "gtnet-public",
  "instanceUuid": "550e8400-e29b-41d4-a716-446655440000",
  "domainHash": "SHA-256(canonical domain URL)",
  "publicKey": "base64(Ed25519 public key)",
  "issuedAt": "2026-07-11T12:00:00Z",
  "expiresAt": "2027-07-11T12:00:00Z",
  "serial": 4711,
  "signature": "base64(issuer signature over all fields above)"
}
```

The certificate contains the **domain hash**, not the domain, so presenting the certificate to a
peer does not reveal the domain of a forwarded origin; the direct peer already knows the sender's
domain from the connection itself and can verify `domainHash` against it.

**Key management**:
- *Issuer key rotation*: The issuer publishes old + new key during a transition window; the daily
  query carries the current issuer key set and a monotonically increasing **configuration epoch**.
  Certificates are re-signed lazily at renewal.
- *Certificate renewal*: Instances renew via message code 49 before `expiresAt` (recommended at
  75% of validity). Renewal keeps UUID and domain, may rotate the instance key.
- *Revocation*: The central server marks a certificate serial revoked (lifecycle state REVOKED or
  key compromise). Revoked serials are distributed via the daily query; peers must drop
  connections whose certificate serial is revoked.
- *Disagreeing central servers*: All entries in `g.gnet.central.servers` must belong to the same
  issuer/network. If a server presents an unknown issuer key, the client ignores that server and
  raises an admin alert; it never mixes answers from different issuers.

---

## Message Codes

All security interactions run through the existing M2M channel (`MessageEnvelope` →
`GTNetM2MResource` → handler registry). No new REST endpoints are introduced. New **core** codes
are added to `GNetCoreMessageCode` in the free range 40–49 (current core codes occupy 0–30 and
50–54; 60+ is reserved for applications), plus one handshake rejection code in the free 8/9 slot:

| Code | Name | Direction | Purpose |
|------|------|-----------|---------|
| 8 | `GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` | responder → initiator | Handshake rejected: incompatible network mode or issuer/network, or invalid/revoked certificate |
| 40 | `GT_NET_SECURE_REGISTER_SEL_RR_C` | instance → central | Registration request (public key + canonical domain; manual approval → response may be delayed) |
| 41 | `GT_NET_SECURE_REGISTER_ACCEPT_S` | central → instance | Certificate issued (also used as renewal acceptance) |
| 42 | `GT_NET_SECURE_REGISTER_REJECTED_S` | central → instance | Registration/renewal rejected (machine-readable reason code) |
| 43 | `GT_NET_SECURE_VERIFY_SEL_RR_C` | instance → central | Verify a partner's UUID/certificate serial |
| 44 | `GT_NET_SECURE_VERIFY_RESPONSE_S` | central → instance | validity + sanction state (never the domain) |
| 45 | `GT_NET_SECURE_DAILY_QUERY_SEL_RR_C` | instance → central | Trust/sanction states for partner UUIDs |
| 46 | `GT_NET_SECURE_DAILY_QUERY_RESPONSE_S` | central → instance | Results + issuer key set + epoch + app payload (GT: thresholds) |
| 47 | `GT_NET_SECURE_ANOMALY_REPORT_SEL_C` | instance → central | Submit anomaly report (idempotent, generic core + app detail payload) |
| 48 | `GT_NET_SECURE_ANOMALY_REPORT_ACK_S` | central → instance | Report accepted / duplicate / rejected |
| 49 | `GT_NET_SECURE_CERT_RENEW_SEL_RR_C` | instance → central | Certificate renewal / instance key rotation (answered with 41/42) |

Because these codes live in `GNetCoreMessageCode`, they are auto-registered by the
`GTNetMessageCodeRegistry` constructor. Payload models are registered in `GTNetModelHelper`;
handlers are ordinary Spring `@Component` beans discovered by `GTNetMessageHandlerRegistry`.
The central-side handlers are active only when `g.gnet.central.role.enabled=true`. The manual
approval step of code 40 uses the existing `HandlerResult.AwaitingManualResponse` mechanism that
already serves handshake approval.

### Protocol Requirements (all new codes)

Every new request/response payload includes:

- **`schemaVersion`** (int): payload schema version. Unknown *fields* are ignored (rolling
  upgrades); unknown *versions* are rejected with a machine-readable error.
- **Correlation**: the existing `MessageEnvelope` reply correlation
  (`idSourceGtNetMessage`/`replyToSourceId`) is mandatory for all `_RR_` codes.
- **Idempotency key** for state-changing requests: registration and renewal use a client-generated
  `requestUuid`; anomaly reports use `reportUuid`. The central server enforces uniqueness and
  answers duplicates with the original result (duplicate-safe acknowledgement).
- **Replay protection**: `MessageEnvelope.timestamp` must be within a configurable clock-skew
  window (default ±5 minutes) of the receiver's clock; outside the window the request is rejected.
  Single-use challenge nonces (registration, handshake) are additionally tracked until expiry.
- **Size limits**: maximum envelope payload size, maximum `queryUuids` batch size (default 100,
  partial processing is not allowed — over-limit requests are rejected with the limit in the error
  payload), maximum `appPayload` size.
- **Stable error codes**: rejections carry a machine-readable code
  (e.g. `REG_DOMAIN_NOT_CANONICAL`, `REG_CALLBACK_FAILED`, `REG_PENDING_APPROVAL`,
  `REG_RATE_LIMITED`, `VERIFY_UNKNOWN_UUID`, `REPORT_DUPLICATE`, `SCHEMA_VERSION_UNSUPPORTED`,
  `CLOCK_SKEW_EXCEEDED`, `NOT_AUTHORIZED_FOR_STATE`), plus a human-readable message.
- **Authorization matrix**: each code defines who may send it and in which lifecycle state:

| Code | Requires established tokens | Requires lifecycle state | Notes |
|------|-----------------------------|--------------------------|-------|
| 40 | yes (handshake with central first) | none (creates PENDING) | Rate-limited per source IP and registrable domain |
| 41/42 | yes | — | Central → applicant only |
| 43/44/45/46 | yes | ACTIVE | PENDING instances may not query others |
| 47/48 | yes | ACTIVE | Reports from non-ACTIVE or immature reporters are stored but never counted |
| 49 | yes | ACTIVE or SUSPENDED | Proves possession of the current instance key |

- **Rate limits**: per-source and global limits for registration attempts, verification, daily
  queries and anomaly intake, on top of the existing per-peer `dailyRequestLimit` mechanism.
- **Unknown message codes** are rejected explicitly before handler dispatch (the
  `GTNetMessageCodeRegistry` lookup already fails for unknown codes; the rejection must produce a
  stable error, not a silent drop).
- **Bootstrap exceptions** (messages processable without full authorization) are exactly:
  `GT_NET_PING` (0), `GT_NET_FIRST_HANDSHAKE_SEL_RR_S` (1) — as today — and nothing else. Code 40
  requires the handshake-established tokens.

---

## Registration

### Canonical Domain URL

A registered domain URL is normalized before hashing, storing or comparing:

- Scheme **must be `https`**; the URL must be publicly resolvable (no plain HTTP, no IP literals).
- Host is lower-cased; internationalized domain names are converted to punycode (A-label) form.
- Default port `:443` is removed; any explicit non-default port is retained.
- Path must be empty or `/` and is stored without a trailing slash; query and fragment are
  forbidden.
- Example: `HTTPS://Example.COM:443/` → `https://example.com`.

### Registration Lifecycle

```
             manual approval + verified callback
  PENDING ────────────────────────────────────────► ACTIVE
     │                                                │  ▲
     │ rejected / expired (cleanup)                   │  │ reinstate (manual)
     ▼                                                ▼  │
  (removed)                                       SUSPENDED
                                                      │
                                                      ▼ manual, audited
                                                   REVOKED (terminal; serial revoked)
```

- **PENDING**: Application received, challenge verified or awaiting verification, awaiting manual
  approval. A PENDING instance is **not** an accepted GTNet partner: it may not query, report or
  exchange data. PENDING applications and their provisional peer entries (GTNet row + tokens
  created by the bootstrap handshake) **expire and are cleaned up** after a configurable period
  (default 14 days) if not approved.
- **ACTIVE**: Certificate issued. Reputational influence still gated by the maturation period.
- **SUSPENDED**: Temporarily barred (e.g. certificate expired without renewal, quarantine
  escalation, admin action). May renew/appeal.
- **REVOKED**: Terminal. Certificate serial distributed as revoked.

### Registration Flow

The instance communicates with the central server as a regular GTNet peer: first the normal
handshake (mutual transport tokens), then the registration message. The handshake with a central
server while unregistered is a narrowly scoped **bootstrap**: the central server accepts it but
marks the peer provisional, and the applicant may send only code 40 (and ping) until ACTIVE.

```
Instance                                    Central Server
──────────────────────────────────────────────────────────────────────────
1. Generate Ed25519 keypair locally (private key never leaves the instance)

2. Normal first handshake (existing protocol; provisional peer state)
   GT_NET_FIRST_HANDSHAKE_SEL_RR_S  ────────────────────────────────►
   ◄──────────────────────────────  GT_NET_FIRST_HANDSHAKE_ACCEPT_S

3. GT_NET_SECURE_REGISTER_SEL_RR_C
   { requestUuid, schemaVersion, publicKey, canonicalDomainUrl }
   ─────────────────────────────────────────────────────────────────►

4.                                  Validate canonical URL + rate limits.
                                    Create registration challenge:
                                    cryptographically random nonce,
                                    single-use, TTL 10 minutes.

5.                                  Hardened callback to the CLAIMED domain
                                    (see Callback Hardening): challenge
                                    request to the M2M endpoint
   ◄─────────────────────────────────────────────────────────────────
   Respond: nonce signed with the instance private key ──────────────►
                                    (proves domain control AND key
                                     possession in one step)

6.                                  State = PENDING; admin notified
                                    (manual approval, existing
                                     AwaitingManualResponse mechanism)

7.                                  On approval: issue certificate,
                                    state = ACTIVE
   ◄─────────────────────────────  GT_NET_SECURE_REGISTER_ACCEPT_S
   payload: { certificate }         (on rejection/expiry: 42 + cleanup)

8. Store certificate in gt_net_instance_identity; instance is now SECURE
```

### Callback Hardening

The verification callback is a server-side request to an attacker-supplied URL and must be treated
as an SSRF risk:

- Only the canonical `https` URL is called; **redirects are not followed**.
- Before **every** callback the host is DNS-resolved and each resolved IP is validated: loopback,
  private (RFC 1918), link-local, multicast, reserved and cloud-metadata ranges
  (e.g. 169.254.169.254) are rejected. The validated IP is the one actually connected to
  (no re-resolution between check and use).
- Strict timeout (default 10 s), response-size limit (default 64 KB), and a concurrency cap on
  outstanding callbacks.
- Registration attempts are rate-limited per source IP and per registrable domain (eTLD+1);
  repeated failures back off exponentially.
- The challenge nonce is cryptographically random, bound to the request UUID, single-use and
  expires after 10 minutes; used or expired nonces are rejected (replay protection). Challenge
  state lives in `gtnet_central` (`gt_net_registration_challenge`) so any cluster node can
  complete a verification started by another node.

### Identity Lifecycle Events

| Event | Procedure |
|-------|-----------|
| **Certificate renewal** | Code 49 before expiry; signed with the current instance key; issues a new serial. |
| **Instance key rotation** | Code 49 carrying the new public key, signed with the *old* key. Old serial is revoked. |
| **Domain migration** | New canonical URL submitted via code 49, signed with the instance key; central re-runs the callback verification against the new domain, keeps UUID, issues a new certificate (new domain hash). Manual approval required. |
| **Compromised key / installation** | Admin-initiated revocation (state REVOKED or forced re-registration). Revoked serial distributed via daily query; peers drop matching connections. |
| **Database restore / clone** | Two installations sharing one identity will present the same certificate from different domains; peers detect the domain-hash mismatch, and the central server detects concurrent conflicting daily queries → automatic SUSPENDED + admin alert. Recovery = key rotation (49) from the legitimate installation, or re-registration. |
| **Identity loss (DB gone, no key backup)** | The old identity cannot be recovered (private key gone). Re-register the domain: the old certificate for that domain hash is revoked upon successful re-verification of domain control. |

The private key is stored encrypted at rest (Jasypt, consistent with existing secret handling) and
is never exported through the API or backups tooling; operators should treat it like the JWT
secret.

---

## Verification

Any ACTIVE instance can verify a partner's identity via message code 43/44:

```
Request  (43): { "uuid": "...", "certificateSerial": 4711 }
Response (44): {
  "valid": true,              // UUID exists, serial current, state ACTIVE
  "lifecycleState": "ACTIVE", // PENDING/ACTIVE/SUSPENDED/REVOKED
  "sanctionState": "NORMAL",  // NORMAL/SUSPECTED/QUARANTINED/BLACKLISTED
  "epoch": 1234               // configuration epoch of the answering node
}
// domainUrl is NOT returned (privacy)
```

Verification responses are cached in `gt_net_trust_cache` with the positive-cache max age
(`g.gnet.trust.cache.max.age.days`, default 14). **Query authorization**: an instance may verify
only UUIDs of declared partners (peers it has a relationship edge with or an incoming handshake
from) — arbitrary enumeration of the UUID space is rejected and logged (see Privacy).

---

## Network Separation Enforcement

### Handshake Extension

`FirstHandshakeMsg` (grafiosch-base) is extended:

```java
public class FirstHandshakeMsg {
  public String tokenThis;          // existing
  public Boolean secureNetwork;     // NEW: sender's network mode (null = legacy peer → unsecure)
  public String certificate;        // NEW: sender's registration certificate (JSON, if secure)
  public String challengeSignature; // NEW: sender's signature over the responder-supplied
                                    //      handshake nonce (proves key possession)
}
```

The initiator obtains a handshake nonce via an initial ping exchange (or the responder returns its
nonce in an intermediate handshake step); the exact nonce transport is settled in Phase 1 design.
The responder's ACCEPT carries the responder's own certificate and signature so verification is
mutual.

### Connection Validation

`FirstHandshakeRequestHandler.preProcess(...)` — where the not-in-list rejection already happens —
gains the compatibility check:

```java
boolean canConnect(FirstHandshakeMsg msg, byte[] handshakeNonce) {
  GTNetInstanceIdentity identity = instanceIdentityService.get();
  if (identity.isSecureNetwork()) {
    if (!Boolean.TRUE.equals(msg.secureNetwork) || msg.certificate == null) {
      return false;                                   // unsecure or legacy peer
    }
    Certificate cert = parse(msg.certificate);
    return issuerTrust.isTrusted(cert.issuerId, cert.signature)   // pinned issuer key
        && cert.networkId.equals(identity.getNetworkId())          // same trust network
        && cert.isCurrentlyValid()                                 // validity window
        && !revocationCache.isRevoked(cert.serial)                 // revocation
        && verifySignature(cert.publicKey, handshakeNonce, msg.challengeSignature)
        && sanctionCache.allowsNewConnections(cert.instanceUuid);  // not QUARANTINED/BLACKLISTED
  }
  return !Boolean.TRUE.equals(msg.secureNetwork);     // unsecure only pairs with unsecure
}
```

Rejections use `GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` (code 8) with a stable reason code.
The partner's UUID and certificate serial are stored in `gt_net_config` on acceptance.

**Bootstrap exception**: an instance in secure mode without a certificate (state PENDING or not
yet registered) may handshake *only* with its configured central servers, in order to register.

### Switching Network Mode

- **Unsecure → secure**: allowed only when no data-exchange relationships are active, or after
  explicitly disconnecting them. Existing unsecure peer entries are kept but demoted to
  *disabled* (tokens retained, exchange blocked) so the operator can switch back. Pooled prices
  gathered in unsecure mode keep `claimed_source_instance_uuid = NULL` and
  `received_from_id_gt_net` of the old peer; they are **retained but marked unattributed** — they
  are served onward only to unsecure networks, never into the secure network.
- **Registration failure/rollback**: if registration is rejected or expires, the instance falls
  back to unsecure mode automatically and re-enables its disabled peers only on operator request.
- **Secure → unsecure**: allowed any time (drops secure connections); the certificate remains
  stored so the instance can return to secure mode without re-registration while the certificate
  is valid. Switching modes is **not** blocked by central-server unavailability — an instance may
  always *leave* the secure network; it just cannot *join* one while no central server is
  reachable.

---

## Price Attribution (Hop Accountability)

**Decision (D1/D5)**: The authenticated immediate supplier is accountable for every price it
delivers, whether self-produced or forwarded. The origin UUID is carried along as a **claim** that
is useful for diagnostics and future signed-origin upgrades, but it is never treated as proven and
never used to assign penalties.

Every imported record retains **both**:

- `received_from_id_gt_net` — the authenticated peer the record came from (FK to `gt_net`,
  `ON DELETE SET NULL`; a deleted peer must not delete price history).
- `claimed_source_instance_uuid` — the UUID the sender claims to be the origin. Explicitly
  unverified: a malicious forwarder can fake it, which is precisely why liability stays with the
  sender.

Rules:

- An instance supplying a price from its **own connectors** sets `claimedSourceInstanceUuid` to
  its own UUID.
- An instance **forwarding** a pooled price passes the stored claim through unchanged.
- Anomaly reports accuse the **authenticated sender** (`accusedUuid` = the peer that delivered the
  bad record). The claimed origin is attached to the report as context only.
- Since `gt_net_lastprice` holds one row per instrument, its attribution columns reflect the
  origin claim and sender of the *currently stored* value.

The wire DTOs (`InstrumentPriceDTO`, `HistoryquoteRecordDTO`) gain the
`claimedSourceInstanceUuid` field; the receiving handlers stamp `received_from_id_gt_net` from the
authenticated envelope source. Cryptographically **signed origin records** — which would upgrade
the claim to proof — are specified under Optional Enhancements and become feasible because every
instance already holds a registered keypair (D2).

---

## Price Verification (Decentralized, GT layer)

### Key Principle

Each instance in the secure network **verifies a random sample of received prices locally** using
its own connectors. Only confirmed anomalies are reported to the central server. Random sampling
makes it hard for a supplier to *anticipate* which prices will be checked; it does not by itself
prevent connector collusion or coordinated false reporting — those are addressed by the evidence
rules and staged sanctions below.

```
Instance receives price from authenticated peer P
                   │
                   ▼
   ┌─────────────────────────────────────────┐
   │  Random sampling (gt.gtnet.verification. │
   │  sampling.rate %, default 5)             │
   └─────────────────────────────────────────┘
                   │
                   ▼
   ┌─────────────────────────────────────────┐
   │  Canonical comparison preconditions met? │──── no ──► INCONCLUSIVE (no report)
   └─────────────────────────────────────────┘
                   │ yes
                   ▼
   ┌─────────────────────────────────────────┐
   │  Fetch reference price from own          │
   │  connector; deviation > threshold AND    │
   │  > minimum absolute deviation?           │
   └─────────────────────────────────────────┘
          │                     │
          ▼                     ▼
   within tolerance      candidate anomaly
   (no action)                  │
                                ▼
                   corroborate with a second,
                   independent connector when available;
                   sanction-relevant reports REQUIRE it
                                │
                                ▼
                   GT_NET_SECURE_ANOMALY_REPORT_SEL_C
                   via the central outbox
```

### Canonical Price Comparison

A received price and a connector reference price may only be compared when all preconditions
hold; otherwise the result is **INCONCLUSIVE** and no report is created:

| Rule | Requirement |
|------|-------------|
| **Instrument identity** | Same instrument key (`ISIN:CURRENCY` / `FROM:TO`); the connector must quote the same listing — GT's per-instrument connector configuration already targets a specific venue/quote, and the comparison uses exactly the instrument's configured connectors. |
| **Currency & unit** | Reference must be in the instrument's currency; minor-unit quotes (e.g. GBp vs GBP) are normalized before comparison. |
| **Price-field semantics** | Last price compares `last` to `last`; historical compares `close` to `close`. Bid/ask or official-close values from a connector that does not deliver a comparable field make the check INCONCLUSIVE. |
| **Historical adjustment** | Historical comparisons require both sides to be raw (unadjusted) closes — GTNet exchanges raw closes; a connector delivering only split/dividend-adjusted series is unusable for comparison. |
| **Timestamp skew** | Last-price comparison requires both timestamps within a configurable window (default 15 minutes) and the market open for the instrument's stock exchange (GT trading calendars); stale reference prices → INCONCLUSIVE. |
| **Trading calendar** | Historical comparison only for dates that are trading days of the instrument's exchange. |
| **Corporate actions** | No comparison within an exclusion window (default ±3 trading days) around known splits/dividends of the instrument, or around symbol/ISIN changes. |
| **Deviation floor** | An anomaly requires percentage deviation > threshold **and** absolute deviation > a per-currency minimum (guards against tick-size noise on tiny prices). |
| **Rounding** | Deviations are computed on values rounded per the project convention (`double(22,8)` storage, `DataHelper` rounding); the deviation itself is rounded to 4 decimals before threshold comparison. |
| **Connector independence** | The reference connector must be a different data provider than the one the accused claims to have used (if that hint is present); for sanction-relevant reports, two references from independent providers are required. |

### Relationship to the Existing Supplier Score

The local `SupplierScoreCalculator` (`coverageCount × successRate`) and the network reputation are
**complementary and remain separate**:

| | Supplier score (existing) | Network reputation (new) |
|---|---|---|
| Scope | Local to one instance | Network-wide, computed centrally |
| Measures | Delivery quality (coverage, update success) | Evidence of incorrect data + partnership breadth |
| Used for | Ordering suppliers for AC_OPEN requests | Quarantining/excluding bad actors, informing admins |

Blending network reputation into supplier ordering is an optional enhancement, considered only
after sanction behavior is proven stable.

---

## Anomaly Reports

A report consists of a **generic core** (lib, feeds reputation) plus an **app-specific detail**
(GT, price data). The instance sends both in one message (code 47) through the central outbox;
the central server stores core and detail **atomically in one `gtnet_central` transaction**.

### Idempotency and Event Deduplication

- Each report carries a client-generated **`reportUuid`** (unique per reporter; unique constraint
  on the central server). Redelivery after a lost ACK is answered with `REPORT_DUPLICATE` and the
  original result — duplicate-safe.
- Each report carries an **event key** identifying the underlying price event:
  `eventKey = SHA-256(accusedUuid | instrumentKey | priceDate-or-timestamp-bucket | reportKind)`
  (last-price timestamps are bucketed to 15 minutes). Many instances observing the same bad record
  produce reports with the same event key; evidence counting uses **unique (eventKey, reporter)**
  pairs, and an event contributes as *one* anomaly regardless of how many reporters saw it —
  reporter multiplicity raises *confidence*, not *count*.

### Generic Core (lib)

`reporterUuid` is taken from the authenticated sender (never from the payload); stored fields
include `reportUuid`, `accusedUuid`, `eventKey`, `reportKind` (app discriminator), `reportedAt`,
and a snapshot of the reporter's standing at intake (`reporterMature`, reporter sanction state) so
later decisions are auditable and unaffected by subsequent reporter changes.

### Last-Price Detail (GT)

```java
public class LastPriceAnomalyReportMsg {
  public int schemaVersion;
  public String reportUuid;             // idempotency key
  public String instrumentKey;          // ISIN:CURRENCY or FROM:TO
  public Byte assetClass;               // AssetclassType value
  public Byte specialInvestmentInstrument;  // SpecialInvestmentInstruments value, null for currency pairs

  public String claimedSourceInstanceUuid; // context only — never the accused

  public LocalDateTime suppliedTimestamp;  // from accused (authenticated sender)
  public double suppliedLast;

  public LocalDateTime expectedTimestamp;  // from own connector
  public double expectedLast;
  public String connectorUsed;          // connector ID, e.g. "gt.datafeed.yahoo"
  public String secondConnectorUsed;    // corroborating provider (required for sanction relevance)
  public Double secondExpectedLast;

  public double deviationPercent;       // rounded to 4 decimals per comparison rules
}
```

### Historical Detail (GT)

Identical structure with `LocalDate priceDate`, `suppliedClose`, `expectedClose` (and second
connector fields) instead of the timestamp/last fields.

### Anomaly Thresholds

Thresholds are configured on the central server **per `AssetclassType`**, optionally refined per
`SpecialInvestmentInstruments`, and distributed via the daily query. Historical prices have
**stricter (smaller) thresholds** than last prices.

Base thresholds per `AssetclassType` (real enum values):

| AssetclassType (value) | Last Price % | Historical % |
|------------------------|--------------|--------------|
| EQUITIES (0) | 3.0 | 1.5 |
| FIXED_INCOME (1) | 2.5 | 1.0 |
| MONEY_MARKET (2) | 1.5 | 0.5 |
| COMMODITIES (3) | 3.0 | 1.5 |
| REAL_ESTATE (4) | 3.0 | 1.5 |
| MULTI_ASSET (5) | 3.0 | 1.5 |
| CONVERTIBLE_BOND (6) | 2.5 | 1.0 |
| CREDIT_DERIVATIVE (7) | 4.0 | 2.0 |
| CURRENCY_PAIR (8) | 1.5 | 0.5 |

Refinements (override rows):

| Combination | Last Price % | Historical % | Rationale |
|-------------|--------------|--------------|-----------|
| any + CFD (4) | 4.0 | 2.0 | Wider issuer spreads |
| CURRENCY_PAIR + crypto | 7.0 | 3.0 | Crypto pairs (detected via `Currencypair.getIsCryptocurrency()` / `GlobalConstants.CRYPTO_CURRENCY_SUPPORTED`) — flagged in the lookup, not a `SpecialInvestmentInstruments` value |

Lookup order on the instance: exact (`assetClass`, `specialInvestmentInstrument`) → crypto rule →
base (`assetClass`, sentinel). A per-currency **minimum absolute deviation** table accompanies the
percentage thresholds. *All values are configurable on the central server; administrator changes
survive software updates (see Database Migration).*

---

## Daily Query

Each ACTIVE instance queries the central server daily (message codes 45/46) for:
- Sanction and trust information of its communication partners
- Revoked certificate serials and the current issuer key set
- App-specific data — for GT: anomaly thresholds

### Request (45)

```json
{
  "schemaVersion": 1,
  "queryUuids": ["partner-1-uuid", "partner-2-uuid"]
}
```

The requester's own UUID is resolved from the authenticated peer connection. `queryUuids` must
contain only declared partners, no duplicates and not the requester itself; violations are
rejected. Batch size is limited (default 100).

### Response (46)

```json
{
  "schemaVersion": 1,
  "epoch": 1234,
  "policyVersion": 3,
  "results": [
    { "uuid": "partner-1-uuid", "sanctionState": "NORMAL",      "confidence": "HIGH",
      "lifecycleState": "ACTIVE" },
    { "uuid": "partner-2-uuid", "sanctionState": "QUARANTINED", "confidence": "MEDIUM",
      "lifecycleState": "ACTIVE", "until": "2026-08-01T00:00:00Z" }
  ],
  "revokedSerials": [17, 233],
  "issuerKeys": [ { "fingerprint": "...", "publicKey": "...", "validFrom": "..." } ],
  "appPayload": {
    "thresholds": [
      { "assetClass": 0, "specialInvestmentInstrument": null, "lastPrice": 3.0, "historical": 1.5 },
      { "assetClass": 8, "crypto": true, "lastPrice": 7.0, "historical": 3.0 }
    ]
  }
}
```

`results`, `revokedSerials` and `issuerKeys` are handled by the lib layer (fills
`gt_net_trust_cache` and the revocation cache); `appPayload` is passed opaque to an application
callback (GT fills `gt_net_threshold_cache`). This keeps the library free of `AssetclassType`
references. Instead of publishing exact numeric scores, the response carries **sanction state plus
a coarse confidence band** — precise evidence values remain on the central server (privacy,
gaming resistance).

### Social Proof: Relationship Edges

The daily query has a dual purpose: obtaining trust information and **signaling active
partnerships**. The central server persists explicit observation edges rather than a bare counter:

```
gt_net_partner_relation:
  requester_uuid, partner_uuid, first_seen_at, last_seen_at
  UNIQUE (requester_uuid, partner_uuid)
```

- An edge is upserted (`last_seen_at` refreshed) whenever `partner_uuid` appears in a requester's
  daily query.
- Self-edges and duplicates are rejected at intake.
- Edges **expire** from all calculations when `last_seen_at` is older than the activity window
  (default 30 days); expired edges are eventually deleted (retention, default 12 months).
- `activePartnerCount(X)` = number of distinct, unexpired edges *pointing at* X from ACTIVE,
  matured requesters. Edges from identities in the same operator group (see collusion limits)
  count at reduced weight.

---

## Reputation and Evidence

The first release computes **auditable evidence components** instead of a single opaque score.
All components are windowed, deduplicated and reproducible from stored data:

| Component | Definition |
|-----------|-----------|
| **Verification status** | Lifecycle state + certificate validity (binary facts). |
| **Partner breadth** | Unexpired unique inbound relationship edges from matured, independent requesters (window: 30 days). Defined as 0 when no edges exist; no normalization by a network maximum (normalization would make scores interdependent and gameable). |
| **Anomaly rate** | Unique anomaly events (by event key) against the accused within a rolling window (default 30 days), divided by prices supplied by the accused in that window (from exchange-log statistics reported with the daily query). 10 anomalies among 10 supplied prices and among 10 million are thereby distinguished. |
| **Absolute anomaly count** | Unique events in the window (guards the rate against tiny denominators; a minimum supplied-volume applies before the rate is meaningful). |
| **Reporter independence** | Number of distinct matured ACTIVE reporters per event and per accused, with operator-group weighting. |
| **Confidence** | Coarse band (LOW/MEDIUM/HIGH) derived from evidence volume, reporter independence and corroboration (second connector present). |

Evidence rules:

- **Maturation**: reports and edges from identities younger than the maturation period
  (default 30 days) are stored but excluded from all calculations (D8).
- **Decay**: anomaly events leave the active window after 30 days; raw reports are retained for
  audit per the retention policy, then deleted.
- **Reporter snapshot**: each report stores the reporter's standing at intake; recalculating
  history with later reporter changes is explicitly not done (auditability).
- **False-reporting penalty**: if an event is manually reviewed and judged unfounded (e.g. caused
  by a corporate action the reporter should have excluded), all reporters of that event receive a
  strike; reporters exceeding a strike rate over their report volume lose reporting eligibility
  (their reports are stored but not counted) until manually reinstated. This is itself recorded in
  the audit trail.
- **Collusion limits**: identities sharing a registrable domain, operator declaration or
  registration source are grouped; a group's aggregate influence on any single accused is capped
  (default: counts as at most 1 independent reporter and at most 1 relationship edge).

---

## Sanctions: Staged States

```
NORMAL ──► SUSPECTED ──► QUARANTINED ──► BLACKLISTED
   ▲            │              │       (manual, audited)
   └────────────┴──────────────┘
        automatic recovery / manual restore
```

| State | Meaning | Effect on peers |
|-------|---------|-----------------|
| **NORMAL** | No significant evidence | None |
| **SUSPECTED** | Evidence above the watch level | None (informational; admins see it) |
| **QUARANTINED** | Automatic, temporary | Peers stop *importing* data from the instance; existing connections stay for announcements; expires automatically (default 7 days) unless renewed by new evidence |
| **BLACKLISTED** | Manual, audited decision | Peers refuse connections; certificate may be revoked |

**Automatic action is limited to QUARANTINED** and requires *all* of:

- a rolling observation window (30 days),
- unique anomaly events (not raw report rows),
- a minimum volume of supplied prices in the window (default ≥ 500),
- anomaly rate above the policy limit **and** an absolute minimum of unique events (default ≥ 5),
- at least 3 independent, matured reporters across those events,
- corroborated (two-connector) evidence on at least one event.

Every automatic transition records an **evidence snapshot** (the component values used) and the
**policy version** in `gt_net_sanction_audit`. During the initial **observation-only phase**, the
same logic runs and records what it *would* have done, without changing any state (D8) — this
calibrates thresholds against false positives before enforcement is switched on.

**BLACKLISTED** requires manual review by a central administrator, except when evidence is
overwhelming per an explicitly versioned policy (e.g. sustained maximal anomaly rate with high
confidence over multiple windows). Manual and calculated states are recorded separately
(`sanction_state` + audit rows carrying decision source, actor, reason, evidence snapshot, policy
version, issue time and optional expiry). Unblacklisting/appeal is a manual, audited transition.

---

## Central Outbox

`GTNetMessageAttempt` tracks delivery of stored GTNet messages to established `idGtNet` targets
and is processed by message-specific background tasks — it is **not** a general offline queue and
provides no endpoint failover. Central-bound traffic (anomaly reports; retriable daily-query and
renewal attempts) uses a dedicated outbox on the instance side (lib):

```
gt_net_central_outbox:
  id_outbox            BIGINT PK AUTO_INCREMENT
  request_uuid         VARCHAR(36) NOT NULL UNIQUE   -- idempotency key (= reportUuid for reports)
  message_code         TINYINT NOT NULL
  schema_version       INT NOT NULL
  payload              MEDIUMTEXT NOT NULL           -- serialized message payload
  issuer_id            VARCHAR(64) NOT NULL          -- target network, not a local idGtNet
  state                TINYINT NOT NULL              -- PENDING / DELIVERED / DEAD
  attempt_count        INT NOT NULL DEFAULT 0
  last_error           VARCHAR(512)
  next_attempt_at      DATETIME
  created_at           DATETIME NOT NULL
```

- Exponential backoff with jitter between attempts; per-attempt failover across the configured
  central endpoints (deterministic shuffled order, see High Availability).
- Maximum retention (default 30 days) after which entries move to DEAD (dead-letter) and surface
  in the admin UI.
- Delivery is duplicate-safe end to end: the server deduplicates on `request_uuid`, the client
  marks DELIVERED on either an accept or a duplicate acknowledgement.

---

## High Availability and Consistency

Central servers replicate `gtnet_central` via MariaDB Galera (configured in the database layer,
wsrep — not in application properties).

### Consistency Rules

| Operation | Requirement |
|-----------|-------------|
| Registration, approval, renewal, revocation, sanction changes | **Quorum required** (writable primary component). A node outside the quorum rejects these with a retriable error. |
| Verify (43), daily query (45) | May be answered from any node, including a stale one; the response carries the node's **epoch** (monotonic configuration version, bumped on policy/issuer-key/sanction changes). |
| Anomaly intake (47) | Any node; idempotent on `request_uuid`; uniqueness enforced by the replicated constraint. |
| Registration challenges | Stored in `gtnet_central` so any node can complete a verification. |

### Client Rules

- Endpoint order per operation is a **deterministic shuffle** seeded by instance UUID + date —
  load is balanced across the network but reproducible for diagnosis. Bounded retries; per-endpoint
  health tracking suppresses known-dead endpoints temporarily.
- A client never accepts a response with a **lower epoch** than the highest it has seen for
  sanction-relevant data; on conflicting responses (e.g. one node says NORMAL, another
  QUARANTINED), the **most restrictive valid response wins** until a higher-epoch answer resolves
  the conflict.
- Split brain on the server side degrades writes (registrations queue up), never reads;
  clients experience this only as retriable registration errors.

### Degraded Mode (no central server reachable)

Positive security data does **not** remain valid indefinitely:

- **Negative states persist**: cached QUARANTINED/BLACKLISTED states and revoked serials remain
  enforced until an authoritative response lifts them.
- **Positive states expire**: cached ACTIVE/NORMAL verification results have a maximum age
  (`g.gnet.trust.cache.max.age.days`, default 14). After expiry:
  - **existing** exchange relationships continue for a grace period
    (`g.gnet.degraded.grace.days`, default 14 more days), then pause;
  - **new** peers and previously unseen partner UUIDs are rejected until a central server is
    reachable again.
- Anomaly reports queue in the central outbox.
- The degraded state and cache ages are visible in the UI, logs and monitoring, so operators know
  the network is running on stale security data.

**Principle**: availability is preferred for *established, recently verified* relationships;
security is preferred for *new or stale* ones.

---

## Privacy

The instance UUID is **pseudonymous, not anonymous**: it hides the domain from ordinary peers, but
a stable identifier allows peers to correlate an origin's instruments, timing and relationships
over time, and the central authority can map UUID → domain.

| Aspect | Rule |
|--------|------|
| Who may verify/query a UUID | Only declared partners (existing relationship edge or incoming handshake). Bulk or arbitrary UUID queries are rejected. |
| Enumeration protection | Verify/daily-query rate limits; unknown-UUID responses are indistinguishable from revoked ones (`valid=false` without detail); repeated unknown-UUID probing is logged and throttled. |
| Query logging | Central servers log verify/daily-query access (requester, target, time) with a bounded retention (default 6 months) for abuse investigation. |
| Score exposure | Exact evidence values are never published; peers receive sanction state + coarse confidence band only. |
| UUID→domain mapping | Accessible only to central-server administrators; access is logged. Legal/administrative disclosure follows the operator's published policy. |
| Retention | Domain data: lifetime of the registration + 6 months. Relationship edges: 12 months. Anomaly detail (price data): 12 months, then deleted; audit rows are kept but reference only event keys and component values. |
| Rotating attribution identifiers | Not in the first release; listed under Optional Enhancements for stronger unlinkability. |

---

## Data Model

Entity conventions apply throughout: enum-backed `Byte` columns get enum-typed getters/setters;
`LocalDate`/`LocalDateTime` fields serialized to the frontend carry `@JsonFormat` with
`BaseConstants` patterns; secrets are `@JsonIgnore` and encrypted at rest.

### Instance Side (normal schema)

#### Instance Identity (lib: `grafiosch-base`)

Named to avoid "security", which denotes financial instruments in GT code. Singleton: the primary
key is fixed to 1 and enforced with a CHECK constraint.

```java
@Entity
@Table(name = "gt_net_instance_identity")
public class GTNetInstanceIdentity {
  @Id
  @Column(name = "id_gt_net_instance_identity")
  private Integer idGtNetInstanceIdentity;   // always 1 (CHECK constraint)

  @Column(name = "secure_network", nullable = false)
  private boolean secureNetwork = false;

  @Column(name = "instance_uuid", length = 36)
  private String instanceUuid;               // null until registered

  @Column(name = "issuer_id", length = 64)
  private String issuerId;

  @Column(name = "network_id", length = 64)
  private String networkId;

  @JsonIgnore
  @Column(name = "private_key", length = 512)
  private String privateKey;                 // encrypted at rest (Jasypt); never serialized

  @Column(name = "certificate", columnDefinition = "TEXT")
  private String certificate;                // issuer-signed registration certificate (JSON)

  @Column(name = "certificate_serial")
  private Long certificateSerial;

  @Column(name = "certificate_expires_at")
  private LocalDateTime certificateExpiresAt;

  @Column(name = "registration_state")
  private Byte registrationState;            // enum accessor: PENDING/ACTIVE/SUSPENDED/REVOKED

  @Column(name = "last_daily_query")
  private LocalDateTime lastDailyQuery;
}
```

#### Trust / Sanction Cache (lib: `grafiosch-base`)

```java
@Entity
@Table(name = "gt_net_trust_cache")
public class GTNetTrustCache {
  @Id
  @Column(name = "partner_uuid", length = 36)
  private String partnerUuid;

  @Column(name = "sanction_state", nullable = false)
  private byte sanctionState;      // enum accessor: NORMAL/SUSPECTED/QUARANTINED/BLACKLISTED

  @Column(name = "lifecycle_state", nullable = false)
  private byte lifecycleState;     // enum accessor: PENDING/ACTIVE/SUSPENDED/REVOKED

  @Column(name = "confidence", nullable = false)
  private byte confidence;         // enum accessor: LOW/MEDIUM/HIGH

  @Column(name = "epoch")
  private Long epoch;              // highest epoch seen for this entry

  @Column(name = "cached_at", nullable = false)
  private LocalDateTime cachedAt;  // positive results expire after g.gnet.trust.cache.max.age.days
}
```

A small revocation cache (`gt_net_revoked_serial`: serial, cached_at) accompanies it. The
partner's UUID and certificate serial are added to `gt_net_config`
(`instance_uuid VARCHAR(36)`, `certificate_serial BIGINT`).

#### Central Outbox (lib) — see [Central Outbox](#central-outbox)

#### Threshold Cache (app: `grafioschtrader-common`)

`GTNetThresholdCache` (`gt_net_threshold_cache`) — same shape as `GTNetThresholdConfig` plus
`cached_at`, filled from the daily-query `appPayload`.

#### Price Attribution (app)

```java
// Historyquote.java — attribution for prices imported from GTNet
@Column(name = "received_from_id_gt_net")
private Integer receivedFromIdGtNet;   // authenticated sender; FK gt_net, ON DELETE SET NULL

@Column(name = "claimed_source_instance_uuid", length = 36)
private String claimedSourceInstanceUuid;   // claimed origin — explicitly unverified

// GTNetHistoryquote.java and GTNetLastprice.java — same two columns in the shared pool
```

### Central Side (schema `gtnet_central`)

#### Registered Instance (lib)

```java
@Entity
@Table(name = "gt_net_registered_instance")
public class GTNetRegisteredInstance {
  @Id
  @Column(name = "instance_uuid", length = 36)
  private String instanceUuid;

  @JsonIgnore                       // never exposed via API (privacy requirement)
  @Column(name = "domain_url", length = 128, unique = true, nullable = false)
  private String domainUrl;         // canonical form; same length as gt_net.domain_remote_name

  @Column(name = "domain_hash", length = 64, nullable = false)
  private String domainHash;        // SHA-256 of canonical URL (appears in the certificate)

  @Column(name = "public_key", length = 128, nullable = false)
  private String publicKey;

  @Column(name = "certificate_serial", nullable = false)
  private Long certificateSerial;

  @Column(name = "certificate_expires_at", nullable = false)
  private LocalDateTime certificateExpiresAt;

  @Column(name = "lifecycle_state", nullable = false)
  private byte lifecycleState;      // enum accessor: PENDING/ACTIVE/SUSPENDED/REVOKED

  @Column(name = "sanction_state", nullable = false)
  private byte sanctionState;       // enum accessor: NORMAL/SUSPECTED/QUARANTINED/BLACKLISTED

  @Column(name = "sanction_until")
  private LocalDateTime sanctionUntil;   // quarantine expiry

  @Column(name = "registered_at", nullable = false)
  private LocalDateTime registeredAt;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;      // maturation period counts from here

  @Column(name = "approved_by", length = 64)
  private String approvedBy;

  @Column(name = "operator_group", length = 64)
  private String operatorGroup;          // collusion grouping (registrable domain / declaration)
}
```

#### Relationship Edges (lib)

`gt_net_partner_relation` — `requester_uuid`, `partner_uuid`, `first_seen_at`, `last_seen_at`,
`UNIQUE (requester_uuid, partner_uuid)`, FKs to `gt_net_registered_instance`, index on
`(partner_uuid, last_seen_at)` for breadth queries and on `last_seen_at` for expiry cleanup.

#### Generic Anomaly Report Core (lib)

```java
@Entity
@Table(name = "gt_net_anomaly_report")
public class GTNetAnomalyReport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_anomaly_report")
  private Long idAnomalyReport;

  @Column(name = "report_uuid", length = 36, nullable = false, unique = true)
  private String reportUuid;        // client idempotency key

  @Column(name = "reporter_uuid", length = 36, nullable = false)
  private String reporterUuid;      // from authenticated sender; FK gt_net_registered_instance

  @Column(name = "accused_uuid", length = 36, nullable = false)
  private String accusedUuid;       // FK gt_net_registered_instance

  @Column(name = "event_key", length = 64, nullable = false)
  private String eventKey;          // dedup: unique price event (see Anomaly Reports)

  @Column(name = "report_kind", nullable = false)
  private byte reportKind;          // app discriminator (GT: 0=lastprice, 1=historical)

  @Column(name = "reporter_mature", nullable = false)
  private boolean reporterMature;   // snapshot at intake

  @Column(name = "counted", nullable = false)
  private boolean counted;          // false for immature/ineligible reporters (stored, not counted)

  @Column(name = "reported_at", nullable = false)
  private LocalDateTime reportedAt;
}
```

`UNIQUE (event_key, reporter_uuid)` prevents the same reporter inflating one event; indexes on
`(accused_uuid, reported_at)`, `(reporter_uuid, reported_at)`, `event_key`.

#### Price Anomaly Details (app)

`gt_net_anomaly_report_lastprice` / `gt_net_anomaly_report_historical` — 1:1 FK to
`gt_net_anomaly_report` (`ON DELETE CASCADE`), fields as in the message DTOs
(`instrument_key VARCHAR(50)`, `asset_class TINYINT`, `special_investment_instrument TINYINT NULL`,
supplied/expected values, `connector_used VARCHAR(64)`, `second_connector_used VARCHAR(64)`,
`deviation_percent`). Written in the same central-datasource transaction as the core row.

#### Sanction Audit (lib)

`gt_net_sanction_audit` — append-only: `instance_uuid`, `old_state`, `new_state`,
`decision_source` (AUTOMATIC/MANUAL), `actor` (admin ID for manual), `policy_version`,
`evidence_snapshot` (JSON: component values used), `reason`, `decided_at`, `expires_at`.

#### Threshold Configuration (app)

As in the instance cache, plus a **NOT NULL sentinel** for the unique key: MariaDB unique indexes
allow multiple NULLs, so `special_investment_instrument` uses `-1` for "base row" and the natural
key is `UNIQUE (asset_class, special_investment_instrument, crypto)`.

---

## Database Migration (sketch)

Instance-side migrations live in `grafioschtrader-server/src/main/resources/db/migration/`;
central-schema migrations in a separate location (e.g. `db/migration-central/`) executed by the
second Flyway instance only on central servers. All migrations must be idempotent (see root
`CLAUDE.md`). Never edit `gt_ddl.sql` — it is auto-generated.

```sql
-- ===== Instance side =====
CREATE TABLE IF NOT EXISTS gt_net_instance_identity (
  id_gt_net_instance_identity INT PRIMARY KEY CHECK (id_gt_net_instance_identity = 1),
  secure_network TINYINT(1) NOT NULL DEFAULT 0,
  instance_uuid VARCHAR(36),
  issuer_id VARCHAR(64),
  network_id VARCHAR(64),
  private_key VARCHAR(512),
  certificate TEXT,
  certificate_serial BIGINT,
  certificate_expires_at DATETIME,
  registration_state TINYINT,
  last_daily_query DATETIME
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_trust_cache (
  partner_uuid VARCHAR(36) PRIMARY KEY,
  sanction_state TINYINT NOT NULL,
  lifecycle_state TINYINT NOT NULL,
  confidence TINYINT NOT NULL,
  epoch BIGINT,
  cached_at DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_revoked_serial (
  serial BIGINT PRIMARY KEY,
  cached_at DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_central_outbox (
  id_outbox BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_uuid VARCHAR(36) NOT NULL UNIQUE,
  message_code TINYINT NOT NULL,
  schema_version INT NOT NULL,
  payload MEDIUMTEXT NOT NULL,
  issuer_id VARCHAR(64) NOT NULL,
  state TINYINT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512),
  next_attempt_at DATETIME,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB;

-- gt_net_threshold_cache analogous to gt_net_threshold_config + cached_at

ALTER TABLE gt_net_config
  ADD COLUMN IF NOT EXISTS instance_uuid VARCHAR(36),
  ADD COLUMN IF NOT EXISTS certificate_serial BIGINT;

ALTER TABLE historyquote
  ADD COLUMN IF NOT EXISTS claimed_source_instance_uuid VARCHAR(36),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;
ALTER TABLE historyquote DROP FOREIGN KEY IF EXISTS FK_Historyquote_ReceivedFromGtNet;
ALTER TABLE historyquote ADD CONSTRAINT FK_Historyquote_ReceivedFromGtNet
  FOREIGN KEY (received_from_id_gt_net) REFERENCES gt_net (id_gt_net) ON DELETE SET NULL;
ALTER TABLE gt_net_historyquote
  ADD COLUMN IF NOT EXISTS claimed_source_instance_uuid VARCHAR(36),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;
ALTER TABLE gt_net_lastprice
  ADD COLUMN IF NOT EXISTS claimed_source_instance_uuid VARCHAR(36),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;

-- ===== Central schema (gtnet_central), second Flyway location =====
CREATE TABLE IF NOT EXISTS gt_net_registered_instance (
  instance_uuid VARCHAR(36) PRIMARY KEY,
  domain_url VARCHAR(128) NOT NULL UNIQUE,
  domain_hash VARCHAR(64) NOT NULL,
  public_key VARCHAR(128) NOT NULL,
  certificate_serial BIGINT NOT NULL,
  certificate_expires_at DATETIME NOT NULL,
  lifecycle_state TINYINT NOT NULL,
  sanction_state TINYINT NOT NULL DEFAULT 0,
  sanction_until DATETIME,
  registered_at DATETIME NOT NULL,
  approved_at DATETIME,
  approved_by VARCHAR(64),
  operator_group VARCHAR(64)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS gt_net_partner_relation (
  requester_uuid VARCHAR(36) NOT NULL,
  partner_uuid VARCHAR(36) NOT NULL,
  first_seen_at DATETIME NOT NULL,
  last_seen_at DATETIME NOT NULL,
  PRIMARY KEY (requester_uuid, partner_uuid),
  CONSTRAINT FK_PartnerRelation_Requester FOREIGN KEY (requester_uuid)
    REFERENCES gt_net_registered_instance (instance_uuid) ON DELETE CASCADE,
  CONSTRAINT FK_PartnerRelation_Partner FOREIGN KEY (partner_uuid)
    REFERENCES gt_net_registered_instance (instance_uuid) ON DELETE CASCADE
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_relation_partner_seen ON gt_net_partner_relation;
ALTER TABLE gt_net_partner_relation ADD INDEX idx_relation_partner_seen (partner_uuid, last_seen_at);

CREATE TABLE IF NOT EXISTS gt_net_anomaly_report (
  id_anomaly_report BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_uuid VARCHAR(36) NOT NULL UNIQUE,
  reporter_uuid VARCHAR(36) NOT NULL,
  accused_uuid VARCHAR(36) NOT NULL,
  event_key VARCHAR(64) NOT NULL,
  report_kind TINYINT NOT NULL,
  reporter_mature TINYINT(1) NOT NULL,
  counted TINYINT(1) NOT NULL,
  reported_at DATETIME NOT NULL,
  CONSTRAINT UK_AnomalyReport_EventReporter UNIQUE (event_key, reporter_uuid),
  CONSTRAINT FK_AnomalyReport_Reporter FOREIGN KEY (reporter_uuid)
    REFERENCES gt_net_registered_instance (instance_uuid),
  CONSTRAINT FK_AnomalyReport_Accused FOREIGN KEY (accused_uuid)
    REFERENCES gt_net_registered_instance (instance_uuid)
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_anomaly_accused_time ON gt_net_anomaly_report;
ALTER TABLE gt_net_anomaly_report ADD INDEX idx_anomaly_accused_time (accused_uuid, reported_at);

-- gt_net_anomaly_report_lastprice / _historical: 1:1 detail tables, FK ON DELETE CASCADE
-- gt_net_sanction_audit: append-only audit table as specified in the data model
-- gt_net_registration_challenge: request_uuid PK, nonce, expires_at, used TINYINT(1)

CREATE TABLE IF NOT EXISTS gt_net_threshold_config (
  id_threshold_config INT AUTO_INCREMENT PRIMARY KEY,
  asset_class TINYINT NOT NULL,
  special_investment_instrument TINYINT NOT NULL DEFAULT -1,  -- -1 = base row (NOT NULL sentinel)
  crypto TINYINT(1) NOT NULL DEFAULT 0,
  last_price_threshold DOUBLE NOT NULL,
  historical_threshold DOUBLE NOT NULL,
  updated_at DATETIME,
  CONSTRAINT UK_Threshold_Natural UNIQUE (asset_class, special_investment_instrument, crypto)
) ENGINE=InnoDB;

-- Defaults: INSERT IGNORE on the natural key — reruns and updates never overwrite admin changes
INSERT IGNORE INTO gt_net_threshold_config
  (asset_class, special_investment_instrument, crypto, last_price_threshold, historical_threshold) VALUES
(0, -1, 0, 3.0, 1.5),   -- EQUITIES
(1, -1, 0, 2.5, 1.0),   -- FIXED_INCOME
(2, -1, 0, 1.5, 0.5),   -- MONEY_MARKET
(3, -1, 0, 3.0, 1.5),   -- COMMODITIES
(4, -1, 0, 3.0, 1.5),   -- REAL_ESTATE
(5, -1, 0, 3.0, 1.5),   -- MULTI_ASSET
(6, -1, 0, 2.5, 1.0),   -- CONVERTIBLE_BOND
(7, -1, 0, 4.0, 2.0),   -- CREDIT_DERIVATIVE
(8, -1, 0, 1.5, 0.5),   -- CURRENCY_PAIR
(8, -1, 1, 7.0, 3.0);   -- CURRENCY_PAIR crypto override
```

Boolean columns read through Spring Data interface projections must be `TINYINT(1)`; plain
`TINYINT` (no display width) is used where a numeric byte is projected.

---

## Attack Scenario Analysis

Central registration **reduces casual identity recreation**; it does not make Sybil attacks
impossible — domains are inexpensive and one operator can control many. The honest assessment:

| Attack | Defense | Residual risk |
|--------|---------|---------------|
| Self-issued UUID / forged certificate | Issuer signature verified against the pinned issuer key | None (crypto assumption) |
| Claiming another instance's identity | Handshake requires signing the peer's nonce with the registered private key | Key theft (mitigated by revocation + rotation) |
| Re-registering after blacklist | New domain needed + manual approval + maturation period + rate limits | Determined attacker with new domains and patience |
| Many colluding identities (Sybil) | Manual approval, operator grouping, influence caps, maturation, independence requirements for sanctions | Slow, well-disguised multi-domain operations |
| Forged origin attribution | Claimed origin is never trusted; liability is on the authenticated sender | Claim remains unverifiable until origin signatures exist |
| Gaming the sampling | Random selection prevents anticipation of *which* prices are checked | Colluding connectors or reporters — countered by corroboration + independence rules, not by sampling |
| Cloned installation / stolen DB | Domain-hash mismatch detected by peers; conflicting daily queries detected centrally → SUSPENDED | Window until detection |

---

## Configuration

### Instance (deployment properties)

```properties
# Central servers, comma-separated; deterministic shuffled failover (library-owned → g. prefix)
g.gnet.central.servers=https://grafioschtrader.info,https://grafioschtrader.com
# Pinned issuer public-key fingerprint(s); comma-separated during rotation
g.gnet.central.issuer.fingerprint=SHA256:...
```

### Instance (globalparameters, admin-editable)

```sql
-- Library-owned (g. prefix)
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.cache.max.age.days', 14, 0, 'min:1,max:90');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.degraded.grace.days', 14, 0, 'min:0,max:90');

-- Application-owned (gt. prefix)
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.verification.sampling.rate', 5, 0, 'min:0,max:100');
```

The secure-network switch, keypair, certificate and registration state live in
`gt_net_instance_identity` (UI-managed).

### Central Server (deployment properties)

```properties
# Enable central-server role (library-owned → g. prefix)
g.gnet.central.role.enabled=true
# Second datasource for the gtnet_central schema
g.gnet.central.datasource.url=jdbc:mariadb://localhost/gtnet_central
g.gnet.central.datasource.username=gtnet_central
g.gnet.central.datasource.password=ENC(...)
```

Central policy values (maturation days, observation windows, quarantine criteria, policy version)
are rows in the central schema, editable by central admins and stamped into every automatic
decision. Galera synchronization is configured in MariaDB (wsrep), not in the application.

---

## Implementation Phases

The sequence puts identity and evidence collection first; enforcement comes last, after
calibration.

### Phase 1: Identity Foundation & Central Persistence (lib)
- `GTNetInstanceIdentity` (keypair generation, encrypted private key, singleton row)
- Second datasource + Flyway location for `gtnet_central`; central entity/repository packages
- Registration codes 40–42 + lifecycle (PENDING/ACTIVE/SUSPENDED/REVOKED), hardened callback
  (SSRF rules, challenge table), manual approval via `HandlerResult.AwaitingManualResponse`,
  provisional-peer cleanup, rate limits
- Certificate issuance/renewal/rotation (code 49), revocation distribution

### Phase 2: Handshake Network Separation (lib)
- `FirstHandshakeMsg` extension (certificate + challenge signature), code 8 rejection,
  issuer/network compatibility check, revocation + sanction gating
- `gt_net_config` UUID/serial columns; mode-switch semantics incl. degraded-mode rules
- Frontend: network-mode UI, registration status, degraded-state display

### Phase 3: Price Attribution (GT)
- `received_from_id_gt_net` (FK, ON DELETE SET NULL) + `claimed_source_instance_uuid` columns
- Stamp own UUID when supplying own-connector prices; pass claims through unchanged when
  forwarding (extend `InstrumentPriceDTO`, `HistoryquoteRecordDTO` and the exchange/push handlers)

### Phase 4: Daily Query, Caches & Relationship Edges (lib + GT)
- Codes 45–46 with epoch, issuer key set, revoked serials, sanction states + confidence bands
- `gt_net_trust_cache` with positive-cache expiry; `gt_net_partner_relation` edge upserts with
  activity window; app-payload callback (GT thresholds → `gt_net_threshold_cache`)
- Scheduled daily task (follows the existing `GTNet*Task` pattern in `grafiosch.task.exec`)

### Phase 5: Canonical Comparison & Anomaly Evidence — observation only (lib transport + GT detection)
- Central outbox; codes 47–48 with `reportUuid` idempotency and event-key dedup
- GT sampling verification with the full canonical-comparison rule set (INCONCLUSIVE path,
  corroboration), threshold lookup incl. crypto rule
- Central evidence storage (core + detail atomic), maturation/eligibility gating,
  reporter snapshots — **no state changes yet**

### Phase 6: Calibration & Staged Sanctions
- Run observation-only against real traffic; tune thresholds/policy against false positives
- Enable SUSPECTED/QUARANTINED automation per the criteria in Sanctions; sanction audit trail;
  manual blacklist workflow with review UI; false-reporting penalties

### Phase 7: High Availability
- Galera rollout for `gtnet_central`; quorum rules for writes; epoch versioning;
  deterministic failover + endpoint health tracking; conflicting-response handling;
  failover and split-brain tests

---

## Optional Enhancements

Deliberately excluded from the first release; none blocks the phases above:

- **Signed origin records**: the origin signs a canonical price record with its registered key;
  recipients verify. Upgrades claimed attribution to proven attribution. The keypair and
  certificate infrastructure from Phase 1 already provides the key distribution.
- **Reputation-aware supplier ordering**: blend cached sanction/confidence data into
  `SupplierScoreCalculator` ordering once sanction behavior is proven stable.
- **Graph analysis**: detect suspicious relationship/report clusters (collusion) beyond the static
  operator-group caps.
- **Private-network federation**: explicit trust relationships between issuers so separate secure
  networks can interoperate selectively.
- **Transparency & appeals tooling**: evidence-inspection UI for accused operators, transparency
  reports, structured appeal workflow.
- **Rotating attribution identifiers**: periodically rotating origin pseudonyms for stronger
  unlinkability, at the cost of long-term quality statistics.

---

## Conclusion

| Feature | Benefit |
|---------|---------|
| **Two separate networks** | Clear security boundaries |
| **Keypair + issuer-signed certificates** | Verifiable identity, clone detection, revocation — on top of existing token auth |
| **Manual approval + maturation** | Materially raises the cost of identity recreation and collusion |
| **Hop accountability** | Liability always lands on an authenticated party; forged origin claims are harmless to third parties |
| **Canonical comparison + INCONCLUSIVE** | Anomaly evidence is meaningful; legitimate market differences don't become accusations |
| **Auditable evidence + staged sanctions** | Reproducible decisions, calibrated before enforcement, manual review for permanent exclusion |
| **Central outbox + idempotent intake** | Reliable reporting through outages without duplicates |
| **Explicit HA + degraded-mode rules** | Predictable behavior under partition and outage; stale positive data expires |
| **Lib/app layering** | Identity & reputation reusable via grafiosch; price semantics stay in GT |

Participants choose their network based on security requirements:
- **Secure GTNet**: verified identity, accountability, evidence-based quality assurance
- **Unsecure GTNet**: no overhead, no guarantees
