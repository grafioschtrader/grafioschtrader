# GTNet Security Concept

## Problem Statement

### Current Situation

GTNet enables peer-to-peer exchange of price data (historical and intraday) between Grafioschtrader instances. Currently, there is **no accountability or quality assurance** for this exchanged data:

1. **No Attribution**: When a price is received via GTNet, there is no record of which instance originally provided it
2. **No Quality Verification**: Incorrect prices (whether accidental or malicious) cannot be traced back to their source
3. **No Defense Against Bad Actors**: A malicious instance can inject incorrect prices without consequences
4. **No Blacklist Mechanism**: Even if a bad actor is identified, there is no systematic way to block them

### Threat Scenarios

| Threat | Description | Current Defense |
|--------|-------------|-----------------|
| **Accidental Bad Data** | Instance has misconfigured connector, provides wrong prices | None |
| **Malicious Manipulation** | Actor intentionally provides false prices to manipulate portfolios | None |
| **Sybil Attack** | Bad actor creates new identity after being identified | None |
| **Data Poisoning** | Gradual injection of slightly wrong prices to avoid detection | None |

### Privacy Requirement

Price recipients should not be able to identify the **source domain URL** of prices that were forwarded through intermediate instances. A centrally-issued UUID provides attribution without revealing the domain.

---

## Solution: Two Separate Networks

The solution divides GTNet into two **completely separate networks** that do not communicate with each other:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│                    ┌───────────────────────────────┐                     │
│                    │      CENTRAL SERVER CLUSTER   │                     │
│                    │         (Galera Sync)         │                     │
│                    │                               │                     │
│                    │  grafioschtrader.info ◄─────► grafioschtrader.com  │
│                    │                               │                     │
│                    │  • Issues UUIDs               │                     │
│                    │  • Verifies instances         │                     │
│                    │  • Manages blacklist          │                     │
│                    │  • Stores trust scores        │                     │
│                    │  • Receives anomaly reports   │                     │
│                    │  • Provides thresholds        │                     │
│                    └───────────────┬───────────────┘                     │
│                                    │                                     │
│         ┌──────────────────────────┼──────────────────────────┐          │
│         │                          │                          │          │
│         ▼                          ▼                          ▼          │
│   ┌───────────┐             ┌───────────┐             ┌───────────┐      │
│   │Instance A │◄───────────►│Instance B │◄───────────►│Instance C │      │
│   │UUID: xxx  │             │UUID: yyy  │             │UUID: zzz  │      │
│   └───────────┘             └───────────┘             └───────────┘      │
│         │                                                   │            │
│         │  Price exchange with UUID attribution             │            │
│         └───────────────────────────────────────────────────┘            │
│                                                                          │
│                         SECURE GTNET                                     │
│                    (Central-issued UUIDs)                                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│   ┌───────────┐             ┌───────────┐             ┌───────────┐      │
│   │Instance X │◄───────────►│Instance Y │◄───────────►│Instance Z │      │
│   │(no UUID)  │             │(no UUID)  │             │(no UUID)  │      │
│   └───────────┘             └───────────┘             └───────────┘      │
│                                                                          │
│                        UNSECURE GTNET                                    │
│                      (No central server)                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘


              ╔═══════════════════════════════════════════╗
              ║  SECURE AND UNSECURE NETWORKS DO NOT      ║
              ║  COMMUNICATE WITH EACH OTHER              ║
              ╚═══════════════════════════════════════════╝
```

---

## Network Comparison

| Aspect | Secure GTNet | Unsecure GTNet |
|--------|--------------|----------------|
| **UUID** | Issued by central server | None |
| **Registration** | Required with central server | Not required |
| **Verification** | Central server validates UUIDs | None |
| **UUID Change** | Not possible without central server | N/A |
| **Blacklist** | Centrally managed and distributed | None |
| **Price Attribution** | Full traceability via UUID | None |
| **Communication** | Only with other secure instances | Only with other unsecure instances |
| **Sybil Attack** | Not possible (UUID bound to domain) | Possible |
| **Price Verification** | Decentralized, anomalies reported | None |

---

## Central Server Architecture

### Central Server = Normal Grafioschtrader Instance

The central server is **not a dedicated server** but a normal Grafioschtrader instance with the central server role enabled. Users can track their portfolios on these instances while they also serve the secure GTNet.

**Initial Central Servers**:
- `https://grafioschtrader.info`
- `https://grafioschtrader.com`

### Central Server Cluster (High Availability)

Multiple central servers are synchronized using **MariaDB Galera Cluster**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     CENTRAL SERVER CLUSTER                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────────────┐              ┌─────────────────────┐          │
│   │ grafioschtrader.info│◄────────────►│ grafioschtrader.com │          │
│   │                     │    Galera    │                     │          │
│   │ • Central Server    │     Sync     │ • Central Server    │          │
│   │ • User Portfolios   │  (GTNet      │ • User Portfolios   │          │
│   │ • Price Data        │   tables)    │ • Price Data        │          │
│   └─────────────────────┘              └─────────────────────┘          │
│                                                                          │
│   Only GTNet security tables are synchronized.                          │
│   User portfolio data remains local to each instance.                   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Tables Synchronized via Galera

| Table | Purpose |
|-------|---------|
| `gtnet_registered_instance` | UUID ↔ domain mapping, trust scores |
| `gtnet_blacklist` | Blacklisted UUIDs |
| `gtnet_anomaly_report_lastprice` | Last price anomaly reports |
| `gtnet_anomaly_report_historical` | Historical price anomaly reports |
| `gtnet_threshold_config` | Thresholds per asset class |

User portfolio tables are **not synchronized** (local to each instance).

### Central Server Role

| Function | Description |
|----------|-------------|
| **UUID Issuance** | Issues unique UUID to each registered instance |
| **Domain Binding** | UUID is permanently bound to domain URL |
| **Instance Verification** | Validates UUID authenticity (does not reveal domain) |
| **Trust Score Management** | Calculates and stores trust scores |
| **Blacklist Management** | Maintains authoritative blacklist |
| **Anomaly Collection** | Receives anomaly reports from instances |
| **Threshold Configuration** | Provides thresholds per asset class |

### Client Instance Behavior

Instances connect to central servers with:
- **Random selection** from configured list
- **Failover** to next server on failure

```properties
# Instance configuration
gt.gtnet.secure.enabled=true
gt.gtnet.central.servers=https://grafioschtrader.info,https://grafioschtrader.com
```

### Optional Custom Central Server

Participants can optionally configure a different central server, enabling:
- Private secure GTNet deployments
- Enterprise internal networks
- Testing environments

```
┌─────────────────────────┐     ┌─────────────────────────┐
│  PUBLIC SECURE GTNET    │     │  PRIVATE SECURE GTNET   │
│  Central: gt.info/.com  │     │  Central: company.com   │
│                         │     │                         │
│  Instance A ◄─► B ◄─► C │     │  Instance X ◄─► Y       │
└─────────────────────────┘     └─────────────────────────┘
        │                               │
        └───── No communication ────────┘
```

---

## Registration Flow

### Domain Verification via Spring Boot Actuator

Every instance in secure GTNet must have a DNS-resolvable domain. Verification uses Spring Boot Actuator endpoints:

```
Instance                              Central Server
────────────────────────────────────────────────────────────────────
1. POST /api/gtnet/register
   { "domainUrl": "https://example.com" }
   ─────────────────────────────────────────────────────────────────►

2.                                    GET https://example.com/actuator/health
                                      (Verify domain is reachable and
                                       controlled by requester)
   ◄─────────────────────────────────────────────────────────────────

3. Return actuator response
   { "status": "UP" }
   ─────────────────────────────────────────────────────────────────►

4.                                    Issue UUID
   ◄─────────────────────────────────────────────────────────────────
   {
     "instanceUuid": "550e8400-e29b-41d4-a716-446655440000",
     "issuedAt": "2025-01-06T12:00:00Z",
     "authToken": "secret-token-for-api-calls..."
   }

5. Store UUID and authToken locally
```

### UUID Properties

- **Centrally issued**: Only central server can create UUIDs
- **Domain bound**: UUID permanently linked to domain URL
- **Immutable**: Cannot be changed or transferred
- **Privacy preserving**: Central server stores domain but never exposes it

---

## UUID Verification

Any secure instance can verify another instance's UUID:

```
GET /api/gtnet/verify?uuid={uuid}

Response:
{
    "valid": true,           // UUID was issued by this central server
    "blacklisted": false     // Not on blacklist
}

// Note: domainUrl is NOT returned (privacy)
```

This confirms the UUID is legitimate without revealing which domain it belongs to.

---

## Price Verification (Decentralized)

### Key Principle

Each instance in the secure network **verifies prices locally** using its own connectors. Only **anomalies are reported** to the central server.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DECENTRALIZED PRICE VERIFICATION                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Instance receives price from supplier (UUID: abc123)                   │
│                          │                                               │
│                          ▼                                               │
│   ┌─────────────────────────────────────────┐                           │
│   │  Random Selection (e.g., 5% of prices)  │                           │
│   └─────────────────────────────────────────┘                           │
│                          │                                               │
│                          ▼                                               │
│   ┌─────────────────────────────────────────┐                           │
│   │  Fetch reference price from connector   │                           │
│   │  (Yahoo, Finnhub, etc.)                 │                           │
│   └─────────────────────────────────────────┘                           │
│                          │                                               │
│                          ▼                                               │
│   ┌─────────────────────────────────────────┐                           │
│   │  Compare: deviation > threshold?        │                           │
│   │  (Threshold from daily query)           │                           │
│   └─────────────────────────────────────────┘                           │
│                          │                                               │
│              ┌───────────┴───────────┐                                  │
│              ▼                       ▼                                   │
│        Within tolerance         ANOMALY DETECTED                        │
│        (no action)                   │                                   │
│                                      ▼                                   │
│                          ┌─────────────────────┐                        │
│                          │  Report to central  │                        │
│                          │  server             │                        │
│                          └─────────────────────┘                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Advantages of Decentralized Verification

| Advantage | Description |
|-----------|-------------|
| **Scalability** | No bottleneck at central server |
| **No gaming** | Instances choose random prices to verify |
| **Distributed load** | Connector requests spread across network |
| **Real verification** | Each instance uses its own trusted connectors |

---

## Anomaly Reports

### Last Price Anomaly Report

```java
public class LastPriceAnomalyReport {

    // Reporter and accused
    private String reporterUuid;
    private String accusedUuid;

    // Instrument identification
    private String instrumentKey;      // ISIN:CURRENCY or FROM:TO
    private byte assetClass;           // AssetClassType for threshold lookup

    // Supplied price (from accused)
    private LocalDateTime suppliedTimestamp;
    private double suppliedLast;

    // Expected price (from connector)
    private LocalDateTime expectedTimestamp;
    private double expectedLast;
    private String connectorUsed;      // "YAHOO", "FINNHUB", etc.

    // Calculated
    private double deviationPercent;
}
```

### Historical Price Anomaly Report

```java
public class HistoricalPriceAnomalyReport {

    // Reporter and accused
    private String reporterUuid;
    private String accusedUuid;

    // Instrument identification
    private String instrumentKey;
    private byte assetClass;

    // Supplied price (from accused)
    private LocalDate priceDate;
    private double suppliedClose;

    // Expected price (from connector)
    private double expectedClose;
    private String connectorUsed;

    // Calculated
    private double deviationPercent;
}
```

### Anomaly Thresholds

Thresholds are configured **per AssetClassType** on the central server and distributed via daily query. Historical prices have **stricter (smaller) thresholds** than last prices.

| AssetClassType | Last Price Threshold | Historical Threshold |
|----------------|---------------------|----------------------|
| EQUITY | 3.0% | 1.5% |
| BOND | 2.5% | 1.0% |
| CURRENCY_PAIR | 1.5% | 0.5% |
| CRYPTO | 7.0% | 3.0% |
| ETF | 3.0% | 1.5% |
| FUND | 3.0% | 1.5% |
| CFD | 4.0% | 2.0% |
| COMMODITY | 3.0% | 1.5% |

*Note: These values are configurable on the central server.*

---

## Daily Query

Each instance queries the central server daily for:
- Trust scores of communication partners
- Current blacklist status
- Anomaly thresholds per asset class

### Request

```json
POST /api/gtnet/daily-query
Authorization: Bearer {authToken}

{
    "myUuid": "my-instance-uuid",
    "queryUuids": ["partner-1-uuid", "partner-2-uuid", "partner-3-uuid"]
}
```

### Response

```json
{
    "results": [
        {
            "uuid": "partner-1-uuid",
            "trustScore": 0.85,
            "blacklisted": false
        },
        {
            "uuid": "partner-2-uuid",
            "trustScore": 0.72,
            "blacklisted": false
        },
        {
            "uuid": "partner-3-uuid",
            "trustScore": 0.15,
            "blacklisted": true
        }
    ],
    "thresholds": {
        "lastPrice": {
            "EQUITY": 3.0,
            "BOND": 2.5,
            "CURRENCY_PAIR": 1.5,
            "CRYPTO": 7.0,
            "ETF": 3.0,
            "FUND": 3.0,
            "CFD": 4.0,
            "COMMODITY": 3.0
        },
        "historical": {
            "EQUITY": 1.5,
            "BOND": 1.0,
            "CURRENCY_PAIR": 0.5,
            "CRYPTO": 3.0,
            "ETF": 1.5,
            "FUND": 1.5,
            "CFD": 2.0,
            "COMMODITY": 1.5
        }
    }
}
```

### Social Proof Mechanism

The daily query serves a dual purpose:

1. **Get trust information** for partners
2. **Signal active partnerships** to central server

The central server tracks which UUIDs are queried by which instances. If an instance stops being queried by all its partners, it indicates loss of trust across the network.

---

## Trust Score Calculation

### Formula

```
TrustScore = PartnerScore - AnomalyPenalty + AgeBonus

Where:
┌─────────────────────────────────────────────────────────────────┐
│ PartnerScore = (activePartnerCount / maxPartnerCount) × 0.5    │
│   • More instances trust you = higher score                     │
│   • maxPartnerCount = highest in network (normalization)        │
├─────────────────────────────────────────────────────────────────┤
│ AnomalyPenalty = (anomalyReports / activePartnerCount) × 0.4   │
│   • Normalized by partner count (fair to large suppliers)       │
│   • Reports weighted by reporter's trust score                  │
├─────────────────────────────────────────────────────────────────┤
│ AgeBonus = min(0.1, daysActive / 365 × 0.1)                    │
│   • Longevity bonus (max 0.1 after 1 year)                      │
└─────────────────────────────────────────────────────────────────┘

Range: 0.0 to 1.0
New instances start at: 0.5 (neutral)
Blacklist threshold: < 0.2
```

### Implementation

```java
public double calculateTrustScore(GTNetRegisteredInstance instance) {
    // Partner score (0.0 to 0.5)
    int maxPartners = getMaxPartnerCountInNetwork();
    double partnerScore = maxPartners > 0
        ? (double) instance.getActivePartnerCount() / maxPartners * 0.5
        : 0.25;

    // Anomaly penalty (0.0 to 0.4)
    double anomalyPenalty = 0.0;
    if (instance.getActivePartnerCount() > 0) {
        double weightedAnomalies = calculateWeightedAnomalies(instance);
        anomalyPenalty = Math.min(0.4,
            weightedAnomalies / instance.getActivePartnerCount() * 0.4);
    }

    // Age bonus (0.0 to 0.1)
    long daysActive = ChronoUnit.DAYS.between(
        instance.getIssuedAt(), LocalDateTime.now());
    double ageBonus = Math.min(0.1, daysActive / 365.0 * 0.1);

    // Calculate final score
    double score = partnerScore - anomalyPenalty + ageBonus;
    return Math.max(0.0, Math.min(1.0, score));
}

private double calculateWeightedAnomalies(GTNetRegisteredInstance instance) {
    // Weight each anomaly report by the reporter's trust score
    return instance.getAnomalyReports().stream()
        .mapToDouble(report -> {
            double reporterTrust = getTrustScore(report.getReporterUuid());
            return reporterTrust; // Each report weighted by reporter's trust
        })
        .sum();
}
```

### Mitigating False Accusations

False accusations cannot be completely prevented, but their impact is reduced by:

1. **Weighting by reporter trust**: Low-trust reporters have less impact
2. **Normalization**: Large suppliers aren't unfairly penalized
3. **Multiple reporters needed**: Single reports have limited effect
4. **Reporter tracking**: Patterns of false reporting affect reporter's own trust

---

## Blacklist Management

### Automatic Blacklisting

An instance is automatically blacklisted when:
- Trust score falls below **0.2**, OR
- More than **10 verified anomaly reports** from different reporters

### Blacklist Distribution

The blacklist is distributed via the daily query response (`blacklisted: true`).

### Manual Blacklisting

Central server administrators can manually blacklist instances for:
- Domain verification failure
- Terms of service violations
- Administrative reasons

---

## Central Server Unavailability

### Reduced Security Mode

If the central server is unavailable:
- **Data exchange continues** using cached data
- **Cached trust scores** remain valid **indefinitely**
- **Cached blacklist** remains in effect
- **Anomaly reports** are queued locally, sent when available
- **New connections** use last known verification status

**Principle**: Data exchange is more important than security. The network continues operating with the last known security state.

### Failover Behavior

```
Instance attempts daily query:
  1. Try random central server from list
  2. If failed, try next server
  3. If all failed, use cached data
  4. Retry next day
```

---

## Data Model

### Central Server Entities

#### Registered Instance

```java
@Entity
@Table(name = "gtnet_registered_instance")
public class GTNetRegisteredInstance {

    @Id
    @Column(name = "instance_uuid", length = 36)
    private String instanceUuid;

    @Column(name = "domain_url", length = 255, unique = true, nullable = false)
    private String domainUrl;  // Stored but never exposed via API

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    // Trust metrics
    @Column(name = "active_partner_count")
    private Integer activePartnerCount = 0;

    @Column(name = "anomaly_report_count")
    private Integer anomalyReportCount = 0;

    @Column(name = "trust_score")
    private Double trustScore = 0.5;

    // Blacklist
    @Column(name = "blacklisted")
    private Boolean blacklisted = false;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(name = "blacklist_reason")
    private String blacklistReason;
}
```

#### Last Price Anomaly Report

```java
@Entity
@Table(name = "gtnet_anomaly_report_lastprice")
public class GTNetAnomalyReportLastprice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_anomaly_report")
    private Long idAnomalyReport;

    @Column(name = "reporter_uuid", length = 36, nullable = false)
    private String reporterUuid;

    @Column(name = "accused_uuid", length = 36, nullable = false)
    private String accusedUuid;

    @Column(name = "instrument_key", length = 50, nullable = false)
    private String instrumentKey;

    @Column(name = "asset_class", nullable = false)
    private Byte assetClass;

    @Column(name = "supplied_timestamp", nullable = false)
    private LocalDateTime suppliedTimestamp;

    @Column(name = "supplied_last", nullable = false)
    private Double suppliedLast;

    @Column(name = "expected_timestamp", nullable = false)
    private LocalDateTime expectedTimestamp;

    @Column(name = "expected_last", nullable = false)
    private Double expectedLast;

    @Column(name = "connector_used", length = 20)
    private String connectorUsed;

    @Column(name = "deviation_percent", nullable = false)
    private Double deviationPercent;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;
}
```

#### Historical Price Anomaly Report

```java
@Entity
@Table(name = "gtnet_anomaly_report_historical")
public class GTNetAnomalyReportHistorical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_anomaly_report")
    private Long idAnomalyReport;

    @Column(name = "reporter_uuid", length = 36, nullable = false)
    private String reporterUuid;

    @Column(name = "accused_uuid", length = 36, nullable = false)
    private String accusedUuid;

    @Column(name = "instrument_key", length = 50, nullable = false)
    private String instrumentKey;

    @Column(name = "asset_class", nullable = false)
    private Byte assetClass;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "supplied_close", nullable = false)
    private Double suppliedClose;

    @Column(name = "expected_close", nullable = false)
    private Double expectedClose;

    @Column(name = "connector_used", length = 20)
    private String connectorUsed;

    @Column(name = "deviation_percent", nullable = false)
    private Double deviationPercent;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;
}
```

#### Threshold Configuration

```java
@Entity
@Table(name = "gtnet_threshold_config")
public class GTNetThresholdConfig {

    @Id
    @Column(name = "asset_class")
    private Byte assetClass;

    @Column(name = "last_price_threshold", nullable = false)
    private Double lastPriceThreshold;

    @Column(name = "historical_threshold", nullable = false)
    private Double historicalThreshold;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Instance-Side Entities

#### Security Configuration

```java
@Entity
@Table(name = "gt_net_security_config")
public class GTNetSecurityConfig {

    @Id
    @Column(name = "id_gt_net_security_config")
    private Integer idGtNetSecurityConfig;

    // Network mode
    @Column(name = "secure_network", nullable = false)
    private Boolean secureNetwork = false;

    // Our UUID (received from central server)
    @Column(name = "instance_uuid", length = 36)
    private String instanceUuid;

    @Column(name = "uuid_issued_at")
    private LocalDateTime uuidIssuedAt;

    // Central server connection
    @Column(name = "central_server_urls", length = 500)
    private String centralServerUrls;  // Comma-separated

    @Column(name = "central_server_token", length = 255)
    private String centralServerToken;

    // Cache metadata
    @Column(name = "last_daily_query")
    private LocalDateTime lastDailyQuery;
}
```

#### Trust Score Cache

```java
@Entity
@Table(name = "gt_net_trust_cache")
public class GTNetTrustCache {

    @Id
    @Column(name = "partner_uuid", length = 36)
    private String partnerUuid;

    @Column(name = "trust_score")
    private Double trustScore;

    @Column(name = "blacklisted")
    private Boolean blacklisted;

    @Column(name = "cached_at")
    private LocalDateTime cachedAt;
}
```

#### Threshold Cache

```java
@Entity
@Table(name = "gt_net_threshold_cache")
public class GTNetThresholdCache {

    @Id
    @Column(name = "asset_class")
    private Byte assetClass;

    @Column(name = "last_price_threshold")
    private Double lastPriceThreshold;

    @Column(name = "historical_threshold")
    private Double historicalThreshold;

    @Column(name = "cached_at")
    private LocalDateTime cachedAt;
}
```

#### Price Attribution

```java
// In Historyquote.java - add attribution fields
@Column(name = "source_instance_uuid", length = 36)
private String sourceInstanceUuid;

@Column(name = "received_from_id_gt_net")
private Integer receivedFromIdGtNet;

// In GTNetHistoryquote.java and GTNetLastprice.java
@Column(name = "source_instance_uuid", length = 36)
private String sourceInstanceUuid;
```

---

## Database Migration

### Central Server Tables

```sql
-- Registered instances
CREATE TABLE gtnet_registered_instance (
    instance_uuid VARCHAR(36) PRIMARY KEY,
    domain_url VARCHAR(255) NOT NULL UNIQUE,
    issued_at DATETIME NOT NULL,
    last_verified_at DATETIME,
    active_partner_count INT DEFAULT 0,
    anomaly_report_count INT DEFAULT 0,
    trust_score DOUBLE DEFAULT 0.5,
    blacklisted BOOLEAN DEFAULT FALSE,
    blacklisted_at DATETIME,
    blacklist_reason VARCHAR(255),
    INDEX idx_blacklisted (blacklisted),
    INDEX idx_trust_score (trust_score)
) ENGINE=InnoDB;

-- Last price anomaly reports
CREATE TABLE gtnet_anomaly_report_lastprice (
    id_anomaly_report BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid VARCHAR(36) NOT NULL,
    accused_uuid VARCHAR(36) NOT NULL,
    instrument_key VARCHAR(50) NOT NULL,
    asset_class TINYINT NOT NULL,
    supplied_timestamp DATETIME NOT NULL,
    supplied_last DOUBLE NOT NULL,
    expected_timestamp DATETIME NOT NULL,
    expected_last DOUBLE NOT NULL,
    connector_used VARCHAR(20),
    deviation_percent DOUBLE NOT NULL,
    reported_at DATETIME NOT NULL,
    INDEX idx_accused (accused_uuid),
    INDEX idx_reporter (reporter_uuid),
    INDEX idx_reported_at (reported_at)
) ENGINE=InnoDB;

-- Historical price anomaly reports
CREATE TABLE gtnet_anomaly_report_historical (
    id_anomaly_report BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid VARCHAR(36) NOT NULL,
    accused_uuid VARCHAR(36) NOT NULL,
    instrument_key VARCHAR(50) NOT NULL,
    asset_class TINYINT NOT NULL,
    price_date DATE NOT NULL,
    supplied_close DOUBLE NOT NULL,
    expected_close DOUBLE NOT NULL,
    connector_used VARCHAR(20),
    deviation_percent DOUBLE NOT NULL,
    reported_at DATETIME NOT NULL,
    INDEX idx_accused (accused_uuid),
    INDEX idx_reporter (reporter_uuid),
    INDEX idx_reported_at (reported_at)
) ENGINE=InnoDB;

-- Threshold configuration
CREATE TABLE gtnet_threshold_config (
    asset_class TINYINT PRIMARY KEY,
    last_price_threshold DOUBLE NOT NULL,
    historical_threshold DOUBLE NOT NULL,
    updated_at DATETIME
) ENGINE=InnoDB;

-- Insert default thresholds
INSERT INTO gtnet_threshold_config (asset_class, last_price_threshold, historical_threshold) VALUES
(0, 3.0, 1.5),   -- EQUITY
(1, 2.5, 1.0),   -- BOND
(2, 1.5, 0.5),   -- CURRENCY_PAIR
(3, 7.0, 3.0),   -- CRYPTO
(4, 3.0, 1.5),   -- ETF
(5, 3.0, 1.5),   -- FUND
(6, 4.0, 2.0),   -- CFD
(7, 3.0, 1.5);   -- COMMODITY
```

### Instance-Side Tables

```sql
-- Security configuration
CREATE TABLE gt_net_security_config (
    id_gt_net_security_config INT PRIMARY KEY,
    secure_network BOOLEAN NOT NULL DEFAULT FALSE,
    instance_uuid VARCHAR(36),
    uuid_issued_at DATETIME,
    central_server_urls VARCHAR(500),
    central_server_token VARCHAR(255),
    last_daily_query DATETIME
) ENGINE=InnoDB;

-- Trust score cache
CREATE TABLE gt_net_trust_cache (
    partner_uuid VARCHAR(36) PRIMARY KEY,
    trust_score DOUBLE,
    blacklisted BOOLEAN,
    cached_at DATETIME
) ENGINE=InnoDB;

-- Threshold cache
CREATE TABLE gt_net_threshold_cache (
    asset_class TINYINT PRIMARY KEY,
    last_price_threshold DOUBLE,
    historical_threshold DOUBLE,
    cached_at DATETIME
) ENGINE=InnoDB;

-- Add attribution to price tables
ALTER TABLE historyquote
    ADD COLUMN source_instance_uuid VARCHAR(36),
    ADD COLUMN received_from_id_gt_net INT;

ALTER TABLE gt_net_historyquote
    ADD COLUMN source_instance_uuid VARCHAR(36);

ALTER TABLE gt_net_lastprice
    ADD COLUMN source_instance_uuid VARCHAR(36);

-- Indexes
CREATE INDEX idx_historyquote_source_uuid ON historyquote(source_instance_uuid);
CREATE INDEX idx_gtnet_historyquote_source_uuid ON gt_net_historyquote(source_instance_uuid);
CREATE INDEX idx_gtnet_lastprice_source_uuid ON gt_net_lastprice(source_instance_uuid);
```

---

## Central Server API

### Registration

```
POST /api/gtnet/register
Content-Type: application/json

Request:
{
    "domainUrl": "https://example.com:8080"
}

Response (after actuator verification):
{
    "instanceUuid": "550e8400-e29b-41d4-a716-446655440000",
    "issuedAt": "2025-01-06T12:00:00Z",
    "authToken": "secret-token-for-api-calls"
}
```

### Verify UUID

```
GET /api/gtnet/verify?uuid={uuid}

Response:
{
    "valid": true,
    "blacklisted": false
}
```

### Daily Query

```
POST /api/gtnet/daily-query
Authorization: Bearer {authToken}
Content-Type: application/json

Request:
{
    "myUuid": "my-instance-uuid",
    "queryUuids": ["partner-1", "partner-2", "partner-3"]
}

Response:
{
    "results": [
        { "uuid": "partner-1", "trustScore": 0.85, "blacklisted": false },
        { "uuid": "partner-2", "trustScore": 0.72, "blacklisted": false },
        { "uuid": "partner-3", "trustScore": 0.15, "blacklisted": true }
    ],
    "thresholds": {
        "lastPrice": {
            "EQUITY": 3.0,
            "BOND": 2.5,
            "CURRENCY_PAIR": 1.5,
            "CRYPTO": 7.0,
            "ETF": 3.0,
            "FUND": 3.0,
            "CFD": 4.0,
            "COMMODITY": 3.0
        },
        "historical": {
            "EQUITY": 1.5,
            "BOND": 1.0,
            "CURRENCY_PAIR": 0.5,
            "CRYPTO": 3.0,
            "ETF": 1.5,
            "FUND": 1.5,
            "CFD": 2.0,
            "COMMODITY": 1.5
        }
    }
}
```

### Report Anomaly (Last Price)

```
POST /api/gtnet/report/lastprice
Authorization: Bearer {authToken}
Content-Type: application/json

Request:
{
    "reporterUuid": "my-uuid",
    "accusedUuid": "supplier-uuid",
    "instrumentKey": "US0378331005:USD",
    "assetClass": 0,
    "suppliedTimestamp": "2025-01-06T15:30:00Z",
    "suppliedLast": 182.50,
    "expectedTimestamp": "2025-01-06T15:30:00Z",
    "expectedLast": 192.30,
    "connectorUsed": "YAHOO",
    "deviationPercent": 5.1
}

Response:
{
    "accepted": true,
    "reportId": 12345
}
```

### Report Anomaly (Historical)

```
POST /api/gtnet/report/historical
Authorization: Bearer {authToken}
Content-Type: application/json

Request:
{
    "reporterUuid": "my-uuid",
    "accusedUuid": "supplier-uuid",
    "instrumentKey": "US0378331005:USD",
    "assetClass": 0,
    "priceDate": "2025-01-05",
    "suppliedClose": 180.00,
    "expectedClose": 190.50,
    "connectorUsed": "YAHOO",
    "deviationPercent": 5.5
}

Response:
{
    "accepted": true,
    "reportId": 12346
}
```

---

## Sybil Attack Prevention

### Why Sybil Attacks Are Impossible

1. **Centrally Issued UUIDs**: Instances cannot generate their own UUIDs
2. **Domain Binding**: UUID is permanently bound to domain URL
3. **Domain Verification**: Central server verifies domain via actuator
4. **No UUID Transfer**: UUID cannot be moved to a different domain

### Attack Scenario Analysis

| Attack | Defense |
|--------|---------|
| Generate new UUID | Not possible - only central server issues UUIDs |
| Register new domain | Requires real DNS domain, verifiable |
| Claim another's UUID | Fails actuator verification |
| Modify UUID locally | Other instances verify against central server |

---

## Network Separation Enforcement

### Handshake Extension

During GTNet handshake, instances exchange network mode:

```java
public class GTNetHandshakeExtension {
    private Boolean secureNetwork;
    private String instanceUuid;  // Only present if secureNetwork=true
}
```

### Connection Validation

```java
public boolean canConnect(GTNet remoteConfig, GTNetHandshakeExtension extension) {
    GTNetSecurityConfig localSecurity = getSecurityConfig();

    if (localSecurity.isSecureNetwork()) {
        // Secure instance: only connect to verified secure instances
        if (!extension.getSecureNetwork()) {
            return false;  // Reject unsecure instance
        }
        // Verify UUID with central server (use cache if available)
        return isValidSecureInstance(extension.getInstanceUuid());
    } else {
        // Unsecure instance: only connect to unsecure instances
        return !extension.getSecureNetwork();
    }
}
```

---

## Configuration

### Instance Configuration

```properties
# Enable secure GTNet
gt.gtnet.secure.enabled=true

# Central servers (comma-separated, random selection + failover)
gt.gtnet.central.servers=https://grafioschtrader.info,https://grafioschtrader.com

# Verification sampling rate (percentage of prices to verify)
gt.gtnet.verification.sampling-rate=5
```

### Central Server Configuration

```properties
# Enable central server role
gt.gtnet.central.role.enabled=true

# Galera cluster nodes (for synchronization)
gt.gtnet.central.galera.nodes=grafioschtrader.info,grafioschtrader.com
```

---

## Implementation Phases

### Phase 1: Network Mode Configuration
- Add `GTNetSecurityConfig` entity
- Add network mode to handshake
- Enforce network separation
- Reject connections between secure and unsecure

### Phase 2: Central Server - Registration
- Implement registration API
- Implement actuator verification
- Implement UUID issuance
- Implement verify API

### Phase 3: Price Attribution
- Add `sourceInstanceUuid` to price entities
- Propagate UUID through price exchange
- Store attribution on received prices

### Phase 4: Daily Query & Trust
- Implement daily query API
- Implement trust score calculation
- Implement threshold distribution
- Implement caching on instance side

### Phase 5: Anomaly Reporting
- Implement decentralized verification
- Implement anomaly report APIs
- Integrate reports into trust calculation
- Implement automatic blacklisting

### Phase 6: High Availability
- Configure Galera synchronization
- Test failover behavior
- Document reduced security mode

---

## Conclusion

The GTNet Security concept provides:

| Feature | Benefit |
|---------|---------|
| **Two separate networks** | Clear security boundaries |
| **Centrally-issued UUIDs** | Sybil attack prevention |
| **Domain binding** | Accountability without privacy loss |
| **Decentralized verification** | Scalable, no gaming possible |
| **Trust scores** | Reputation-based quality assurance |
| **Automatic blacklisting** | Bad actors eliminated |
| **High availability** | Galera cluster for central servers |
| **Graceful degradation** | Network continues if central unavailable |

Participants choose their network based on security requirements:
- **Secure GTNet**: Full accountability and verification
- **Unsecure GTNet**: No overhead, no guarantees
