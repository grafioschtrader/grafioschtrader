package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.text.ParseException;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestGTMappings.GLOBALPARAMETERS_GT_MAP)
@Tag(name = Globalparameters.TABNAME, description = "Controller for global parameters")
public class GlobalparametersGTResource {

  @Autowired
  private GlobalparametersService globalparametersService;

  @Operation(summary = "Return waiting time in seconds before the next intraday price query of the watch list", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/updatetimeout", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Integer> getIntraUpdateQuotesTimeoutSeconds() {
    return new ResponseEntity<>(globalparametersService.getWatchlistIntradayUpdateTimeout(), HttpStatus.OK);
  }

  @Operation(summary = "Return start date of historical data", description = "", tags = { Globalparameters.TABNAME })
  @GetMapping(value = "/startfeeddate", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Date> getStartFeedDate() throws ParseException {
    return new ResponseEntity<>(globalparametersService.getStartFeedDate(), HttpStatus.OK);
  }

  @Operation(summary = "Returns the possible currencies as it can be used in HTML option", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/currencies", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getCurrencies() {
    return new ResponseEntity<>(globalparametersService.getCurrencies(), HttpStatus.OK);
  }

  @Operation(summary = "Return of the possible combination of asset class to financial instrument.", description = "", tags = {
      Globalparameters.TABNAME })
  @GetMapping(value = "/possibleassetclassspezinstrument", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<EnumMap<AssetclassType, SpecialInvestmentInstruments[]>> getPossibleAssetclassInstrumentMap() {
    return new ResponseEntity<>(Assetclass.possibleInstrumentsMap, HttpStatus.OK);
  }
}
