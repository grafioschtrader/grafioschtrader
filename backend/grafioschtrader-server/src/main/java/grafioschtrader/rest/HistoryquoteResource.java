package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.DeleteHistoryquotesSuccess;
import grafioschtrader.dto.HistoryquotesWithMissings;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.SupportedCSVFormat;
import grafioschtrader.dto.SupportedCSVFormat.SupportedCSVFormats;
import grafioschtrader.dto.UploadHistoryquotesSuccess;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.priceupdate.historyquote.HistoryquoteImport;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.ta.TaFormDefinition;
import grafioschtrader.ta.TaIndicatorHelper;
import grafioschtrader.ta.TaIndicators;
import grafioschtrader.ta.TaTraceIndicatorData;
import grafioschtrader.ta.indicator.model.ShortMediumLongInputPeriod;
import grafioschtrader.types.HistoryquoteCreateType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;

@RestController
@RequestMapping(RequestMappings.HISTORYQUOTE_MAP)
@Tag(name = Historyquote.TABNAME, description = "Controller for historyquote")
public class HistoryquoteResource extends UpdateCreateDeleteAudit<Historyquote> {

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private ObjectMapper jacksonObjectMapper;

  @Autowired
  private Validator validator;

  @Operation(summary = "Returns youngest close price before a specified date", description = "", tags = {
      Historyquote.TABNAME })
  @GetMapping(value = "/{idSecuritycurrency}/{dateString}/{asTraded}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ISecuritycurrencyIdDateClose> getIdSecurityAndCertainOrOlderDay(
      @Parameter(description = "Id of security or currency pair", required = true) @PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "Date as string in format yyyymmdd", required = true) @PathVariable final String dateString,
      @Parameter(description = "Price at that time when true otherweise adjusted price", required = true) @PathVariable final boolean asTraded)
      throws ParseException {
    return new ResponseEntity<>(historyquoteJpaRepository.getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(
        idSecuritycurrency, dateString, asTraded), HttpStatus.OK);
  }

  @Operation(summary = "Returns historyquotes with its summary of missing quotes", description = "Missing prices were determined based on the trading calendar.", tags = {
      Historyquote.TABNAME })
  @GetMapping(value = "/securitycurrency/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public <S extends Securitycurrency<S>> ResponseEntity<HistoryquotesWithMissings<S>> getHistoryqoutesByIdSecuritycurrency(
      @Parameter(description = "Id of security or currency pair", required = true) @PathVariable final Integer idSecuritycurrency,
      @Parameter(description = "True if it is a currency pair", required = true) @RequestParam() final boolean isCurrencypair)
      throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(
        historyquoteJpaRepository.getHistoryqoutesByIdSecuritycurrencyWithMissing(idSecuritycurrency, isCurrencypair),
        HttpStatus.OK);
  }

  @Operation(summary = "Return the close prices of a security or currency pair", description = "Includes only date and close price", tags = {
      Historyquote.TABNAME })
  @GetMapping(value = "/securitycurrency/{idSecuritycurrency}/dateclose", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<IDateAndClose>> getDateCloseByIdSecuritycurrency(
      @Parameter(description = "Id of security or currency pair", required = true) @PathVariable final Integer idSecuritycurrency) {
    return new ResponseEntity<>(historyquoteJpaRepository.getHistoryquoteDateClose(idSecuritycurrency), HttpStatus.OK);
  }

  @Operation(summary = "Returns the meta data model for all tecnical indicators like SMA, EMA ", description = "May be used to for the UI to build a dynamic form", tags = {
      Historyquote.TABNAME })
  @GetMapping(value = "/alltaforms", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<TaIndicators, TaFormDefinition>> getAllTaForms() {
    return new ResponseEntity<>(TaIndicatorHelper.getTaFormMap(), HttpStatus.OK);
  }

  
  @Operation(summary = "Returns requested data for a tecnical indicator like SMA, EMA ", description = "", tags = {
      Historyquote.TABNAME })
  @PostMapping(value = "/{idSecuritycurrency}/taindicator/{taIndicator}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<TaTraceIndicatorData>> getTaWithShortMediumLongInputPeriod(
      @PathVariable final Integer idSecuritycurrency, 
      @Parameter(description = "The required tecnical indicator", required = true) @PathVariable final TaIndicators taIndicator,
      @RequestBody String dynamicModel) throws IOException {
    List<TaTraceIndicatorData> taTraceIndicatorData = new ArrayList<>();
    switch (taIndicator) {
    case SMA:
    case EMA:
      ShortMediumLongInputPeriod shortMediumLongInputPeriod = jacksonObjectMapper.readValue(dynamicModel,
          ShortMediumLongInputPeriod.class);
      taTraceIndicatorData = historyquoteJpaRepository.getTaWithShortMediumLongInputPeriod(idSecuritycurrency,
          taIndicator, shortMediumLongInputPeriod);
      break;
    }
    return new ResponseEntity<>(taTraceIndicatorData, HttpStatus.OK);
  }

  @Operation(summary = "Returns the supported separators for numbers and the date formats", description = "May be used to show this selection in the UI", tags = {
      Historyquote.TABNAME })
  @GetMapping(value = "/supportedcsvformat", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SupportedCSVFormats> getPossibleCSVFormats() {
    return new ResponseEntity<>(new SupportedCSVFormats(), HttpStatus.OK);
  }

  @Operation(summary = "Import delimited data to EOD", description = "", tags = { Historyquote.TABNAME })
  @PostMapping(value = "/{idSecuritycurrency}/uploadhistoryquotes", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UploadHistoryquotesSuccess> uploadPdfTransactions(@PathVariable() Integer idSecuritycurrency,
      @RequestParam("file") MultipartFile[] uploadFiles, @RequestParam(required = false) char decimalSeparator,
      @RequestParam(required = false) char thousandSeparator, @RequestParam(required = false) String dateFormat)
      throws Exception {

    HistoryquoteImport historyquoteImport = new HistoryquoteImport(historyquoteJpaRepository, validator);
    return new ResponseEntity<>(historyquoteImport.uploadHistoryquotes(idSecuritycurrency, uploadFiles,
        new SupportedCSVFormat(decimalSeparator, thousandSeparator, dateFormat)), HttpStatus.OK);
  }

  @Operation(summary = "Delete linear filled and/or manual imported quotes", description = "", tags = {
      Historyquote.TABNAME })
  @DeleteMapping(value = "/delete/{idSecuritycurrency}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<DeleteHistoryquotesSuccess> deleteHistoryquotesByCreateTypes(
      @Parameter(description = "Id of security or currency pair", required = true) @PathVariable Integer idSecuritycurrency,
      @Parameter(description = "Possible values 2 (MANUAL_IMPORTED) or 3 (FILLED_CLOSED_LINEAR_TRADING_DAY)", required = true) @RequestParam(value = "createTypes") List<Byte> historyquoteCreateTypesAsBytes) {
    return new ResponseEntity<>(
        historyquoteJpaRepository.deleteHistoryquotesByCreateTypes(idSecuritycurrency, historyquoteCreateTypesAsBytes),
        HttpStatus.OK);
  }

  @Operation(summary = "Delete single history quote", description = "", tags = { Historyquote.TABNAME })
  @DeleteMapping(value = "/{idHistoryquote}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteHistoryquote(@PathVariable final Integer idHistoryquote) {
    Optional<Historyquote> deletedHistoryquoteOpt = deleteById(idHistoryquote);
    historyquoteJpaRepository.afterDelete(deletedHistoryquoteOpt);
    return ResponseEntity.ok().build();
  }

  @Override
  protected ResponseEntity<Historyquote> updateSpecialEntity(User user, Historyquote historyquote) throws Exception {
    Auditable auditable = historyquoteJpaRepository.getParentSecurityCurrency(user, historyquote);
    return checkProposeChangeAndSave(user, historyquote, auditable, UserAccessHelper.hasHigherPrivileges(user));

  }

  @Override
  protected boolean hasRightsForEditingEntity(User user, Historyquote newEntity, Historyquote existingEntity,
      Auditable parentEntity) {
    if (existingEntity.getCreateType() != HistoryquoteCreateType.CALCULATED) {
      return UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, parentEntity);
    }
    return false;
  }

  @Override
  protected boolean hasRightsForDeleteEntity(User user, Historyquote historyquote) {
    if (historyquote.getCreateType() != HistoryquoteCreateType.CALCULATED) {
      Auditable auditable = historyquoteJpaRepository.getParentSecurityCurrency(user, historyquote);
      return UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, auditable);
    }
    return false;
  }

  @Override
  protected UpdateCreateJpaRepository<Historyquote> getUpdateCreateJpaRepository() {
    return historyquoteJpaRepository;
  }

}
