import type { TranslateService } from '@ngx-translate/core';

/** The order of the blocks of the German tax report in the tables. */
export const GERMAN_TAX_BLOCK_ORDER: string[] = [
  'SHARES',
  'OTHER_INCOME',
  'FUNDS',
  'FOREIGN_TAX',
  'WITHHELD',
  'UNREVIEWED',
  'LEGACY'
];

/** The order of the lines within a block. */
export const GERMAN_TAX_LINE_ORDER: string[] = [
  'SHARE_GAINS',
  'SHARE_LOSSES',
  'SHARE_DIVIDENDS',
  'INTEREST',
  'ACCRUED_INTEREST_RECEIVED',
  'ACCRUED_INTEREST_PAID',
  'CLAIM_GAINS',
  'CLAIM_LOSSES',
  'FUND_DISTRIBUTIONS',
  'FUND_PRELIMINARY',
  'FUND_DISPOSAL',
  'TAX_RECORDED',
  'TAX_CEILING',
  'TAX_NOT_CREDITED',
  'LEGACY_DISPOSAL'
];

/** Translations of the names the server uses in the German tax report. */
export class GermanTaxLabels {
  /** The label of a block, with the category in the blocks that repeat the form blocks. */
  static block(translateService: TranslateService, block: string, category?: string | null): string {
    const text = translateService.instant('GERMAN_TAX_BLOCK_' + block);
    return category ? text + ' / ' + translateService.instant('GERMAN_TAX_CATEGORY_' + category) : text;
  }

  static line(translateService: TranslateService, line: string): string {
    return translateService.instant('GERMAN_TAX_LINE_' + line);
  }

  /** The label of a fund class, empty for an instrument that is no fund. */
  static fundTaxClass(translateService: TranslateService, fundTaxClass?: string | null): string {
    return fundTaxClass ? translateService.instant(fundTaxClass) : '';
  }

  /**
   * The label of a sum named by the server as block/category/line/fund class.
   *
   * @param key - the identity of a sum
   */
  static sumKey(translateService: TranslateService, key: string): string {
    const [block, category, line, fundTaxClass] = key.split('/');
    const fund = GermanTaxLabels.fundTaxClass(translateService, fundTaxClass);
    return (
      GermanTaxLabels.block(translateService, block, category) +
      ': ' +
      GermanTaxLabels.line(translateService, line) +
      (fund ? ' (' + fund + ')' : '')
    );
  }

  static sortKey(block: string, line: string, fundTaxClass?: string | null): string {
    const blockIndex = GERMAN_TAX_BLOCK_ORDER.indexOf(block);
    const lineIndex = GERMAN_TAX_LINE_ORDER.indexOf(line);
    return String(blockIndex).padStart(2, '0') + '-' + String(lineIndex).padStart(2, '0') + '-' + (fundTaxClass ?? '');
  }
}
