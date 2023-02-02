package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.csv.TemplateIdPurposeCsv;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface ImportTransactionTemplateJpaRepository extends JpaRepository<ImportTransactionTemplate, Integer>,
    ImportTransactionTemplateJpaRepositoryCustom, UpdateCreateJpaRepository<ImportTransactionTemplate> {

  @Query(nativeQuery = true)
  List<TemplateIdPurposeCsv> getTemplateIdPurposeCsv(Integer idTransactionImportPlatform);

  Optional<ImportTransactionTemplate> findByIdTransactionImportPlatformAndTemplateCategoryAndTemplateFormatTypeAndValidSinceAndTemplateLanguage(
      Integer idTransactionImportPlatform, byte templateCategory, byte templateFormatType, Date validSince,
      String templateLanguage);

  List<ImportTransactionTemplate> findByIdTransactionImportPlatformOrderByTemplatePurpose(
      Integer idTransactionImportPlatform);

  List<ImportTransactionTemplate> findByIdTransactionImportPlatformAndTemplateFormatTypeOrderByTemplatePurpose(
      Integer idTransactionImportPlatform, byte templateFormatType);

  @Query(nativeQuery = true)
  List<ImportTransactionTemplate> getImportTemplateByImportTransPos(Integer idTransactionHead, Integer idTenant);
}
