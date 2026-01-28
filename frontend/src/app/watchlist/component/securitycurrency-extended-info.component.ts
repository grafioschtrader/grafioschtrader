import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {Securitycurrency} from '../../entities/securitycurrency';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {AppSettings} from '../../shared/app.settings';
import {ContentBase, SecuritycurrencyBaseInfoFields} from './securitycurrency.base.info.fields';
import {Security} from '../../entities/security';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {WatchlistService} from '../service/watchlist.service';
import {WatchlistHelper} from './watchlist.helper';
import {CommonModule} from '@angular/common';
import {TooltipModule} from 'primeng/tooltip';
import {ReplacePipe} from '../../shared/pipe/replace.pipe';
import {AppHelper} from '../../lib/helper/app.helper';

/**
 * Component that displays detailed information for a currency or financial instrument including quotation data,
 * feed connectors, historical/intraday settings, and dividend/split configurations. Extends the base security
 * currency info fields with comprehensive data provider and feed management capabilities.
 */
@Component({
  selector: 'securitycurrency-extended-info',
  templateUrl: '../view/securitycurrency.base.info.fields.html',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    TooltipModule,
    ReplacePipe
  ]
})
export class SecuritycurrencyExtendedInfoComponent extends SecuritycurrencyBaseInfoFields implements OnInit, OnChanges {
  /** URL for intraday price data feed */
  @Input() intradayUrl: string;
  /** URL for historical price data feed */
  @Input() historicalUrl: string;
  /** URL for dividend data feed */
  @Input() dividendUrl: string;
  /** URL for stock split data feed */
  @Input() splitUrl: string;
  /** Key-value mapping of feed connector IDs to human-readable names */
  @Input() feedConnectorsKV: { [id: string]: string };
  /** Fieldset name constant for quotation data grouping */
  readonly QUOTATION_DATA = 'QUOTATION_DATA';

  /**
   * Creates an instance of SecuritycurrencyExtendedInfoComponent.
   * @param watchlistService Service for watchlist operations and data retrieval
   * @param securityService Service for security-related operations
   * @param translateService Service for internationalization and translation
   * @param gps Global parameter service for application-wide settings
   */
  constructor(watchlistService: WatchlistService,
    securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(watchlistService, securityService, translateService, gps);
  }

  /** Flag to track if fields have been initialized */
  private fieldsInitialized = false;

  /**
   * Initializes the component by setting up field definitions, adding quotation data fields,
   * translating headers and columns, and creating the content model with extended information.
   */
  ngOnInit(): void {
    super.initializeFields();
    this.addQuotationDataFields();
    this.translateHeadersAndColumns();
    this.content = new ContentExtendedInfo(this.intradayUrl, this.historicalUrl, this.dividendUrl, this.splitUrl,
      this.securitycurrency);
    this.createTranslatedValueStore([this.content]);
    this.fieldsInitialized = true;
  }

  /**
   * Handles changes to input properties. Updates the content model when URL inputs change
   * after initial setup, ensuring the display reflects the latest URL values.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (this.fieldsInitialized && this.content) {
      const urlChanged = changes['intradayUrl'] || changes['historicalUrl']
        || changes['dividendUrl'] || changes['splitUrl'];
      if (urlChanged) {
        // Update the content object with new URL values
        (this.content as ContentExtendedInfo).intradayUrl = this.intradayUrl;
        (this.content as ContentExtendedInfo).historicalUrl = this.historicalUrl;
        (this.content as ContentExtendedInfo).dividendUrl = this.dividendUrl;
        (this.content as ContentExtendedInfo).splitUrl = this.splitUrl;
      }
    }
  }

  /**
   * Adds field definitions for real-time quotation data including timestamp, last price, daily change,
   * previous close, high/low values, and trading volume. All fields are grouped under QUOTATION_DATA fieldset.
   */
  private addQuotationDataFields(): void {
    this.addFieldProperty(DataType.DateTimeNumeric, this.SECURITYCURRENCY + 'sTimestamp', 'TIMEDATE',
      {fieldsetName: this.QUOTATION_DATA});
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLast', 'LAST', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sChangePercentage', 'DAILY_CHANGE', {
      fieldsetName: this.QUOTATION_DATA, headerSuffix: '%', templateName: 'greenRed'
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sPrevClose', 'DAY_BEFORE_CLOSE', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sHigh', 'HIGH', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldProperty(DataType.Numeric, this.SECURITYCURRENCY + 'sLow', 'LOW', {
      fieldsetName: this.QUOTATION_DATA, maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldProperty(DataType.NumericInteger, this.SECURITYCURRENCY + 'sVolume', 'VOLUME',
      {fieldsetName: this.QUOTATION_DATA});
  }

  /**
   * Adds field definitions for historical and intraday data settings including data providers,
   * retry counters, URL extensions, and feed URLs. Also includes dividend and split group configurations.
   */
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
    this.addFieldPropertyFeqH(DataType.URLString, WatchlistHelper.INTRADAY_URL, {
      fieldsetName: 'INTRA_SETTINGS',
      templateName: 'long'
    });
    this.addDividendGroup();
    this.addSplitGroup();
  }

  /**
   * Adds dividend-related field definitions for securities that support dividend connectors.
   * Only adds fields if the security is not a currency pair and meets dividend connector criteria.
   */
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

  /**
   * Adds stock split-related field definitions for securities that support split connectors.
   * Only adds fields if the security is not a currency pair and meets split connector criteria.
   */
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

  /**
   * Converts feed connector ID to human-readable name using the feedConnectorsKV mapping.
   * @param dataobject The data object containing the field value
   * @param field The column configuration for the field
   * @param valueField The raw connector ID value to convert
   * @returns Human-readable name for the feed connector or undefined if not found
   */
  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.feedConnectorsKV[valueField];
  }

  protected readonly AppHelper = AppHelper;
}

/**
 * Extended content model that includes URLs for various data feeds (intraday, historical, dividend, split)
 * in addition to the base security currency information.
 */
class ContentExtendedInfo extends ContentBase {
  /**
   * Creates an instance of ContentExtendedInfo with feed URL information.
   * @param intradayUrl URL for intraday price data feed
   * @param historicalUrl URL for historical price data feed
   * @param dividendUrl URL for dividend data feed
   * @param splitUrl URL for stock split data feed
   * @param securitycurrency The base security or currency object
   */
  constructor(public intradayUrl: string, public historicalUrl: string,
    public dividendUrl, public splitUrl, securitycurrency: Securitycurrency) {
    super(securitycurrency);
  }
}
