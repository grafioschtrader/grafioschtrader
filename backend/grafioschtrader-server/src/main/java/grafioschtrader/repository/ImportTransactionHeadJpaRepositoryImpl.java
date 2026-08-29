package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.service.DailyLimitService;
import grafiosch.service.EntityLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.platform.GenericTransactionImport;
import grafioschtrader.platform.IPlatformTransactionImport;
import grafioschtrader.platformimport.ImportTransactionHelper;
import grafioschtrader.repository.ImportTransactionPosJpaRepositoryImpl.CreatedTransactionsResult;
import grafioschtrader.repository.ImportTransactionPosJpaRepositoryImpl.SavedImpPosAndTransaction;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TemplateFormatType;
import jakarta.transaction.Transactional;

public class ImportTransactionHeadJpaRepositoryImpl extends BaseRepositoryImpl<ImportTransactionHead>
    implements ImportTransactionHeadJpaRepositoryCustom {

  @Autowired
  private ImportTransactionHeadJpaRepository importTransactionHeadJpaRepository;

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  private DailyLimitService dailyLimitService;

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Autowired
  private ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired(required = false)
  public List<IPlatformTransactionImport> platformTransactionImportList = new ArrayList<>();

  @Override
  public ImportTransactionHead saveOnlyAttributes(ImportTransactionHead importTransactionHead,
      ImportTransactionHead existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {

    // An import head is tenant private, so UpdateCreate.createEntity takes the tenant branch and never reaches the
    // generic daily check. The counter is written by the resource on create, update and delete alike, so the budget
    // is checked here for both directions. The lifetime cap needs nothing here: it is registered as checked on the
    // generic create path.
    checkDailyLimit();
    ImportTransactionHead createImportTransactionHead = importTransactionHead;
    if (existingEntity != null) {
      createImportTransactionHead = existingEntity;
      createImportTransactionHead.setName(importTransactionHead.getName());
      createImportTransactionHead.setNote(importTransactionHead.getNote());
      createImportTransactionHead.setUseGtPlatform(importTransactionHead.isUseGtPlatform());
    }
    if (createImportTransactionHead.isUseGtPlatform()) {
      checkGtPlatformConfigured(createImportTransactionHead.getIdTenant());
    }
    return importTransactionHeadJpaRepository.save(createImportTransactionHead);
  }

  /** Checks today's CUD budget of the acting user for import heads. Skipped when there is no authenticated user. */
  private void checkDailyLimit() {
    User user = entityLimitService.getCurrentUserOrNull();
    if (user != null) {
      dailyLimitService.check(user, ImportTransactionHead.class.getSimpleName(), 1);
    }
  }

  /**
   * Ensures the Grafioschtrader import templates are available for this import head; required whenever the head carries
   * the use GT platform flag. Two independent settings have to agree: an administrator must have chosen the import
   * platform holding those templates for this instance, and the tenant must have opted in.
   *
   * @param idTenant the tenant of the import head
   * @return the instance wide Grafioschtrader import platform ID
   */
  private Integer checkGtPlatformConfigured(Integer idTenant) {
    Integer idGtImportPlatform = globalparametersService.getGtImportPlatformId();
    if (idGtImportPlatform == null || !tenantJpaRepository.getReferenceById(idTenant).isUseGtImportTemplates()) {
      throw new DataViolationException("use.gt.platform", "gt.imphead.gtplatform.notconfigured", null);
    }
    return idGtImportPlatform;
  }

  @Override
  @Modifying
  @Transactional
  public SuccessFailedDirectImportTransaction uploadPdfFileSecurityAccountTransactions(Integer idSecuritycashaccount,
      MultipartFile[] uploadFiles, boolean useGtPlatform) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Securityaccount securityaccount = this.securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(idSecuritycashaccount, user.getIdTenant());
    if (securityaccount != null) {
      // The head is written with a plain save() rather than through UpdateCreate, so neither the lifetime cap nor the
      // daily budget would be consumed here. On a fully successful direct import the head is deleted again further
      // down, which is why only the daily budget is permanently spent by this path.
      checkHeadLimits();
      checkRoomForOneFurtherPosition();
      ImportTransactionHead importTransactionHead = new ImportTransactionHead(user.getIdTenant(), securityaccount,
          LocalDateTime.now().toString(), "Computer generated");
      importTransactionHead.setUseGtPlatform(useGtPlatform);
      importTransactionHead = importTransactionHeadJpaRepository.save(importTransactionHead);
      dailyLimitService.log(user.getIdUser(), ImportTransactionHead.class.getSimpleName(), OperationType.ADD, 1);
      this.getTemplateReadFilesAndSaveAsImport(importTransactionHead, uploadFiles, null);

      List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
          .findByIdTransactionHeadAndIdTenant(importTransactionHead.getIdTransactionHead(), user.getIdTenant());
      Optional<ImportTransactionPos> itpOpt = importTransactionPosList.stream()
          .filter(importTransactionPos -> !importTransactionPos.isReadyForTransaction()).findFirst();
      if (itpOpt.isPresent()) {
        return new SuccessFailedDirectImportTransaction(importTransactionHead.getIdTransactionHead());
      } else {
        return createRealTransactions(importTransactionHead, importTransactionPosList);
      }

    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  /**
   * Every pdf import is ready for transaction
   */
  private SuccessFailedDirectImportTransaction createRealTransactions(ImportTransactionHead importTransactionHead,
      List<ImportTransactionPos> importTransactionPosList) {
    CreatedTransactionsResult result = importTransactionPosJpaRepository
        .createAndSaveTransactionsFromImpPos(importTransactionPosList, null);
    List<SavedImpPosAndTransaction> savedImpPosAndTransactions = result.savedImpPosAndTransactions;
    Optional<ImportTransactionPos> itpErrorOpt = importTransactionPosList.stream()
        .filter(itp -> itp.getTransactionError() != null).findFirst();
    if (itpErrorOpt.isPresent()) {
      // Failed to create a transaction
      return new SuccessFailedDirectImportTransaction(importTransactionHead.getIdTransactionHead());
    } else {
      // An import either happens in full or not at all: a batch that would breach the total transaction limit is
      // rejected before the first write, so reaching this point means every position was imported.
      importTransactionPosJpaRepository.deleteAll(
          savedImpPosAndTransactions.stream().map(spat -> spat.importTransactionPos).collect(Collectors.toList()));
      importTransactionHeadJpaRepository.delete(importTransactionHead);
      int noOfDifferentSecurities = (int) savedImpPosAndTransactions.stream()
          .map(spat -> spat.transaction.getSecurity().getIdSecuritycurrency()).distinct().count();
      return new SuccessFailedDirectImportTransaction(savedImpPosAndTransactions.size(), noOfDifferentSecurities);
    }
  }

  @Override
  public void uploadCsvPdfTxtFileSecurityAccountTransactions(Integer idTransactionHead, MultipartFile[] uploadFiles,
      Integer idTransactionImportTemplate) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    ImportTransactionHead importTransactionHead = importTransactionHeadJpaRepository
        .getReferenceById(idTransactionHead);
    if (user.getIdTenant().equals(importTransactionHead.getIdTenant())) {
      checkRoomForOneFurtherPosition();
      this.getTemplateReadFilesAndSaveAsImport(importTransactionHead, uploadFiles, idTransactionImportTemplate);
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

  }

  private void getTemplateReadFilesAndSaveAsImport(ImportTransactionHead importTransactionHead,
      MultipartFile[] uploadFiles, Integer idTransactionImportTemplate) throws Exception {
    SingleMultiTemplateFormatType singleMultiTemplateFormatType = getTemplateFormatTypeOfUpload(uploadFiles);

    if (singleMultiTemplateFormatType != null) {
      ImportTransactionPlatform importTransactionPlatform = resolveImportTransactionPlatform(importTransactionHead);

      List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
          .findByIdTransactionImportPlatformAndTemplateFormatTypeOrderByTemplatePurpose(
              importTransactionPlatform.getIdTransactionImportPlatform(),
              singleMultiTemplateFormatType.templateFormatType.getValue());

      Optional<IPlatformTransactionImport> pti = platformTransactionImportList.stream()
          .filter(platformTransactionImport -> platformTransactionImport.getID()
              .equals(importTransactionPlatform.getIdCsvImportImplementation()))
          .findFirst();

      if (pti.isPresent()) {
        readUploadedFilesAndSaveAsImport(importTransactionHead, importTransactionTemplateList, pti.get(), uploadFiles,
            singleMultiTemplateFormatType, idTransactionImportTemplate);
      } else {
        // Use Generic
        IPlatformTransactionImport ptiGeneric = new GenericTransactionImport();
        readUploadedFilesAndSaveAsImport(importTransactionHead, importTransactionTemplateList, ptiGeneric, uploadFiles,
            singleMultiTemplateFormatType, idTransactionImportTemplate);
      }
    }
  }

  /**
   * Resolves the import platform whose templates parse the uploaded documents. With the head's use GT platform flag
   * set, the instance's Grafioschtrader import platform is used (the GT platform has no platform specific import
   * implementation, so the generic import applies); otherwise the securities account's trading platform mapping, which
   * must exist in that case.
   *
   * @param importTransactionHead the import head of this upload
   * @return the platform providing the import templates
   */
  private ImportTransactionPlatform resolveImportTransactionPlatform(ImportTransactionHead importTransactionHead) {
    if (importTransactionHead.isUseGtPlatform()) {
      Integer idGtImportPlatform = checkGtPlatformConfigured(importTransactionHead.getIdTenant());
      return importTransactionPlatformJpaRepository.getReferenceById(idGtImportPlatform);
    }
    ImportTransactionPlatform importTransactionPlatform = importTransactionHead.getSecurityaccount()
        .getTradingPlatformPlan().getImportTransactionPlatform();
    if (importTransactionPlatform == null) {
      throw new DataViolationException("platform", "gt.imphead.platform.missing", null);
    }
    return importTransactionPlatform;
  }

  private SingleMultiTemplateFormatType getTemplateFormatTypeOfUpload(MultipartFile[] uploadFiles) {
    if (uploadFiles.length > 0) {
      String ending = uploadFiles[0].getOriginalFilename().toLowerCase();
      if (ending.endsWith(ImportTransactionHelper.CSV_FILE_NAME_ENDING)) {
        return new SingleMultiTemplateFormatType(TemplateFormatType.CSV, uploadFiles.length == 1, false);
      } else if (ending.endsWith(ImportTransactionHelper.TXT_FILE_NAME_ENDING)
          || ending.endsWith(ImportTransactionHelper.PDF_FILE_NAME_ENDING)) {
        return new SingleMultiTemplateFormatType(TemplateFormatType.PDF, uploadFiles.length == 1,
            ending.endsWith(ImportTransactionHelper.TXT_FILE_NAME_ENDING));
      }
    }
    return null;
  }

  private void readUploadedFilesAndSaveAsImport(ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList, IPlatformTransactionImport pti,
      MultipartFile[] uploadFiles, SingleMultiTemplateFormatType singleMultiTemplateFormatType,
      Integer idTransactionImportTemplate) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (singleMultiTemplateFormatType.singleFile) {
      if (singleMultiTemplateFormatType.gtTransformed) {
        // Import a GT (Grafiosch Transform) Transform txt file
        pti.importGTTransform(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      } else if (singleMultiTemplateFormatType.templateFormatType == TemplateFormatType.CSV) {
        // Import a csv file with many transaction
        pti.importCSV(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale(), idTransactionImportTemplate);
      } else {
        // import single pdf
        pti.importSinglePdfAsPdf(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      }
    } else {
      // 2 files and more
      if (singleMultiTemplateFormatType.templateFormatType == TemplateFormatType.PDF) {
        pti.importMultiplePdfAsPdf(importTransactionHead, uploadFiles, importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      } else {
        // Multiple files were uploaded but the leading file is a CSV. CSV import supports only a single file (one file
        // carries many transactions); only PDF/TXT support multi-file batches.
        throw new GeneralNotTranslatedWithArgumentsException("gt.import.csv.single.file", null);
      }
    }
  }

  static class SingleMultiTemplateFormatType {
    public final TemplateFormatType templateFormatType;
    public final boolean singleFile;
    public final boolean gtTransformed;

    public SingleMultiTemplateFormatType(TemplateFormatType templateFormatType, boolean singleFile,
        boolean gtTransformed) {

      this.templateFormatType = templateFormatType;
      this.singleFile = singleFile;
      this.gtTransformed = gtTransformed;
    }
  }

  /**
   * Enforces the lifetime cap and the daily budget of an import head created outside {@code UpdateCreate}.
   *
   * @throws SecurityException when the tenant already holds the maximum number of import heads
   */
  private void checkHeadLimits() {
    User user = entityLimitService.getCurrentUserOrNull();
    if (user != null) {
      if (!entityLimitService.fitsWithinLimit(user, LimitKeyConfig.KEY_IMPORT_TRANSACTION_HEAD, null, 1)) {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
      }
      dailyLimitService.check(user, ImportTransactionHead.class.getSimpleName(), 1);
    }
  }

  /**
   * Rejects an upload before a single document is parsed when there is no room left for even one further position.
   *
   * <p>
   * How many positions a document yields is only known after it has been parsed, so the caps themselves are enforced
   * per row in {@code ImportTransactionPosJpaRepositoryImpl.saveNewPosWithLimitCheck}. This pre-check exists so that a
   * tenant that is already at its ceiling gets a clean refusal instead of parsing a whole batch to fail on its first
   * row.
   * </p>
   *
   * @throws SecurityException when the tenant total of import positions is exhausted
   */
  private void checkRoomForOneFurtherPosition() {
    User user = entityLimitService.getCurrentUserOrNull();
    if (user != null && !entityLimitService.fitsWithinLimit(user, LimitKeyConfig.KEY_IMPORT_TRANSACTION_POS, null, 1)) {
      throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
    }
  }

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return importTransactionHeadJpaRepository.deleteByIdTransactionHeadAndIdTenant(id, idTenant);
  }

  public static class SuccessFailedDirectImportTransaction {
    public Integer idTransactionHead;
    public Integer noOfImportedTransactions;
    public Integer noOfDifferentSecurities;
    public boolean failed = true;

    public SuccessFailedDirectImportTransaction(Integer idTransactionHead) {
      this.idTransactionHead = idTransactionHead;
    }

    public SuccessFailedDirectImportTransaction(Integer noOfImportedTransactions, Integer noOfDifferentSecurities) {
      this.noOfImportedTransactions = noOfImportedTransactions;
      this.noOfDifferentSecurities = noOfDifferentSecurities;
      this.failed = false;
    }

  }

}
