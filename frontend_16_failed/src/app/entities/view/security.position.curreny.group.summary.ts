import {SecurityPositionGroupSummary} from './security.position.group.summary';


export class SecurityPositionCurrenyGroupSummary extends SecurityPositionGroupSummary {
  public currencyExchangeRate: number;
  public currency: string;
  public groupGainLossSecurity: number;
  public groupTransactionCost: number;
  public groupAccountValueSecurity: number;
  public groupTaxCost: number;

}
