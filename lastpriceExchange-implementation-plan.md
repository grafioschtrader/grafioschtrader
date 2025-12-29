# Implementation Plan: GTNet Intraday Price Data Exchange

## Overview

This document outlines the technical implementation for exchanging intraday price data between Grafioschtrader instances via GTNet. Two server types are supported:

1. **Push-Open Server** - High-performance bidirectional exchange with separate price storage
2. **Open Server** - Basic exchange updating instruments directly

## 1. Entity Changes

### 1.1 New Entity: `GTNetLastprice`

Stores intraday price data independently of local instruments (for push-open servers).

```java
@Entity
@Table(name = "gt_net_lastprice")
public class GTNetLastprice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gt_net_lastprice")
    private Integer idGTNetLastprice;

    // Security identification (nullable for currency pairs)
    @Column(name = "isin", length = 12)
    private String isin;

    @Column(name = "currency", length = 3)
    private String currency;

    // Currency pair identification (nullable for securities)
    @Column(name = "from_currency", length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", length = 3)
    private String toCurrency;

    // Price data
    @Column(name = "last_price", precision = 16, scale = 8)
    private Double lastPrice;

    @Column(name = "price_timestamp")
    private LocalDateTime priceTimestamp;

    @Column(name = "source_domain_remote_key")
    private String sourceDomainRemoteKey;  // Origin server identifier

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### 1.2 Modify Entity: `GTNet`

Add fields for exchange configuration:

```java
// Existing entity additions
@Column(name = "intraday_exchange_mode")
@Enumerated(EnumType.STRING)
private IntradayExchangeMode intradayExchangeMode;  // NONE, OPEN, PUSH_OPEN

@Column(name = "intraday_priority")
private Integer intradayPriority;  // Lower = higher priority

@Column(name = "last_intraday_exchange")
private LocalDateTime lastIntradayExchange;

@Column(name = "intraday_exchange_count")
private Integer intradayExchangeCount;  // Successful exchanges today
```

### 1.3 New Enum: `IntradayExchangeMode`

```java
public enum IntradayExchangeMode {
    NONE,       // No intraday exchange
    OPEN,       // Basic open server exchange
    PUSH_OPEN   // Push-open server with separate storage
}
```

## 2. DTO Classes for Network Communication

### 2.1 `IntradayPriceRequestDTO`

Request payload sent to remote servers:

```java
public class IntradayPriceRequestDTO {
    private List<InstrumentPriceDTO> securities;
    private List<CurrencyPairPriceDTO> currencyPairs;
    private LocalDateTime requestTimestamp;
    private String requestingDomainRemoteKey;
    private IntradayExchangeMode senderMode;
}
```

### 2.2 `InstrumentPriceDTO`

Individual security price data:

```java
public class InstrumentPriceDTO {
    private String isin;
    private String currency;
    private Double lastPrice;          // Can be null in request
    private LocalDateTime timestamp;   // Can be null in request
}
```

### 2.3 `CurrencyPairPriceDTO`

Currency pair price data:

```java
public class CurrencyPairPriceDTO {
    private String fromCurrency;
    private String toCurrency;
    private Double lastPrice;
    private LocalDateTime timestamp;
}
```

### 2.4 `IntradayPriceResponseDTO`

Response from remote server:

```java
public class IntradayPriceResponseDTO {
    private List<InstrumentPriceDTO> updatedSecurities;
    private List<CurrencyPairPriceDTO> updatedCurrencyPairs;
    private int receivedUpdatesCount;  // How many prices the server accepted
    private LocalDateTime responseTimestamp;
}
```

## 3. Repository Layer

### 3.1 `GTNetLastpriceJpaRepository`

```java
public interface GTNetLastpriceJpaRepository extends JpaRepository<GTNetLastprice, Integer> {

    Optional<GTNetLastprice> findByIsinAndCurrency(String isin, String currency);

    Optional<GTNetLastprice> findByFromCurrencyAndToCurrency(String from, String to);

    List<GTNetLastprice> findByPriceTimestampAfter(LocalDateTime since);

    @Query("SELECT g FROM GTNetLastprice g WHERE g.isin IN :isins")
    List<GTNetLastprice> findByIsinIn(@Param("isins") List<String> isins);

    @Modifying
    @Query("DELETE FROM GTNetLastprice g WHERE g.priceTimestamp < :cutoff")
    int deleteOldPrices(@Param("cutoff") LocalDateTime cutoff);
}
```

### 3.2 Named Queries for `GTNet`

Add to `jpa-named-queries.properties`:

```properties
GTNet.findByIntradayExchangeModeOrderByPriority=SELECT g FROM GTNet g \
  WHERE g.intradayExchangeMode = :mode \
  AND g.gtNetServerStateType = 'SS_ACCEPTED' \
  ORDER BY g.intradayPriority ASC

GTNet.findPushOpenServers=SELECT g FROM GTNet g \
  WHERE g.intradayExchangeMode = 'PUSH_OPEN' \
  AND g.gtNetServerStateType = 'SS_ACCEPTED' \
  ORDER BY g.intradayPriority ASC
```

## 4. Service Layer

### 4.1 `GTNetIntradayExchangeService`

Main orchestration service:

```java
@Service
public class GTNetIntradayExchangeService {

    /**
     * Main entry point for intraday price exchange.
     * Called by scheduler or manually triggered.
     */
    public void executeIntradayExchange(Integer idWatchlist);

    /**
     * Step 1: Build intersection of watchlist instruments with exchange-enabled instruments.
     */
    List<InstrumentIdentifier> buildExchangeIntersection(Integer idWatchlist);

    /**
     * Step 2: Query own push-open server storage first (if configured as push-open).
     */
    Map<InstrumentIdentifier, IntradayPrice> queryOwnPushOpenStorage(
        List<InstrumentIdentifier> instruments);

    /**
     * Step 3: Query remote push-open servers by priority.
     */
    IntradayExchangeResult queryRemotePushOpenServers(
        List<InstrumentIdentifier> instruments,
        Map<InstrumentIdentifier, IntradayPrice> currentPrices);

    /**
     * Step 4: Query remote open servers for remaining instruments.
     */
    IntradayExchangeResult queryRemoteOpenServers(
        List<InstrumentIdentifier> remaining,
        Map<InstrumentIdentifier, IntradayPrice> currentPrices);

    /**
     * Step 5: Fall back to connectors for instruments without updates.
     */
    Map<InstrumentIdentifier, IntradayPrice> fetchFromConnectors(
        List<InstrumentIdentifier> remaining);

    /**
     * Step 6: Send updated prices back to remote servers.
     */
    void sendPriceUpdatesToRemoteServers(
        List<GTNet> queriedServers,
        Map<InstrumentIdentifier, IntradayPrice> newPrices);

    /**
     * Step 7: Update own push-open storage (if applicable).
     */
    void updateOwnPushOpenStorage(Map<InstrumentIdentifier, IntradayPrice> prices);
}
```

### 4.2 `GTNetIntradayClientService`

HTTP client for remote server communication:

```java
@Service
public class GTNetIntradayClientService {

    /**
     * Send price request to remote server and receive updates.
     */
    IntradayPriceResponseDTO requestPrices(
        GTNet remoteServer,
        IntradayPriceRequestDTO request);

    /**
     * Send price updates to remote server (post-exchange).
     */
    int sendPriceUpdates(
        GTNet remoteServer,
        IntradayPriceRequestDTO updates);
}
```

### 4.3 `GTNetIntradayServerService`

Server-side handler for incoming requests:

```java
@Service
public class GTNetIntradayServerService {

    /**
     * Handle incoming price request from remote instance.
     * Returns prices that are more current than those in the request.
     */
    IntradayPriceResponseDTO handlePriceRequest(
        IntradayPriceRequestDTO request,
        GTNet requestingInstance);

    /**
     * Handle incoming price updates from remote instance.
     * Updates push-open storage and returns count of accepted updates.
     */
    int handlePriceUpdates(
        IntradayPriceRequestDTO updates,
        GTNet sendingInstance);
}
```

## 5. REST Controller

### 5.1 `GTNetIntradayResource`

```java
@RestController
@RequestMapping("/api/gtnet/intraday")
public class GTNetIntradayResource {

    /**
     * Endpoint for remote servers to request/exchange prices.
     * POST /api/gtnet/intraday/exchange
     */
    @PostMapping("/exchange")
    public ResponseEntity<IntradayPriceResponseDTO> exchangePrices(
        @RequestBody IntradayPriceRequestDTO request,
        @RequestHeader("X-GTNet-Domain-Key") String domainKey);

    /**
     * Endpoint for remote servers to send price updates.
     * POST /api/gtnet/intraday/update
     */
    @PostMapping("/update")
    public ResponseEntity<Integer> receivePriceUpdates(
        @RequestBody IntradayPriceRequestDTO updates,
        @RequestHeader("X-GTNet-Domain-Key") String domainKey);

    /**
     * Manual trigger for exchange (admin only).
     * POST /api/gtnet/intraday/trigger/{idWatchlist}
     */
    @PostMapping("/trigger/{idWatchlist}")
    @Secured(ROLE_ADMIN)
    public ResponseEntity<Void> triggerExchange(
        @PathVariable Integer idWatchlist);
}
```

## 6. Database Migration

File: `V0_33_13__GTNet_Intraday_Exchange.sql`

```sql
-- New table for push-open server price storage
CREATE TABLE gt_net_lastprice (
    id_gt_net_lastprice INT AUTO_INCREMENT PRIMARY KEY,
    isin VARCHAR(12),
    currency VARCHAR(3),
    from_currency VARCHAR(3),
    to_currency VARCHAR(3),
    last_price DECIMAL(16,8),
    price_timestamp DATETIME(3),
    source_domain_remote_key VARCHAR(40),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_security (isin, currency),
    UNIQUE KEY uk_currency_pair (from_currency, to_currency),
    INDEX idx_timestamp (price_timestamp),

    CONSTRAINT chk_instrument_type CHECK (
        (isin IS NOT NULL AND currency IS NOT NULL AND from_currency IS NULL AND to_currency IS NULL) OR
        (isin IS NULL AND currency IS NULL AND from_currency IS NOT NULL AND to_currency IS NOT NULL)
    )
);

-- Add columns to gt_net table
ALTER TABLE gt_net
    ADD COLUMN intraday_exchange_mode VARCHAR(20) DEFAULT 'NONE',
    ADD COLUMN intraday_priority INT DEFAULT 100,
    ADD COLUMN last_intraday_exchange DATETIME,
    ADD COLUMN intraday_exchange_count INT DEFAULT 0;

-- Index for priority-based queries
CREATE INDEX idx_gtnet_intraday ON gt_net (intraday_exchange_mode, intraday_priority);
```

## 7. Configuration Properties

Add to `application.properties`:

```properties
# GTNet Intraday Exchange Configuration
gt.gtnet.intraday.enabled=true
gt.gtnet.intraday.own-mode=NONE
gt.gtnet.intraday.max-servers-per-exchange=2
gt.gtnet.intraday.price-stale-minutes=30
gt.gtnet.intraday.cleanup-days=1

# Scheduled task for intraday exchange
gt.gtnet.intraday.cron=0 */15 8-22 * * MON-FRI
```

## 8. Flow Diagrams

### 8.1 Push-Open to Push-Open Flow

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Local Instance │     │ Push-Open Srv 1 │     │ Push-Open Srv 2 │
│  (Push-Open)    │     │                 │     │                 │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │ 1. Query own storage  │                       │
         ├──────────────────────►│                       │
         │◄──────────────────────┤                       │
         │                       │                       │
         │ 2. Request prices     │                       │
         │   (with timestamps)   │                       │
         ├───────────────────────────────────────────────►
         │◄───────────────────────────────────────────────┤
         │                       │                       │
         │ 3. Request prices     │                       │
         │   (updated timestamps)│                       │
         ├──────────────────────►│                       │
         │◄──────────────────────┤                       │
         │                       │                       │
         │ 4. Fetch from connectors (remaining)          │
         │                       │                       │
         │ 5. Send updates to Srv 1 (connector prices)   │
         ├──────────────────────►│                       │
         │◄──count───────────────┤                       │
         │                       │                       │
         │ 6. Send updates to Srv 2 (all new prices)     │
         ├───────────────────────────────────────────────►
         │◄──count───────────────────────────────────────┤
         │                       │                       │
         │ 7. Update own storage │                       │
         │                       │                       │
```

### 8.2 Open Server Flow

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Local Instance │     │  Open Server 1  │     │  Open Server 2  │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │ 1. Request prices     │                       │
         │   (with/without ts)   │                       │
         ├──────────────────────►│                       │
         │◄──prices──────────────┤                       │
         │                       │                       │
         │ 2. Update local instruments                   │
         │                       │                       │
         │ 3. Request prices     │                       │
         ├───────────────────────────────────────────────►
         │◄──prices──────────────────────────────────────┤
         │                       │                       │
         │ 4. Update local instruments                   │
         │                       │                       │
         │ 5. Fetch from connectors (remaining)          │
         │                       │                       │
```

## 9. Key Implementation Details

### 9.1 Instrument Identification

- **Securities**: Unique by `ISIN + Currency` combination
- **Currency Pairs**: Unique by `FromCurrency + ToCurrency` combination

### 9.2 Price Freshness Logic

```java
boolean isMoreRecent(LocalDateTime incoming, LocalDateTime existing) {
    if (existing == null) return incoming != null;
    if (incoming == null) return false;
    return incoming.isAfter(existing);
}
```

### 9.3 Exchange Configuration Validation

"Exchange Price Data Securities" setting controls which instruments participate:
- Check `SecurityJpaRepository` for instruments with `exchangeIntraday = true`
- Check `CurrencypairJpaRepository` for currency pairs with `exchangeIntraday = true`

### 9.4 Priority-Based Server Selection

When multiple servers have the same priority, select randomly to distribute load:

```java
List<GTNet> serversAtSamePriority = servers.stream()
    .filter(s -> s.getIntradayPriority().equals(targetPriority))
    .collect(Collectors.toList());
Collections.shuffle(serversAtSamePriority);
return serversAtSamePriority.get(0);
```

## 10. Frontend Changes

### 10.1 GTNet Configuration UI

Add fields to GTNet edit form:
- Intraday Exchange Mode dropdown (None/Open/Push-Open)
- Intraday Priority input (integer)

### 10.2 Monitoring Dashboard

Display:
- Last exchange timestamp
- Exchange success count
- Number of prices in push-open storage

## 11. Testing Strategy

### 11.1 Unit Tests

- `GTNetIntradayExchangeServiceTest` - Test flow logic with mocked dependencies
- `GTNetLastpriceJpaRepositoryTest` - Test repository queries

### 11.2 Integration Tests

- `GTNetIntradayResourceTest` - Test REST endpoints with RestAssured
- End-to-end exchange simulation with two test instances

## 12. Open Questions

1. **Cleanup Policy**: How long should push-open storage retain prices? (Default: 1 day)
2. **Rate Limiting**: Should we limit exchange frequency per remote server?
3. **Error Handling**: Retry strategy for failed remote server communication?
4. **Watchlist Selection**: Which watchlist(s) determine the instrument intersection?
5. **Authentication**: Use existing GTNet message authentication or separate mechanism?
