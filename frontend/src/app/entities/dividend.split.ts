import { Expose } from 'class-transformer';

export class DividendSplit {
  @Expose() idSecuritycurrency?: number;
  @Expose() createType?: string | CreateType;
  @Expose() createModifyTime: string | Date;
}

export class Dividend extends DividendSplit {
  @Expose() idDividend?: number;
  @Expose() exDate: string | Date | null = null;
  @Expose() payDate: string | Date | null = null;
  @Expose() amount: number | null = null;
  @Expose() amountAdjusted: number | null = null;
  @Expose() currency: string | null = null;
}

export class Securitysplit extends DividendSplit {
  @Expose() idSecuritysplit?: number;
  @Expose() splitDate: string | Date | null = null;
  @Expose() fromFactor: number | null = null;
  @Expose() toFactor: number | null = null;
}

export enum CreateType {
  CONNECTOR_CREATED = 0,
  ADD_MODIFIED_USER = 5
}
