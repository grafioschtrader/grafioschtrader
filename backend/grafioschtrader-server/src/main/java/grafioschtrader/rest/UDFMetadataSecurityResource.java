package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import grafiosch.rest.UpdateCreateDeleteWithUserIdResource;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.dto.RuleProposalPreview;
import grafioschtrader.dto.RuleProposalResult;
import grafioschtrader.dto.UDFFillProposal;
import grafioschtrader.dto.UDFFillProposalDecision;
import grafioschtrader.dto.UDFFillProposalDecisionResult;
import grafioschtrader.dto.UDFFillSourcesOffer;
import grafioschtrader.dto.UDFFillStatus;
import grafioschtrader.dto.UDFFillTestRequest;
import grafioschtrader.dto.UDFFillTestResult;
import grafioschtrader.dto.UDFSuggestTestCandidate;
import grafioschtrader.dto.UDFSuggestTestResult;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.udffill.UDFFillService;
import grafioschtrader.udffill.UDFFillSources;
import grafioschtrader.udffill.ai.AIChatModelProvider;
import grafioschtrader.udffill.ai.RuleProposalService;
import grafioschtrader.udffill.ai.ValueSuggestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.UDF_METADATA_SECURITY_MAP)
@Tag(name = RequestGTMappings.UDFMETADATASECURITY, description = "Controller for user defined field of security")
public class UDFMetadataSecurityResource extends UpdateCreateDeleteWithUserIdResource<UDFMetadataSecurity> {

  private final UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;
  private final UDFFillService udfFillService;
  private final UDFFillSources udfFillSources;
  private final RuleProposalService ruleProposalService;
  private final AIChatModelProvider aiChatModelProvider;
  private final ValueSuggestService valueSuggestService;

  public UDFMetadataSecurityResource(UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository,
      UDFFillService udfFillService, UDFFillSources udfFillSources, RuleProposalService ruleProposalService,
      AIChatModelProvider aiChatModelProvider, ValueSuggestService valueSuggestService) {
    super(UDFMetadataSecurity.class);
    this.uDFMetadataSecurityJpaRepository = uDFMetadataSecurityJpaRepository;
    this.udfFillService = udfFillService;
    this.udfFillSources = udfFillSources;
    this.ruleProposalService = ruleProposalService;
    this.aiChatModelProvider = aiChatModelProvider;
    this.valueSuggestService = valueSuggestService;
  }

  @Operation(summary = "Return all security UDF metadata for the current user", description = "This is intended for editing the metadata and contains all of them.", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFMetadataSecurity>> getAllByIdUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<UDFMetadataSecurity> fields = uDFMetadataSecurityJpaRepository
        .getAllByIdUserInAndUiOrderLessThanOrderByUiOrder(new int[] { user.getIdUser(), BaseConstants.UDF_ID_USER },
            BaseConstants.MAX_USER_UI_ORDER_VALUE);
    boolean tenantHasModel = aiChatModelProvider.isValueSuggestionAvailable(user);
    fields.forEach(f -> f.setActive(udfFillService.isProcessed(f, tenantHasModel)));
    return new ResponseEntity<>(fields, HttpStatus.OK);
  }

  @Operation(summary = "Return of the input or output field descriptions for the securities.", description = "The deactivated metadata is excluded here.", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/fielddescriptor", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<FieldDescriptorInputAndShowExtendedSecurity>> getFieldDescriptorByIdUserAndEveryUser() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        uDFMetadataSecurityJpaRepository.getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(user.getIdUser()),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the fill sources this instance offers for user-defined security fields", description = """
      Each fill source names the data types of a field it can fill; the resolvers of the fill source RESOLVER follow
      with their data types and default argument. The edit form builds its selections from this answer only.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/fillsources", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFFillSourcesOffer> getFillSources() {
    return new ResponseEntity<>(udfFillSources.getOffer(currentUser()), HttpStatus.OK);
  }

  @Operation(summary = "Evaluate a draft fill rule against the tenant scope of the caller", description = """
      Nothing is written. The result separates coverage from agreement with the stored values and lists up to 20
      mismatches and up to 20 samples for securities without a value.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/testfillrule", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFFillTestResult> testFillRule(@RequestBody UDFFillTestRequest request) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(udfFillService.testFillRule(request, user), HttpStatus.OK);
  }

  @Operation(summary = "Pick the securities on which a draft description for the model is tested", description = """
      At most 5 non-private securities of the tenant scope, those holding a value of the field first. Nothing is sent.
      Answers 403 when no language model is configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/testsuggest/candidates", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFSuggestTestCandidate>> getSuggestTestCandidates(
      @RequestBody UDFFillTestRequest request) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.getSuggestTestCandidates(request, currentUser()), HttpStatus.OK);
  }

  @Operation(summary = "Test a draft description for the language model on one security", description = """
      Paid requests as in a run, with the same check against the source, but nothing is stored. The stored value of
      this security is withheld from the examples and compared with the answer. Answers 403 when no language model is
      configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/testsuggest/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFSuggestTestResult> testSuggestion(@PathVariable Integer idSecuritycurrency,
      @RequestBody UDFFillTestRequest request) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.testSuggestion(request, idSecuritycurrency, currentUser()),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the fill status of a field of the caller", description = """
      Size of the tenant scope, cells per fill state, manual values and whether a fill run is waiting or running.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/{idUDFMetadata}/fillstatus", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UDFFillStatus> getFillStatus(@PathVariable Integer idUDFMetadata) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(udfFillService.getFillStatus(idUDFMetadata, user), HttpStatus.OK);
  }

  @Operation(summary = "Take a cell over manually", description = """
      Nothing automatic touches the cell afterwards; its value stays as it is, empty or not.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @PutMapping(value = "/{idUDFMetadata}/fill/{idSecuritycurrency}/lock", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> lockCell(@PathVariable Integer idUDFMetadata, @PathVariable Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    udfFillService.lockCell(idUDFMetadata, idSecuritycurrency, user);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "Fill a locked cell automatically again", description = """
      Deletes the lock; for a template the cell is evaluated at once.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @DeleteMapping(value = "/{idUDFMetadata}/fill/{idSecuritycurrency}/lock", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> unlockCell(@PathVariable Integer idUDFMetadata,
      @PathVariable Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    udfFillService.unlockCell(idUDFMetadata, idSecuritycurrency, user);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "Show what a rule proposal of the language model would send", description = """
      Returns the provider, the model and the up to 20 examples of the field that would leave the instance. Nothing
      is sent. Answers 403 when no language model is configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/{idUDFMetadata}/proposerule/preview", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<RuleProposalPreview> previewRuleProposal(@PathVariable Integer idUDFMetadata) {
    if (!aiChatModelProvider.isRuleProposalAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(ruleProposalService.preview(idUDFMetadata, user), HttpStatus.OK);
  }

  @Operation(summary = "Let the language model propose a template rule from the selected examples", description = """
      The body lists the securities the user kept from the preview. One paid request goes out; the rule is evaluated
      like the test of a rule and returned. The model never writes a value. Answers 403 when no language model is
      configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/{idUDFMetadata}/proposerule", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<RuleProposalResult> proposeRule(@PathVariable Integer idUDFMetadata,
      @RequestBody List<Integer> idSecuritycurrencies) {
    if (!aiChatModelProvider.isRuleProposalAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(ruleProposalService.propose(idUDFMetadata, idSecuritycurrencies, user), HttpStatus.OK);
  }

  @Operation(summary = "Start a value proposal run of the language model for a field of the caller", description = """
      Enqueues the paid run; it is skipped when one for this field is waiting. Answers 403 when no language model is
      configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/{idUDFMetadata}/suggest", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> suggestValues(@PathVariable Integer idUDFMetadata) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    valueSuggestService.enqueue(idUDFMetadata, currentUser());
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Operation(summary = "List the verified value proposals of a field of the caller", description = """
      Each proposal carries its value, source link, excerpt, as-of date and rule version. Answers 403 when no language
      model is configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @GetMapping(value = "/{idUDFMetadata}/proposals", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFFillProposal>> getProposals(@PathVariable Integer idUDFMetadata) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.getProposals(idUDFMetadata, currentUser()), HttpStatus.OK);
  }

  @Operation(summary = "Accept value proposals the user saw", description = """
      Per proposal APPLIED, CONFLICT when the cell got a manual value meanwhile, or STALE when the proposal changed.
      Idempotent. Answers 403 when no language model is configured.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/{idUDFMetadata}/proposals/accept", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFFillProposalDecisionResult>> acceptProposals(@PathVariable Integer idUDFMetadata,
      @RequestBody List<UDFFillProposalDecision> decisions) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.decide(idUDFMetadata, decisions, true, currentUser()),
        HttpStatus.OK);
  }

  @Operation(summary = "Reject value proposals the user saw", description = """
      Per proposal REJECTED or STALE. Idempotent. Answers 403 when no language model is configured.""", tags = {
      RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/{idUDFMetadata}/proposals/reject", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UDFFillProposalDecisionResult>> rejectProposals(@PathVariable Integer idUDFMetadata,
      @RequestBody List<UDFFillProposalDecision> decisions) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.decide(idUDFMetadata, decisions, false, currentUser()),
        HttpStatus.OK);
  }

  @Operation(summary = "Ask the language model again for rejected, discarded and not found cells", description = """
      Deletes those fill rows of the field; the next run asks for them again. Answers 403 when no language model is
      configured.""", tags = { RequestGTMappings.UDFMETADATASECURITY })
  @PostMapping(value = "/{idUDFMetadata}/proposals/retry", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> retryProposals(@PathVariable Integer idUDFMetadata) {
    if (!aiChatModelProvider.isValueSuggestionAvailable(currentUser())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    return new ResponseEntity<>(valueSuggestService.retry(idUDFMetadata, currentUser()), HttpStatus.OK);
  }

  private static User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }

  @Override
  protected UpdateCreateDeleteWithUserIdJpaRepository<UDFMetadataSecurity> getUpdateCreateJpaRepository() {
    return uDFMetadataSecurityJpaRepository;
  }

}
