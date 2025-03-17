package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;
import grafioschtrader.dto.FieldDescriptorInputAndShowExtendedSecurity;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;

public interface UDFMetadataSecurityJpaRepositoryCustom extends BaseRepositoryCustom<UDFMetadataSecurity>,
  IUDFRepository<UDFMetadataSecurity>{
  
  List<FieldDescriptorInputAndShowExtendedSecurity> getFieldDescriptorByIdUserAndEveryUserExcludeDisabled(Integer idUser);
 
  void recreateUDFFieldsForEveryUser(List<IUDFForEveryUser> uDFForEveryUser);
}
