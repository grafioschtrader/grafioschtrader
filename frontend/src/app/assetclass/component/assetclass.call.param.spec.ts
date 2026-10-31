import 'reflect-metadata';
import { describe, expect, it } from 'vitest';
import { Assetclass } from '../../entities/assetclass';
import { AssetclassCallParam } from './assetclass.call.param';

function assetclass(de: string, en: string): Assetclass {
  const value = new Assetclass();
  value.subCategoryNLS.map.de = de;
  value.subCategoryNLS.map.en = en;
  return value;
}

describe('asset class dialog suggestions', () => {
  it('derives independent, unique language suggestions and refreshes them from loaded data', () => {
    const first = assetclass('Aktien', 'Stocks');
    const second = assetclass('', 'Bonds');
    const params = new AssetclassCallParam(first, true, [first, second, first]);
    expect(params.subCategorySuggestionsDE).toEqual(['Aktien']);
    expect(params.subCategorySuggestionsEN).toEqual(['Stocks', 'Bonds']);

    params.setSuggestionsArrayOfAssetclassList([assetclass('Anleihen', 'Bonds')]);
    expect(params.subCategorySuggestionsDE).toEqual(['Anleihen']);
    expect(params.subCategorySuggestionsEN).toEqual(['Bonds']);
    expect(first.subCategoryNLS.map.de).toBe('Aktien');
  });

  it('accepts creating a record after the loaded list is genuinely empty', () => {
    const params = new AssetclassCallParam(null, false, []);
    expect(params.assetclass).toBeNull();
    expect(params.hasSecurity).toBe(false);
    expect(params.subCategorySuggestionsDE).toEqual([]);
    expect(params.subCategorySuggestionsEN).toEqual([]);
  });
});
