package grafioschtrader.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafioschtrader.repository.WatchlistJpaRepository;

/**
 * Reserves a watchlist price update in a short transaction, releasing its row lock before external providers or
 * asynchronous report queries are awaited. The reservation survives a later report failure, so retries respect the
 * configured update interval.
 *
 * <p>
 * READ COMMITTED applies only to this reservation. With MariaDB 11.8 snapshot isolation, a REPEATABLE READ ownership
 * query followed by a competing update can otherwise fail with "Record has changed since last read". The conditional
 * UPDATE still serializes competing reservations through its row lock.
 */
@Service
public class WatchlistPriceUpdateService {

  private final WatchlistJpaRepository watchlistJpaRepository;

  public WatchlistPriceUpdateService(WatchlistJpaRepository watchlistJpaRepository) {
    this.watchlistJpaRepository = watchlistJpaRepository;
  }

  /**
   * Checks ownership and atomically reserves an update if the previous attempt is stale. Called through the Spring
   * proxy so that the reservation commits independently of the report transaction.
   *
   * @param idWatchlist the watchlist to update
   * @param idTenant    the requesting tenant
   * @param now         the timestamp of this attempt
   * @param threshold   only timestamps strictly before this value (or null) permit a new attempt
   * @return the update decision and the stored timestamp, independent of the caller's database snapshot
   * @throws SecurityException if the watchlist does not belong to the tenant or no longer exists
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public PriceUpdateClaim claimUpdate(Integer idWatchlist, Integer idTenant, LocalDateTime now,
      LocalDateTime threshold) {
    if (watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, idTenant) == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    boolean needsPriceUpdate = watchlistJpaRepository.updateLastTimestampIfStale(now, idWatchlist, threshold) > 0;
    return new PriceUpdateClaim(needsPriceUpdate, watchlistJpaRepository.getCurrentLastTimestamp(idWatchlist));
  }

  /** The timestamp describes an update attempt, not a guarantee that every provider delivered a price. */
  public record PriceUpdateClaim(boolean needsPriceUpdate, LocalDateTime lastTimestamp) {
  }
}
