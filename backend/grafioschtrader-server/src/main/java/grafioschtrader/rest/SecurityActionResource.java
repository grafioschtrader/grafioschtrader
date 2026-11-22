package grafioschtrader.rest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;
import grafioschtrader.dto.SecurityActionTreeData;
import grafioschtrader.dto.SpinOffAllocationProposalDto;
import grafioschtrader.dto.SpinOffDecisionDto;
import grafioschtrader.dto.SpinOffPreviewDto;
import grafioschtrader.dto.SpinOffProviderSplitsDto;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.SecurityTransfer;
import grafioschtrader.instrument.SpinOffHistoryBasisService;
import grafioschtrader.instrument.lookup.SuccessorSecurityProposer;
import grafioschtrader.service.SecurityActionService;
import grafioschtrader.service.SpinOffActionService;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffTaxTreatmentDe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for system security actions and user account transfers.
 */
@RestController
@RequestMapping("/api/securityaction")
@Tag(name = "SecurityAction", description = "System security actions and security account transfers")
public class SecurityActionResource {

  /** Option key of the automatically decided history basis of a spin-off. */
  private static final String HISTORY_BASIS_AUTOMATIC = "AUTOMATIC";

  @Autowired
  private SecurityActionService securityActionService;

  @Autowired
  private SuccessorSecurityProposer successorSecurityProposer;

  @Autowired
  private SpinOffActionService spinOffActionService;

  @Autowired
  private SpinOffHistoryBasisService spinOffHistoryBasisService;

  @Operation(summary = "Returns tree data for the SecurityAction TreeTable")
  @GetMapping("/tree")
  public ResponseEntity<SecurityActionTreeData> getTree() {
    return ResponseEntity.ok(securityActionService.getTreeData());
  }

  /**
   * Creates a system action. Instrument lookup can take several seconds, so it runs before the save transaction.
   */
  @Operation(summary = "Admin creates an ISIN change or spin-off")
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<SecurityAction> createSecurityAction(@RequestBody SecurityAction securityAction)
      throws Exception {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<Security> proposal;
    if (securityAction.getActionType() == SecurityActionType.SPIN_OFF) {
      Security child = securityAction.getSecurityNew();
      Security parent = securityAction.getSecurityOld();
      proposal = child != null && child.getIdSecuritycurrency() != null ? Optional.empty()
          : Optional.of(spinOffActionService.previewChild(parent == null ? null : parent.getIdSecuritycurrency(),
              securityAction.getIsinNew(), child == null ? null : child.getCurrency(), securityAction.getActionDate(),
              user.createAndGetJavaLocale()));
    } else {
      proposal = successorSecurityProposer.propose(securityAction, user.createAndGetJavaLocale());
    }
    return ResponseEntity.ok(securityActionService.createSecurityAction(securityAction, proposal));
  }

  @Operation(summary = "Returns the system security action types")
  @GetMapping("/actiontypes")
  public List<ValueKeyHtmlSelectOptions> getActionTypes() {
    return enumOptions(SecurityActionType.values());
  }

  @Operation(summary = "Returns the German tax treatment options for spin-offs")
  @GetMapping("/spinoff/taxtreatments")
  public List<ValueKeyHtmlSelectOptions> getSpinOffTaxTreatments() {
    return enumOptions(SpinOffTaxTreatmentDe.values());
  }

  @GetMapping("/spinoff/decisions")
  public List<ValueKeyHtmlSelectOptions> getSpinOffDecisions() {
    return enumOptions(SpinOffPreviewDto.Decision.values());
  }

  @Operation(summary = "Admin previews an existing or proposed spin-off child instrument")
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/spinoff/child")
  public Security previewSpinOffChild(@RequestParam Integer idSecurityOld, @RequestParam String isin,
      @RequestParam String currency,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actionDate) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return spinOffActionService.previewChild(idSecurityOld, isin, currency, actionDate, user.createAndGetJavaLocale());
  }

  @Operation(summary = "Admin proposes a spin-off allocation from historical prices")
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/spinoff/allocation")
  public SpinOffAllocationProposalDto proposeSpinOffAllocation(@RequestParam Integer idSecurityOld,
      @RequestParam Integer idSecurityNew,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actionDate,
      @RequestParam Integer fromFactor, @RequestParam Integer toFactor) {
    return spinOffActionService.proposeAllocation(idSecurityOld, idSecurityNew, actionDate, fromFactor, toFactor);
  }

  @Operation(summary = "Admin reads the provider splits and the history basis of a spin-off parent around a date")
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/spinoff/providersplits")
  public SpinOffProviderSplitsDto getSpinOffProviderSplits(@RequestParam Integer idSecurityOld,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actionDate) {
    return spinOffHistoryBasisService.describe(idSecurityOld, actionDate);
  }

  /**
   * Options of the history basis setting of a spin-off: automatic returns the decision to the parent refresh, the two
   * others set it. Undetermined is a result of the automatic decision and no setting of its own.
   */
  @Operation(summary = "Returns the history basis options an administrator can set for a spin-off")
  @GetMapping("/spinoff/historybases")
  public List<ValueKeyHtmlSelectOptions> getSpinOffHistoryBases() {
    return List.of(new ValueKeyHtmlSelectOptions(HISTORY_BASIS_AUTOMATIC, HISTORY_BASIS_AUTOMATIC),
        new ValueKeyHtmlSelectOptions(SpinOffHistoryBasis.UNADJUSTED.name(), SpinOffHistoryBasis.UNADJUSTED.name()),
        new ValueKeyHtmlSelectOptions(SpinOffHistoryBasis.ADJUSTED.name(), SpinOffHistoryBasis.ADJUSTED.name()));
  }

  @Operation(summary = "Admin corrects the recorded spin-off allocation and tax treatment")
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public SecurityAction correctSpinOff(@PathVariable Integer id, @RequestBody SecurityAction action) {
    return spinOffActionService.correct(id, action);
  }

  private List<ValueKeyHtmlSelectOptions> enumOptions(Enum<?>[] values) {
    return Arrays.stream(values).map(option -> new ValueKeyHtmlSelectOptions(option.name(), option.name())).toList();
  }

  /**
   * Deletes an ISIN change event. Restricted to an administrator, because the event is shared data that only an
   * administrator creates; the absence of applications is a precondition, not an ownership check.
   */
  @Operation(summary = "Admin deletes a system action (only if no applications exist)")
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteSecurityAction(@PathVariable Integer id) {
    securityActionService.deleteSecurityAction(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Previews spin-off bookings for the current tenant")
  @GetMapping("/{id}/spinoff/preview")
  public SpinOffPreviewDto previewSpinOff(@PathVariable Integer id) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return securityActionService.previewSpinOff(id, user.getIdTenant());
  }

  @Operation(summary = "User applies a security action to their holdings")
  @PostMapping("/{id}/apply")
  public ResponseEntity<SecurityActionApplication> applySecurityAction(@PathVariable Integer id,
      @RequestBody(required = false) List<SpinOffDecisionDto> decisions) {
    return ResponseEntity.ok(securityActionService.applySecurityAction(id, decisions));
  }

  public ResponseEntity<SecurityActionApplication> applySecurityAction(Integer id) {
    return applySecurityAction(id, null);
  }

  @Operation(summary = "User reverses an applied ISIN change")
  @PostMapping("/{id}/reverse")
  public ResponseEntity<Void> reverseSecurityAction(@PathVariable Integer id) {
    securityActionService.reverseSecurityAction(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "User creates a security transfer between accounts")
  @PostMapping("/transfer")
  public ResponseEntity<SecurityTransfer> createTransfer(@RequestBody SecurityTransfer securityTransfer) {
    return ResponseEntity.ok(securityActionService.createTransfer(securityTransfer));
  }

  @Operation(summary = "User reverses/deletes a security transfer")
  @DeleteMapping("/transfer/{id}")
  public ResponseEntity<Void> reverseTransfer(@PathVariable Integer id) {
    securityActionService.reverseTransfer(id);
    return ResponseEntity.noContent().build();
  }
}
