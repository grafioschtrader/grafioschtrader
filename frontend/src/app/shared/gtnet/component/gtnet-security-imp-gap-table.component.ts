import { Component, input, computed, effect, untracked } from '@angular/core';

import { ShowRecordConfigBase } from '../../../lib/datashowbase/show.record.config.base';
import { ConfigurableTableComponent } from '../../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../../lib/datashowbase/column.config';
import type { GTNetSecurityImpGap } from '../model/gtnet-security-imp-gap';
import { GTNet } from '../../../lib/gnet/model/gtnet';
import { GapCodeType } from '../model/gap-code.type';

/** Maps numeric gap codes to translation keys */
const GAP_CODE_TRANSLATION_KEYS: { [key: number]: string } = {
  [GapCodeType.ASSET_CLASS]: 'GAP_CODE_ASSET_CLASS',
  [GapCodeType.INTRADAY_CONNECTOR]: 'GAP_CODE_INTRADAY_CONNECTOR',
  [GapCodeType.HISTORY_CONNECTOR]: 'GAP_CODE_HISTORY_CONNECTOR',
  [GapCodeType.DIVIDEND_CONNECTOR]: 'GAP_CODE_DIVIDEND_CONNECTOR',
  [GapCodeType.SPLIT_CONNECTOR]: 'GAP_CODE_SPLIT_CONNECTOR'
};

/**
 * Standalone component for displaying GTNet security import gaps in a table.
 * Shows what didn't match during the import attempt (asset class, connectors).
 */
@Component({
  selector: 'gtnet-security-imp-gap-table',
  template: `
    <configurable-table
      [data]="displayGaps()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [containerClass]="''" />
  `,
  imports: [ConfigurableTableComponent]
})
export class GTNetSecurityImpGapTableComponent extends ShowRecordConfigBase {
  /**
   * The list of gap records to display.
   */
  readonly gaps = input<GTNetSecurityImpGap[]>([]);
  readonly displayGaps = computed(() => {
    const peers = this.gtNetsMap();
    return this.gaps().map((gap) => ({
      ...gap,
      gapCode: GAP_CODE_TRANSLATION_KEYS[gap.gapCode] || 'GAP_CODE_ASSET_CLASS',
      gtNetDomain: peers.get(gap.idGtNet)?.domainRemoteName || '?'
    }));
  });

  /**
   * Map of GTNet IDs to GTNet entities for displaying domain names.
   */
  readonly gtNetsMap = input<Map<number, GTNet>>(new Map());

  constructor() {
    super();

    this.addColumn(DataType.String, 'gapCode', 'GAP_CODE', true, false, {
      translateValues: TranslateValue.NORMAL,
      width: 150
    });
    this.addColumn(DataType.String, 'gapMessage', 'GAP_MESSAGE', true, false, {
      width: 300
    });
    this.addColumn(DataType.String, 'gtNetDomain', 'GT_NET_DOMAIN', true, false, { width: 200 });

    this.translateHeadersAndColumns();

    effect(() => {
      this.displayGaps();
      untracked(() => {
        this.createTranslatedValueStore(this.displayGaps());
      });
    });
  }
}
