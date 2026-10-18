package grafioschtrader.udffill.resolver;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import grafiosch.exceptions.DataViolationException;
import grafiosch.types.UDFDataType;
import grafioschtrader.entities.Security;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;

/**
 * Collects the resolver beans, parses and normalises the fill rule of the fill source {@code RESOLVER}, and resolves
 * with an in-memory cache. {@link Found} and {@link NotFound} are cached for 24 hours under resolver, normalised
 * argument and ISIN, so the fields of several users over the same instruments share one lookup. Provenance and writes
 * stay per field; the cache never survives a restart.
 */
@Service
public class UDFValueResolverRegistry {

  public static final Duration CACHE_DURATION = Duration.ofHours(24);
  private static final int CACHE_CLEANUP_SIZE = 5000;

  /**
   * A parsed rule of the fill source {@code RESOLVER}.
   *
   * @param resolverId the ID of the resolver
   * @param argument   the argument, null when the rule names the resolver only
   */
  public record ResolverRule(String resolverId, String argument) {

    /**
     * Splits a rule at the first whitespace into resolver ID and argument.
     */
    public static ResolverRule parse(String rule) {
      String r = rule == null ? "" : rule.strip();
      int space = r.indexOf(' ');
      return space < 0 ? new ResolverRule(r, null)
          : new ResolverRule(r.substring(0, space), r.substring(space + 1).strip());
    }
  }

  private record CacheKey(String resolverId, String argument, String isin) {
  }

  private record CacheEntry(ResolveOutcome outcome, long expiresAtMillis) {
  }

  private final Map<String, IUDFValueResolver> resolvers;
  private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

  public UDFValueResolverRegistry(List<IUDFValueResolver> resolvers) {
    this.resolvers = resolvers.stream()
        .collect(Collectors.toMap(IUDFValueResolver::getResolverId, Function.identity()));
  }

  /**
   * Returns every resolver this instance offers, ordered by ID.
   */
  public List<IUDFValueResolver> getResolvers() {
    return resolvers.values().stream().sorted(Comparator.comparing(IUDFValueResolver::getResolverId)).toList();
  }

  public Optional<IUDFValueResolver> getResolver(String resolverId) {
    return Optional.ofNullable(resolvers.get(resolverId));
  }

  /**
   * Validates a rule for saving and returns it normalised: the resolver must exist and support the data type, and the
   * argument is stored as returned by {@link IUDFValueResolver#normalizeArgument(String)}.
   *
   * @param fillRule    the rule as typed
   * @param udfDataType the data type of the field
   * @return {@code <resolverId> <normalised argument>}
   * @throws DataViolationException with a translated message when the rule is invalid
   */
  public String normalizeRule(String fillRule, UDFDataType udfDataType) {
    ResolverRule rule = ResolverRule.parse(fillRule);
    IUDFValueResolver resolver = resolvers.get(rule.resolverId());
    if (resolver == null) {
      throw new DataViolationException("fill.rule", "gt.udf.fill.resolver.unknown", new Object[] { rule.resolverId() });
    }
    if (!resolver.getSupportedDataTypes().contains(udfDataType)) {
      throw new DataViolationException("fill.source", "gt.udf.fill.source.data.type",
          new Object[] { UDFFillSource.RESOLVER.name() + " " + resolver.getResolverId(), udfDataType.name() });
    }
    try {
      return resolver.getResolverId() + " " + resolver.normalizeArgument(rule.argument());
    } catch (IllegalArgumentException e) {
      throw new DataViolationException("fill.rule", e.getMessage(),
          new Object[] { rule.argument() == null ? "" : rule.argument() });
    }
  }

  /**
   * Resolves the value of a stored rule for a security, from the cache when possible. Must be called outside any
   * transaction.
   *
   * @param fillRule the stored, normalised rule
   * @param security the security
   * @return the outcome of the resolver
   */
  public ResolveOutcome resolve(String fillRule, Security security) {
    ResolverRule rule = ResolverRule.parse(fillRule);
    IUDFValueResolver resolver = resolvers.get(rule.resolverId());
    if (resolver == null) {
      return new Retryable("Resolver " + rule.resolverId() + " is not available on this instance");
    }
    String argument = resolver.normalizeArgument(rule.argument());
    String isin = security.getIsin();
    if (isin == null || isin.isBlank()) {
      return resolver.resolve(security, argument);
    }
    CacheKey key = new CacheKey(resolver.getResolverId(), argument, isin);
    long now = System.currentTimeMillis();
    CacheEntry entry = cache.get(key);
    if (entry != null && entry.expiresAtMillis() > now) {
      return entry.outcome();
    }
    ResolveOutcome outcome = resolver.resolve(security, argument);
    if (!(outcome instanceof Retryable)) {
      if (cache.size() > CACHE_CLEANUP_SIZE) {
        cache.values().removeIf(e -> e.expiresAtMillis() <= now);
      }
      cache.put(key, new CacheEntry(outcome, now + CACHE_DURATION.toMillis()));
    }
    return outcome;
  }

  /**
   * Empties the cache; used by tests and the comparison report, which must ask the source itself.
   */
  public void clearCache() {
    cache.clear();
  }
}
