package grafiosch.security.passkey;

import java.security.SecureRandom;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.repository.UserJpaRepository;

/**
 * Maps the WebAuthn user entity onto the {@code user} table: name is the e-mail address, display name the nickname, id
 * the user handle in {@code user.passkey_user_handle}. {@link #findByUsername(String)} returns an entity for every
 * existing user and creates the handle on first use, so the relying party operations never fall back to an entity of
 * their own, which would carry the e-mail address as display name. The handle stays for the life of the user, also
 * after the last passkey was deleted.
 */
@Component
public class PasskeyUserEntityRepository implements PublicKeyCredentialUserEntityRepository {
  private static final int HANDLE_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserJpaRepository users;

  public PasskeyUserEntityRepository(UserJpaRepository users) {
    this.users = users;
  }

  @Override
  public PublicKeyCredentialUserEntity findById(Bytes id) {
    Integer idUser = users.findIdUserByPasskeyUserHandle(id.getBytes());
    return idUser == null ? null : users.findById(idUser).map(user -> toEntity(user, id.getBytes())).orElse(null);
  }

  @Override
  public PublicKeyCredentialUserEntity findByUsername(String username) {
    return users.findByEmail(username).map(user -> toEntity(user, handleOf(user.getIdUser()))).orElse(null);
  }

  /**
   * The user handle of a user, created when the user has none. A parallel first registration writes at most one handle;
   * both read back the same value.
   */
  public byte[] handleOf(Integer idUser) {
    byte[] handle = users.findPasskeyUserHandle(idUser);
    if (handle == null) {
      byte[] created = new byte[HANDLE_BYTES];
      RANDOM.nextBytes(created);
      users.setPasskeyUserHandleIfAbsent(idUser, created);
      handle = users.findPasskeyUserHandle(idUser);
    }
    return handle;
  }

  /** The library calls this only for an entity it created itself, which {@link #findByUsername} never lets happen. */
  @Override
  public void save(PublicKeyCredentialUserEntity userEntity) {
    throw new UnsupportedOperationException("User entities are derived from the user table");
  }

  @Override
  public void delete(Bytes id) {
    throw new UnsupportedOperationException("The user handle stays for the life of the user");
  }

  private static PublicKeyCredentialUserEntity toEntity(User user, byte[] handle) {
    return ImmutablePublicKeyCredentialUserEntity.builder().name(user.getUsername()).displayName(user.getNickname())
        .id(new Bytes(handle)).build();
  }
}
