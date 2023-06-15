import {Expose} from 'class-transformer';

export class DividendSplit {
  @Expose() idSecuritycurrency?: number;
  @Expose() createType?: string | CreateType;
  @Expose() createModifyTime: string | Date;
}

export class Dividend extends DividendSplit {
  @Expose() idDividend?: number;
  @Expose() exDate = null;
  @Expose() payDate = null;
  @Expose() amount: number = null;
  @Expose() amountAdjusted: number = null;
  @Expose() currency: string = null;
}

export class Securitysplit extends DividendSplit {
  @Expose() idSecuritysplit?: number;
  @Expose() splitDate = null;
  @Expose() fromFactor: number = null;
  @Expose() toFactor: number = null;
}

export enum CreateType {
  CONNECTOR_CREATED = 0,
  ADD_MODIFIED_USER = 5
}
