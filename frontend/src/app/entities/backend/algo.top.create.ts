import { AlgoTop } from '../../algo/model/algo.top';

export class AlgoTopCreate extends AlgoTop {
  assetclassPercentageList: AssetclassPercentage[] = [];
}

export class AssetclassPercentage {
  constructor(
    public idAssetclass: number,
    public percentage: number
  ) {}
}

/** Generates the hierarchy from dated portfolio holdings without linking a watchlist. */
export class AlgoTopCreateFromPortfolio extends AlgoTopCreate {
  override referenceDate: Date = null;
}

/**
 * Generates the whole hierarchy from the instruments of the linked watchlist. It adds no field of its own: the name and
 * the watchlist come from AlgoTop, and assetclassPercentageList stays empty because the buckets are derived.
 */
export class AlgoTopCreateFromWatchlist extends AlgoTopCreate {}
