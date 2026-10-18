package grafioschtrader.udffill;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.AllUserFieldsSecurity;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.UDFDataFillJpaRepository;
import grafioschtrader.repository.UDFDataFillJpaRepository.IdEntityValue;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFSpecialGTType;
import grafioschtrader.udffill.UDFFillOutcome.None;
import grafioschtrader.udffill.UDFFillOutcome.Retryable;
import grafioschtrader.udffill.UDFFillOutcome.Value;
import grafioschtrader.udffill.UDFFillTemplate.TemplateResult;
import grafioschtrader.udffill.resolver.ResolveOutcome;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.UDFValueResolverRegistry;
import grafioschtrader.udffill.resolver.UDFValueResolverRegistry.ResolverRule;

/**
 * Determines what a fill run works on — the tenant scope of a field — and computes the outcome of a fill source for one
 * security. Parsed templates are cached per field and fill rule version.
 */
@Service
public class UDFFillScopeService {

  private record TemplateKey(Integer idUDFMetadata, int fillRuleVersion) {
  }

  private static final Logger log = LoggerFactory.getLogger(UDFFillScopeService.class);

  private final Map<TemplateKey, UDFFillTemplate> templateCache = new ConcurrentHashMap<>();

  private final UDFDataFillJpaRepository udfDataFillJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final UDFValueResolverRegistry resolverRegistry;

  public UDFFillScopeService(UDFValueResolverRegistry resolverRegistry,
      UDFDataFillJpaRepository udfDataFillJpaRepository, SecurityJpaRepository securityJpaRepository,
      UDFMetadataSecurityJpaRepository uMetaSecurityRepository) {
    this.udfDataFillJpaRepository = udfDataFillJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.resolverRegistry = resolverRegistry;
  }

  /**
   * Returns the tenant scope of a field: every security in a watchlist of the tenant and every security referenced by a
   * transaction of the tenant, restricted to securities the tenant may access and to the asset classes and financial
   * instruments of the field. Securities that appear only in algorithmic strategies are outside the scope.
   *
   * @param field    the field, possibly an unsaved draft; only its asset classes and instruments are read
   * @param idTenant the home tenant of the owner of the field
   * @return the securities, ordered by their ID
   */
  public List<Security> getTenantScope(UDFMetadataSecurity field, Integer idTenant) {
    List<Integer> ids = udfDataFillJpaRepository.getTenantScopeSecurityIds(idTenant);
    return securityJpaRepository.findAllById(ids).stream()
        .filter(s -> s.tenantHasAccess(idTenant)
            && AllUserFieldsSecurity.matchAssetclassAndSpecialInvestmentInstruments(field, s.getAssetClass()))
        .sorted(Comparator.comparing(Security::getIdSecuritycurrency)).toList();
  }

  /**
   * Returns the persisted Yahoo symbols, the user-0 values of {@code UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE}. A security
   * whose symbol has not been stored yet is absent.
   *
   * @return per security ID its Yahoo symbol
   */
  public Map<Integer, String> getYahooSymbols() {
    UDFMetadataSecurity yahooField = uMetaSecurityRepository.getByUdfSpecialTypeAndIdUser(
        UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE.getValue(), BaseConstants.UDF_ID_USER);
    return yahooField == null ? Map.of() : getFieldValues(BaseConstants.UDF_ID_USER, yahooField.getIdUDFMetadata());
  }

  /**
   * Returns the non-empty values of one field of a user for every security.
   *
   * @param idUser        the owner of the values
   * @param idUDFMetadata the field
   * @return per security ID the value as text
   */
  public Map<Integer, String> getFieldValues(Integer idUser, Integer idUDFMetadata) {
    Map<Integer, String> values = new HashMap<>();
    for (IdEntityValue iev : udfDataFillJpaRepository.getFieldValuesOfUser(idUser, idUDFMetadata)) {
      if (!UDFFillDecision.isEmpty(iev.getJsonValue())) {
        values.put(iev.getIdEntity(), iev.getJsonValue());
      }
    }
    return values;
  }

  /**
   * Returns the parsed template of a field, cached per field and fill rule version.
   *
   * @param field a saved field with the fill source TEMPLATE
   * @return the parsed template
   * @throws UDFFillTemplateException when the stored rule does not parse
   */
  public UDFFillTemplate getTemplate(UDFMetadataSecurity field) {
    TemplateKey key = new TemplateKey(field.getIdUDFMetadata(), field.getFillRuleVersion());
    UDFFillTemplate template = templateCache.get(key);
    if (template == null) {
      template = UDFFillTemplate.parse(field.getFillRule());
      templateCache.keySet().removeIf(k -> k.idUDFMetadata().equals(key.idUDFMetadata()));
      templateCache.put(key, template);
    }
    return template;
  }

  /**
   * Computes the outcome of the fill source of a field for one security. Makes no write and opens no transaction.
   *
   * @param field       the field
   * @param security    the security
   * @param yahooSymbol the persisted Yahoo symbol of the security, or null
   * @return the outcome for {@link UDFFillWriter}
   */
  public UDFFillOutcome computeOutcome(UDFMetadataSecurity field, Security security, String yahooSymbol) {
    if (field.getFillSource() == UDFFillSource.TEMPLATE) {
      TemplateResult result = getTemplate(field).evaluate(UDFFillContext.of(security, yahooSymbol), field);
      return result.hasValue() ? new Value(result.value(), null) : new None();
    }
    if (field.getFillSource() == UDFFillSource.RESOLVER) {
      return toFillOutcome(resolverRegistry.resolve(field.getFillRule(), security), field);
    }
    return new Retryable("Fill source " + field.getFillSource() + " is not offered by this instance");
  }

  /**
   * Maps the answer of a resolver to the outcome of the writer. A found value that fails the validation of the field,
   * for example a URL longer than 254 characters, counts as not found.
   */
  static UDFFillOutcome toFillOutcome(ResolveOutcome resolveOutcome, UDFMetadataSecurity field) {
    return switch (resolveOutcome) {
    case Found found -> UDFFillTemplate.validate(found.value(), field) == null
        ? new Value(found.value(), found.sourceUrl())
        : new None();
    case NotFound _ -> new None();
    case ResolveOutcome.Retryable retryable -> new Retryable(retryable.reason());
    };
  }

  /**
   * Tells whether the stored rule of a field can be evaluated on this instance: a template must parse, a resolver must
   * exist.
   *
   * @param field the field
   * @return true when a fill run can compute the field
   */
  public boolean canCompute(UDFMetadataSecurity field) {
    return switch (field.getFillSource()) {
    case TEMPLATE -> {
      try {
        getTemplate(field);
        yield true;
      } catch (UDFFillTemplateException e) {
        log.warn("Stored fill rule of user field {} does not parse: {}", field.getIdUDFMetadata(), e.getMessage());
        yield false;
      }
    }
    case RESOLVER -> {
      boolean available = resolverRegistry.getResolver(ResolverRule.parse(field.getFillRule()).resolverId())
          .isPresent();
      if (!available) {
        log.warn("Resolver of user field {} is not available: {}", field.getIdUDFMetadata(), field.getFillRule());
      }
      yield available;
    }
    default -> false;
    };
  }
}
