package grafiosch.security.passkey;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import grafiosch.entities.UserPasskey;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasskeyJpaRepository;

/**
 * The credential store of the relying party operations over {@code user_passkey}. The library calls
 * {@link #save(CredentialRecord)} for a new credential and again after every verified assertion; the two views below
 * give that call its meaning. The registration view inserts. The sign-in view only updates the usage, enforcing the
 * signature counter, and refuses when no row changed.
 */
public abstract sealed class PasskeyCredentialRepository implements UserCredentialRepository {
  protected final UserPasskeyJpaRepository passkeys;
  protected final UserJpaRepository users;

  protected PasskeyCredentialRepository(UserPasskeyJpaRepository passkeys, UserJpaRepository users) {
    this.passkeys = passkeys;
    this.users = users;
  }

  @Override
  public CredentialRecord findByCredentialId(Bytes credentialId) {
    return passkeys.findByCredentialId(credentialId.getBytes())
        .map(passkey -> toRecord(passkey, users.findPasskeyUserHandle(passkey.getIdUser()))).orElse(null);
  }

  @Override
  public List<CredentialRecord> findByUserId(Bytes userId) {
    Integer idUser = users.findIdUserByPasskeyUserHandle(userId.getBytes());
    return idUser == null ? Collections.emptyList()
        : passkeys.findByIdUserOrderByCreatedAt(idUser).stream()
            .map(passkey -> (CredentialRecord) toRecord(passkey, userId.getBytes())).toList();
  }

  @Override
  public void delete(Bytes credentialId) {
    throw new UnsupportedOperationException("Passkeys are deleted through CredentialChangeService");
  }

  static CredentialRecord toRecord(UserPasskey passkey, byte[] userHandle) {
    var created = passkey.getCreatedAt().toInstant(ZoneOffset.UTC);
    return ImmutableCredentialRecord.builder().credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(new Bytes(passkey.getCredentialId())).userEntityUserId(new Bytes(userHandle))
        .publicKey(new ImmutablePublicKeyCose(passkey.getPublicKey())).signatureCount(passkey.getSignatureCount())
        .uvInitialized(passkey.isUvInitialized()).transports(transports(passkey.getTransports()))
        .backupEligible(passkey.isBackupEligible()).backupState(passkey.isBackupState())
        .attestationObject(new Bytes(passkey.getAttestationObject()))
        .attestationClientDataJSON(
            passkey.getAttestationClientDataJson() == null ? null : new Bytes(passkey.getAttestationClientDataJson()))
        .label(passkey.getLabel()).created(created)
        .lastUsed(passkey.getLastUsedAt() == null ? created : passkey.getLastUsedAt().toInstant(ZoneOffset.UTC))
        .build();
  }

  private static Set<AuthenticatorTransport> transports(String text) {
    return text == null || text.isBlank() ? Collections.emptySet()
        : Arrays.stream(text.split(",")).map(AuthenticatorTransport::valueOf).collect(Collectors.toUnmodifiableSet());
  }

  static String transports(Set<AuthenticatorTransport> transports) {
    return transports == null || transports.isEmpty() ? null
        : transports.stream().map(AuthenticatorTransport::getValue).sorted().collect(Collectors.joining(","));
  }

  /** Inserts a new credential; called by {@code registerCredential} inside the transaction of the registration. */
  public static final class Registration extends PasskeyCredentialRepository {
    public Registration(UserPasskeyJpaRepository passkeys, UserJpaRepository users) {
      super(passkeys, users);
    }

    @Override
    public void save(CredentialRecord record) {
      Integer idUser = users.findIdUserByPasskeyUserHandle(record.getUserEntityUserId().getBytes());
      if (idUser == null || record.getAttestationObject() == null) {
        throw new IllegalArgumentException("Unknown user handle or missing attestation object");
      }
      passkeys.save(new UserPasskey(idUser, record.getLabel(), record.getCredentialId().getBytes(),
          record.getPublicKey().getBytes(), record.getSignatureCount(), record.isUvInitialized(),
          record.isBackupEligible(), record.isBackupState(), transports(record.getTransports()),
          record.getAttestationObject().getBytes(),
          record.getAttestationClientDataJSON() == null ? null : record.getAttestationClientDataJSON().getBytes(),
          LocalDateTime.now(ZoneOffset.UTC).withNano(0)));
    }
  }

  /** Records the usage after a verified assertion; a counter that went backwards or a deleted passkey is refused. */
  public static final class SignIn extends PasskeyCredentialRepository {
    public SignIn(UserPasskeyJpaRepository passkeys, UserJpaRepository users) {
      super(passkeys, users);
    }

    @Override
    public void save(CredentialRecord record) {
      if (passkeys.recordUsage(record.getCredentialId().getBytes(), record.getSignatureCount(),
          LocalDateTime.now(ZoneOffset.UTC).withNano(0)) != 1) {
        throw new BadCredentialsException("Signature counter did not increase or the passkey no longer exists");
      }
    }
  }
}
