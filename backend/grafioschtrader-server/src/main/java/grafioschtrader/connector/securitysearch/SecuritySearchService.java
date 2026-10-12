package grafioschtrader.connector.securitysearch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import grafioschtrader.types.SecuritySearchStatus;

/**
 * Asks several {@link ISecuritySearchProvider}s for the same instrument at once and returns one result per provider.
 * The providers run in parallel on virtual threads under a common deadline. A provider that fails, throws or misses
 * the deadline contributes its status - {@link SecuritySearchStatus#TIMEOUT} for the deadline - and never discards the
 * results of the others. Merging candidates across providers is left to the caller.
 */
@Service
public class SecuritySearchService {

  /** Deadline of a whole search across all providers. */
  public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(25);

  private final List<ISecuritySearchProvider> providers;


  public SecuritySearchService(List<ISecuritySearchProvider> providers) {
    this.providers = List.copyOf(providers);
  }

  /**
   * Ids of all registered providers.
   *
   * @return the provider ids
   */
  public List<String> getProviderIds() {
    return providers.stream().map(ISecuritySearchProvider::getProviderId).toList();
  }

  /**
   * Searches all registered providers with the default deadline.
   *
   * @param query the search query
   * @return one result per provider, in registration order
   */
  public List<SecuritySearchResult> search(SecuritySearchQuery query) {
    return search(query, null, DEFAULT_DEADLINE);
  }

  /**
   * Searches the selected providers. A provider that does not support the query reports
   * {@link SecuritySearchStatus#UNSUPPORTED_QUERY} without being asked.
   *
   * @param query       the search query
   * @param providerIds ids of the providers to ask; null asks all
   * @param deadline    time after which unfinished providers are reported as {@link SecuritySearchStatus#TIMEOUT}
   * @return one result per selected provider, in registration order
   */
  public List<SecuritySearchResult> search(SecuritySearchQuery query, Set<String> providerIds, Duration deadline) {
    Map<ISecuritySearchProvider, Future<SecuritySearchResult>> futures = new LinkedHashMap<>();
    long deadlineNanos = System.nanoTime() + deadline.toNanos();
    // Not closed by try-with-resources: close() would wait for a provider that ignores the interrupt.
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      for (ISecuritySearchProvider provider : providers) {
        if (providerIds == null || providerIds.contains(provider.getProviderId())) {
          futures.put(provider, executor.submit(() -> provider.search(query)));
        }
      }
      List<SecuritySearchResult> results = new ArrayList<>();
      futures.forEach((provider, future) -> results.add(await(provider, future, deadlineNanos)));
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private SecuritySearchResult await(ISecuritySearchProvider provider, Future<SecuritySearchResult> future,
      long deadlineNanos) {
    try {
      return future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return SecuritySearchResult.failure(provider.getProviderId(), SecuritySearchStatus.TIMEOUT,
          "Deadline of the search exceeded");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return SecuritySearchResult.failure(provider.getProviderId(), SecuritySearchStatus.PROVIDER_ERROR, "Interrupted");
    } catch (ExecutionException e) {
      return SecuritySearchResult.failure(provider.getProviderId(), SecuritySearchStatus.PROVIDER_ERROR,
          String.valueOf(e.getCause()));
    }
  }
}
