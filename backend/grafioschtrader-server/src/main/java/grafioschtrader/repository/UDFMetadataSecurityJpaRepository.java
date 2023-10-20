package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface UDFMetadataSecurityJpaRepository extends JpaRepository<UDFMetadataSecurity, Integer>,
    UDFMetadataSecurityJpaRepositoryCustom, UpdateCreateJpaRepository<UDFMetadataSecurity> {

  List<UDFMetadataSecurity> getAllByIdUser(Integer idUser);
}
