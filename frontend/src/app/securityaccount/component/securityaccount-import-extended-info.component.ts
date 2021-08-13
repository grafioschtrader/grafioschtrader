import {Component, Input, OnInit} from '@angular/core';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {DataType} from '../../dynamic-form/models/data.type';
import {ImportSettings} from './import.settings';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {SecurityaccountImportTransactionTableComponent} from './securityaccount-import-transaction-table.component';

/**
 * Shows the extended information to a single import import transaction record in case when a
 * import template could read the data.
 */
@Component({
  selector: 'securityaccount-import-extended-info',
  templateUrl: '../view/securityaccount.import.extended.info.html'
})
export class SecurityaccountImportExtendedInfoComponent extends SingleRecordConfigBase implements OnInit {
  @Input() combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos;
  private readonly IMPORT_VALUE = 'IMPORT_VALUE';
  private readonly IMPORT_ASSIGN = 'IMPORT_ASSIGN';
  private readonly IMPORT_STATE = 'IMPORT_STATE';

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addFieldProperty(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionTime', 'DATE',
      {fieldsetName: this.IMPORT_VALUE});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionType', 'TRANSACTION_TYPE_IMP',
      {translateValues: TranslateValue.NORMAL, fieldsetName: this.IMPORT_VALUE});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyAccount', 'ACCOUNT_CURRENCY',
      {fieldsetName: this.IMPORT_VALUE});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'securityNameImp', 'NAME',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'isin',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'symbolImp', 'SYMBOL',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldPropertyFeqH(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'taxableInterest',
      {fieldsetName: this.IMPORT_VALUE, templateName: 'check'});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencySecurity', 'SECURITY_CURRENCY',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyExRate', 'EXCHANGE_RATE',
      {fieldsetName: this.IMPORT_VALUE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'units', 'QUANTITY',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'quotation', 'QUOTATION_DIV',
      {fieldsetName: this.IMPORT_VALUE, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldPropertyFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'accruedInterest',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldPropertyFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'taxCost',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldPropertyFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionCost',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccountAmount', 'TOTAL_AMOUNT',
      {fieldsetName: this.IMPORT_VALUE});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'security.name', AppSettings.SECURITY.toUpperCase(),
      {fieldsetName: this.IMPORT_ASSIGN});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccount.name',
      AppSettings.CASHACCOUNT.toUpperCase(), {fieldsetName: this.IMPORT_ASSIGN});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'calcCashaccountAmount', 'CALC_TOTAL_VALUE',
      {fieldsetName: this.IMPORT_ASSIGN});
    this.addFieldPropertyFeqH(DataType.NumericInteger, ImportSettings.IMPORT_TRANSACTION_POS + 'idFilePart',
      {fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()});
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'templatePurpose',
      {fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()});
    this.addFieldPropertyFeqH(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'validSince',
      {fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()});
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'fileNameOriginal',
      {fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()});
    this.addFieldPropertyFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'diffCashaccountAmount',
      {fieldsetName: this.IMPORT_STATE});
    this.addFieldProperty(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'readyForTransaction', 'IMPORT_TRANSACTIONAL', {
      fieldsetName: this.IMPORT_STATE,
      templateName: 'check'
    });
    this.addFieldProperty(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'idTransaction', 'IMPORT_HAS_TRANSACTION', {
      fieldsetName: this.IMPORT_STATE,
      fieldValueFN: SecurityaccountImportTransactionTableComponent.hasTransaction,
      templateName: 'icon'
    });

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionError', null,
      {fieldsetName: 'TRANSACTION_ERROR'});

    this.translateHeadersAndColumns();
    this.createTranslatedValueStore([this.combineTemplateAndImpTransPos]);
  }

}
