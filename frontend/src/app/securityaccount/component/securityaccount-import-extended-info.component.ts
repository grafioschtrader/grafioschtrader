import {Component, Input, OnInit} from '@angular/core';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {DataType} from '../../dynamic-form/models/data.type';
import {ImportSettings} from './import.settings';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Shows the extended information to a single import import transaction record.
 */
@Component({
  selector: 'securityaccount-import-extended-info',
  templateUrl: '../view/securityaccount.import.extended.info.html'
})
export class SecurityaccountImportExtendedInfoComponent extends SingleRecordConfigBase implements OnInit {
  @Input() combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addFieldProperty(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionTime', 'DATE',
      {fieldsetName: 'IMPORT_VALUE'});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionType', 'TRANSACTION_TYPE_IMP',
      {translateValues: TranslateValue.NORMAL, fieldsetName: 'IMPORT_VALUE'});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyAccount', 'ACCOUNT_CURRENCY',
      {fieldsetName: 'IMPORT_VALUE'});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'securityNameImp', 'NAME',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'isin',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'symbolImp', 'SYMBOL',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'currencySecurity', 'SECURITY_CURRENCY',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'currencyExRate', 'EXCHANGE_RATE',
      {fieldsetName: 'IMPORT_VALUE', maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'units', 'QUANTITY',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.NumericRaw, ImportSettings.IMPORT_TRANSACTION_POS + 'quotation', 'QUOTATION_DIV',
      {fieldsetName: 'IMPORT_VALUE', maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'accruedInterest', 'ACCRUED_INTEREST',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'taxCost', 'TAX_COST',
      {fieldsetName: 'IMPORT_VALUE'});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'transactionCost', 'TRANSACTION_COST',
      {fieldsetName: 'IMPORT_VALUE'});

    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccountAmount', 'TOTAL_AMOUNT',
      {fieldsetName: 'IMPORT_VALUE'});

    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'security.name', 'SECURITY',
      {fieldsetName: 'IMPORT_ASSIGN'});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'cashaccount.name', 'ACCOUNT',
      {fieldsetName: 'IMPORT_ASSIGN'});
    this.addFieldProperty(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'calcCashaccountAmount', 'CALC_TOTAL_VALUE',
      {fieldsetName: 'IMPORT_ASSIGN'});

    this.addFieldProperty(DataType.NumericInteger, ImportSettings.IMPORT_TRANSACTION_POS + 'idFilePart', 'IMPORT_ID_FILE_PART',
      {fieldsetName: 'IMPORTTRANSACTIONTEMPLATE'});
    this.addFieldProperty(DataType.String, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'templatePurpose', 'TEMPLATE_PURPOSE',
      {fieldsetName: 'IMPORTTRANSACTIONTEMPLATE'});
    this.addFieldProperty(DataType.DateString, ImportSettings.IMPORT_TRANSACTION_TEMPLATE + 'validSince', 'VALID_SINCE',
      {fieldsetName: 'IMPORTTRANSACTIONTEMPLATE'});
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'fileNameOriginal',
      {fieldsetName: 'IMPORTTRANSACTIONTEMPLATE'});

    this.addFieldPropertyFeqH(DataType.Numeric, ImportSettings.IMPORT_TRANSACTION_POS + 'diffCashaccountAmount',
      {fieldsetName: 'IMPORT_STATE'});
    this.addFieldProperty(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'readyForTransaction', 'IMPORT_TRANSACTIONAL', {
      fieldsetName: 'IMPORT_STATE',
      templateName: 'check'
    });
    this.addFieldProperty(DataType.Boolean, ImportSettings.IMPORT_TRANSACTION_POS + 'idTransaction', 'IMPORT_HAS_TRANSACTION', {
      fieldsetName: 'IMPORT_STATE',
      templateName: 'check'
    });


    this.translateHeadersAndColumns();
    this.createTranslatedValueStore([this.combineTemplateAndImpTransPos]);
  }
}
