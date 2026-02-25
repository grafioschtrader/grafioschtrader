import {AlgoTop} from '../../algo/model/algo.top';

export class AlgoTopCreate extends AlgoTop {

  assetclassPercentageList: AssetclassPercentage[] = [];
}

export class AssetclassPercentage {
  constructor(public idAssetclass: number, public percentage: number) {
  }
}

export class AlgoTopCreateFromPortfolio extends AlgoTopCreate {
  override referenceDate: Date = null;
}
