package grafiosch.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UDFSpecialTypeDisableUser;
import grafiosch.entities.UDFSpecialTypeDisableUser.UDFSpecialTypeDisableUserId;

public interface UDFSpecialTypeDisableUserRepository
    extends JpaRepository<UDFSpecialTypeDisableUser, UDFSpecialTypeDisableUserId> {

  @Query("SELECT u.ID.udfSpecialType FROM UDFSpecialTypeDisableUser u WHERE u.id.idUser = ?1")
  Set<Byte> findByIdIdUser(Integer idUser);

}
