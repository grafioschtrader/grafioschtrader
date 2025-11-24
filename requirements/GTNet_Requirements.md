# GTNet Requirements Overview (Draft)

## Source Material
- GitHub issues [#82](https://github.com/grafioschtrader/grafioschtrader/issues/82), [#83](https://github.com/grafioschtrader/grafioschtrader/issues/83) and [#105](https://github.com/grafioschtrader/grafioschtrader/issues/105)
- Backend domain model and services under `backend/grafioschtrader-common/src/main/java/grafioschtrader/gtnet` and `backend/grafioschtrader-server/src/main/java/grafioschtrader`
- Frontend implementation in `frontend/src/app/gtnet`

## Vision and Goals
1. Allow Grafioschtrader instances to register themselves, discover other participants and exchange trust tokens so that machine-to-machine (M2M) traffic can be authenticated without a central broker (#83).
2. Provide an optional marketplace for intraday prices and entity data (e.g., history quotes) between trusted peers before external feeds are queried (#82).
3. Leverage and extend the GT messaging subsystem so that administrators can negotiate data access, report data issues and announce maintenance windows across instances (#105).
4. Keep participation voluntary. Each administrator decides which capabilities are shared (`spreadCapability`) and enforces daily limits for both inbound and outbound traffic.

## Functional Requirements

### 1. Network Membership & Configuration
- Each remote domain is represented by `GTNet` (`backend/.../entities/GTNet.java`). Required data includes:
  - `domainRemoteName` (base URL), `timeZone`, and capability toggles for entity and last price exchange.
  - Locally generated `tokenThis` and remotely supplied `tokenRemote`. The combination of both tokens authenticates requests.
  - `spreadCapability` determines whether a domain may announce other providers.
  - `entityServerState` / `lastpriceServerState` use `GTNetServerStateTypes` to tell consumers whether services are OPEN, CLOSED, in MAINTENANCE, etc.
  - Daily request limits and their counters exist on both sides; they must be reset at UTC midnight.
  - Consumer specific knobs such as `lastpriceConsumerUsage` (priority) and `lastpriceUseDetailLog` (audit toggle) define how aggressively this server consumes remote data.
- When saving a `GTNet` entry, the backend (`GTNetJpaRepositoryImpl.saveOnlyAttributes`) validates the remote by downloading `/actuator/info` via `BaseDataClient`. If the domain belongs to the current machine, the local instance id is persisted under `gtnet.my.entry.id` (`GlobalParamKeyDefault`).

### 2. Messaging Model & Handshake
- Messages are stored in `GTNetMessage` (`backend/.../GTNetMessage.java`). Conversation threading uses `replyTo` and `idSourceGtNetMessage` so both peers can correlate replies.
- `SendReceivedType` distinguishes outgoing, incoming and transient answer messages. `gtNetMessageParamMap` (map of `BaseParam`) keeps typed fields that are defined per message type.
- `GTNetMessageCodeType` enumerates all workflow steps: handshake, server list, last price/entity/both requests, revocations, and broadcast maintenance announcements. Codes ending with `_C` are initiated by the client UI; codes ending with `_S` are responses. Codes containing `_ALL_` are broadcast.
- `GTNetModelHelper` maps message codes to request models (e.g., `FirstHandshakeMsg`, `MaintenanceMsg`). The helper exposes form descriptors so the frontend (`GTNetMessageEditComponent`) can build a dynamic dialog for each message type and know whether a response is mandatory.
- Submitting a message (`GTNetJpaRepositoryImpl.submitMsg`) works as follows:
  1. Determine the target list (single domain or broadcast) and check whether a response is expected.
  2. Create a `GTNetMessage` row per target via `GTNetMessageJpaRepository.saveMsg`.
  3. Call `sendMessage`, which ensures a successful first contact (`hasOrCreateFirstContact`) before non-handshake messages are sent.
  4. Persist received responses in the local database so the UI tree can display them.
- First contact: when `tokenThis` is null, the system generates a GUID, sends `GT_NET_FIRST_HANDSHAKE_S` that includes the local GTNet payload, and expects either ACCEPT or REJECT. The receiver must validate reachability, store `tokenRemote`, and reply accordingly.

### 3. Machine-to-Machine Transport
- `MessageEnvelope` (`backend/.../gtnet/m2m/model/MessageEnvelope.java`) wraps every M2M call. Besides the `GTNetMessage` attributes it can contain an arbitrary JSON payload (e.g., serialized `GTNet` during handshake).
- HTTP transport uses `BaseDataClient`:
  - `sendToMsg` posts to `POST /m2m/gtnet` on the remote and attaches the remote supplied token in the `Authorization` header.
  - `getActuatorInfo` performs GET `/api/actuator/info` to verify remote metadata (currently used as liveness check while editing GTNet entries).
- Incoming requests hit `GTNetM2MResource.receiveMessage`, which delegates to `GTNetJpaRepository.getMsgResponse`. The implementation currently handles ping and handshake, but other message codes still need to be wired.
- Requirement: the resource must validate `Authorization` tokens against `GTNet.tokenThis` and reject callers that are unknown or revoked. This check is not implemented yet.

### 4. Server List & Data Exchange Tracking
- `GTNetDataExchange` (`backend/.../entities/GTNetDataExchange.java`) declares which entity instances are exchanged, who owns the master record and when messages were last sent/received. Fields `sendMsgCode`, `recvMsgCode`, `requestEntityTimestamp`, and `giveEntityTimestamp` make it possible to audit the current state.
- `MessageCodesGTNetwork` is a reduced enum devoted to this table ("data ready" / "cannot be delivered anymore").
- Requirements derived from the data model:
  - Each exchanged entity pair must have a record specifying direction (`inOut`), remote ids and indirection depth.
  - Every outgoing or incoming message needs to update the corresponding timestamps to support resumption after outages.
  - Admins must be able to revoke access (`GT_NET_*_REVOKE_*`) which flips the records into a terminal state.

### 5. Intraday Last Price Distribution
- `GTNetLastprice` (base), `GTNetLastpriceSecurity`, and `GTNetLastpriceCurrencypair` store normalized OHLCV data. They back the provider and consumer monitors.
- Aggregated logging occurs via `GTNetLastpriceLog` and the optional `GTNetLastpriceDetailLog` to reconstruct who changed which price at what time.
- `GTNetLastpriceService` orchestrates updates:
  - Finds providers with `lastpriceServerState = SS_OPEN` and `lastpriceConsumerUsage > 0`.
  - Reads remote values (`readUpdateGetLastpriceValues`) and pushes them into the local `Security` / `Currencypair` tables.
  - The current implementation is incomplete—currency fetching and MultiKeyMap mapping still need to be finished and invoked from update workflows.
- Requirement: daily request counters for consumer/provider flows must be enforced before contacting external instances.

### 6. Auto Answers & Manual Review
- Certain request codes demand a timely reply. `GTNetMessageAnswer` (`backend/.../entities/GTNetMessageAnswer.java`) allows administrators to predefine up to three conditional responses evaluated with EvalEx expressions (e.g., time-of-day, request load). `waitDaysApply` enforces a cooling-off period after negative answers.
- `GTNetMessageAnswerJpaRepositoryImpl` currently only looks up an answer template; the actual evaluation logic is a placeholder. Implementation must:
  - Validate the provided conditions.
  - Substitute payload variables (`meRequest.payload`, daily counts, timezone differences) in the expressions.
  - Automatically create and send `GTNetMessage` responses when a condition matches.
- The Angular component `GTNetMessageAutoAnswerComponent` is empty; it needs CRUD operations to maintain these templates and to surface evaluation results to admins.

### 7. Frontend UX Requirements
- `GTNetSetupTableComponent` lists all domains, highlights whether the local system is registered, exposes CRUD, and provides a context menu entry "GT_NET_MESSAGE_SEND" that launches `GTNetMessageEditComponent`. The table must also render nested message threads using `GTNetMessageTreeTableComponent`.
- Dynamic message forms rely on descriptors fetched from `/api/gtnetmessage/msgformdefinition`. Every message code exposed in the UI must have a matching descriptor in `GTNetModelHelper`, otherwise the form builder fails.
- The admin tree contributor (`frontend/src/app/shared/contributor/admindata-main-tree.contributor.ts`) still comments out the GTNet branch. Once the feature matures the branch has to be added back so navigation matches the routes defined in `app.routes.ts`.
- Monitor views (`GTNetConsumerMonitorComponent`, `GTNetProviderMonitorComponent`) currently contain placeholder text. Requirements include:
  - Consumer dashboard showing which providers are queried, last response times and whether limits are hit.
  - Provider dashboard listing queued requests, accepted vs rejected counts, and log drill-down based on `GTNetLastpriceLog`/`GTNetLastpriceDetailLog`.
- All GTNet screens reuse the existing help ids (`HelpIds.HELP_GT_NET`, etc.); documentation at `HELP_URL_BASE` must contain the matching topics.

## Non-Functional & Operational Requirements
1. **Authentication** – Every M2M request must include the remote-supplied `tokenRemote`; local services shall verify this token, log failed attempts, and optionally revoke the token by clearing `tokenRemote`.
2. **Observability** – Each send/receive operation is persisted in `GTNetMessage`, `GTNetDataExchange` and, for prices, `GTNetLastpriceLog`. Administrators must be able to audit who triggered any change.
3. **Rate Limiting** – Daily counters on `GTNet` entities enforce fairness. Background jobs must reset counters at `00:00 UTC`.
4. **Resilience** – When a remote instance is unreachable (`BaseDataClient` errors), the system shall retry per connector policy, mark the message as failed, and surface the problem in the UI.
5. **Extensibility** – New message types only require updating `GTNetModelHelper` plus their request/response models; the UI consumes the metadata automatically.
6. **Security** – Handshake payloads include `GTNet` metadata. Sensitive fields (`tokenThis`, `tokenRemote`) are `@JsonIgnore` so they never leak to untrusted clients.

## Known Gaps & Follow-Up Tasks
1. `GTNetJpaRepositoryImpl.checkHandshake` returns `null`; it must produce ACCEPT/REJECT envelopes, update local tokens and consider pinging the caller before persisting the record.
2. `GTNetMessageAnswerJpaRepositoryImpl.getMessageAnswerBy` lacks the EvalEx evaluation logic and never returns actionable responses.
3. Token validation is missing on the M2M entry point. Requests currently bypass authentication.
4. Broadcast message handling (maintenance/operation discontinued) returns an empty list because `getTargetDomains` only handles `_ALL_` for maintenance and does not iterate over the selected providers.
5. Frontend monitor and auto-answer components still show placeholders; they must fetch repositories, support filters and expose aggregated statistics.
6. `GTNetLastpriceService` does not yet merge remote data into local securities/currency pairs; `readUpdateGetLastpriceValues` is unused.
7. `GTNetLastpriceCurrencypairJpaRepositoryImpl` inherits from an empty base class; shared logic for updating/merging last prices should be implemented in `GTNetLastpriceSecurityCurrencyService`.
8. UI navigation: the Admin tree intentionally hides the GTNet nodes. Re-enable them when permissions, help pages and user guidance are ready.
9. Form validation: `submitMsg` contains a `TODO check model integrity`. Incoming payloads from the UI should be validated against the dynamic descriptors before a message is sent.
10. Test coverage and migration scripts for the new tables (`gt_net_*`) must be expanded; currently no automated tests exercise the handshake or messaging flows.

## Appendix – Message Codes & Intent
- `GT_NET_PING` – Liveness probe; response mirrors the ping.
- `GT_NET_FIRST_HANDSHAKE_S` / `_ACCEPT_S` / `_REJECT_S` – Token exchange and connection approval.
- `GT_NET_UPDATE_SERVERLIST_*` – Remote discovery; `_SEL_C` requests the list, `_ACCEPT_S` returns it, `_REJECTED_S` denies access, `_REVOKE_SEL_C` withdraws permission.
- `GT_NET_LASTPRICE_*` – Intraday price sharing lifecycle (request, in-progress, accept, reject, revoke).
- `GT_NET_ENTITY_*` – Same lifecycle for entity/history data.
- `GT_NET_BOTH_*` – Combined entity + price negotiation.
- `GT_NET_MAINTENANCE_ALL_C` – Broadcast maintenance with `MaintenanceMsg` payload to inform all consumers.
- `GT_NET_OPERATION_DISCONTINUED_ALL_C` – Broadcast shutdown announcement.

This document serves as a baseline. Please review, correct and expand it before implementation continues.
