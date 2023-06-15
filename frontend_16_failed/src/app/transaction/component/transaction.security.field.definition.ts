import {TransactionContextMenu} from './transaction.context.menu';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../shared/datashowbase/column.config';
import {TransactionSecurityOptionalParam} from '../model/transaction.security.optional.param';
import {AppSettings} from '../../shared/app.settings';

/**
 * Definition of fields which are used in many table or tree for showing transactions
 */
export class TransactionSecurityFieldDefinition {
  static getFieldDefinition(tcm: TransactionContextMenu, idTenant: number, isMarginInstrument: boolean,
                            tsop: TransactionSecurityOptionalParam[]): ColumnConfig[] {
    const currencyColumnConfigMC: ColumnConfig[] = [];
    tcm.addColumn(DataType.DateString, 'transaction.transactionTime', 'DATE', true, false, {width: 100});
    if (idTenant) {
      tcm.addColumn(DataType.String, 'transaction.cashaccount.name', AppSettings.CASHACCOUNT.toUpperCase(), true, false);
    }
    tcm.addColumnFeqH(DataType.String, 'transaction.transactionType', true, false,
      {translateValues: TranslateValue.NORMAL});
    tcm.addColumn(DataType.Numeric, 'transaction.units', 'QUANTITY', true, false);

    if (isMarginInstrument) {
      tcm.addColumn(DataType.Numeric, 'transaction.assetInvestmentValue2', 'VALUE_PER_POINT', true, false);
    }
    tcm.addColumn(DataType.Numeric, 'transaction.quotation', 'QUOTATION_DIV', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    tcm.addColumnFeqH(DataType.Numeric, 'transaction.taxCost', true, false);

    if (tsop && tsop.indexOf(TransactionSecurityOptionalParam.SHOW_TAXABLE_COLUMN) >= 0) {
      tcm.addColumnFeqH(DataType.Boolean, 'transaction.taxableInterest', true, false, {templateName: 'check'});
    }
    tcm.addColumnFeqH(DataType.Numeric, 'holdingsSplitAdjusted', true, false);
    tcm.addColumnFeqH(DataType.Numeric, 'transaction.transactionCost', true, false);
    tcm.addColumnFeqH(DataType.Numeric, 'transaction.cashaccountAmount', true, false);
    tcm.addColumn(DataType.Numeric, 'transactionGainLoss', 'GAIN', true, false);
    tcm.addColumn(DataType.Numeric, 'transactionGainLossPercentage', 'GAIN_PERCENTAGE', true, false);
    tcm.addColumn(DataType.Numeric, 'transactionExchangeRate', 'EXCHANGE_RATE', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    currencyColumnConfigMC.push(tcm.addColumn(DataType.Numeric, 'transactionGainLossMC', 'GAIN', true, false));
    currencyColumnConfigMC.push(tcm.addColumn(DataType.Numeric, 'transactionCurrencyGainLossMC', 'GAIN_LOSS_CURRENCY', true, false));
    tcm.fields.filter(cc => cc.dataType === DataType.Numeric).map(cc => cc.templateName = 'greenRed');
    tcm.prepareTableAndTranslate();

    return currencyColumnConfigMC;
  }
}
