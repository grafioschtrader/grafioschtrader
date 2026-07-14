# GTNet Security Concept A1 — Decentralized Variant: Self-Certifying Identity, Signed Provenance and Local Verification

**Status**: Concept only — nothing in this document is implemented yet.

**Positioning**: This is **variant A1**, a decentralized alternative to
`GTNet_Security_Central_Server_Concept.md`. It solves the same problem — the spread of incorrect
price data through GTNet — **without any central server**. The two concepts are mutually
exclusive: they compete for the same free message-code range (8/9 and 40–49 in
`GNetCoreMessageCode`) and extend the same handshake, so exactly one of them is implemented.
The security loss compared to the central variant is **accepted and documented explicitly**
(see [Accepted Security Losses](#accepted-security-losses)).

The decentralized security product is **signed provenance plus local verification**: every price
in the secure network is cryptographically bound to the identity that produced it, every delivery
is bound to the peer that delivered it, and every instance checks a sample of what it receives
against its own connectors. Peer evidence exchange exists, but it is bounded, independently
verifiable, and always subordinate to local policy — it is a way to *discover* problems sooner,
never a substitute for local judgment. Like the central variant, the first release is an
**identity, accountability and evidence-collection system**; automatic local punishment
(temporary quarantine) is enabled only after evidence semantics and false-positive behavior have
been calibrated in observation-only mode.

---

## Relation to the Central-Server Concept

| Element of the central concept | In this variant |
|---|---|
| Canonical price comparison, INCONCLUSIVE rules, deviation floors, rounding | **Reused unchanged** (see [Price Verification](#price-verification-decentralized-gt-layer)) |
| Threshold values per `AssetclassType` (+ CFD/crypto overrides) | **Reused unchanged**, but stored and edited locally, no distribution |
| Staged sanction states NORMAL/SUSPECTED/QUARANTINED/BLACKLISTED | **Reused**, but every state is a purely local decision, and supplier state is separated from reporter confidence |
| Protocol requirements (schema version, idempotency, replay, size limits, error codes) | **Reused unchanged** for all new message codes, extended by signature-specific rules |
| Lib/app layering (GNet core vs grafioschtrader) | **Reused unchanged** |
| Observation-only calibration before enforcement | **Reused unchanged** |
| Hop accountability ("sender liable for everything") | **Refined**: liability is split between the signed origin (wrong values) and the immediate sender (tampering, protocol violations, defined forwarding violations) |
| Issuer, certificates, central registration, manual central approval | **Replaced** by self-certifying identity + local admission |
| Central UUID verification, daily query, trust cache | **Replaced** by key continuity (TOFU pinning) |
| Central reputation evidence, network-wide sanctions | **Replaced** by local evidence + bounded, verifiable observation notices |
| Central outbox, Galera HA, quorum/epoch rules, degraded mode | **Removed** — no central dependency exists |
| Signed origin records (optional enhancement there) | **Promoted to a mandatory core feature** here, extended by hop receipts |

---

## Design Decisions

| # | Question | Decision |
|---|----------|----------|
| A1-D1 | Is price-origin authenticity required? | **Yes, via mandatory signed origin records**: the origin signs a canonical, schema-versioned form of every price record it produces. Recipients verify the signature against the origin's self-certifying identity. A signature proves *who signed which exact record* — never that the value is correct. |
| A1-D2 | What identity material does an instance hold? | **Self-certifying keypair**: locally generated Ed25519 keypair; the instance identifier is `gti1:<base32(SHA-256(canonical public key))>` (see [Self-Certifying Identity](#self-certifying-identity)). No issuer, no certificate. Key algorithm and key version are explicit, versioned attributes. |
| A1-D3 | Who may join the secure network? | **Each instance decides for itself** (local admission policy): manual approval (default) or open. Signed endorsements from existing peers are advisory input to the human decision, never a bypass. A local maturation period gates reputational influence of new peers. |
| A1-D4 | Where does security data live? | **Only in the normal instance schema.** No second datasource, no dedicated schema, no replication. |
| A1-D5 | Who is liable for bad data? | **Split liability**: the *signed origin* for economically wrong but validly signed values; the *immediate sender* for tampering, invalid signatures, unverifiable origin claims and defined forwarding violations. Supplier state per identity is enforced independently for the origin role and the sender role. |
| A1-D6 | What is exchanged during handshake instead of issuer/network ID? | Full identifier + public key (with algorithm and key version) + domain-separated challenge signature. Compatibility = both peers in secure mode with verifiable identities; there is no network ID — network membership is emergent (who peers with whom). |
| A1-D7 | How stale may trust data become? | Pinned keys never expire (key continuity). Received observation envelopes expire (default 30 days). Local sanction states follow their own expiry (quarantine default 7 days). Local states of different instances may diverge indefinitely; that is not a protocol error. |
| A1-D8 | When does evidence change a peer's local sanction state? | Only after the observation-only calibration phase, introduced stepwise: auditable components first, then SUSPECTED warnings, then temporary quarantine requiring **locally confirmed** evidence. Received observations alone can never trigger quarantine. Permanent blocking is manual, except for locally proven key compromise. |

---

## Problem Statement

The problem statement, existing protections and threat scenarios are identical to the central
concept (see its "Current Situation" and "Threat Scenarios" sections): no portable identity, no
per-price attribution, no cross-instance reputation, no verification against reference sources,
no network-wide sanctions.

### Why No Central Server

The central variant concentrates four roles in one operator: identity issuer, evidence registry,
sanction authority and policy distributor. That brings strong guarantees, but also:

1. **Operational burden**: someone must run, patch, back up and Galera-replicate the central
   cluster indefinitely, and manually approve every registration.
2. **Single point of trust**: all participants must trust the central operator's key handling,
   approval judgment and sanction decisions; the operator can map every identity to a domain.
3. **Single point of failure**: central outages force every instance into degraded mode with
   grace-period bookkeeping; a discontinued central service strands the whole secure network.
4. **Admin bottleneck**: manual approval and manual blacklist review do not scale with the
   network and create latency for legitimate joiners.

This variant removes all four at the price of weaker guarantees, quantified below.

---

## Accepted Security Losses

This section is normative: every claim elsewhere in this document must be consistent with it.

| Central guarantee | Decentralized status | Consequence |
|---|---|---|
| Identity creation is rate-limited and manually approved | **Lost.** Creating a new identity costs one keypair generation — effectively free. | Sybil resistance rests entirely on *local* admission policies and per-peer maturation. A determined attacker can always mint fresh identities; each instance must individually decide to let them in. Nothing in this design prevents Sybils — it only limits what an unadmitted identity can do to any given instance. |
| Network-wide sanctions (one quarantine/blacklist protects everyone) | **Lost.** Sanctions are local. Observation notices warn direct peers, but only the observer's neighborhood, only if those peers verify them, and never as binding decisions. | A bad actor remains usable by instances that neither observed its behavior nor received (and confirmed) a notice. An instance two relationships away never learns of a problem it does not observe itself. Warning coverage is partial by design and does **not** converge network-wide. |
| Global revocation of a compromised key | **Lost.** A key-transition statement reaches only direct peers; there is no authoritative revocation list. | A stolen private key remains usable against peers that have not seen the transition and have no other reason to unpin. Self-revocation works only while the owner still controls the key. **No decentralized procedure can authoritatively revoke a stolen key for all instances** — recovery is peer-by-peer and partly out of band. |
| Central clone detection (conflicting daily queries) | **Reduced.** Only peers that interact with the same identity under inconsistent metadata (e.g. conflicting domains) can notice a clone. | A cloned installation talking to disjoint peer sets goes undetected. |
| Curated, network-consistent thresholds and policy versions | **Lost.** Thresholds are local defaults, individually editable. | Two instances may classify the same deviation differently; anomaly evidence is not directly comparable across instances. Detail-on-demand fetching plus local re-verification absorbs part of this. |
| Central evidence audit (who reported what, false reporting adjudicated by an admin) | **Replaced** by local bookkeeping: each instance records which reporter's observations it later confirmed or contradicted and adjusts that reporter's local confidence. | No network-wide notion of a "discredited reporter"; each instance learns it separately. Reporter assessments are local and are **not** gossiped in the first release — distributing them would create a recursive reputation system that is easy to weaponize. |
| UUID→domain mapping available to a (legally accountable) central admin | **Lost — deliberately.** No one can map a forwarded origin identifier to a domain except its direct peers. | Stronger privacy, weaker legal recourse against abusive origins. |

What is **gained** in exchange:

- No central operator, no approval latency, no Galera, no second datasource, no outbox, no
  degraded-mode machinery — the security layer works fully offline and in arbitrary partitions.
- No single party that must be trusted with the identity map or sanction power.
- Origin authenticity is *stronger* than in the first central release: signed origin records and
  hop receipts are mandatory here, so tampering and forged attribution are detectable from
  day one.

---

## Solution Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            SECURE GTNET (A1)                              │
│                                                                           │
│   ┌─────────────────┐        ┌─────────────────┐       ┌───────────────┐  │
│   │  Instance A     │◄──────►│  Instance B     │◄─────►│  Instance C   │  │
│   │  keypair KA     │  pin   │  keypair KB     │  pin  │  keypair KC   │  │
│   │  id=gti1:h(KA)  │        │  id=gti1:h(KB)  │       │  id=gti1:h(KC)│  │
│   └─────────────────┘        └─────────────────┘       └───────────────┘  │
│                                                                           │
│   Every price record:   signed by its origin (canonical bytes preserved)  │
│   Every delivery:       hop receipt signed by the immediate sender        │
│   Every instance:       samples received prices against own connectors,   │
│                         keeps immutable local observations, decides       │
│                         supplier state and reporter confidence locally    │
│   Warnings:             signed observation envelopes to direct peers      │
│                         (local-neighborhood warning, verifiable,          │
│                          detail fetched on demand, never binding)         │
│                                                                           │
│   Identity: self-certifying (identifier derived from public key)          │
│   Trust:    key continuity (TOFU pinning) + local decisions               │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│   ┌───────────┐             ┌───────────┐             ┌───────────┐       │
│   │Instance X │◄───────────►│Instance Y │◄───────────►│Instance Z │       │
│   │(no keys)  │             │(no keys)  │             │(no keys)  │       │
│   └───────────┘             └───────────┘             └───────────┘       │
│                        UNSECURE GTNET                                     │
│                  (unchanged, tokens only)                                 │
└──────────────────────────────────────────────────────────────────────────┘

              ╔═══════════════════════════════════════════╗
              ║  SECURE AND UNSECURE NETWORKS DO NOT      ║
              ║  COMMUNICATE WITH EACH OTHER              ║
              ╚═══════════════════════════════════════════╝
```

### Network Comparison

| Aspect | Secure GTNet (A1) | Unsecure GTNet |
|--------|-------------------|----------------|
| **Identity** | Self-certifying keypair + derived full-hash identifier | None (domain name only) |
| **Registration** | None — identity is generated locally | Not required |
| **Verification** | Key continuity: peers pin each other's keys | None |
| **Sanctions** | Local staged states; direct peers warned via signed observations | None |
| **Price attribution** | Signed origin record + signed hop receipt | None |
| **Communication** | Only secure-mode instances with verifiable identity | Only with other unsecure instances |
| **Identity recreation** | Free (accepted); gated per instance by admission policy | Trivially possible |
| **Price verification** | Decentralized sampling, evidence handled locally | None |
| **Peer authentication** | Mutual GUID tokens (existing) + key possession proof | Mutual GUID tokens (existing) |

The existing token handshake remains the transport-level authentication in **both** networks,
exactly as in the central variant.

---

## Layering: GNet Core vs Grafioschtrader

The library/application seam is identical to the central concept. **Library code must never
reference grafioschtrader classes.**

### GNet core (grafiosch library) — domain-agnostic

| Concern | Artifacts |
|---------|-----------|
| Instance identity, keypair, network mode | Entity `GTNetInstanceIdentity` (`gt_net_instance_identity`) |
| Known identities (identifier → public key, key versions) | Entity `GTNetKnownIdentity` (`gt_net_known_identity`) |
| Key pinning of direct peers | New columns on `gt_net_config` |
| Canonicalization, signature domains, hashing utilities, test vectors | Crypto utility package + fixture resources |
| Signed-record storage (canonical bytes) | Entity `GTNetSignedRecord` (`gt_net_signed_record`) — generic: payload semantics are app-defined |
| Hop receipts | Entity `GTNetHopReceipt` (`gt_net_hop_receipt`) |
| Local supplier state / reporter confidence per identity | Entity `GTNetPeerTrust` (`gt_net_peer_trust`) |
| Immutable observations (own + received) | Entity `GTNetObservation` (`gt_net_observation`) |
| Observation notice transport | Message codes 43–44 |
| Evidence detail fetch | Message codes 45–46 (detail payload app-defined) |
| Key transition | Message code 40 |
| Endorsements | Message codes 41–42, entity `GTNetEndorsement` (`gt_net_endorsement`) |
| Anomaly notice transport (generic envelope) | Message codes 47–48 |
| Handshake gating (mode + identity verification) | Extension of `FirstHandshakeMsg` + `FirstHandshakeRequestHandler`, rejection code 8 |
| Config keys | `g.gnet.trust.*`, `g.gnet.admission.*` (globalparameters) |

### Grafioschtrader application — price-specific

| Concern | Artifacts |
|---------|-----------|
| What an anomaly *is* | Canonical comparison of received prices against own connectors — **identical rule set as the central concept** |
| Canonical signed **price** record schema | Field definitions and semantics binding (instrument, venue, currency, unit, field, adjustment) |
| Observation detail persistence | `gt_net_observation_lastprice`, `gt_net_observation_historical` (1:1 to the generic observation rows) |
| Thresholds per asset class | `gt_net_threshold_config` (local, Flyway-seeded, admin-editable) |
| Price attribution | `received_from_id_gt_net`, `origin_gti`, `record_hash` columns; propagation in `InstrumentPriceDTO` / `HistoryquoteRecordDTO` |
| Notice / detail payloads | App-defined JSON inside the generic envelopes |
| Config keys | `gt.gtnet.verification.*` (globalparameters with `input_rule`) |

Wiring uses the same extension points as today and as the central concept:
`GTNetMessageCodeRegistry` (core codes auto-registered), `GTNetModelHelper.registerModel(...)`,
Spring auto-discovery of `GTNetMessageHandler` beans.

---

## Self-Certifying Identity

### Keypair and Identifier Derivation

Each instance entering secure mode generates an **Ed25519 keypair** locally; the private key
never leaves the instance (encrypted at rest with Jasypt, `@JsonIgnore`, never exported —
same handling as in the central variant).

The instance identifier (**GTI**, "GTNet identity") is derived from the full public-key digest
with an explicit derivation-version prefix:

```
gti1:<base32-no-padding( SHA-256( canonical Ed25519 public key bytes ) )>
```

- `gti1` names the derivation and encoding version; a future algorithm change introduces `gti2`
  without ambiguity (crypto agility). The identity material always records the **key algorithm**
  (`Ed25519`) and a **key version** (integer, incremented on rotation).
- The **full 256-bit digest** is used — no truncation, no UUID formatting. The identifier is
  ~57 characters and is stored in `VARCHAR(64)` columns named `*_gti`.
- **Protocol comparisons always use the complete identifier.** The UI may display a short
  fingerprint (e.g. first 12 characters) for human comparison, but nothing in the protocol or
  database keys on a truncated form.

Verification rule used everywhere a GTI appears together with a public key:

```
valid(gti, publicKey) := gti == deriveGti(publicKey)
```

Consequences:

- **Claiming a foreign identifier is infeasible**: it requires a second preimage of SHA-256.
  No issuer signature is needed to make identifiers unforgeable.
- **The public key is the identity.** Whoever has the key can verify any signature by that
  identity without any directory or authority — this is what makes signed origin records and
  hop receipts work without central key distribution.
- **Anyone can mint identities for free.** This is the fundamental accepted loss; the
  identifier proves *key possession*, never *good standing*.

### Identity Lifecycle

| Event | Procedure |
|-------|-----------|
| **Creation** | Switching to secure mode generates the keypair and derives the GTI. Immediate — no registration, no approval, no waiting. Key backup guidance (encrypted export for disaster recovery) is part of the admin UI; the backup status is tracked in the identity row. |
| **Planned key transition** | The instance generates a new keypair and broadcasts a **dual-signed key-transition statement** (code 40): the statement carries both full identifiers, both public keys, both key versions, effective time, reason and schema version; it is **signed by the old key and countersigned by the new key** (proving possession of both — a statement naming a key that never demonstrated possession is invalid). Receivers verify both signatures, re-pin the new key, mark the old GTI as superseded in `gt_net_known_identity`, and may transfer local history (pinning age, maturation, trust state) to the new identity **according to local policy**. Peers that miss the broadcast see a key mismatch at the next handshake and must re-approve manually. |
| **Key compromise** | A transition helps only while the legitimate operator still controls the old key. Otherwise the operator must contact peers out of band; each peer unpins manually. Warnings received from third parties about a compromised key are advisory. **There is no authoritative decentralized revocation** — accepted loss. |
| **Identity loss (DB gone, no key backup)** | The identity is gone. The instance generates a new keypair (new GTI) and re-enters admission at every peer like a newcomer. **A lost-key replacement never inherits reputation, maturation or pinning history** — there is no dual-signed transition to justify it. Historical attribution under the old GTI remains valid but is no longer extendable. |
| **Database restore / accidental clone** | Two installations share one keypair. Peers can detect this only through inconsistent metadata (conflicting domains or overlapping, contradictory traffic from one identity); they then refuse and alert. Clones on disjoint peer sets are undetected (accepted loss). The lifecycle documentation must cover deliberate restore (legitimate) vs accidental live clone (both installations active). |
| **Domain migration** | Domain binding is peer-local metadata (see Admission); the instance announces the new canonical URL to each peer, which re-verifies per its own policy and updates its configuration. The identity is unaffected. |

---

## Network Separation and Handshake

### Handshake Extension

`FirstHandshakeMsg` (grafiosch-base, currently only `tokenThis`) is extended:

```java
public class FirstHandshakeMsg {
  public String tokenThis;           // existing
  public Boolean secureNetwork;      // NEW: sender's network mode (null = legacy peer → unsecure)
  public String instanceGti;         // NEW: full identifier — must equal deriveGti(publicKey)
  public String publicKey;           // NEW: base64 canonical Ed25519 public key (if secure)
  public String keyAlgorithm;        // NEW: "Ed25519" (explicit, for agility)
  public Integer keyVersion;         // NEW: current key version
  public String challengeSignature;  // NEW: domain-separated signature over the responder nonce
  public String canonicalDomainUrl;  // NEW, optional: sender's claimed canonical https URL
  public String endorsement;         // NEW, optional: signed endorsement statement (JSON)
}
```

The nonce transport (initial ping exchange vs intermediate handshake step) is settled in
Phase 2 design — the same open point as in the central variant. The challenge signature is
computed over `"gtnet-a1/handshake/v1" || nonce` (see Signature Domains). The responder's ACCEPT
carries the responder's own identity fields and signature so key-possession proof is **mutual**.

### Identity vs Domain

**The root of identity is key possession, not domain control.** A verified handshake proves that
the peer at the other end of this authenticated connection holds the private key for the claimed
GTI. Domain binding is **optional metadata about a direct peer**, useful for operators, and is
verified — where wanted — by one of three means, in descending order of assurance:

1. **Out-of-band fingerprint comparison** (default for manual admission): the operators compare
   the full identifier (or a sufficiently long fingerprint of it) through an independent channel
   (existing forum contact, e-mail, phone). This is the classic high-assurance pairing and needs
   no protocol support beyond displaying the identifier.
2. **Configured-URL consistency**: for *outbound* connections the local administrator already
   configured the peer's URL when creating the GTNet entry; a successful handshake over a
   connection *to that URL* is itself evidence that the service at the configured address holds
   the key. The presented `canonicalDomainUrl` is additionally compared with the configured one;
   mismatch ⇒ warning to the operator.
3. **Optional hardened callback** for installations that specifically require proof of service
   control for *inbound* first contacts (where no configured URL exists yet): a challenge is
   delivered to the claimed canonical URL's M2M endpoint and must come back signed. This is
   **optional and off by default** (`g.gnet.admission.domain.callback`), because it proves only
   momentary service control — not operator uniqueness, reputability or entitlement — while
   making every instance an SSRF-capable callback client.

If the callback is enabled, the full hardening rule set applies: `https` only, no redirects,
DNS resolution and per-IP validation on every call (loopback, RFC 1918, link-local, multicast,
reserved and cloud-metadata ranges rejected for both IPv4 and IPv6), connection made to the
validated IP with **no re-resolution between validation and use** (DNS-rebinding protection),
TLS certificate-name validation against the canonical host, defined proxy behavior (callbacks do
not traverse configured HTTP proxies unless explicitly allowed), strict timeout (10 s),
response-size limit (64 KB), concurrency cap, per-domain (eTLD+1) and per-source rate limits
with exponential backoff, cryptographically random single-use nonces (10-minute TTL) bound to
the handshake attempt, and **defined cleanup**: an incomplete or failed verification leaves no
provisional peer state behind beyond a rate-limit counter.

Canonical URL rules (when a URL is used at all) are identical to the central concept: `https`
only, lower-cased punycode host, default port removed, empty path, no query/fragment.

### Key Continuity (TOFU Pinning)

Trust-on-first-use with the SSH `known_hosts` discipline:

- On the **first** secure handshake with a peer, after key-possession proof succeeds and local
  admission (below) approves, the tuple `(instance_gti, public_key, key_version,
  canonical_domain?)` is pinned in `gt_net_config` with `key_pinned_at`.
- On **every subsequent** handshake and token refresh, the presented key must equal the pinned
  key. Any mismatch ⇒ connection refused with code 8 and an admin alert; recovery requires
  either a valid dual-signed key transition (code 40) or explicit manual re-approval.
- Pinned keys **never expire silently**. Continuity is the guarantee: "the peer I talk to today
  holds the same key as the peer I approved back then."

### Connection Validation

`FirstHandshakeRequestHandler.preProcess(...)` gains the compatibility check:

```java
boolean canConnect(FirstHandshakeMsg msg, byte[] handshakeNonce) {
  GTNetInstanceIdentity self = instanceIdentityService.get();
  if (self.isSecureNetwork()) {
    if (!Boolean.TRUE.equals(msg.secureNetwork) || msg.publicKey == null) {
      return false;                                        // unsecure or legacy peer
    }
    if (!msg.instanceGti.equals(deriveGti(msg.publicKey))) return false;   // self-certification
    if (!verifyDomainSeparated(HANDSHAKE_DOMAIN, msg.publicKey, handshakeNonce,
                               msg.challengeSignature)) return false;
    PinnedIdentity pinned = pinStore.find(msg.instanceGti);
    if (pinned != null) {                                  // known peer: continuity check
      return pinned.publicKey.equals(msg.publicKey)
          && peerTrust.allowsConnections(msg.instanceGti);   // not locally BLACKLISTED
    }
    return admissionPolicy.evaluate(msg);                  // new peer: manual (default) / open
  }
  return !Boolean.TRUE.equals(msg.secureNetwork);          // unsecure only pairs with unsecure
}
```

Rejections use `GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` (code 8) with a stable reason code
(`MODE_MISMATCH`, `IDENTITY_INVALID`, `KEY_MISMATCH`, `LOCALLY_BLOCKED`, `ADMISSION_DENIED`,
`AWAITING_APPROVAL`). Manual approval reuses the existing
`HandlerResult.AwaitingManualResponse` mechanism that already serves handshake approval.

### Switching Network Mode

Simpler than the central variant because there is no registration that can fail or be delayed:

- **Unsecure → secure**: generate keypair, derive GTI — effective immediately. Existing
  unsecure peer entries are demoted to *disabled* (tokens retained, exchange blocked) so the
  operator can switch back. Pooled prices gathered in unsecure mode have no origin signature;
  they are **retained but marked unattributed** and are served onward only to unsecure networks.
  **Switching modes never silently promotes unattributed legacy records into the signed pool.**
- **Secure → unsecure**: allowed any time; drops secure connections. The keypair remains stored,
  so the instance can return to secure mode with the *same identity* and peers' pins stay valid.
- There is no dependency on any third party for either direction.

---

## Admission: Local Approval, Maturation, Endorsements

There is no central gatekeeper; **each instance is its own gatekeeper**. Three layers:

### 1. Local Admission Policy

A globalparameter `g.gnet.admission.policy` selects how unknown secure identities are handled at
first handshake:

| Value | Policy | Behavior |
|-------|--------|----------|
| 0 | **MANUAL** (default) | Every new identity awaits operator approval (`AwaitingManualResponse`), exactly like today's not-in-list handling. The approval dialog shows the identifier fingerprint, claimed domain (if any), verification means available, and any endorsements. **Nothing bypasses this approval when it is configured.** |
| 1 | **OPEN** | Auto-accept every identity that passes key-possession proof. Maturation and elevated sampling still apply. Intended for instances that deliberately prioritize coverage over caution — the exposure is their own choice. |

The existing `GTNet.allowServerCreation` and `GTNetMessageAnswer` auto-answer rules remain the
underlying mechanism; the policy plugs into the same decision point.

### 2. Local Maturation

Per peer, counted from `key_pinned_at` at *this* instance (`g.gnet.trust.maturation.days`,
default 30). While a peer is immature **locally**:

- its prices are sampled at the **elevated** rate (`gt.gtnet.verification.sampling.rate.elevated`,
  default 20%, vs the normal default 5%),
- its observation envelopes are stored but **not counted** as corroboration,
- endorsements it issues carry **no weight**,
- data exchange itself is allowed (otherwise no evidence could ever accumulate).

Maturation time alone is not sufficient for reputational influence: a peer's envelopes and
endorsements can corroborate automatic decisions only after a minimum locally supplied volume
(`g.gnet.trust.min.supplied.events`, default 1,000 price events at this instance). Time can be
waited out silently; volume requires observable, sampled activity.

Because maturation is per-instance, a Sybil identity must sit out the maturation period at
*every* instance it wants to influence — there is no single clock to wait out.

### 3. Endorsements (advisory introductions)

An endorsement is a signed, transferable introduction — **advisory information for the admitting
operator, never an admission certificate**:

```json
{
  "schemaVersion": 1,
  "endorserId": "gti1:…",
  "endorserPublicKey": "base64…",
  "subjectId": "gti1:…",
  "subjectPublicKey": "base64…",
  "issuedAt": "2026-07-11T12:00:00Z",
  "expiresAt": "2026-10-11T12:00:00Z",
  "signature": "base64(Ed25519, domain gtnet-a1/endorsement/v1, over all fields above)"
}
```

- A newcomer N obtains an endorsement from an existing member V it already peers with
  (codes 41/42: N asks, V's operator approves manually, V returns the signed statement).
- N attaches the endorsement to handshakes with third parties (field in `FirstHandshakeMsg`).
- A receiver M **displays** a valid endorsement during manual admission review (M must have
  pinned V itself; V matured and NORMAL at M; signature valid and unexpired). It may also raise
  initial confidence — e.g. start sampling at the normal instead of a stricter rate — but it
  **never substitutes for the configured approval step**. One compromised mature peer must not
  be able to admit anything anywhere by itself.
- Endorsements are **never transitive**: a chain V→N→W gives W nothing at M.
- Influence is capped by **locally assessed independence**: multiple endorsements that M cannot
  distinguish as independent (same endorser, or endorsers M groups together) count as one.
- **Endorser accountability (local)**: if an endorsed peer is quarantined at M during its
  maturation period, M records the outcome against V's endorsement confidence
  (`gt_net_endorsement.outcome`). This lowers the weight of V's *future endorsements* at M; it
  does not retroactively declare every peer V introduced malicious.

---

## Signed Objects: Canonical Form, Domains and Hashing

All security value in this variant rests on signatures, so their byte-level definition is
specified before any message that carries them.

### Canonical Serialization

Every signed object is serialized deterministically before signing:

- **Canonical JSON**: UTF-8; object fields in the **exact order defined by the schema** (not
  alphabetical, not implementation-dependent); no insignificant whitespace; strings NFC-
  normalized.
- **Timestamps**: ISO-8601 UTC with `Z` suffix, second precision (`2026-07-11T15:30:12Z`);
  dates as `yyyy-MM-dd`.
- **Decimal values as strings**, normalized: no exponent, no thousands separators, `.` decimal
  separator, no trailing zeros beyond the source value's precision, no leading `+`. The signed
  value preserves the **source's semantic precision** — it is *not* forced to the storage scale.
- **Null handling**: optional fields that are absent are serialized as explicit `null` (fixed
  field count per schema version — no field omission ambiguity).
- **Enumerations are serialized as defined string names**, never Java ordinals.
- The **exact canonical bytes that were signed are stored and forwarded verbatim**
  (`gt_net_signed_record.canonical_payload`). Verification and forwarding never reconstruct an
  approximation from parsed database fields.

### Signature Domain Separation

Every signature is computed over `domainTag || 0x0A || canonicalBytes`, where `domainTag` is a
fixed ASCII string naming protocol, purpose and version. A byte sequence valid for one purpose
can never be reinterpreted for another:

| Signed object | Domain tag |
|---------------|-----------|
| Handshake challenge | `gtnet-a1/handshake/v1` |
| Price record | `gtnet-a1/price-record/v1` |
| Hop receipt | `gtnet-a1/hop-receipt/v1` |
| Observation envelope | `gtnet-a1/observation/v1` |
| Key transition | `gtnet-a1/key-transition/v1` |
| Endorsement | `gtnet-a1/endorsement/v1` |

### Hashes and Identifiers

| Identifier | Definition | Purpose |
|------------|-----------|---------|
| `recordHash` | SHA-256 over the record's canonical bytes (hex, 64 chars) | Identifies **exact signed bytes**; two different signed values for the same event have different record hashes (equivocation is auditable) |
| `eventKey` | SHA-256 over `subjectGti \| subjectRole \| instrumentKey \| temporalBucket \| kindClass` | Groups records/observations referring to the **same economic event**; last-price times are bucketed to 15 minutes *for grouping only* (never in signed content) |
| `observationUuid` | Client-generated UUID | Makes one observation and its delivery **idempotent** |
| `detailHash` | SHA-256 over the canonical detail payload | Binds a compact envelope to its on-demand detail |

### Test Vectors

The implementation must ship **published canonicalization and signature test vectors** —
positive (fixed inputs → expected canonical bytes, hashes, signatures for a fixed test key) and
negative (wrong field order, locale-formatted decimals, ordinal enums, missing domain tag) — and
verify them in unit tests. Vectors must produce identical results across all supported JVM and
application versions; a vector regression is a release blocker.

---

## Price Attribution: Signed Origin Records and Hop Receipts

### Responsibility Model

Liability is **split by role**, replacing the central variant's blanket immediate-supplier rule:

| Situation | Assessment |
|-----------|-----------|
| Record validly signed by its origin, but the value is economically wrong | Evidence against the **signed origin** (`subjectRole = ORIGIN`). The forwarder authenticated delivery; it cannot vouch for the economic correctness of another instance's connectors and is not penalized. |
| Record modified in transit, or origin signature invalid, or claimed origin without a signature | **Protocol violation by the immediate sender** (`subjectRole = SENDER`). The record is rejected. No blame attaches to the *claimed* origin identity — an unverified claim never damages the identity it names. |
| Sender forwards a record whose origin *this receiver* has quarantined | Not a sender violation — the sender cannot know the receiver's local states. The receiver's origin-level quarantine simply blocks the import. |
| Sender keeps forwarding records it was itself notified are anomalous (courtesy notices, code 47), or violates an explicit forwarding rule (e.g. re-signing, altering canonical bytes, stripping receipts) | Evidence against the **sender**. |
| Record older than its signed validity limit | Stale — not imported; repeated systematic delivery of stale records is a sender-side quality signal, not an anomaly. |

Local records retain the signed origin, the authenticated immediate sender, **and the reason each
identity was or was not assessed** (the observation rows carry `subjectRole` and
`observationKind`). Supplier state for an identity's *origin role* (are records it signed
importable?) and its *sender role* (is data delivered by it importable?) are **independently
enforceable** — quarantining a faulty origin does not silence an honest relay, and vice versa.

### Canonical Signed Price Record (app schema)

An instance producing a price from its **own connectors** creates and signs:

```json
{
  "schemaVersion": 1,
  "recordUuid": "…",
  "recordKind": "LASTPRICE",              // or "HISTORICAL" — string names, never ordinals
  "originId": "gti1:…",
  "keyAlgorithm": "Ed25519",
  "keyVersion": 1,
  "instrumentKey": "ISIN:CURRENCY or FROM:TO",
  "venue": "XETR",                        // instrument's configured listing/venue; null for FX
  "currency": "EUR",
  "quotationUnit": "MAJOR",               // MAJOR or MINOR (e.g. GBp) — bound, not assumed
  "priceField": "LAST",                   // LAST or CLOSE — field semantics bound
  "value": "123.456",                     // decimal string, source precision preserved
  "priceTime": "2026-07-11T15:30:12Z",    // exact timestamp (LASTPRICE) …
  "priceDate": null,                      // … or exact trading date (HISTORICAL)
  "adjustment": null,                     // HISTORICAL: "RAW" (GTNet exchanges raw closes)
  "createdAt": "2026-07-11T15:30:15Z",
  "validUntil": "2026-07-11T16:30:15Z",   // freshness limit set by the origin
  "connectorClass": "sha256:…"            // non-sensitive connector-independence class/hash
}
```

`originSignature = Ed25519-sign(privateKey, "gtnet-a1/price-record/v1" || 0x0A || canonicalBytes)`.

The exact timestamp (not a bucket) is signed, so two distinct observations can never share one
signed temporal key; replay of stale values is bounded by the signed `validUntil` instead.

### Historical Batch Records

A historical import easily covers thousands of trading days; one signed record per row would
mean one `gt_net_signed_record` row (canonical payload + signature) and one signature
verification per history row. Historical series are therefore signed as **bounded batch
records** (`recordKind = "HISTORICAL_BATCH"`, own fixed field set per schema version):

```json
{
  "schemaVersion": 1,
  "recordUuid": "…",
  "recordKind": "HISTORICAL_BATCH",
  "originId": "gti1:…",
  "keyAlgorithm": "Ed25519",
  "keyVersion": 1,
  "instrumentKey": "ISIN:CURRENCY or FROM:TO",
  "venue": "XETR",
  "currency": "EUR",
  "quotationUnit": "MAJOR",
  "priceField": "CLOSE",
  "adjustment": "RAW",
  "series": [ { "date": "2026-07-09", "value": "123.456" }, … ],
  "createdAt": "2026-07-11T15:30:15Z",
  "connectorClass": "sha256:…"
}
```

- `series` holds **strictly ascending** dates of one instrument; a batch has a configured
  maximum row count (default 5,000). Receivers reject oversized, unordered or multi-instrument
  batches.
- The batch is signed and stored as **one** `gt_net_signed_record` row; every imported
  `historyquote` row from it carries the batch's `record_hash`. The batch row's `event_key` is
  derived from instrument key and date range; per-date anomaly observations use the ordinary
  per-date historical event key and reference the batch `recordHash` in their detail payload.
- **Forwarders keep a batch intact** — canonical bytes and signature pass through unchanged like
  any signed record. Serving a subset requires individually signed `HISTORICAL` records from the
  origin; an intermediary cannot derive a validly signed subset from a batch.
- Single historical records (e.g. gap fills, corrections) use the ordinary `HISTORICAL` record.

### Hop Receipts

The origin signature proves who *created* a record; it says nothing about who *delivered* it.
A receiver's local `received_from_id_gt_net` stamp is useful operationally, but it is not
portable evidence — it is only a claim the receiver makes about the sender. Therefore every
delivery of signed records is accompanied by a **recipient-bound hop receipt** signed by the
immediate sender:

```json
{
  "schemaVersion": 1,
  "receiptUuid": "…",
  "recordHashes": ["…", "…"],             // the records of this delivery batch (bounded, default ≤ 200)
  "senderId": "gti1:…",
  "recipientId": "gti1:…",
  "sentAt": "2026-07-11T15:31:02Z",
  "previousReceiptHash": null,            // optional chaining; unused in the first release
  "signature": "base64(Ed25519, domain gtnet-a1/hop-receipt/v1)"
}
```

- One receipt covers one delivery batch (the existing exchange/push granularity), so the
  overhead is one signature per exchange, not per record.
- The receipt binds *record, sender, recipient and time* — portable proof that this sender
  delivered exactly these signed bytes to this recipient. Combined with the origin signature,
  the receiver can prove both provenance facts to a third party (e.g. inside an observation
  envelope) without any trusted registry.
- **Forwarding rules**: a forwarder passes the origin's canonical bytes and signature through
  **unchanged** (no re-signing, no reconstruction) and issues a **new hop receipt** for its own
  delivery. Full hop-chain accumulation (`previousReceiptHash`) remains disabled by default for
  privacy and payload-size reasons; retaining the immediate receipt suffices for local
  accountability.

### Wire and Storage

`InstrumentPriceDTO` / `HistoryquoteRecordDTO` gain: `originCanonicalPayload` (exact bytes),
`originSignature`, `originPublicKey`; the batch envelope carries the hop receipt. Receiving
handlers:

1. Verify the hop receipt (sender = authenticated envelope source, recipient = self, record
   hashes match the delivered records, time within skew). Missing/invalid receipt ⇒ batch
   rejected, protocol violation by the sender.
2. Per record: `deriveGti(originPublicKey) == originId` inside the payload, domain-separated
   signature valid over the exact received bytes, `validUntil` not passed.
   - **Valid** → store: canonical bytes + signature in `gt_net_signed_record` (keyed by
     `record_hash`), price row with `origin_gti` + `record_hash` + `received_from_id_gt_net`;
     upsert `(originId, originPublicKey, keyVersion)` into `gt_net_known_identity`.
   - **Invalid** → reject the record and log a **protocol-violation observation against the
     authenticated sender** (`observationKind = SIGNATURE_INVALID`). Tampering is detected
     immediately, not statistically.
3. Records without origin signature do not enter the secure pool (unattributed; legacy rules
   apply).

Historical batches are verified **once** (signature over the batch's canonical bytes) and stored
as one signed record; each imported history row carries the batch's `record_hash` (see
Historical Batch Records).

### What the Signature Does and Does Not Prove

- It proves the origin identity produced *exactly this record*. A forwarder can no longer alter
  values or stamp a foreign identity undetected. **It does not make the price correct** — a
  faulty or malicious origin signs wrong values just as easily as right ones; correctness is
  established only by local verification against reference connectors.
- An origin can **equivocate** (sign different values for the same instrument and time toward
  different peers). Equivocation is not preventable, but it is **auditable**: two valid records
  with the same event key and different record hashes are portable, self-proving evidence
  (`observationKind = EQUIVOCATION`), verifiable by anyone without trusting the reporter.
- A forwarder can still **select or withhold** signed records. The signed `validUntil` bounds
  staleness; withholding is a coverage/quality issue visible in the existing supplier score,
  not a security violation.

---

## Price Verification (Decentralized, GT layer)

The verification pipeline, sampling approach, **canonical comparison rules, INCONCLUSIVE
semantics, deviation floors, rounding rules and threshold values are adopted unchanged from the
central concept** (see its "Canonical Price Comparison" and "Anomaly Thresholds" sections —
they are not repeated here; that section of the central document is the normative reference for
this variant too). Differences are confined to what happens *after* a comparison:

```
Instance receives signed price record from authenticated peer P
                   │
                   ▼
   receipt + signature verification  ── invalid ──► reject; protocol-violation
                   │ valid                          observation against P
                   ▼
   sampling: normal rate (default 5%), elevated rate for immature
   peers and for identities under probation
                   │
                   ▼
   canonical comparison preconditions met?  ── no ──► INCONCLUSIVE (tallied, no accusation)
                   │ yes
                   ▼
   deviation > threshold AND > minimum absolute deviation?
          │                     │
          ▼                     ▼
   CONFIRMED observation   candidate anomaly
   (tallied locally)            │
                                ▼
                 corroborate with a second, independent
                 connector when available (required for
                 any sanction-relevant counting)
                                │
                                ▼
              immutable local ANOMALY observation
              (subjectRole = ORIGIN for a validly signed
               record; SENDER for protocol violations)
                                │
                ┌───────────────┼──────────────────┐
                ▼               ▼                  ▼
        feeds local        courtesy notice     may be shared as a signed
        supplier state     to the supplier     observation envelope with
        (see below)        (codes 47/48)       direct peers (see below)
```

Four points differ deliberately from a naive design:

- **Sample selection is unpredictable to the sender**: whether a record is verified is decided
  by a keyed hash of a locally generated secret and the record hash, compared against the
  applicable sampling rate — not by a plain random draw. The selection is deterministic for the
  receiver (auditable) yet underivable by the sender. Records from identities that are locally
  SUSPECTED are always checked when a usable reference is available.
- **Confirmations and INCONCLUSIVE results are recorded too** (as local tallies / observation
  rows), not only anomalies — the sanction policy needs rates, and audits need the denominator.
- **A connector outage or failed reference fetch is INCONCLUSIVE**, never a confirmation and
  never an anomaly.
- **Anomaly notices (codes 47/48)** are a courtesy to the supplier: since no central party can
  tell an honest operator that its connector is misconfigured, the observer notifies the origin
  (via the delivering peer when the origin is not a direct peer). Honest operators fix
  accidental bad data quickly; for malicious ones it changes nothing. Configurable
  (`gt.gtnet.verification.notify.supplier`, default on). Notices received are also the trigger
  that turns *continued* forwarding of the same bad records into a sender-side violation.

### Relationship to the Existing Supplier Score

Unchanged from the central concept: `SupplierScoreCalculator` (delivery quality, local) and the
trust layer (data correctness, local) remain separate; blending them is an optional enhancement.

---

## Local Evidence Model

### Observations (immutable)

Every assessment produces an immutable **observation** row; nothing is ever updated in place.
Corrections are new observations that reference the superseded one.

| Field | Meaning |
|-------|---------|
| `observationUuid` | Idempotency identifier (client-generated for own observations) |
| `reporterGti` | Who observed (self, or the peer an envelope came from) |
| `subjectGti` + `subjectRole` | Which identity is assessed, in which role (ORIGIN / SENDER) |
| `senderGti` | The immediate sender involved in the delivery, when distinct |
| `eventKey` | Economic-event grouping key |
| `recordHash` | Exact signed record concerned (null for violations without a record) |
| `observationKind` | ANOMALY / SIGNATURE_INVALID / EQUIVOCATION / CONFIRMED / INCONCLUSIVE |
| `direct` | true = own verification; false = received envelope |
| `policyVersion`, `observedAt`, `expiresAt`, `detailHash`, `supersedesUuid` | Audit + lifecycle |

Uniqueness: `observationUuid` is unique; additionally at most **one unexpired observation per
(reporter, eventKey, observationKind)** is accepted at intake — one reporter cannot inflate one
event, while distinct kinds, distinct reporters and competing signed records (different
`recordHash`, e.g. equivocation) remain separately stored and auditable. Expired rows are purged
by retention, after which the same reporter may observe the same event again in a new window.

### Supplier State and Reporter Confidence (separate)

Being a reliable price supplier and being a reliable reporter are **independent properties** —
an identity can deliver honest data while filing misleading observations about competitors, and
vice versa. `gt_net_peer_trust` therefore keeps two assessments per identity:

1. **Supplier state** — may prices *originating from* (origin role) or *delivered by* (sender
   role) this identity enter authoritative data? Staged: NORMAL / SUSPECTED / QUARANTINED /
   BLACKLISTED, per role.
2. **Reporter confidence** — how much may this identity's signed observations corroborate local
   decisions? NONE / LOW / MEDIUM / HIGH, derived from **locally reproducible** facts: envelopes
   later confirmed by own verification raise it, contradicted envelopes lower it, protocol
   violations floor it; relationship maturity and the minimum supplied-volume floor
   (`g.gnet.trust.min.supplied.events`) gate it; independence caps apply. Supplied price
   volume alone never raises reporter confidence.

Reporter-confidence assessments are **local and are not gossiped** in the first release —
distributing "who is a bad reporter" claims would create a recursive reputation system that is
easy to weaponize.

### Sanction States (local)

```
NORMAL ──► SUSPECTED ──► QUARANTINED ──► BLACKLISTED
   ▲            │              │        (manual, local)
   └────────────┴──────────────┘
        automatic recovery / manual restore
```

| State | Meaning | Local effect |
|-------|---------|--------------|
| **NORMAL** | No significant evidence | None |
| **SUSPECTED** | Evidence above the watch level, or received unconfirmed envelopes | Informational; sampling elevated for the subject |
| **QUARANTINED** | Automatic, temporary, per role | Origin role: records signed by the identity are rejected at import, from any deliverer. Sender role: imports from that peer stop; the connection stays for announcements. Expires automatically (default 7 days) unless renewed by new evidence. Observation envelopes are emitted to pinned peers. |
| **BLACKLISTED** | Manual local decision by this instance's admin | Refuse connections (sender) / reject records permanently (origin); pins retained so the block survives |

**Rollout is stepwise**:

1. **Component collection (observation-only)**: locally verified confirmations, anomalies and
   INCONCLUSIVE tallies; unique economic events and exact signed records; supplied volume and
   anomaly rate (volume from `gt_net_exchange_log`); direct protocol violations; direct vs
   received evidence; reporter confirmations/contradictions. Every *simulated* decision records
   its policy version and evidence snapshot in `gt_net_sanction_audit_local`.
2. **SUSPECTED automation**: warnings and elevated sampling only.
3. **Temporary quarantine**, only after calibration on production-like data, requiring **all**
   of: rolling 30-day window; supplied-volume floor (default ≥ 500); anomaly rate above the
   local policy limit; a minimum of unique corroborated (two-connector) local events (default
   ≥ 5 for the own-evidence path, ≥ 2 when at least 2 confirmed envelopes from distinct
   reporters with sufficient reporter confidence corroborate — this second path exists only
   once envelope exchange is deployed). **Received envelopes alone — however many — can raise
   at most SUSPECTED.** Protocol violations (invalid signatures, invalid receipts) have a
   separate, stricter track since they are deterministic facts, not statistics.
4. **Permanent blocking stays manual**, except for locally proven key compromise or similarly
   severe, deterministic conditions defined by explicit local policy.

---

## Observation Notices (Local-Neighborhood Warnings)

Peer evidence exchange is deliberately modest: it is a **local-neighborhood warning system**,
not network reputation, not general gossip, and it does not converge network-wide. An instance
two relationships away from every observer learns nothing unless it observes the problem itself.
This is stated as a property, not hidden: a selectively malicious origin can target peripheral
peers, and warnings do not propagate during partitions (see
[Availability Semantics](#availability-semantics)).

Three principles:

1. **Advisory, never binding** — a received envelope is a hint to look, not a verdict.
2. **Verifiable** — envelopes are signed, reference exact record hashes, and carry a detail
   hash; recipients can fetch details and re-verify rather than believe. Envelopes about signed
   records (equivocation, provably wrong signed values) are **self-proving** once the referenced
   records are fetched.
3. **Bounded** — one hop, rate-limited, compact; details on demand only.

### Emission

When a local supplier state transitions to QUARANTINED (automatic) or BLACKLISTED (manual,
optional), the instance sends the **individual observation envelopes** underlying the decision
(not an aggregate) to its pinned secure peers (code 43, batched). Receivers do **not**
re-forward. Free-text comments and administrator notes are local-only and are never part of an
envelope or detail payload.

### Envelope (code 43 payload item; lib-generic, detail app-defined)

```json
{
  "schemaVersion": 1,
  "observationUuid": "…",
  "reporterId": "gti1:…",
  "subjectId": "gti1:…",
  "subjectRole": "ORIGIN",
  "senderId": "gti1:…",
  "eventKey": "…",
  "recordHash": "…",
  "observationKind": "ANOMALY",          // ANOMALY / SIGNATURE_INVALID / EQUIVOCATION
  "policyVersion": 3,
  "observedAt": "2026-07-11T18:00:00Z",
  "expiresAt": "2026-08-10T18:00:00Z",
  "detailHash": "…",
  "supersedesUuid": null,
  "signature": "base64(Ed25519, domain gtnet-a1/observation/v1)"
}
```

CONFIRMED and INCONCLUSIVE observations are **never disseminated** (a positive-gossip channel
would be a whitewashing vector; inconclusive results are noise).

### Receiver Processing

```
Envelope batch received from authenticated, pinned peer
(reporterId must equal the sender's identity — relayed envelopes are rejected)
   │
   ├─ per envelope: signature valid, unexpired, reporter matured?  ── no ──► reject entry
   ▼
1. Store as observation rows (direct = false)
2. Subject → at most SUSPECTED locally; elevated sampling for the subject
   (probation window gt.gtnet.verification.probation.days, default 7)
3. Optional corroboration attempt, by cost:
   a. EQUIVOCATION / provable signed-record claims → fetch the referenced
      records (codes 45/46), verify signatures: cryptographic confirmation,
      no trust in the reporter needed
   b. historical-price anomalies → fetch detail, re-fetch own connector data
      for the named dates, re-run the canonical comparison
   c. last-price anomalies → not re-checkable after the fact; probation
      sampling produces own evidence going forward
4. Outcome updates the row's verification state (CONFIRMED_LOCALLY / CONTRADICTED /
   UNVERIFIED) and the reporter's local confidence; CONFIRMED_LOCALLY envelopes
   from confident reporters count toward the quarantine corroboration path
```

### Detail Fetch (codes 45/46)

Details (full canonical records, comparison values, connector classes) are fetched **on demand
only**, by `recordHash`/`detailHash`, from the reporter that sent the envelope. Authorization:
a peer may fetch only details for envelopes it actually received from that reporter; fetches are
logged. This keeps eager dissemination compact and limits instrument/activity leakage to peers
that actively investigate.

### Abuse Defenses

| Attack | Defense |
|--------|---------|
| **Badmouthing** (false envelopes against a competitor) | Envelopes alone cap at SUSPECTED; quarantine needs own corroborated evidence; CONTRADICTED envelopes lower the reporter's local confidence until its envelopes stop counting. Provable claims (signed records) can't be faked at all. |
| **Whitewashing** | Positive envelopes do not exist in the protocol; recovery is only local evidence decay and quarantine expiry. |
| **Replay / staleness** | `observationUuid` idempotency, signed `observedAt`/`expiresAt`, clock-skew window, per-entry batch results (accepted/duplicate/expired/rejected). |
| **Flooding** | Per-reporter daily envelope limit (`g.gnet.trust.observation.max.per.day`, default 50), batch size limits, on top of the existing per-peer `dailyRequestLimit`. |
| **Sybil reporter clusters** | Each reporter must be individually pinned, matured and confident *at the receiver*; identities the receiver never admitted cannot deliver envelopes at all; independence caps group non-independent reporters. |

---

## Message Codes

New **core** codes in `GNetCoreMessageCode`, free range 40–49 plus the free 8 slot (occupied
today: 0–7, 10–13, 20, 24–28, 30, 50–54; 60+ reserved for applications). This allocation
replaces — and is incompatible with — the central variant's allocation of the same range.

| Code | Name | Direction | Purpose |
|------|------|-----------|---------|
| 8 | `GT_NET_FIRST_HANDSHAKE_REJECT_NETWORK_MODE_S` | responder → initiator | Handshake rejected: mode mismatch, identity/signature invalid, key mismatch, locally blocked, admission denied |
| 40 | `GT_NET_KEY_TRANSITION_ALL_C` | instance → all pinned peers | Dual-signed key-transition statement |
| 41 | `GT_NET_ENDORSEMENT_SEL_RR_C` | newcomer → endorser | Request a signed endorsement (manual approval at the endorser) |
| 42 | `GT_NET_ENDORSEMENT_RESPONSE_S` | endorser → newcomer | Signed endorsement statement, or rejection with reason code |
| 43 | `GT_NET_OBSERVATION_NOTICE_SEL_C` | observer → its pinned peers | Batch of signed observation envelopes (1 hop, no re-forwarding) |
| 44 | `GT_NET_OBSERVATION_NOTICE_ACK_S` | receiver → observer | Per-entry results: accepted / duplicate / expired / rejected(reason) |
| 45 | `GT_NET_EVIDENCE_DETAIL_SEL_RR_C` | envelope receiver → reporter | Fetch signed records / detail payloads by hash |
| 46 | `GT_NET_EVIDENCE_DETAIL_RESPONSE_S` | reporter → receiver | Canonical bytes + signatures / detail payloads (app-defined) |
| 47 | `GT_NET_ANOMALY_NOTICE_SEL_C` | observer → supplier | Courtesy notification: "this record deviated from my reference" |
| 48 | `GT_NET_ANOMALY_NOTICE_ACK_S` | supplier → observer | Acknowledgement |
| 49 | — | — | Reserved |

Codes are auto-registered by the `GTNetMessageCodeRegistry` constructor; payload models go into
`GTNetModelHelper`; handlers are Spring `@Component` beans discovered by
`GTNetMessageHandlerRegistry`.

### Protocol Requirements (all new codes and signed objects)

The central concept's protocol-requirements section applies, extended for signatures:

- **`schemaVersion`** in every payload and every signed object; within a supported version
  unknown fields are ignored on unsigned envelope parts, but **signed canonical forms have a
  fixed field count per version** — unknown versions are rejected with a machine-readable
  error. Stored records keep their original schema version and remain verifiable after upgrades
  (rolling-upgrade rule: verifiers support all schema versions still within retention).
- **Signature algorithm and domain** are explicit for every signed object (see Signature
  Domains); the identity material carries `keyAlgorithm` and `keyVersion`.
- **Correlation** via the existing `MessageEnvelope` reply mechanism for all `_RR_` codes.
- **Idempotency keys**: `observationUuid` (43), `transitionUuid` (40), `endorsementRequestUuid`
  (41), `noticeUuid` (47), `receiptUuid` (hop receipts); duplicates answered with the original
  result.
- **Replay protection**: envelope timestamp within the clock-skew window (default ±5 minutes);
  signed objects additionally carry their own signed times (`observedAt`, `sentAt`, `issuedAt`)
  with object-specific freshness rules (`expiresAt`, `validUntil`).
- **Permanent vs transient failures**: malformed signatures, identity-derivation mismatches and
  invalid canonical forms are **permanent** failures — they are rejected with a permanent error
  code and must never be retried as transient transport errors.
- **Partial-batch results**: batch messages (43, hop-receipted deliveries) return per-entry
  results — accepted, duplicate, expired, rejected(reason) — so one bad entry does not poison a
  batch and duplicates stay duplicate-safe.
- **Size limits**: maximum envelope payload, batch sizes (envelopes per notice default 50,
  record hashes per receipt default 200, hashes per detail fetch default 20), maximum canonical
  record and detail sizes — checked **before** persistence.
- **Stable error codes**: e.g. `SCHEMA_VERSION_UNSUPPORTED`, `CLOCK_SKEW_EXCEEDED`,
  `SIGNATURE_INVALID`, `IDENTITY_MISMATCH`, `DOMAIN_TAG_MISMATCH`, `RECEIPT_INVALID`,
  `REPORTER_NOT_ELIGIBLE`, `OBSERVATION_DUPLICATE`, `OBSERVATION_EXPIRED`, `RATE_LIMITED`,
  `ENDORSEMENT_REJECTED`, `EVIDENCE_UNKNOWN_HASH`, `NOT_PINNED`, `NOT_AUTHORIZED_FOR_DETAIL`.
- **Authorization matrix**:

| Code | Requires established tokens | Requires pinned identity | Additional conditions |
|------|-----------------------------|--------------------------|-----------------------|
| 40 | yes | yes (old key) | Dual signature (old + new key) is the effective authorization |
| 41/42 | yes | yes | Endorser-side manual approval; requester matured at the endorser |
| 43/44 | yes | yes | Reporter = authenticated sender; matured at the receiver; per-reporter daily limit |
| 45/46 | yes | yes | Requester may only fetch hashes from envelopes this reporter sent it; fetches logged |
| 47/48 | yes | yes | Only about records the receiver actually delivered or signed |

- **Rate limits** per source and per code on top of the existing per-peer `dailyRequestLimit`;
  bounded retries with backoff and negative caching for detail fetches.
- **Unknown message codes** rejected explicitly with a stable error before handler dispatch.
- **Bootstrap exceptions** remain exactly `GT_NET_PING` (0) and
  `GT_NET_FIRST_HANDSHAKE_SEL_RR_S` (1) — nothing else.

---

## Availability Semantics

There is no central availability dependency, but decentralized availability must not be
oversold:

- Direct peers can validate pinned keys, signed records and receipts **during any partition** —
  verification is fully local once identities are known.
- A connector outage on the verifying side produces **INCONCLUSIVE**, never a confirmation of
  received data and never an anomaly.
- **Local trust states of different instances may diverge indefinitely.** Divergence is not a
  protocol error and no reconciliation is attempted; each instance's state answers only "what
  do I import?".
- One-hop notices **do not converge** beyond each observer's neighborhood; partitions delay even
  that until the observer reconnects to each peer.
- **No peer can claim its evidence set is complete** for the network, and no message semantics
  imply it.

---

## Privacy

Self-certifying identities are **pseudonymous, not anonymous**: stable identifiers plus signed
records allow recipients to correlate an origin's instruments, timestamps, connector classes and
behavior over time. Direct peers know each other's URLs from the connection anyway; wider
distribution of domains is avoided by design.

| Aspect | Rule |
|--------|------|
| Identifier→domain mapping | **No party can perform it** for non-peers — there is no registry. Stronger privacy, weaker legal recourse. |
| Enumeration / bulk queries | Not applicable — there is no query service. Identity data spreads only through direct exchange. |
| Envelope disclosure | A compact envelope reveals subject, role, event key, record hash and kind — no prices, no instruments in clear (the event key is a hash). Instrument-level detail is disclosed only via authorized detail fetch. |
| Detail-on-demand | Fetch authorization is envelope-scoped (only what that reporter sent you); fetches are logged locally on both sides for abuse review. |
| Connector identifiers | Signed records carry a hashed connector-independence class, not the raw connector configuration; the independence *class* stays visible because corroboration rules need it. |
| Comments / admin notes | Local only; never part of envelopes, details or notices. |
| Callback metadata | If the optional domain callback is enabled, both parties necessarily learn of the relationship attempt and its timing — noted as an accepted disclosure of that option. |
| Retention | Signed records referenced by live prices: as long as the price rows. Observation rows and detail payloads: 12 months, then deleted. Hop receipts: 12 months. Key-transition statements: retained (identity continuity). Local sanction audit: kept, but references only event keys and component values. Expired envelopes: purged at expiry + 3 months. |
| Rotating identities | Not offered. Identity rotation for privacy would discard continuity and accountability — the stable identifier is a deliberate trade-off, stated here explicitly. |

---

## Data Model

Entity conventions as in the central concept: enum-backed `Byte` columns with enum-typed
accessors, `@JsonFormat` with `BaseConstants` patterns for temporal fields, secrets `@JsonIgnore`
and encrypted at rest, `TINYINT(1)` only for booleans read via interface projections.
**Everything lives in the normal instance schema.** All identity columns are `*_gti VARCHAR(64)`
(full identifier, never truncated).

### Instance Identity (lib: `grafiosch-base`)

Singleton (PK fixed to 1, CHECK constraint):

```java
@Entity
@Table(name = "gt_net_instance_identity")
public class GTNetInstanceIdentity {
  @Id
  @Column(name = "id_gt_net_instance_identity")
  private Integer idGtNetInstanceIdentity;   // always 1 (CHECK constraint)

  @Column(name = "secure_network", nullable = false)
  private boolean secureNetwork = false;

  @Column(name = "instance_gti", length = 64)
  private String instanceGti;                // deriveGti(publicKey); null until secure mode

  @Column(name = "key_algorithm", length = 16)
  private String keyAlgorithm;               // "Ed25519"

  @Column(name = "key_version")
  private Integer keyVersion;                // incremented on transition

  @Column(name = "public_key", length = 64)
  private String publicKey;                  // base64 canonical form

  @JsonIgnore
  @Column(name = "private_key", length = 512)
  private String privateKey;                 // encrypted at rest (Jasypt); never serialized

  @Column(name = "key_backed_up", nullable = false)
  private boolean keyBackedUp = false;       // operator confirmed an encrypted key backup

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "key_rotated_at")
  private LocalDateTime keyRotatedAt;
}
```

### Known Identities (lib)

```sql
CREATE TABLE IF NOT EXISTS gt_net_known_identity (
  instance_gti VARCHAR(64) PRIMARY KEY,
  key_algorithm VARCHAR(16) NOT NULL,
  key_version INT NOT NULL,
  public_key VARCHAR(64) NOT NULL,
  superseded_by_gti VARCHAR(64),          -- set by a verified dual-signed key transition
  first_seen_at DATETIME NOT NULL
) ENGINE=InnoDB;
```

### Peer Pinning (lib) — columns on `gt_net_config`

```sql
ALTER TABLE gt_net_config
  ADD COLUMN IF NOT EXISTS partner_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_public_key VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_key_version INT,
  ADD COLUMN IF NOT EXISTS partner_canonical_domain VARCHAR(128),
  ADD COLUMN IF NOT EXISTS key_pinned_at DATETIME;
```

### Local Trust State per Identity (lib)

Supplier state per role and reporter confidence, independently:

```sql
CREATE TABLE IF NOT EXISTS gt_net_peer_trust (
  subject_gti VARCHAR(64) PRIMARY KEY,
  origin_state TINYINT NOT NULL DEFAULT 0,      -- NORMAL/SUSPECTED/QUARANTINED/BLACKLISTED
  origin_state_until DATETIME,
  sender_state TINYINT NOT NULL DEFAULT 0,
  sender_state_until DATETIME,
  reporter_confidence TINYINT NOT NULL DEFAULT 0,  -- NONE/LOW/MEDIUM/HIGH
  decision_source TINYINT NOT NULL DEFAULT 0,      -- AUTOMATIC/MANUAL
  updated_at DATETIME NOT NULL
) ENGINE=InnoDB;
```

### Signed Records (lib generic; payload semantics app-defined)

Preserves the **exact canonical bytes** that were verified; price rows reference these by hash:

```sql
CREATE TABLE IF NOT EXISTS gt_net_signed_record (
  record_hash VARCHAR(64) PRIMARY KEY,      -- SHA-256 hex of canonical_payload
  origin_gti VARCHAR(64) NOT NULL,
  schema_version INT NOT NULL,
  event_key VARCHAR(64) NOT NULL,
  canonical_payload MEDIUMTEXT NOT NULL,    -- exact signed bytes (UTF-8)
  signature VARCHAR(96) NOT NULL,
  created_at DATETIME NOT NULL,
  valid_until DATETIME,
  received_at DATETIME NOT NULL
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_signed_record_event ON gt_net_signed_record;
ALTER TABLE gt_net_signed_record ADD INDEX idx_signed_record_event (event_key);
```

A `HISTORICAL_BATCH` record covers a bounded series and is stored as **one** row; all imported
history rows of the batch share its `record_hash` (see Historical Batch Records).

### Hop Receipts (lib)

```sql
CREATE TABLE IF NOT EXISTS gt_net_hop_receipt (
  receipt_uuid VARCHAR(36) PRIMARY KEY,
  sender_gti VARCHAR(64) NOT NULL,
  recipient_gti VARCHAR(64) NOT NULL,
  record_hashes MEDIUMTEXT NOT NULL,        -- JSON array of record hashes (bounded)
  sent_at DATETIME NOT NULL,
  canonical_payload MEDIUMTEXT NOT NULL,    -- exact signed receipt bytes
  signature VARCHAR(96) NOT NULL,
  received_at DATETIME NOT NULL
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_hop_receipt_sender ON gt_net_hop_receipt;
ALTER TABLE gt_net_hop_receipt ADD INDEX idx_hop_receipt_sender (sender_gti, sent_at);
```

### Observations (lib core + app detail)

```sql
CREATE TABLE IF NOT EXISTS gt_net_observation (
  id_observation BIGINT AUTO_INCREMENT PRIMARY KEY,
  observation_uuid VARCHAR(36) NOT NULL UNIQUE,
  reporter_gti VARCHAR(64) NOT NULL,          -- self for direct observations
  subject_gti VARCHAR(64) NOT NULL,
  subject_role TINYINT NOT NULL,              -- ORIGIN / SENDER
  sender_gti VARCHAR(64),                     -- immediate sender involved, if distinct
  event_key VARCHAR(64) NOT NULL,
  record_hash VARCHAR(64),                    -- exact record; NULL for record-less violations
  observation_kind TINYINT NOT NULL,          -- ANOMALY/SIGNATURE_INVALID/EQUIVOCATION/CONFIRMED/INCONCLUSIVE
  direct TINYINT(1) NOT NULL,                 -- own verification vs received envelope
  verification_state TINYINT NOT NULL DEFAULT 0,  -- received only: UNVERIFIED/CONFIRMED_LOCALLY/CONTRADICTED
  counted TINYINT(1) NOT NULL,
  policy_version INT NOT NULL,
  detail_hash VARCHAR(64),
  supersedes_uuid VARCHAR(36),                -- corrections reference, never mutate
  canonical_payload MEDIUMTEXT,               -- exact envelope bytes for received/emitted rows
  signature VARCHAR(96),
  observed_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  CONSTRAINT UK_Observation_ReporterEventKind UNIQUE (reporter_gti, event_key, observation_kind)
) ENGINE=InnoDB;
DROP INDEX IF EXISTS idx_observation_subject ON gt_net_observation;
ALTER TABLE gt_net_observation ADD INDEX idx_observation_subject (subject_gti, subject_role, observed_at);
```

The unique constraint holds among retained rows; retention purges expired rows, after which a
new observation window for the same (reporter, event, kind) opens. Rows are immutable
(corrections via `supersedes_uuid`). App detail tables `gt_net_observation_lastprice` /
`gt_net_observation_historical` (1:1 FK, `ON DELETE CASCADE`) carry the comparison details:
`instrument_key VARCHAR(50)`, `asset_class TINYINT`, `special_investment_instrument TINYINT`,
supplied/expected values and times, `connector_class VARCHAR(80)`,
`second_connector_class VARCHAR(80)`, `deviation_percent`.

### Endorsements (lib)

`gt_net_endorsement` — endorsements this instance has **issued** and **received/used**:
`endorser_gti`, `subject_gti`, `direction` (ISSUED/ACCEPTED), `statement` (exact signed JSON),
`issued_at`, `expires_at`, `outcome` (OPEN / SUBJECT_OK / SUBJECT_SANCTIONED — feeds endorser
confidence).

### Local Sanction Audit (lib)

`gt_net_sanction_audit_local` — append-only: `subject_gti`, `subject_role`, `old_state`,
`new_state`, `decision_source` (AUTOMATIC/MANUAL/SIMULATED), `actor` (admin ID for manual),
`policy_version`, `evidence_snapshot` (JSON: component values used), `reason`, `decided_at`,
`expires_at`. Observation-only phase writes SIMULATED rows.

### Thresholds (app)

`gt_net_threshold_config` — identical structure and default values as the central variant's
table (including the NOT-NULL sentinel `-1` for `special_investment_instrument` and the
`crypto` flag column), but living in the instance schema and edited by the local admin. Seeded
via `INSERT IGNORE` so upgrades never overwrite admin changes.

### Price Attribution (app)

```java
// Historyquote.java, GTNetHistoryquote.java, GTNetLastprice.java
@Column(name = "received_from_id_gt_net")
private Integer receivedFromIdGtNet;   // local peer row; FK gt_net, ON DELETE SET NULL —
                                       // signed origin + hop receipt keep audit intact
                                       // even after peer deletion

@Column(name = "origin_gti", length = 64)
private String originGti;              // verified signer; NULL = unattributed (legacy pool)

@Column(name = "record_hash", length = 64)
private String recordHash;             // → gt_net_signed_record (canonical bytes + signature)
```

---

## Database Migration (sketch)

All migrations live in the normal location
(`grafioschtrader-server/src/main/resources/db/migration/`, lib tables via the grafiosch
mechanism) and must be idempotent per root `CLAUDE.md`, with stable constraint/index names.
Never edit `gt_ddl.sql`.

```sql
CREATE TABLE IF NOT EXISTS gt_net_instance_identity (
  id_gt_net_instance_identity INT PRIMARY KEY CHECK (id_gt_net_instance_identity = 1),
  secure_network TINYINT(1) NOT NULL DEFAULT 0,
  instance_gti VARCHAR(64),
  key_algorithm VARCHAR(16),
  key_version INT,
  public_key VARCHAR(64),
  private_key VARCHAR(512),
  key_backed_up TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME,
  key_rotated_at DATETIME
) ENGINE=InnoDB;

-- gt_net_known_identity, gt_net_peer_trust, gt_net_signed_record, gt_net_hop_receipt,
-- gt_net_observation (+ app detail tables), gt_net_endorsement, gt_net_sanction_audit_local:
-- CREATE TABLE IF NOT EXISTS as specified in the data model, indexes via
-- DROP INDEX IF EXISTS … ; ALTER TABLE … ADD INDEX … (stable names)

ALTER TABLE gt_net_config
  ADD COLUMN IF NOT EXISTS partner_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_public_key VARCHAR(64),
  ADD COLUMN IF NOT EXISTS partner_key_version INT,
  ADD COLUMN IF NOT EXISTS partner_canonical_domain VARCHAR(128),
  ADD COLUMN IF NOT EXISTS key_pinned_at DATETIME;

ALTER TABLE historyquote
  ADD COLUMN IF NOT EXISTS origin_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;
ALTER TABLE historyquote DROP FOREIGN KEY IF EXISTS FK_Historyquote_ReceivedFromGtNet;
ALTER TABLE historyquote ADD CONSTRAINT FK_Historyquote_ReceivedFromGtNet
  FOREIGN KEY (received_from_id_gt_net) REFERENCES gt_net (id_gt_net) ON DELETE SET NULL;
ALTER TABLE gt_net_historyquote
  ADD COLUMN IF NOT EXISTS origin_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;
ALTER TABLE gt_net_lastprice
  ADD COLUMN IF NOT EXISTS origin_gti VARCHAR(64),
  ADD COLUMN IF NOT EXISTS record_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS received_from_id_gt_net INT;

-- gt_net_threshold_config: identical CREATE + INSERT IGNORE defaults as the central concept
-- (structure and values, incl. the -1 sentinel and crypto flag), located in the instance schema.
```

Retention jobs delete, per class: expired observations and detail rows, expired envelopes, old
hop receipts, superseded signed records no longer referenced by price rows — each with its own
retention setting (see Privacy).

---

## Attack Scenario Analysis

Honest assessment, including where this variant is weaker than the central one:

| Attack | Defense | Residual risk (vs central in parentheses) |
|--------|---------|-------------------------------------------|
| Forged identifier / impersonating an identity | Identifier is the full hash of the public key; handshake requires signing the nonce | None under crypto assumptions (equal) |
| Tampering with forwarded prices | Origin signature over exact canonical bytes fails → record rejected, deterministic protocol violation by the sender | None for signed records (stronger than central's first release) |
| Forged origin attribution | Stamping a foreign identity requires forging its signature; unverified claims never damage the claimed identity | None for signed records; unattributed records are excluded from the secure pool (stronger) |
| Denying a delivery ("I never sent that") | Recipient-bound hop receipt signed by the sender | None for receipted batches (stronger) |
| Origin signs wrong values (bad connector or malice) | Local sampled verification against own connectors; origin-role quarantine; courtesy notices help honest origins | Detected only where verification runs; statistical, threshold-dependent (central: same detection, but network-wide sanction once found) |
| Origin equivocation | Two valid records, same event, different hashes = portable, self-proving evidence | Detected only where the conflicting records meet (weaker: central sees all reports) |
| Re-appearing after being blocked | New keypair is free; gated only by each instance's admission policy and maturation | **Materially weaker than central** (no manual authority, no global rate limit); mitigated per instance, never globally |
| Sybil swarm | Each identity must be individually admitted, pinned and matured per instance; envelopes from non-pinned identities impossible; envelopes cap at SUSPECTED; independence caps | **Weaker than central**: no operator grouping, no global influence caps; an OPEN-policy instance is exposed by its own choice. Sybils are not prevented — only their per-instance influence is bounded |
| Badmouthing campaign | Quarantine requires own corroborated evidence; contradicted envelopes sink reporter confidence; provable claims cannot be faked | Reputation damage limited to SUSPECTED + wasted re-verification effort (roughly equal) |
| Whitewashing | No positive gossip exists in the protocol | None (equal) |
| Compromised mature peer endorses attackers | Endorsements never bypass manual approval; influence capped; endorser confidence sinks with outcomes | Bounded by the admitting operator's judgment (central: same operator judgment, plus issuer approval) |
| Key theft | Dual-signed transition (both keys must prove possession); peers alert on key mismatch | **Weaker than central**: no authoritative revocation; unrotated/unreached peers stay exposed until manual unpinning |
| Cloned installation / DB restore | Inconsistent metadata visible to shared peers | **Weaker**: clones on disjoint peer sets undetected (central detects via conflicting daily queries) |
| Gaming the sampling | Keyed-hash selection (local secret + record hash) + elevated/probation rates prevent anticipation | Colluding connectors — countered by corroboration requirements, not by sampling (equal) |
| Warning suppression | None — envelopes reach only the observer's direct peers by design | Accepted: 1-hop coverage, no convergence |

---

## Configuration

No deployment properties are required — there are no central servers to configure. All settings
are globalparameters (admin-editable, with `input_rule`):

```sql
-- Library-owned (g. prefix)
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.admission.policy', 0, 0, 'min:0,max:1');          -- 0=MANUAL 1=OPEN
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.admission.domain.callback', 0, 0, 'min:0,max:1'); -- optional callback off by default
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.maturation.days', 30, 0, 'min:0,max:180');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.min.supplied.events', 1000, 0, 'min:0,max:100000');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.observation.expiry.days', 30, 0, 'min:1,max:90');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.observation.max.per.day', 50, 0, 'min:1,max:500');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('g.gnet.trust.quarantine.days', 7, 0, 'min:1,max:90');

-- Application-owned (gt. prefix)
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.verification.sampling.rate', 5, 0, 'min:0,max:100');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.verification.sampling.rate.elevated', 20, 0, 'min:0,max:100');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.verification.probation.days', 7, 0, 'min:1,max:30');
INSERT IGNORE INTO globalparameters (property_name, property_int, changed_by_system, input_rule)
  VALUES ('gt.gtnet.verification.notify.supplier', 1, 0, 'min:0,max:1');
```

Local quarantine policy parameters (window days, volume floor, event minima) are further
globalparameters following the same pattern; their values are snapshotted into every automatic
or simulated decision in `gt_net_sanction_audit_local`.

The secure-network switch, keypair and identity state live in `gt_net_instance_identity`
(UI-managed).

---

## Implementation Phases

Signed provenance and local verification come first; anything resembling reputation comes last,
after calibration. Enforcement steps are gated on the results of the preceding observation-only
steps.

### Phase 1: Identity & Canonical Foundations (lib)
- `GTNetInstanceIdentity` (keypair generation, encrypted private key, backup guidance, singleton
  row), GTI derivation + verification utility, `GTNetKnownIdentity`
- Canonical-serialization utility, signature-domain constants, hashing; **test vectors** as unit
  fixtures (release-blocking)
- Dual-signed key-transition statement (code 40): creation, broadcast, verification,
  supersession and local-history policy

### Phase 2: Handshake, Pinning & Network Separation (lib)
- `FirstHandshakeMsg` extension, nonce transport design, key-possession proof, code 8 rejection
  with reason codes
- TOFU pinning columns on `gt_net_config`; continuity checks on every handshake/token refresh;
  key-mismatch alerting; manual bilateral admission (MANUAL/OPEN policies); optional domain
  verification paths incl. the hardened callback (off by default, full SSRF/rebinding rule set)
- Mode-switch semantics (unsecure-peer demotion, unattributed-pool rule)
- Frontend: network-mode UI, identity display (own GTI, fingerprint), pinned-peer list with
  fingerprints, mismatch alerts

### Phase 3: Signed Records, Hop Receipts & Attribution (GT + lib)
- Canonical signed price record and historical batch record (app schema), signing of
  own-connector prices
- `gt_net_signed_record` (exact-byte preservation), `gt_net_hop_receipt`, verification + reject
  on failure at import, pass-through forwarding rules
- `received_from_id_gt_net`, `origin_gti`, `record_hash` columns; DTO/handler extension
- **Gate**: no signed-network price enters authoritative data before this phase is complete

### Phase 4: Local Verification — observation only (GT)
- Secret-keyed sampling (normal/elevated rates), canonical comparison per the central concept's
  rule set;
  CONFIRMED / ANOMALY / INCONCLUSIVE observation rows; local threshold table seeded by migration
- Event keys, immutable observation storage, exchange-log volume statistics
- Elevated maturation sampling; courtesy anomaly notices (codes 47/48)
- **No sanction states change in this phase**

### Phase 5: Supplier/Reporter State & Calibration
- `gt_net_peer_trust` (origin/sender/reporter split), component calculation, SIMULATED decisions
  in `gt_net_sanction_audit_local`, calibration against production-like data
- Enable SUSPECTED automation (warnings + elevated sampling only)

### Phase 6: Temporary Quarantine
- Enable the own-evidence quarantine path per calibrated policy; role-separated enforcement on
  import; manual local blacklist workflow + UI; protocol-violation fast track

### Phase 7: Observation Notices (lib transport + GT payloads)
- Codes 43–46: envelope emission on local quarantine/blacklist, receiver pipeline (verification
  states, probation sampling, detail fetch with envelope-scoped authorization), reporter
  confidence updates, rate limits; measure value and volume before relying on the
  envelope-corroborated quarantine path

### Phase 8: Endorsements & Lifecycle Completion (lib)
- Codes 41/42, `gt_net_endorsement`, endorsement display in admission, endorser-outcome
  bookkeeping
- Remaining lifecycle workflows: key backup/restore procedures, clone warnings, retention jobs,
  metrics/logging of trust decisions, rolling-upgrade verification of stored schema versions

---

## Optional Enhancements

Deliberately excluded from the first release:

- **Inventory/fetch evidence synchronization** (the successor to 1-hop notices, added only if
  operationally justified by Phase-7 measurements): peers periodically exchange bounded
  inventories of recent evidence hashes or time-partitioned summaries; missing compact envelopes
  are requested by hash; details remain on-demand; per-hash accept/duplicate/expired/rejected
  acknowledgements; TTL and hop metadata, rate and batch limits, retry backoff and negative
  caching. Provides eventual *partial* dissemination without treating any peer's inventory as
  complete or any assessment as authoritative.
- **Weighted reporter scoring**: continuous per-reporter weights from confirmed/contradicted
  history instead of the coarse confidence bands.
- **Trust-state snapshot on request**: a newcomer asks an established peer for its current
  warning set as a batch of (re-)sent envelopes — explicit pull instead of having missed the
  original notices.
- **Reputation-aware supplier ordering**: blend local trust data into
  `SupplierScoreCalculator` once sanction behavior is proven stable (same as central).
- **Upgrade path to the central variant**: the identity foundation is deliberately compatible —
  a later central issuer could certify existing self-certified keypairs (binding identifier,
  domain hash and public key exactly as the central concept's certificate does) without changing
  any instance's identity, keys or attribution history. The two concepts converge on the same
  keypair; only the trust layer on top differs.

---

## Conclusion

| Feature | Benefit |
|---------|---------|
| **Self-certifying, versioned identity** | Unforgeable identifiers and verifiable signatures without any issuer, registration or approval latency; crypto-agile by construction |
| **Canonical signed origin records + hop receipts** | Tampering, forged attribution and denied deliveries are deterministically detectable; evidence is portable and self-proving — stronger provenance than the central variant's first release |
| **Split origin/forwarder liability** | Penalties land on the responsible role; honest relays are not punished for foreign mistakes |
| **Key continuity (TOFU) + manual admission** | Proven SSH-style trust model; impersonation requires key theft; every instance controls its own door |
| **Local evidence + canonical comparison** | Identical anomaly quality as the central variant; confirmations and INCONCLUSIVE results are first-class, so rates and audits are meaningful |
| **Staged, observation-first local sanctions** | Calibrated, auditable decisions with policy snapshots; no external authority needed |
| **Bounded, verifiable observation notices** | Direct peers learn of problems sooner without trusting the messenger; gossip can never exclude anyone by itself |
| **No central infrastructure** | No operator burden, no single point of trust or failure, full offline/partition tolerance |

The price is stated in [Accepted Security Losses](#accepted-security-losses): free identity
creation, one-hop warning coverage without network convergence, no global revocation, weaker
clone detection and no policy coordination. Operators who need those guarantees should prefer
the central variant; operators who value autonomy, privacy and zero central dependency get
verifiable provenance and locally controlled quality assurance that improve on today's GTNet at
every step of the phase plan — and a compatible upgrade path if a central issuer is ever
introduced.
