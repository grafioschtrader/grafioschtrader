package grafioschtrader.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.InstrumentLogoJpaRepository;
import grafioschtrader.repository.InstrumentLogoJpaRepository.InstrumentLogoMetadataProjection;
import grafioschtrader.types.LogoScope;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Resolves company and issuer logos for securities and serves catalog searches for the logo selection dialog.
 *
 * <p>
 * Resolution follows four strictly disjoint stages (first match wins):
 * </p>
 * <ol>
 * <li>Explicit assignment via {@code security.id_instrument_logo} (either deployed or DB-stored SVG).</li>
 * <li>Exact ISIN match against deployed catalog entries ({@code asset_path IS NOT NULL}).</li>
 * <li>Automatic issuer matching for {@code ETF}, {@code MUTUAL_FUND} and {@code ISSUER_RISK_PRODUCT} against deployed
 * {@link LogoScope#ISSUER} entries: longest whole-token {@code name_patterns} match first, then product-link
 * {@code hosts} / {@code host_suffixes} in stored catalog order.</li>
 * <li>{@code null} when none of the above matches.</li>
 * </ol>
 *
 * <p>
 * User-uploaded DB SVGs ({@code asset_path IS NULL}) never trigger automatic stages 2 or 3; they take effect only when
 * explicitly assigned to a security.
 * </p>
 */
@Service
public class InstrumentLogoService {

  private static final int MAX_SEARCH_RESULTS = 100;

  /**
   * Shortest catalog name that is matched as a whole word inside the entered search text. Shorter names would hit too
   * many unrelated instrument names.
   */
  private static final int MIN_NAME_TOKEN_LENGTH = 3;

  private static final Set<SpecialInvestmentInstruments> ISSUER_LOGO_INSTRUMENTS = EnumSet.of(
      SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
      SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT);

  private static final Pattern HTTP_SCHEME_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

  @Autowired
  private InstrumentLogoJpaRepository instrumentLogoJpaRepository;

  private volatile CatalogSnapshot snapshot;

  @EventListener
  public void onLogoChanged(InstrumentLogoChangedEvent event) {
    invalidateCache();
  }

  public void invalidateCache() {
    this.snapshot = null;
  }

  /** Attaches the resolved logo metadata to every security in {@code securities} using a single snapshot read. */
  public void attachResolvedLogos(Collection<Security> securities) {
    if (securities == null || securities.isEmpty()) {
      return;
    }
    CatalogSnapshot current = getSnapshot();
    for (Security security : securities) {
      if (security != null) {
        security.setResolvedLogo(resolve(security, current));
      }
    }
  }

  /** Resolves the effective logo of a persisted or in-memory {@link Security}. */
  public InstrumentLogoRefDto resolve(Security security) {
    if (security == null) {
      return null;
    }
    return resolve(security, getSnapshot());
  }

  /** Resolves the effective logo for an unsaved security form state or proposal comparison. */
  public InstrumentLogoRefDto resolve(Integer idInstrumentLogo, String isin, String name, String productLink,
      SpecialInvestmentInstruments instrument) {
    return resolve(idInstrumentLogo, isin, name, productLink, instrument, getSnapshot());
  }

  /**
   * Searches the shared logo catalog for Dialog (D).
   *
   * <p>
   * {@code isin} matches exact ISINs only; {@code name} matches substring hits on {@code instrument_logo.name}, the
   * catalog name appearing as whole words inside the entered search text, and whole-token {@code name_patterns}
   * appearing inside the entered search text. The second rule lets the name of a derived instrument such as
   * "Pfizer Inc. CFD" find the company logo "PFIZER". When initial security context
   * ({@code idInstrumentLogo}, {@code productLink} or {@code instrument}) is supplied, the security's currently
   * resolved logo is guaranteed to appear first in the result list.
   * </p>
   */
  public List<InstrumentLogoRefDto> search(String name, String isin, LogoScope scope, Integer idInstrumentLogo,
      String productLink, SpecialInvestmentInstruments instrument) {
    return search(name, isin, scope, idInstrumentLogo, productLink, instrument, getSnapshot());
  }

  static InstrumentLogoRefDto resolve(Security security, CatalogSnapshot catalog) {
    SpecialInvestmentInstruments instrument = security.getAssetClass() != null
        ? security.getAssetClass().getSpecialInvestmentInstrument()
        : null;
    return resolve(security.getIdInstrumentLogo(), security.getIsin(), security.getName(), security.getProductLink(),
        instrument, catalog);
  }

  static InstrumentLogoRefDto resolve(Integer idInstrumentLogo, String isin, String name, String productLink,
      SpecialInvestmentInstruments instrument, CatalogSnapshot catalog) {
    // Stage 1: explicit assignment (deployed or DB-stored SVG).
    if (idInstrumentLogo != null) {
      LogoEntry explicitEntry = catalog.byId().get(idInstrumentLogo);
      return explicitEntry != null ? explicitEntry.toRefDto(true) : null;
    }

    // Stage 2: exact ISIN match against deployed entries only.
    String normalizedIsin = normalizeIsin(isin);
    if (normalizedIsin != null) {
      LogoEntry deployedCompany = catalog.deployedByIsin().get(normalizedIsin);
      if (deployedCompany != null) {
        return deployedCompany.toRefDto(false);
      }
    }

    // Stage 3: automatic issuer matching for ETF, mutual fund and issuer-risk products against deployed ISSUER rows.
    if (instrument != null && ISSUER_LOGO_INSTRUMENTS.contains(instrument)) {
      LogoEntry matchedByName = matchIssuerByName(name, catalog.deployedIssuers());
      if (matchedByName != null) {
        return matchedByName.toRefDto(false);
      }
      LogoEntry matchedByHost = matchIssuerByHost(productLink, catalog.deployedIssuers());
      if (matchedByHost != null) {
        return matchedByHost.toRefDto(false);
      }
    }

    // Stage 4: no match.
    return null;
  }

  static List<InstrumentLogoRefDto> search(String name, String isin, LogoScope scope, Integer idInstrumentLogo,
      String productLink, SpecialInvestmentInstruments instrument, CatalogSnapshot catalog) {
    String trimmedName = name == null || name.isBlank() ? null : name.trim();
    String normalizedIsin = normalizeIsin(isin);
    boolean hasInitialContext = idInstrumentLogo != null || (productLink != null && !productLink.isBlank())
        || instrument != null;

    LinkedHashMap<Integer, InstrumentLogoRefDto> ordered = new LinkedHashMap<>();

    // 1. Currently resolved logo first (when initial dialog context is supplied).
    if (hasInitialContext || normalizedIsin != null || trimmedName != null) {
      if (hasInitialContext) {
        InstrumentLogoRefDto resolved = resolve(idInstrumentLogo, normalizedIsin, trimmedName, productLink, instrument,
            catalog);
        if (resolved != null) {
          ordered.put(resolved.idInstrumentLogo(), resolved);
        }
      }
    }

    if (trimmedName == null && normalizedIsin == null) {
      catalog.allEntries().stream().filter(e -> scope == null || e.logoScope() == scope)
          .sorted(Comparator.comparing(LogoEntry::name, String.CASE_INSENSITIVE_ORDER)).forEach(e -> {
            if (ordered.size() < MAX_SEARCH_RESULTS) {
              ordered.putIfAbsent(e.idInstrumentLogo(), e.toRefDto(false));
            }
          });
      return new ArrayList<>(ordered.values());
    }

    // 2. Exact ISIN match (deployed or DB-stored).
    if (normalizedIsin != null) {
      LogoEntry exactIsin = catalog.allByIsin().get(normalizedIsin);
      if (exactIsin != null && (scope == null || exactIsin.logoScope() == scope)) {
        ordered.putIfAbsent(exactIsin.idInstrumentLogo(), exactIsin.toRefDto(false));
      }
    }

    // 3. Name substring matches, catalog names and issuer name_patterns found as whole words inside the search text.
    if (trimmedName != null) {
      String lowerTerm = trimmedName.toLowerCase(Locale.ROOT);
      List<LogoEntry> nameHits = new ArrayList<>();
      for (LogoEntry entry : catalog.allEntries()) {
        if (scope != null && entry.logoScope() != scope) {
          continue;
        }
        boolean substringHit = entry.name().toLowerCase(Locale.ROOT).contains(lowerTerm);
        boolean nameTokenHit = !substringHit && entry.compiledName() != null
            && entry.compiledName().regex().matcher(trimmedName).find();
        boolean patternHit = !substringHit && !nameTokenHit
            && matchesAnyPattern(trimmedName, entry.compiledNamePatterns());
        if (substringHit || nameTokenHit || patternHit) {
          nameHits.add(entry);
        }
      }
      nameHits.sort(Comparator.comparing(LogoEntry::name, String.CASE_INSENSITIVE_ORDER));
      for (LogoEntry hit : nameHits) {
        if (ordered.size() >= MAX_SEARCH_RESULTS) {
          break;
        }
        ordered.putIfAbsent(hit.idInstrumentLogo(), hit.toRefDto(false));
      }
    }

    return new ArrayList<>(ordered.values());
  }

  private CatalogSnapshot getSnapshot() {
    CatalogSnapshot current = this.snapshot;
    if (current == null) {
      synchronized (this) {
        current = this.snapshot;
        if (current == null) {
          current = CatalogSnapshot.fromProjections(instrumentLogoJpaRepository.findAllMetadataOrderByIdAsc());
          this.snapshot = current;
        }
      }
    }
    return current;
  }

  private static LogoEntry matchIssuerByName(String securityName, List<LogoEntry> deployedIssuers) {
    if (securityName == null || securityName.isBlank()) {
      return null;
    }
    LogoEntry bestEntry = null;
    int bestLength = -1;
    for (LogoEntry issuer : deployedIssuers) {
      for (CompiledPattern compiled : issuer.compiledNamePatterns()) {
        if (compiled.regex().matcher(securityName).find() && compiled.pattern().length() > bestLength) {
          bestEntry = issuer;
          bestLength = compiled.pattern().length();
        }
      }
    }
    return bestEntry;
  }

  private static LogoEntry matchIssuerByHost(String productLink, List<LogoEntry> deployedIssuers) {
    String host = extractHost(productLink);
    if (host == null) {
      return null;
    }
    for (LogoEntry issuer : deployedIssuers) {
      for (String exactHost : issuer.hosts()) {
        if (exactHost.equals(host)) {
          return issuer;
        }
      }
      for (String suffix : issuer.hostSuffixes()) {
        if (hostMatchesSuffix(host, suffix)) {
          return issuer;
        }
      }
    }
    return null;
  }

  private static boolean matchesAnyPattern(String text, List<CompiledPattern> patterns) {
    for (CompiledPattern compiled : patterns) {
      if (compiled.regex().matcher(text).find()) {
        return true;
      }
    }
    return false;
  }

  private static boolean hostMatchesSuffix(String host, String suffix) {
    String lower = suffix.toLowerCase(Locale.ROOT);
    String dotted = lower.startsWith(".") ? lower : "." + lower;
    String bare = dotted.substring(1);
    return host.equals(bare) || host.endsWith(dotted);
  }

  static String extractHost(String productLink) {
    if (productLink == null || productLink.isBlank()) {
      return null;
    }
    String trimmed = productLink.trim();
    String withScheme = HTTP_SCHEME_PREFIX.matcher(trimmed).find() ? trimmed : "https://" + trimmed;
    try {
      String host = URI.create(withScheme).getHost();
      return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private static String normalizeIsin(String isin) {
    if (isin == null || isin.isBlank()) {
      return null;
    }
    return isin.trim().toUpperCase(Locale.ROOT);
  }

  private static List<String> splitLines(String raw, boolean lowercase) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split("\\R")).map(String::trim).filter(s -> !s.isEmpty())
        .map(s -> lowercase ? s.toLowerCase(Locale.ROOT) : s).toList();
  }

  private static List<CompiledPattern> compilePatterns(List<String> patterns) {
    if (patterns.isEmpty()) {
      return List.of();
    }
    List<CompiledPattern> compiled = new ArrayList<>(patterns.size());
    for (String pattern : patterns) {
      compiled.add(compileWholeToken(pattern));
    }
    return List.copyOf(compiled);
  }

  /** Compiles the catalog name for whole-word search, or returns null when it is too short to be meaningful. */
  private static CompiledPattern compileName(String name) {
    if (name == null || name.isBlank() || name.trim().length() < MIN_NAME_TOKEN_LENGTH) {
      return null;
    }
    return compileWholeToken(name.trim());
  }

  private static CompiledPattern compileWholeToken(String pattern) {
    Pattern regex = Pattern.compile("(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(pattern) + "(?:$|[^\\p{L}\\p{N}])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    return new CompiledPattern(pattern, regex);
  }

  record CompiledPattern(String pattern, Pattern regex) {
  }

  record LogoEntry(Integer idInstrumentLogo, String isin, String name, LogoScope logoScope, String assetPath,
      String contentSha256, CompiledPattern compiledName, List<CompiledPattern> compiledNamePatterns, List<String> hosts, List<String> hostSuffixes,
      Integer createdBy, boolean hasSvgContent) {

    static LogoEntry of(Integer idInstrumentLogo, String isin, String name, LogoScope logoScope, String assetPath,
        String contentSha256, String namePatterns, String hosts, String hostSuffixes) {
      return of(idInstrumentLogo, isin, name, logoScope, assetPath, contentSha256, namePatterns, hosts, hostSuffixes,
          assetPath != null ? 0 : null, assetPath == null);
    }

    static LogoEntry of(Integer idInstrumentLogo, String isin, String name, LogoScope logoScope, String assetPath,
        String contentSha256, String namePatterns, String hosts, String hostSuffixes, Integer createdBy,
        boolean hasSvgContent) {
      return new LogoEntry(idInstrumentLogo, normalizeIsin(isin), name,
          logoScope != null ? logoScope : LogoScope.COMPANY, assetPath, contentSha256, compileName(name),
          compilePatterns(splitLines(namePatterns, false)), splitLines(hosts, true), splitLines(hostSuffixes, true),
          createdBy, hasSvgContent);
    }

    boolean isDeployed() {
      return assetPath != null;
    }

    InstrumentLogoRefDto toRefDto(boolean explicit) {
      // When a DB SVG exists (including an admin/all-edit override of a deployed entry), emit assetPath = null so
      // clients fetch GET /api/instrumentlogo/{id}/svg?v={contentSha256} instead of the static disk file.
      String effectiveAssetPath = hasSvgContent ? null : assetPath;
      return new InstrumentLogoRefDto(idInstrumentLogo, name, isin, logoScope, effectiveAssetPath, contentSha256,
          explicit, createdBy);
    }
  }

  record CatalogSnapshot(Map<Integer, LogoEntry> byId, Map<String, LogoEntry> deployedByIsin,
      Map<String, LogoEntry> allByIsin, List<LogoEntry> deployedIssuers, List<LogoEntry> allEntries) {

    static CatalogSnapshot fromProjections(List<InstrumentLogoMetadataProjection> projections) {
      List<LogoEntry> entries = new ArrayList<>(projections.size());
      for (InstrumentLogoMetadataProjection p : projections) {
        LogoScope scope = LogoScope.getLogoScopeByValue(p.getLogoScope());
        entries.add(LogoEntry.of(p.getIdInstrumentLogo(), p.getIsin(), p.getName(), scope, p.getAssetPath(),
            p.getContentSha256(), p.getNamePatterns(), p.getHosts(), p.getHostSuffixes(), p.getCreatedBy(),
            p.getHasSvgContent()));
      }
      return fromEntries(entries);
    }

    static CatalogSnapshot fromEntries(List<LogoEntry> entries) {
      Map<Integer, LogoEntry> byId = new HashMap<>(entries.size() * 2);
      Map<String, LogoEntry> deployedByIsin = new HashMap<>();
      Map<String, LogoEntry> allByIsin = new HashMap<>();
      List<LogoEntry> deployedIssuers = new ArrayList<>();
      for (LogoEntry entry : entries) {
        byId.put(entry.idInstrumentLogo(), entry);
        if (entry.isin() != null) {
          allByIsin.put(entry.isin(), entry);
          if (entry.isDeployed()) {
            deployedByIsin.put(entry.isin(), entry);
          }
        }
        if (entry.isDeployed() && entry.logoScope() == LogoScope.ISSUER) {
          deployedIssuers.add(entry);
        }
      }
      return new CatalogSnapshot(Map.copyOf(byId), Map.copyOf(deployedByIsin), Map.copyOf(allByIsin),
          List.copyOf(deployedIssuers), List.copyOf(entries));
    }
  }
}
