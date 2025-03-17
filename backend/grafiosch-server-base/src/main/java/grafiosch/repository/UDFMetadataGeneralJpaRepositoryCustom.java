package grafiosch.repository;

import java.util.List;

import grafiosch.dynamic.model.FieldDescriptorInputAndShowExtendedGeneral;
import grafiosch.entities.UDFMetadataGeneral;
import grafiosch.repository.UDFDataJpaRepository.IUDFRepository;

public interface UDFMetadataGeneralJpaRepositoryCustom
    extends BaseRepositoryCustom<UDFMetadataGeneral>, IUDFRepository<UDFMetadataGeneral> {

  List<FieldDescriptorInputAndShowExtendedGeneral> getFieldDescriptorByIdUserAndEveryUserForEntity(Integer idUser,
      String entity);
}
