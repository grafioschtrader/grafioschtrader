import {Component, Input, OnInit} from '@angular/core';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {AppSettings} from '../../shared/app.settings';
import {ContentBase, SecuritycurrencyBaseInfoFields} from './securitycurrency.base.info.fields';
import {Security} from '../../entities/security';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {WatchlistTable} from './watchlist.table';
import {WatchlistService} from '../service/watchlist.service';
import {WatchlistHelper} from './watchlist.helper';

/**
 * Shows detailed information of a currency or instrument
 */
@Component({
    selector: 'securitycurrency-extended-info',
    templateUrl: '../view/securitycurrency.base.info.fields.html',
    standalone: false
})
export class SecuritycurrencyExtendedInfoComponent extends SecuritycurrencyBaseInfoFields implements OnInit {
  @Input() intradayUrl: string;
  @Input() historicalUrl: string;
  @Input() dividendUrl: string;
  @Input() splitUrl: string;
  @Input() feedConnectorsKV: { [id: string]: string };

  readonly QUOTATION_DATA = 'QUOTATION_DATA';

  constructor(watchlistService: WatchlistService,
    securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(watchlistService, securityService, translateService, gps);
  }

  ngOnInit(): void {
    super.initializeFields();
    this.addQuotationDataFields();
    this.translateHeadersAndColumns();

    this.content = new ContentExtendedInfo(this.intradayUrl, this.historicalUrl, this.dividendUrl, this.splitUrl,
      this.securitycurrency);
    this.createTranslatedValueStore([this.content]);
  }

  private addQuotationDataFields(): void {
    this.addFieldProperty(DataType.DateTimeNumeric, this.SECURITYCURRENCY + 'sTimestamp', 'TIMEDATE',
      {fieldsetName: this.QUOTATION_DATA});
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLast', 'LAST', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sChangePercentage', 'DAILY_CHANGE', {
      fieldsetName: this.QUOTATION_DATA, headerSuffix: '%', templateName: 'greenRed'
    });

    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sPrevClose', 'DAY_BEFORE_CLOSE', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sHigh', 'HIGH', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLow', 'LOW', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
    });
    this.addFieldProperty(DataType.NumericInteger, this.SECURITYCURRENCY + 'sVolume', 'VOLUME',
      {fieldsetName: this.QUOTATION_DATA});
  }

  protected override addHistoricalIntraday(): void {
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'idConnectorHistory', 'HISTORY_DATA_PROVIDER',
      {
        fieldsetName: 'HISTORY_SETTINGS', fieldValueFN: this.getFeedConnectorReadableName.bind(this)
      });
    this.addFieldPropertyFeqH(DataType.NumericRaw, this.SECURITYCURRENCY + 'retryHistoryLoad', {
      fieldsetName: 'HISTORY_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlHistoryExtend', {fieldsetName: 'HISTORY_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.URLString, 'historicalUrl', {
      fieldsetName: 'HISTORY_SETTINGS',
      templateName: 'long'
    });
    this.addFieldProperty(DataType.String, this.SECURITYCURRENCY + 'idConnectorIntra', 'INTRA_DATA_PROVIDER',
      {fieldsetName: 'INTRA_SETTINGS', fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addFieldPropertyFeqH(DataType.NumericRaw, this.SECURITYCURRENCY + 'retryIntraLoad', {
      fieldsetName: 'INTRA_SETTINGS'
    });
    this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlIntraExtend', {fieldsetName: 'INTRA_SETTINGS'});
    this.addFieldPropertyFeqH(DataType.URLString, WatchlistHelper.INTRADAY_URL, {fieldsetName: 'INTRA_SETTINGS', templateName: 'long'});
    this.addDividendGroup();
    this.addSplitGroup();
  }

  private addDividendGroup(): void {
    if (!(this.securitycurrency instanceof CurrencypairWatchlist)) {
      const s = <Security>this.securitycurrency;
      if (Security.canHaveDividendConnector(s.assetClass, !s.distributionFrequency
      || s.distributionFrequency === '' ? null : DistributionFrequency[s.distributionFrequency], !s.stockexchange.noMarketValue)) {
        this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'idConnectorDividend', {
          fieldsetName: AppSettings.DIVIDEND_SETTINGS, fieldValueFN: this.getFeedConnectorReadableName.bind(this)
        });
        this.addFieldPropertyFeqH(DataType.NumericRaw, this.SECURITYCURRENCY + 'retryDividendLoad', {
          fieldsetName: AppSettings.DIVIDEND_SETTINGS
        });
        this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlDividendExtend', {
          fieldsetName: AppSettings.DIVIDEND_SETTINGS
        });
        this.addFieldPropertyFeqH(DataType.URLString, 'dividendUrl', {
          fieldsetName: AppSettings.DIVIDEND_SETTINGS, templateName: 'long'
        });
      }
    }
  }

  private addSplitGroup(): void {
    if (!(this.securitycurrency instanceof CurrencypairWatchlist)) {
      const s = <Security>this.securitycurrency;
      if (Security.canHaveSplitConnector(s.assetClass, !s.stockexchange.noMarketValue)) {
        this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'idConnectorSplit', {
          fieldsetName: AppSettings.SPLIT_SETTINGS, fieldValueFN: this.getFeedConnectorReadableName.bind(this)
        });
        this.addFieldPropertyFeqH(DataType.NumericRaw, this.SECURITYCURRENCY + 'retrySplitLoad', {
          fieldsetName: AppSettings.SPLIT_SETTINGS
        });
        this.addFieldPropertyFeqH(DataType.String, this.SECURITYCURRENCY + 'urlSplitExtend', {
          fieldsetName: AppSettings.SPLIT_SETTINGS
        });
        this.addFieldPropertyFeqH(DataType.URLString, 'splitUrl', {
          fieldsetName: AppSettings.SPLIT_SETTINGS, templateName: 'long'
        });
      }
    }
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  protected readonly BusinessHelper = BusinessHelper;
}

class ContentExtendedInfo extends ContentBase {
  constructor(public intradayUrl: string, public historicalUrl: string,
    public dividendUrl, public splitUrl, securitycurrency: Securitycurrency) {
    super(securitycurrency);
  }
}
