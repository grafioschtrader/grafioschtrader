import {Component, Input, OnInit} from '@angular/core';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SecurityCurrencypairDerivedLinks} from '../../securitycurrency/model/security.currencypair.derived.links';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {AppSettings} from '../../shared/app.settings';

/**
 * Shows detailed information of a currency or instrument
 */
@Component({
  selector: 'securitycurrency-extended-info',
  template: `
    <div class="fcontainer">
      <fieldset *ngFor="let fieldSetName of Object.keys(fieldSetGroups)" class="out-border fbox">
        <legend class="out-border-legend">{{fieldSetName | translate}}</legend>
        <div *ngFor="let field of fieldSetGroups[fieldSetName]" class="row">
          <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 showlabel" align="right">
            {{field.headerTranslated}}:
          </div>
          <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 nopadding wrap">
            <ng-container [ngSwitch]="field.templateName">
              <ng-container *ngSwitchCase="'greenRed'">
                              <span [style.color]='isValueByPathMinus(content, field)? "red": "green"'>
                                  {{getValueByPath(content, field)}}
                              </span>
              </ng-container>
              <ng-container *ngSwitchCase="'check'">
                <span><i [ngClass]="{'fa fa-check': getValueByPath(content, field)}" aria-hidden="true"></i></span>
              </ng-container>
              <ng-container *ngSwitchDefault>
                {{getValueByPath(content, field)}}
              </ng-container>
            </ng-container>
          </div>
        </div>
      </fieldset>
    </div>
  `
})
export class SecuritycurrencyExtendedInfoComponent extends SingleRecordConfigBase implements OnInit {
  @Input() securitycurrency: Security | Currencypair;
  @Input() intradayUrl: string;
  @Input() historicalUrl: string;
  @Input() dividendUrl: string;

  readonly SECURITYCURRENCY = 'securitycurrency.';
  readonly PERFORMANCE = 'PERFORMANCE';
  readonly DERIVED_DATA = 'DERIVED_DATA';
  readonly BASE_PRODUCT_NAME = 'baseProductName';
  content: Content;
  private baseInstrument: Security | CurrencypairWatchlist;
  private additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              private securityService: SecurityService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    if ((<Currencypair>this.securitycurrency).fromCurrency) {
      this.addCurrencypairFields();
    } else {
      this.addSecurityFields(<Security>this.securitycurrency);
    }
    this.addPerformanceFields();
    this.translateHeadersAndColumns();

    this.content = new Content(this.securitycurrency, this.intradayUrl, this.historicalUrl, this.dividendUrl);
    this.createTranslatedValueStore([this.content]);
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
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});

    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.categoryType', AppSettings.ASSETCLASS.toUpperCase(),
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT',
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'assetClass.subCategoryNLS.map.'
      + this.gps.getUserLang(),
      'SUB_ASSETCLASS', {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'stockexchange.name', AppSettings.STOCKEXCHANGE.toUpperCase(),
      {fieldsetName: 'BASE_DATA'});

    this.addFieldProperty(DataType.Boolean, this.SECURITYCURRENCY + 'isTenantPrivate', 'PRIVATE_SECURITY', {
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
      fieldsetName: 'BASE_DATA', templateName: 'greenRed', fieldValueFN: BusinessHelper.getDisplayLeverageFactor.bind(this)
    });
    this.addNoteField();
    !security.idLinkSecuritycurrency && this.addHistoricalIntraday();
  }

  private addPerformanceFields(): void {
    this.addFieldProperty(DataType.DateTimeNumeric, this.SECURITYCURRENCY + 'sTimestamp', 'TIMEDATE', {fieldsetName: this.PERFORMANCE});
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLast', 'LAST', {
      fieldsetName: this.PERFORMANCE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sChangePercentage', 'DAILY_CHANGE', {
      fieldsetName: this.PERFORMANCE, headerSuffix: '%', templateName: 'greenRed'
    });

    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sPrevClose', 'DAY_BEFORE_CLOSE', {
      fieldsetName: this.PERFORMANCE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sHigh', 'HIGH', {
      fieldsetName: this.PERFORMANCE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLow', 'LOW', {
      fieldsetName: this.PERFORMANCE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.NumericInteger, this.SECURITYCURRENCY + 'sVolume', 'VOLUME', {fieldsetName: this.PERFORMANCE});
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

  private addCurrencypairFields(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'fromCurrency', 'CURRENCY_FROM', {fieldsetName: 'BASE_DATA'});
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'toCurrency', 'CURRENCY_TO', {fieldsetName: 'BASE_DATA'});
    this.addNoteField();
    this.addHistoricalIntraday();
  }

  private addNoteField(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'note', 'NOTE', {fieldsetName: 'BASE_DATA'});
  }

  private addHistoricalIntraday(): void {
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlHistoryExtend',
      {fieldsetName: 'HISTORY_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'historicalUrl', {fieldsetName: 'HISTORY_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlIntraExtend', {fieldsetName: 'INTRA_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, 'intradayUrl', {fieldsetName: 'INTRA_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'idConnectorDividend', {
      fieldsetName: AppSettings
        .DIVIDEND_SETTINGS
    });
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlDividendExtend', {
      fieldsetName: AppSettings
        .DIVIDEND_SETTINGS
    });
    this.addFieldPropertyFeqH(DataType.String, 'dividendUrl', {
      fieldsetName: AppSettings
        .DIVIDEND_SETTINGS
    });
  }

}

class Content {
  constructor(public securitycurrency: Securitycurrency, public intradayUrl: string, public historicalUrl: string,
              public dividendUrl) {
  }
}
