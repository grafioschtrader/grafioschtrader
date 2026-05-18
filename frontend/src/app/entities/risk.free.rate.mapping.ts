import {Auditable} from '../lib/entities/auditable';

/**
 * Maps an ISO currency code to the synthetic Security whose historical close = risk-free interest rate for that
 * currency. Mirrors the backend grafioschtrader.entities.RiskFreeRateMapping. The PK is the Integer surrogate
 * idRiskFreeRateMapping; currency is functionally unique.
 */
export class RiskFreeRateMapping extends Auditable {
  idRiskFreeRateMapping: number = null;
  currency: string = null;
  idSecuritycurrency: number = null;
}

/**
 * Projection returned by GET /api/riskfreeratemapping/availableinstruments. Used by the editable-table dropdown to
 * populate the instrument-picker column (2) and the derived FRED-series-id display column (3) in a single call.
 */
export interface RiskFreeInstrumentOption {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  urlHistoryExtend: string;
}
