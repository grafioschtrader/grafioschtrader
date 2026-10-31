import type { Assetclass } from '../../entities/assetclass';

export class AssetclassCallParam {
  subCategorySuggestionsDE: string[];
  subCategorySuggestionsEN: string[];

  constructor(
    public assetclass: Assetclass | null,
    public hasSecurity: boolean,
    assetclassList: Assetclass[]
  ) {
    this.subCategorySuggestionsDE = this.suggestions(assetclassList, 'de');
    this.subCategorySuggestionsEN = this.suggestions(assetclassList, 'en');
  }

  private suggestions(assetclassList: Assetclass[], language: 'de' | 'en'): string[] {
    return Array.from(new Set(assetclassList.map((assetclass) => assetclass.subCategoryNLS.map[language]))).filter(
      (value): value is string => typeof value === 'string' && value.length > 0
    );
  }

  setSuggestionsArrayOfAssetclassList(assetclassList: Assetclass[]): void {
    this.subCategorySuggestionsDE = this.suggestions(assetclassList, 'de');
    this.subCategorySuggestionsEN = this.suggestions(assetclassList, 'en');
  }
}
