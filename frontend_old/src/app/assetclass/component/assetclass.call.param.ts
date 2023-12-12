import {Assetclass} from '../../entities/assetclass';

export class AssetclassCallParam {
  assetclass: Assetclass;
  hasSecurity: boolean;
  subCategorySuggestionsDE: string[];
  subCategorySuggestionsEN: string[];

  setSuggestionsArrayOfAssetclassList(assetclassList: Assetclass[]): void {
    this.subCategorySuggestionsDE =
      Array.from(new Set(assetclassList.map(assetclass => assetclass.subCategoryNLS.map.de))).filter(value => !!value);
    this.subCategorySuggestionsEN =
      Array.from(new Set(assetclassList.map(assetclass => assetclass.subCategoryNLS.map.en))).filter(value => !!value);
  }

}

