import { AlgoTop } from './algo.top';
import { AlgoAssetclass } from './algo.assetclass';

/** Overview data and backend-selected field paths for red text or yellow backgrounds, keyed by hierarchy node ID. */
export interface AlgoHierarchyDto {
  algoTop: AlgoTop;
  algoAssetclassList: AlgoAssetclass[];
  invalidFields: Record<number, string[]>;
  warningFields: Record<number, string[]>;
  monitoring: boolean;
  alertEditable: boolean;
}
