import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateService} from '@ngx-translate/core';

import {ShowRecordConfigBase} from '../../../lib/datashowbase/show.record.config.base';
import {ConfigurableTableComponent} from '../../../lib/datashowbase/configurable-table.component';
import {GlobalparameterService} from '../../../lib/services/globalparameter.service';
import {DataType} from '../../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../../lib/datashowbase/column.config';
import {GTNetSecurityImpGap} from '../model/gtnet-security-imp-gap';
import {GTNet} from '../../../gtnet/model/gtnet';
import {GapCodeType} from '../model/gap-code.type';

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
      [data]="gaps"
      [fields]="fields"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale"
      [containerClass]="''">
    </configurable-table>
  `,
  standalone: true,
  imports: [CommonModule, ConfigurableTableComponent]
})
export class GTNetSecurityImpGapTableComponent extends ShowRecordConfigBase implements OnInit, OnChanges {

  /**
   * The list of gap records to display.
   */
  @Input() gaps: GTNetSecurityImpGap[] = [];

  /**
   * Map of GTNet IDs to GTNet entities for displaying domain names.
   */
  @Input() gtNetsMap: Map<number, GTNet> = new Map();

  private fieldsInitialized = false;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'gapCode', 'GAP_CODE', true, false,
      {translateValues: TranslateValue.NORMAL, width: 150});
    this.addColumn(DataType.String, 'gapMessage', 'GAP_MESSAGE', true, false, {width: 300});
    this.addColumn(DataType.String, 'gtNetDomain', 'GT_NET_DOMAIN', true, false, {width: 200});

    this.translateHeadersAndColumns();
    this.fieldsInitialized = true;

    if (this.gaps?.length > 0) {
      this.prepareGapsForDisplay();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['gaps'] && this.gaps && this.fieldsInitialized) {
      this.prepareGapsForDisplay();
    }
  }

  /**
   * Prepares gap data for display by adding computed fields and converting
   * numeric gap codes to translation keys.
   */
  private prepareGapsForDisplay(): void {
    this.gaps.forEach(gap => {
      // Convert numeric gapCode to translation key
      (gap as any).gapCode = GAP_CODE_TRANSLATION_KEYS[gap.gapCode] || 'GAP_CODE_ASSET_CLASS';
      // Add GTNet domain name as computed field
      const gtNet = this.gtNetsMap.get(gap.idGtNet);
      (gap as any).gtNetDomain = gtNet?.domainRemoteName || '?';
    });

    this.createTranslatedValueStore(this.gaps);
  }
}
