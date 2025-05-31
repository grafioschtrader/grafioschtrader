package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.csv.TemplateIdPurposeCsv;

public interface ImportTransactionTemplateJpaRepository extends JpaRepository<ImportTransactionTemplate, Integer>,
    ImportTransactionTemplateJpaRepositoryCustom, UpdateCreateJpaRepository<ImportTransactionTemplate> {

  Optional<ImportTransactionTemplate> findByIdTransactionImportPlatformAndTemplateCategoryAndTemplateFormatTypeAndValidSinceAndTemplateLanguage(
      Integer idTransactionImportPlatform, byte templateCategory, byte templateFormatType, Date validSince,
      String templateLanguage);

  List<ImportTransactionTemplate> findByIdTransactionImportPlatformOrderByTemplatePurpose(
      Integer idTransactionImportPlatform);

  List<ImportTransactionTemplate> findByIdTransactionImportPlatformAndTemplateFormatTypeOrderByTemplatePurpose(
      Integer idTransactionImportPlatform, byte templateFormatType);

  /**
   * Fetches template import metadata (template ID, purpose and parsed templateId) for all CSV-format templates of the
   * specified import platform.
   *
   * @param idTransactionImportPlatform the ID of the import‐transaction platform
   * @return a list of {@link TemplateIdPurposeCsv} projections
   */
  @Query(nativeQuery = true)
  List<TemplateIdPurposeCsv> getTemplateIdPurposeCsv(Integer idTransactionImportPlatform);

  /**
   * Retrieves all distinct import templates linked to the given import transaction header and its positions. ID of
   * Tenant is for security reason.
   *
   * @param idTransactionHead the ID of the transaction‐position header
   * @param idTenant          the tenant ID
   * @return a list of matching {@link ImportTransactionTemplate} entities
   */
  @Query(nativeQuery = true)
  List<ImportTransactionTemplate> getImportTemplateByImportTransPos(Integer idTransactionHead, Integer idTenant);
}
