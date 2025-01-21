import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {Security} from '../../entities/security';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {SecurityCurrencypairDerivedLinks} from '../../securitycurrency/model/security.currencypair.derived.links';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Currencypair} from '../../entities/currencypair';
import {Directive, Input} from '@angular/core';
import {Securitycurrency} from '../../entities/securitycurrency';
import {WatchlistHelper} from './watchlist.helper';
import {WatchlistService} from '../service/watchlist.service';
import {WatchlistTable} from './watchlist.table';

/**
 * Contains the definition of the basic fields as a group of an instrument.
 * Corresponding derivatives of this class will extend this information with their groups.
 */
@Directive()
export abstract class SecuritycurrencyBaseInfoFields extends SingleRecordConfigBase {
  @Input() securitycurrency: Security | CurrencypairWatchlist;

  readonly SECURITYCURRENCY = 'securitycurrency.';
  readonly BASE_PRODUCT_NAME = 'baseProductName';
  readonly DERIVED_DATA = 'DERIVED_DATA';
  protected baseInstrument: Security | CurrencypairWatchlist;
  protected additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};
  content: ContentBase;

  protected constructor(private watchlistService: WatchlistService,
    private securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  public handleLazyClick(event: Event, targetPage: string, content: ContentBase, field: ColumnConfig): void {
    event.preventDefault();
    const url = this.getValueByPath(content, field)
    WatchlistHelper.getDownloadLinkHistoricalIntra(url, targetPage, content.securitycurrency, field.field.endsWith(WatchlistHelper.INTRADAY_URL), this.watchlistService);
  }

  protected initializeFields(): void {
    if ((<Currencypair>this.securitycurrency).fromCurrency) {
      this.addCurrencypairFields();
    } else {
      this.addSecurityFields(<Security>this.securitycurrency);
    }
  }

  private addCurrencypairFields(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'fromCurrency', 'CURRENCY_FROM', {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'toCurrency', 'CURRENCY_TO', {fieldsetName: 'BASE_DATA'});
    this.addNoteField();
    this.addHistoricalIntraday();
  }

  getDerivedValues(dataobject: any, field: ColumnConfig, valueField: any): any {
    if (field.field === this.BASE_PRODUCT_NAME) {
      return this.baseInstrument ? this.baseInstrument.name : '';
    } else {
      return this.additionalInstruments[field.field] ? this.additionalInstruments[field.field].name : '';
    }
  }

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

  protected addHistoricalIntraday(): void {
  }

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

  private addNoteField(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'note', 'NOTE', {fieldsetName: 'BASE_DATA'});
  }

}

export abstract class ContentBase {
  protected constructor(public securitycurrency: Securitycurrency) {
  }
}
