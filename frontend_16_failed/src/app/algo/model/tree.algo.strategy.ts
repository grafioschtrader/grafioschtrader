import {AlgoStrategy} from './algo.strategy';
import {TreeAlgoBase} from './tree.algo.base';

export class TreeAlgoStrategy extends TreeAlgoBase {

  constructor(public algoStrategy: AlgoStrategy) {
    super();
  }

  get data(): any {
    return this.algoStrategy;
  }

  get expanded(): boolean {
    return false;
  }

}
