import {AssetclassType} from '../shared/types/assetclass.type';
import {BaseID} from '../lib/entities/base.id';
import {MultilanguageString} from '../lib/entities/multilanguage.string';
import {SpecialInvestmentInstruments} from '../shared/types/special.investment.instruments';
import {Auditable} from '../lib/entities/auditable';
import {Exclude} from 'class-transformer';

export class Assetclass extends Auditable implements BaseID {

  idAssetClass?: number = null;
  categoryType: AssetclassType | string = null;
  specialInvestmentInstrument: SpecialInvestmentInstruments | string = null;
  subCategoryNLS: MultilanguageString = new MultilanguageString();

  @Exclude()
  override getId(): number {
    return this.idAssetClass;
  }

}

