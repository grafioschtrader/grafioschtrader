package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFDataJpaRepository.IUDFRepository;

public interface UDFMetadataSecurityJpaRepositoryCustom extends BaseRepositoryCustom<UDFMetadataSecurity>,
  IUDFRepository<UDFMetadataSecurity>{
  
  List<FieldDescriptorInputAndShowExtendedSecurity> getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(Integer idUser);
    
}
