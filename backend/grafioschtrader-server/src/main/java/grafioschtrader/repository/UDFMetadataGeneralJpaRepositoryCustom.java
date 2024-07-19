package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dynamic.model.FieldDescriptorInputAndShowExtendedGeneral;
import grafioschtrader.entities.UDFMetadataGeneral;
import grafioschtrader.repository.UDFDataJpaRepository.IUDFRepository;

public interface UDFMetadataGeneralJpaRepositoryCustom
    extends BaseRepositoryCustom<UDFMetadataGeneral>, IUDFRepository<UDFMetadataGeneral> {

  List<FieldDescriptorInputAndShowExtendedGeneral> getFieldDescriptorByIdUserAndEveryUserForEntity(Integer idUser,
      String entity);
}
