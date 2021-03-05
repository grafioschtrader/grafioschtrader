import {AssetclassType} from '../shared/types/assetclass.type';
import {BaseID} from './base.id';
import {MultilanguageString} from './multilanguage.string';
import {SpecialInvestmentInstruments} from '../shared/types/special.investment.instruments';
import {Auditable} from './auditable';
import {Exclude} from 'class-transformer';

export class Assetclass extends Auditable implements BaseID {

  idAssetClass?: number = null;
  categoryType: AssetclassType | string = null;
  specialInvestmentInstrument: SpecialInvestmentInstruments | string = null;
  subCategoryNLS: MultilanguageString = new MultilanguageString();

  @Exclude()
  getId(): number {
    return this.idAssetClass;
  }

}

