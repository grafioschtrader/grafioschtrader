package grafioschtrader.udffill.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.dto.RuleProposalExample;
import grafioschtrader.dto.RuleProposalPreview;
import grafioschtrader.dto.RuleProposalResult;
import grafioschtrader.dto.UDFFillTestRequest;
import grafioschtrader.dto.UDFFillTestResult;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFDataFillJpaRepository;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillContext;
import grafioschtrader.udffill.UDFFillScopeService;
import grafioschtrader.udffill.UDFFillService;
import grafioschtrader.udffill.UDFFillSources;
import grafioschtrader.udffill.UDFFillTemplate;
import grafioschtrader.udffill.UDFFillTemplateException;
import grafioschtrader.udffill.ai.RuleProposalPrompt.Candidate;

/**
 * The language model proposes a template rule from the existing values of a field; the instance evaluates the rule from
 * then on. The user first sees exactly which values would be sent, may deselect some, and only then the request goes
 * out — one round, no tools. The answer is parsed and evaluated against all values of the field like the test of a
 * rule. The model never writes a value, and nothing is stored apart from the request log.
 */
@Service
public class RuleProposalService {

  private static final Logger log = LoggerFactory.getLogger(RuleProposalService.class);

  /** Structured answer of the model. */
  public record RuleAnswer(String rule) {
  }

  private final AIChatModelProvider aiChatModelProvider;
  private final UDFFillScopeService scopeService;
  private final UDFFillService udfFillService;
  private final UDFDataFillJpaRepository udfDataFillJpaRepository;
  private final UDFMetadataSecurityJpaRepository uMetaSecurityRepository;
  private final MessageSource messageSource;

  public RuleProposalService(AIChatModelProvider aiChatModelProvider, UDFFillScopeService scopeService,
      UDFFillService udfFillService, UDFDataFillJpaRepository udfDataFillJpaRepository,
      UDFMetadataSecurityJpaRepository uMetaSecurityRepository, MessageSource messageSource) {
    this.aiChatModelProvider = aiChatModelProvider;
    this.scopeService = scopeService;
    this.udfFillService = udfFillService;
    this.udfDataFillJpaRepository = udfDataFillJpaRepository;
    this.uMetaSecurityRepository = uMetaSecurityRepository;
    this.messageSource = messageSource;
  }

  /**
   * Returns the provider, the model and the examples a proposal would send, before any request.
   *
   * @param idUDFMetadata a field of the caller of type string or URL with fill source MANUAL or TEMPLATE
   * @param user          the caller
   * @return what would leave the instance
   */
  public RuleProposalPreview preview(Integer idUDFMetadata, User user) {
    UDFMetadataSecurity field = getProposableField(idUDFMetadata, user);
    return new RuleProposalPreview(
        aiChatModelProvider.getProviders(user, AIRequestPurpose.PROPOSE_RULE).stream()
            .map(choice -> new RuleProposalPreview.Provider(choice.label(), choice.keySource())).toList(),
        selectExamples(field, user));
  }

  /**
   * Sends the selected examples to the model and evaluates the rule it writes.
   *
   * @param idUDFMetadata the field
   * @param selectedIds   the securities the user kept in the preview; they must belong to its selection
   * @param user          the caller
   * @return the rule, its parse error or its counters
   */
  public RuleProposalResult propose(Integer idUDFMetadata, List<Integer> selectedIds, User user) {
    UDFMetadataSecurity field = getProposableField(idUDFMetadata, user);
    List<RuleProposalExample> selection = selectExamples(field, user);
    Set<Integer> selectionIds = selection.stream().map(RuleProposalExample::idSecuritycurrency)
        .collect(Collectors.toSet());
    Set<Integer> chosen = new HashSet<>(selectedIds == null ? List.of() : selectedIds);
    if (!selectionIds.containsAll(chosen)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    List<RuleProposalExample> examples = selection.stream().filter(e -> chosen.contains(e.idSecuritycurrency()))
        .toList();
    if (examples.isEmpty()) {
      throw new DataViolationException("fill.rule", "gt.ai.propose.rule.no.examples", null);
    }
    String rule = askModel(field, examples, user);
    return evaluate(field, rule, user);
  }

  private String askModel(UDFMetadataSecurity field, List<RuleProposalExample> examples, User user) {
    BeanOutputConverter<RuleAnswer> converter = new BeanOutputConverter<>(RuleAnswer.class);
    RuleProposalPrompt.Messages messages = RuleProposalPrompt.build(currentTemplateRule(field), examples,
        converter.getFormat());
    // Budgets, provider order and the fallback to the next provider on a technical failure are handled there
    ChatResponse response = aiChatModelProvider
        .call(new Prompt(List.of(new SystemMessage(messages.system()), new UserMessage(messages.user()))), user,
            AIRequestPurpose.PROPOSE_RULE)
        .response();
    String text = response.getResult() == null ? null : response.getResult().getOutput().getText();
    try {
      RuleAnswer answer = text == null ? null : converter.convert(text);
      return answer == null || answer.rule() == null || answer.rule().isBlank() ? null : answer.rule().strip();
    } catch (RuntimeException e) {
      log.info("Answer of the language model for user field {} is no rule: {}", field.getIdUDFMetadata(), text);
      return null;
    }
  }

  private RuleProposalResult evaluate(UDFMetadataSecurity field, String rule, User user) {
    if (rule == null) {
      return new RuleProposalResult(null,
          messageSource.getMessage("gt.ai.propose.rule.no.rule", null, user.createAndGetJavaLocale()), null);
    }
    try {
      UDFFillTemplate.parse(rule);
    } catch (UDFFillTemplateException e) {
      return new RuleProposalResult(rule,
          messageSource.getMessage(e.getReason().getNlsKey(), e.getMessageArgs(), user.createAndGetJavaLocale()), null);
    }
    UDFFillTestRequest request = new UDFFillTestRequest();
    request.idUDFMetadata = field.getIdUDFMetadata();
    request.udfDataType = field.getUdfDataType();
    request.fieldSize = field.getFieldSize();
    request.categoryTypeEnums = field.getCategoryTypeEnums();
    request.specialInvestmentInstrumentEnums = field.getSpecialInvestmentInstrumentEnums();
    request.fillSource = UDFFillSource.TEMPLATE;
    request.fillRule = rule;
    UDFFillTestResult testResult = udfFillService.testFillRule(request, user);
    return new RuleProposalResult(rule, null, testResult);
  }

  /**
   * Selects up to 20 examples of the tenant scope: not private, with a value that no APPLIED row describes as
   * automatic, because values the rule already produces teach nothing.
   */
  private List<RuleProposalExample> selectExamples(UDFMetadataSecurity field, User user) {
    Map<Integer, String> values = scopeService.getFieldValues(user.getIdUser(), field.getIdUDFMetadata());
    Map<Integer, UDFDataFill> fills = udfDataFillJpaRepository.findById_IdUDFMetadata(field.getIdUDFMetadata()).stream()
        .collect(Collectors.toMap(f -> f.getId().getIdEntity(), Function.identity()));
    UDFFillTemplate current = currentTemplate(field);
    Map<Integer, String> yahooSymbols = current == null ? Map.of() : scopeService.getYahooSymbols();
    List<Candidate> candidates = new ArrayList<>();
    for (Security security : scopeService.getTenantScope(field, user.getIdTenant())) {
      String value = values.get(security.getIdSecuritycurrency());
      UDFDataFill fill = fills.get(security.getIdSecuritycurrency());
      boolean automatic = fill != null && fill.getFillState() == UDFFillState.APPLIED && value != null
          && value.equals(fill.getFilledValue());
      if (value != null && !automatic) {
        boolean reproduced = current != null && value.equals(current
            .evaluate(UDFFillContext.of(security, yahooSymbols.get(security.getIdSecuritycurrency())), field).value());
        candidates.add(new Candidate(security, value, reproduced));
      }
    }
    return RuleProposalPrompt.selectExamples(candidates, RuleProposalPrompt.MAX_EXAMPLES);
  }

  private String currentTemplateRule(UDFMetadataSecurity field) {
    return field.getFillSource() == UDFFillSource.TEMPLATE ? field.getFillRule() : null;
  }

  private UDFFillTemplate currentTemplate(UDFMetadataSecurity field) {
    String rule = currentTemplateRule(field);
    if (rule == null) {
      return null;
    }
    try {
      return UDFFillTemplate.parse(rule);
    } catch (UDFFillTemplateException e) {
      return null;
    }
  }

  private UDFMetadataSecurity getProposableField(Integer idUDFMetadata, User user) {
    UDFMetadataSecurity field = uMetaSecurityRepository.findById(idUDFMetadata).orElse(null);
    if (field == null || !user.getIdUser().equals(field.getIdUser())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    boolean sourceAllowed = field.getFillSource() == UDFFillSource.MANUAL
        || field.getFillSource() == UDFFillSource.TEMPLATE;
    if (!UDFFillService.isUserFillable(field) || !sourceAllowed
        || !UDFFillSources.TEMPLATE_DATA_TYPES.contains(field.getUdfDataType())) {
      throw new DataViolationException("fill.source", "gt.ai.propose.rule.not.allowed", null);
    }
    return field;
  }
}
