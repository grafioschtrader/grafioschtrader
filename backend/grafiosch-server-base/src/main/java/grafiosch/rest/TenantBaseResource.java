package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import grafiosch.dto.AccountDeletionEligibility;
import grafiosch.dto.CreateClientRequest;
import grafiosch.dto.ShareReadAccessRequest;
import grafiosch.dto.ShareRecipientStatusResponse;
import grafiosch.dto.ShareRecipientStatusResponse.ShareRecipientStatus;
import grafiosch.dto.SharedViewerInfo;
import grafiosch.dto.SharedViewerInfo.SharedViewerType;
import grafiosch.dto.TenantAccessInfo;
import grafiosch.entities.BaseID;
import grafiosch.entities.Role;
import grafiosch.entities.TenantAccess;
import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.repository.TenantBaseCustom;
import grafiosch.repository.UserJpaRepository;
import grafiosch.security.JwtTokenHandler;
import grafiosch.service.MailExternalService;
import grafiosch.types.TenantAccessLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

public abstract class TenantBaseResource<T extends BaseID<Integer>> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** JWT lifetime (minutes) of the token issued when switching tenants. */
  private static final int SWITCH_TOKEN_EXPIRATION_MINUTES = 120;

  @Autowired
  protected TenantAccessJpaRepository tenantAccessJpaRepository;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  @Autowired
  protected UserJpaRepository userJpaRepository;

  @Autowired
  protected RoleJpaRepository roleJpaRepository;

  @Autowired
  protected MailExternalService mailExternalService;

  @Autowired
  protected org.springframework.context.MessageSource messages;

  protected abstract TenantBaseCustom getTenantRepository();

  @Operation(summary = "Export the data of a client with it private ond public data", description = "The created zip file will cotains two files one with ddl and the 2nd with dml statements", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/exportpersonaldataaszip", produces = "application/zip")
  public void getExportPersonalDataAsZip(HttpServletResponse response) throws Exception {
    getTenantRepository().getExportPersonalDataAsZip(response);
  }

  @Operation(summary = "Delete the private data the main tenant of the user. It als removes the user from this application", description = "", tags = {
      TenantBase.TABNAME })
  @DeleteMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteMyDataAndUserAccount() throws Exception {
    log.debug("Delete all data of a user");
    getTenantRepository().deleteMyDataAndUserAccount();
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Read-only pre-check whether the current user may delete their own account", description = "Lets the frontend warn the user to delete managed clients or revoke shared access before deleting; the delete endpoint enforces the same conditions.", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/deletion-eligibility", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AccountDeletionEligibility> getAccountDeletionEligibility() {
    return new ResponseEntity<>(getTenantRepository().getAccountDeletionEligibility(getCurrentUser()), HttpStatus.OK);
  }

  // --- Manage-client feature (g.use.manageclient) ---------------------------------------------------------------

  @Operation(summary = "List all tenants the current user may access (home tenant plus granted tenants)", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/accessible", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TenantAccessInfo>> getAccessibleTenants() {
    final User user = getCurrentUser();
    final Integer homeIdTenant = user.getActualIdTenant();

    List<TenantAccessInfo> result = new ArrayList<>();
    String homeName = getManagedTenantName(homeIdTenant);
    if (homeName != null) {
      TenantAccessInfo home = new TenantAccessInfo(homeIdTenant, homeName, true,
          user.isHomeTenantReadOnly() ? TenantAccessLevel.READ : TenantAccessLevel.MANAGE);
      home.setEmail(user.getUsername());
      result.add(home);
    }
    for (TenantAccess grant : tenantAccessJpaRepository.findByIdUser(user.getIdUser())) {
      String name = getManagedTenantName(grant.getIdTenant());
      if (name != null) {
        TenantAccessInfo info = new TenantAccessInfo(grant.getIdTenant(), name, false, grant.getAccessLevel());
        info.setEmail(resolveTenantUserEmail(grant.getIdTenant()));
        result.add(info);
      }
    }
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Operation(summary = "Switch to a different tenant the user may access and receive a new JWT", tags = {
      TenantBase.TABNAME })
  @PostMapping(value = "/switchto/{idTargetTenant}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> switchTenant(
      @Parameter(description = "ID of the target tenant", required = true) @PathVariable Integer idTargetTenant) {
    final User user = getCurrentUser();
    // actualIdTenant preserves the persisted home tenant even when the user is currently in another tenant.
    final Integer homeIdTenant = user.getActualIdTenant();

    TenantAccessLevel targetLevel = resolveSwitchTargetLevel(user, homeIdTenant, idTargetTenant);
    if (targetLevel == null) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    String token = jwtTokenHandler.createTokenForUser(user, SWITCH_TOKEN_EXPIRATION_MINUTES, idTargetTenant);
    // readOnly lets the frontend render read-only mode immediately without an extra round-trip.
    return new ResponseEntity<>(
        Map.of("token", token, "readOnly", String.valueOf(targetLevel == TenantAccessLevel.READ)), HttpStatus.OK);
  }

  /**
   * Resolves the access level the user holds on the switch target, or {@code null} when switching there is not allowed.
   * The generic rules are: the user's own home tenant, or a tenant the user has a {@link TenantAccess} grant for. An
   * application may permit additional targets (for example simulation tenants) by overriding
   * {@link #resolveAppSpecificSwitchTargetLevel(User, Integer, Integer)}.
   *
   * @param user         the current user
   * @param homeIdTenant the user's persisted home tenant
   * @param idTarget     the tenant the user wants to switch to
   * @return the access level on the target, or null if switching there is forbidden
   */
  private TenantAccessLevel resolveSwitchTargetLevel(User user, Integer homeIdTenant, Integer idTarget) {
    if (homeIdTenant.equals(idTarget)) {
      return user.isHomeTenantReadOnly() ? TenantAccessLevel.READ : TenantAccessLevel.MANAGE;
    }
    TenantAccess grant = tenantAccessJpaRepository.findByIdUserAndIdTenant(user.getIdUser(), idTarget).orElse(null);
    if (grant != null) {
      return grant.getAccessLevel();
    }
    return resolveAppSpecificSwitchTargetLevel(user, homeIdTenant, idTarget);
  }

  /**
   * Hook for application-specific switch targets beyond the user's home tenant and explicit {@link TenantAccess} grants.
   * The default forbids any other target. GrafioschTrader overrides this to allow switching into a simulation tenant
   * that is a child of the user's home tenant.
   *
   * @param user         the current user
   * @param homeIdTenant the user's persisted home tenant
   * @param idTarget     the tenant the user wants to switch to
   * @return the access level to grant on the target, or null to forbid the switch
   */
  protected TenantAccessLevel resolveAppSpecificSwitchTargetLevel(User user, Integer homeIdTenant, Integer idTarget) {
    return null;
  }

  @Operation(summary = "Create a managed client: a new tenant with a read-only client login (advisor capability)", tags = {
      TenantBase.TABNAME })
  @PostMapping(value = "/createclient", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> createClient(@Valid @RequestBody CreateClientRequest request) throws MessagingException {
    final User advisor = getCurrentUser();
    if (advisor.isTenantAccessReadOnly() || advisor.isHomeTenantReadOnly()) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    if (userJpaRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new DataViolationException("email", "email.already.used", new Object[] { request.getEmail() });
    }

    User client = new User(request.getEmail(), new BCryptPasswordEncoder().encode(request.getPassword()),
        deriveUniqueNickname(request.getEmail()), advisor.getLocaleStr(), advisor.getTimezoneOffset());
    client.setEnabled(true);
    client.setHomeTenantReadOnly(true);
    client.setRoles(List.of(roleJpaRepository.findByRolename(Role.ROLE_USER)));
    client = userJpaRepository.save(client);

    Integer idTenant = createManagedClientTenant(deriveTenantName(request.getEmail()), advisor);
    client.setIdTenant(idTenant);
    userJpaRepository.save(client);

    tenantAccessJpaRepository.save(new TenantAccess(advisor.getIdUser(), idTenant, TenantAccessLevel.MANAGE));
    sendClientCreatedMail(client, request.getPassword());
    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @Operation(summary = "Delete a managed client entirely: its tenant, all data and the read-only client user", tags = {
      TenantBase.TABNAME })
  @DeleteMapping(value = "/client/{idTenant}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteManagedClient(
      @Parameter(description = "ID of the managed client tenant", required = true) @PathVariable Integer idTenant)
      throws Exception {
    final User advisor = getCurrentUser();
    if (advisor.isTenantAccessReadOnly() || advisor.isHomeTenantReadOnly()) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    // Refuse deleting the tenant currently in use or the advisor's own home tenant.
    if (idTenant.equals(advisor.getIdTenant()) || idTenant.equals(advisor.getActualIdTenant())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    // The advisor may only delete a client they manage.
    TenantAccess grant = tenantAccessJpaRepository.findByIdUserAndIdTenant(advisor.getIdUser(), idTenant).orElse(null);
    if (grant == null || grant.getAccessLevel() != TenantAccessLevel.MANAGE) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    // The target must be a real read-only managed client, not an arbitrary user/tenant.
    User client = userJpaRepository.findByIdTenant(idTenant).orElse(null);
    if (client == null || !client.isHomeTenantReadOnly()) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    getTenantRepository().deleteManagedClientData(client);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // --- Share read access to my own portfolio (extension of the manage-client feature) -----------------------------

  @Operation(summary = "Grant another person read access to the current owner's own portfolio (home tenant)", tags = {
      TenantBase.TABNAME })
  @PostMapping(value = "/share", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> shareReadAccess(@Valid @RequestBody ShareReadAccessRequest request)
      throws MessagingException {
    final User owner = getCurrentUser();
    final Integer homeIdTenant = owner.getActualIdTenant();
    // Only a writable owner operating in their own home tenant may share it.
    if (owner.isTenantAccessReadOnly() || owner.isHomeTenantReadOnly() || !homeIdTenant.equals(owner.getIdTenant())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    final String email = request.getEmail().trim();
    if (email.equalsIgnoreCase(owner.getUsername())) {
      throw new DataViolationException("email", "g.share.self", null);
    }
    Optional<User> existing = userJpaRepository.findByEmail(email);
    if (existing.isPresent()) {
      User grantee = existing.get();
      // Already a viewer co-resident on this tenant, or already holding a grant on it.
      if (homeIdTenant.equals(grantee.getIdTenant())
          || tenantAccessJpaRepository.findByIdUserAndIdTenant(grantee.getIdUser(), homeIdTenant).isPresent()) {
        throw new DataViolationException("email", "g.share.email.already", new Object[] { email });
      }
      tenantAccessJpaRepository.save(new TenantAccess(grantee.getIdUser(), homeIdTenant, TenantAccessLevel.READ));
    } else {
      if (request.getPassword() == null || request.getPassword().isBlank()) {
        throw new DataViolationException("password", "g.share.password.required", null);
      }
      User viewer = new User(email, new BCryptPasswordEncoder().encode(request.getPassword()),
          deriveUniqueNickname(email), owner.getLocaleStr(), owner.getTimezoneOffset());
      viewer.setEnabled(true);
      viewer.setHomeTenantReadOnly(true);
      viewer.setRoles(List.of(roleJpaRepository.findByRolename(Role.ROLE_USER)));
      viewer.setIdTenant(homeIdTenant);
      viewer = userJpaRepository.save(viewer);
      sendShareInvitedMail(viewer, owner, request.getPassword());
    }
    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @Operation(summary = "Resolve the status of a share recipient e-mail (drives the share dialog, does not change data)", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/share/recipient-status", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ShareRecipientStatusResponse> getShareRecipientStatus(
      @Parameter(description = "The recipient e-mail to look up", required = true) @RequestParam String email) {
    final User owner = getCurrentUser();
    final Integer homeIdTenant = owner.getActualIdTenant();
    if (owner.isTenantAccessReadOnly() || owner.isHomeTenantReadOnly() || !homeIdTenant.equals(owner.getIdTenant())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    final String normalized = email.trim();
    ShareRecipientStatus status;
    if (normalized.equalsIgnoreCase(owner.getUsername())) {
      status = ShareRecipientStatus.SELF;
    } else {
      User grantee = userJpaRepository.findByEmail(normalized).orElse(null);
      if (grantee == null) {
        status = ShareRecipientStatus.NEW;
      } else if (homeIdTenant.equals(grantee.getIdTenant())
          || tenantAccessJpaRepository.findByIdUserAndIdTenant(grantee.getIdUser(), homeIdTenant).isPresent()) {
        status = ShareRecipientStatus.ALREADY_SHARED;
      } else {
        status = ShareRecipientStatus.EXISTS;
      }
    }
    return new ResponseEntity<>(new ShareRecipientStatusResponse(status), HttpStatus.OK);
  }

  @Operation(summary = "List everyone who can read the current owner's portfolio (read grants and viewer logins)", tags = {
      TenantBase.TABNAME })
  @GetMapping(value = "/shares", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<SharedViewerInfo>> getSharedViewers() {
    final User owner = getCurrentUser();
    final Integer homeIdTenant = owner.getActualIdTenant();
    List<SharedViewerInfo> result = new ArrayList<>();
    // Pure read-only viewer logins co-resident on the owner's tenant (the owner itself is not read-only, so excluded).
    for (User viewer : userJpaRepository.findByIdTenantAndHomeTenantReadOnlyTrue(homeIdTenant)) {
      if (!viewer.getIdUser().equals(owner.getIdUser())) {
        result.add(new SharedViewerInfo(viewer.getIdUser(), viewer.getUsername(), SharedViewerType.VIEWER));
      }
    }
    // Registered users holding a read grant on the owner's tenant.
    for (TenantAccess grant : tenantAccessJpaRepository.findByIdTenant(homeIdTenant)) {
      if (grant.getAccessLevel() == TenantAccessLevel.READ) {
        userJpaRepository.findById(grant.getIdUser()).ifPresent(
            u -> result.add(new SharedViewerInfo(u.getIdUser(), u.getUsername(), SharedViewerType.GRANT)));
      }
    }
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Operation(summary = "Revoke a person's read access to the current owner's portfolio", tags = { TenantBase.TABNAME })
  @DeleteMapping(value = "/share/{idUser}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> revokeShare(
      @Parameter(description = "ID of the user whose read access is revoked", required = true) @PathVariable Integer idUser) {
    final User owner = getCurrentUser();
    final Integer homeIdTenant = owner.getActualIdTenant();
    if (owner.isTenantAccessReadOnly() || owner.isHomeTenantReadOnly() || !homeIdTenant.equals(owner.getIdTenant())) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
    // Registered grantee: remove only the grant; the user keeps their own account.
    Optional<TenantAccess> grant = tenantAccessJpaRepository.findByIdUserAndIdTenant(idUser, homeIdTenant);
    if (grant.isPresent()) {
      tenantAccessJpaRepository.delete(grant.get());
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    // Pure read-only viewer login co-resident on this tenant: delete the viewer entirely (user_role cascades).
    User viewer = userJpaRepository.findById(idUser).orElse(null);
    if (viewer != null && homeIdTenant.equals(viewer.getIdTenant()) && viewer.isHomeTenantReadOnly()) {
      userJpaRepository.delete(viewer);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
  }

  /**
   * Resolves the e-mail to label a tenant with: prefers the writable owner, falling back to a read-only login residing
   * on the tenant (for an advisor-managed client tenant whose only user is the read-only client).
   *
   * @param idTenant the tenant to label
   * @return the representative user's e-mail, or null if the tenant has no user
   */
  private String resolveTenantUserEmail(Integer idTenant) {
    return userJpaRepository.findFirstByIdTenantAndHomeTenantReadOnlyFalse(idTenant)
        .or(() -> userJpaRepository.findByIdTenantAndHomeTenantReadOnlyTrue(idTenant).stream().findFirst())
        .map(User::getUsername).orElse(null);
  }

  private void sendShareInvitedMail(User viewer, User owner, String plainPassword) throws MessagingException {
    String subject = messages.getMessage("g.share.invited.subject", null, viewer.createAndGetJavaLocale());
    String body = messages.getMessage("g.share.invited.body",
        new Object[] { viewer.getUsername(), plainPassword, owner.getUsername() }, viewer.createAndGetJavaLocale());
    mailExternalService.sendSimpleMessageAsync(viewer.getUsername(), subject, body);
  }

  /**
   * Creates and persists the concrete tenant for a managed client and returns its id. The concrete tenant type and its
   * currency are application-specific (the library only knows the {@link TenantBase} mapped superclass), so the
   * application supplies the implementation (typically using the advisor's own tenant currency).
   *
   * @param tenantName the name for the new tenant
   * @param advisor    the advisor creating the client
   * @return the id of the newly created tenant
   */
  protected abstract Integer createManagedClientTenant(String tenantName, User advisor);

  /**
   * Returns the display name of a tenant by id, or null if it does not exist. The application supplies this because the
   * concrete tenant repository is application-specific.
   *
   * @param idTenant the tenant id
   * @return the tenant name, or null
   */
  protected abstract String getManagedTenantName(Integer idTenant);

  private User getCurrentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }

  private void sendClientCreatedMail(User client, String plainPassword) throws MessagingException {
    String subject = messages.getMessage("g.client.created.subject", null, client.createAndGetJavaLocale());
    String body = messages.getMessage("g.client.created.body",
        new Object[] { client.getUsername(), plainPassword }, client.createAndGetJavaLocale());
    mailExternalService.sendSimpleMessageAsync(client.getUsername(), subject, body);
  }

  /**
   * Derives a tenant name (max 25 chars per {@link TenantBase}) from the client's e-mail local part.
   */
  private String deriveTenantName(String email) {
    String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
    return local.length() > 25 ? local.substring(0, 25) : local;
  }

  /**
   * Derives a unique nickname (2..30 chars per {@link User}) from the e-mail local part, appending a counter on clash.
   */
  private String deriveUniqueNickname(String email) {
    String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
    if (base.length() < 2) {
      base = "client";
    }
    if (base.length() > 26) {
      base = base.substring(0, 26);
    }
    String candidate = base;
    int counter = 1;
    while (userJpaRepository.findByNickname(candidate).isPresent()) {
      candidate = base + counter++;
    }
    return candidate;
  }
}
