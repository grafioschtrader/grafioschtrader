package grafioschtrader.udffill;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillResolverOption;
import grafioschtrader.dto.UDFFillSourceOption;
import grafioschtrader.dto.UDFFillSourcesOffer;
import grafioschtrader.types.UDFFillPeriod;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.udffill.ai.AIChatModelProvider;
import grafioschtrader.udffill.resolver.IUDFValueResolver;
import grafioschtrader.udffill.resolver.UDFValueResolverRegistry;

/**
 * The fill sources this instance offers, each with the data types of a field it can fill, and the resolvers of the fill
 * source RESOLVER. The edit form builds its selections from this offer only, and the save validation refuses every
 * other combination.
 */
@Component
public class UDFFillSources {

  /** Data types a template can fill: its result is text. */
  public static final EnumSet<UDFDataType> TEMPLATE_DATA_TYPES = EnumSet.of(UDFDataType.UDF_String,
      UDFDataType.UDF_URLString);

  /** Data types whose proposed value can be checked against a fetched page. */
  public static final EnumSet<UDFDataType> LLM_DATA_TYPES = EnumSet.of(UDFDataType.UDF_Numeric,
      UDFDataType.UDF_NumericInteger, UDFDataType.UDF_URLString);

  private final UDFValueResolverRegistry resolverRegistry;
  private final AIChatModelProvider aiChatModelProvider;

  public UDFFillSources(UDFValueResolverRegistry resolverRegistry, AIChatModelProvider aiChatModelProvider) {
    this.resolverRegistry = resolverRegistry;
    this.aiChatModelProvider = aiChatModelProvider;
  }

  /**
   * Returns the complete offer of this instance.
   */
  public UDFFillSourcesOffer getOffer(User user) {
    return new UDFFillSourcesOffer(getOffered(),
        resolverRegistry.getResolvers().stream()
            .map(r -> new UDFFillResolverOption(r.getResolverId(), r.getSupportedDataTypes(), r.getDefaultArgument()))
            .toList(),
        aiChatModelProvider.isRuleProposalAvailable(user), aiChatModelProvider.isValueSuggestionAvailable(user),
        List.of(UDFFillPeriod.values()));
  }

  /**
   * Returns the offered fill sources. RESOLVER is offered for the union of the data types of the resolvers and is
   * missing when no resolver exists. LLM is always offered: a field without a language model of the tenant of its
   * owner keeps its values but is not processed, see {@code UDFFillService.isProcessed}.
   */
  public List<UDFFillSourceOption> getOffered() {
    List<UDFFillSourceOption> offered = new ArrayList<>();
    offered.add(new UDFFillSourceOption(UDFFillSource.MANUAL, EnumSet.allOf(UDFDataType.class)));
    offered.add(new UDFFillSourceOption(UDFFillSource.TEMPLATE, TEMPLATE_DATA_TYPES));
    EnumSet<UDFDataType> resolverTypes = EnumSet.noneOf(UDFDataType.class);
    for (IUDFValueResolver resolver : resolverRegistry.getResolvers()) {
      resolverTypes.addAll(resolver.getSupportedDataTypes());
    }
    if (!resolverTypes.isEmpty()) {
      offered.add(new UDFFillSourceOption(UDFFillSource.RESOLVER, resolverTypes));
    }
    offered.add(new UDFFillSourceOption(UDFFillSource.LLM, LLM_DATA_TYPES));
    return offered;
  }

  /**
   * Returns the offer of one fill source.
   *
   * @param fillSource the fill source
   * @return the offer, or empty when this instance does not offer the source
   */
  public Optional<UDFFillSourceOption> getOffered(UDFFillSource fillSource) {
    return getOffered().stream().filter(o -> o.fillSource() == fillSource).findFirst();
  }
}
