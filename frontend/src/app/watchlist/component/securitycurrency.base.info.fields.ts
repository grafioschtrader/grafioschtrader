import {SingleRecordConfigBase} from '../../lib/datashowbase/single.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SecurityCurrencypairDerivedLinks} from '../../securitycurrency/model/security.currencypair.derived.links';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Currencypair} from '../../entities/currencypair';
import {Directive, Input} from '@angular/core';
import {Securitycurrency} from '../../entities/securitycurrency';
import {WatchlistHelper} from './watchlist.helper';
import {WatchlistService} from '../service/watchlist.service';

/**
 * Abstract directive class that contains the definition of the basic fields as a group of an instrument.
 * Corresponding derivatives of this class will extend this information with their groups.
 * Provides base functionality for displaying security and currency pair information in organized field groups.
 */
@Directive()
export abstract class SecuritycurrencyBaseInfoFields extends SingleRecordConfigBase {

  /** The security or currency pair to display information for */
  @Input() securitycurrency: Security | CurrencypairWatchlist;

  /** Prefix for accessing securitycurrency properties in field paths */
  readonly SECURITYCURRENCY = 'securitycurrency.';
  /** Field name for base product name in derived instruments */
  readonly BASE_PRODUCT_NAME = 'baseProductName';
  /** Fieldset name for derived data group */
  readonly DERIVED_DATA = 'DERIVED_DATA';
  /** Base instrument for derived securities */
  protected baseInstrument: Security | CurrencypairWatchlist;
  /** Additional instruments mapped by field name for derived securities */
  protected additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};
  /** Content object containing the security currency data for display */
  content: ContentBase;

  /**
   * Creates a new SecuritycurrencyBaseInfoFields instance.
   * @param watchlistService Service for watchlist operations and data retrieval
   * @param securityService Service for security-related operations
   * @param translateService Angular translation service for internationalization
   * @param gps Global parameter service for application-wide settings
   */
  protected constructor(private watchlistService: WatchlistService,
    private securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  /**
   * Handles lazy loading of external data provider links.
   * Prevents default event behavior and retrieves download links for historical or intraday data.
   * @param event The click event to handle
   * @param targetPage Target page identifier for the external link
   * @param content Content object containing the security currency data
   * @param field Column configuration containing field information
   */
  public handleLazyClick(event: Event, targetPage: string, content: ContentBase, field: ColumnConfig): void {
    event.preventDefault();
    const url = this.getValueByPath(content, field)
    WatchlistHelper.getDownloadLinkHistoricalIntra(url, targetPage, content.securitycurrency, field.field.endsWith(WatchlistHelper.INTRADAY_URL), this.watchlistService);
  }

  /**
   * Initializes field definitions based on the type of security currency.
   * Determines whether to add currency pair fields or security fields based on the presence of fromCurrency property.
   */
  protected initializeFields(): void {
    if ((<Currencypair>this.securitycurrency).fromCurrency) {
      this.addCurrencypairFields();
    } else {
      this.addSecurityFields(<Security>this.securitycurrency);
    }
  }

  /**
   * Adds field definitions specific to currency pairs.
   * Includes from/to currency fields, note field, and historical/intraday data fields.
   */
  private addCurrencypairFields(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'fromCurrency', 'CURRENCY_FROM', {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'toCurrency', 'CURRENCY_TO', {fieldsetName: 'BASE_DATA'});
    this.addNoteField();
    this.addHistoricalIntraday();
  }

  /**
   * Retrieves values for derived instrument fields.
   * Returns base instrument name or additional instrument names based on the field configuration.
   * @param dataobject The data object (unused in current implementation)
   * @param field Column configuration containing field information
   * @param valueField The raw field value (unused in current implementation)
   * @returns The instrument name or empty string if not found
   */
  getDerivedValues(dataobject: any, field: ColumnConfig, valueField: any): any {
    if (field.field === this.BASE_PRODUCT_NAME) {
      return this.baseInstrument ? this.baseInstrument.name : '';
    } else {
      return this.additionalInstruments[field.field] ? this.additionalInstruments[field.field].name : '';
    }
  }

  /**
   * Adds comprehensive field definitions for securities.
   * Includes asset class information, exchange details, dates, and other security-specific properties.
   * @param security The security object to create fields for
   */
  private addSecurityFields(security: Security): void {
    this.addDerivedFields(security);
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'name',
      {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(),
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT',
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.subCategoryNLS.map.'
      + this.gps.getUserLang(),
      'SUB_ASSETCLASS', {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'stockexchange.name', AppSettings.STOCKEXCHANGE.toUpperCase(),
      {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.Boolean, this.SECURITYCURRENCY + 'idTenantPrivate', 'PRIVATE_SECURITY', {
      fieldsetName: 'BASE_DATA', templateName: 'check'
    });
    if (!security.idLinkSecuritycurrency) {
      this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'isin', {fieldsetName: 'BASE_DATA'});
      this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'tickerSymbol', {fieldsetName: 'BASE_DATA'});
    }
    if (BusinessHelper.hasSecurityDenomination(security.assetClass, !security.stockexchange.noMarketValue)) {
      this.addFieldPropertyFeqH(DataType.NumericInteger, this.SECURITYCURRENCY + 'denomination',
        {fieldsetName: 'BASE_DATA'});
    }
    this.addFieldPropertyFeqH(DataType.DateString, this.SECURITYCURRENCY + 'activeFromDate',
      {fieldsetName: 'BASE_DATA'});
    this.addFieldPropertyFeqH(DataType.DateString, this.SECURITYCURRENCY + 'activeToDate',
      {fieldsetName: 'BASE_DATA'});
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'distributionFrequency',
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});
    this.addFieldPropertyFeqH(DataType.NumericRaw, this.SECURITYCURRENCY + 'leverageFactor', {
      fieldsetName: 'BASE_DATA',
      templateName: 'greenRed',
      fieldValueFN: BusinessHelper.getDisplayLeverageFactor.bind(this)
    });
    this.addNoteField();
    !security.idLinkSecuritycurrency && this.addHistoricalIntraday();
  }

  /**
   * Hook method for subclasses to add historical and intraday data fields.
   * Default implementation is empty, intended to be overridden by subclasses.
   */
  protected addHistoricalIntraday(): void {
  }

  /**
   * Adds field definitions for derived securities that are linked to base instruments.
   * Parses formula prices to identify additional instruments and creates corresponding fields.
   * @param security The derived security to create fields for
   */
  private addDerivedFields(security: Security): void {
    if (security.idLinkSecuritycurrency) {
      this.addFieldPropertyFeqH(DataType.String, this.BASE_PRODUCT_NAME, {
        fieldValueFN: this.getDerivedValues.bind(this),
        fieldsetName: this.DERIVED_DATA
      });
      this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'formulaPrices', {fieldsetName: this.DERIVED_DATA});

      let match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(security.formulaPrices);
      while (match != null) {
        if (match[1] !== SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.charAt(0)) {
          const varName = match[1];
          const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + varName;
          this.addFieldProperty(DataType.String, fieldName, 'ADDITIONAL_INSTRUMENT_NAME', {
            fieldValueFN: this.getDerivedValues.bind(this),
            fieldsetName: this.DERIVED_DATA,
            headerSuffix: `(${varName})`
          });
        }
        match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(security.formulaPrices);
      }
      this.securityService.getDerivedInstrumentsLinksForSecurity(security.idSecuritycurrency).subscribe(
        (scdl: SecurityCurrencypairDerivedLinks) => {
          this.baseInstrument = SecurityCurrencypairDerivedLinks.getBaseInstrument(scdl, security.idLinkSecuritycurrency);
          this.additionalInstruments = SecurityCurrencypairDerivedLinks.getAdditionalInstrumentsForExistingSecurity(scdl);
        });
    }
  }

  /** Adds a note field to the BASE_DATA fieldset for displaying additional notes or comments */
  private addNoteField(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'note', 'NOTE', {fieldsetName: 'BASE_DATA'});
  }

}

/**
 * Abstract base class for content objects that contain security currency data for display.
 * Provides a common structure for different types of content implementations.
 */
export abstract class ContentBase {
  /**
   * Creates a new ContentBase instance.
   * @param securitycurrency The security currency object containing the data to display
   */
  protected constructor(public securitycurrency: Securitycurrency) {
  }
}
