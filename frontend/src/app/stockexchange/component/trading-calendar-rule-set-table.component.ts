import {Component, Injector, OnDestroy} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {TooltipModule} from '@openng/optimus-ui/tooltip';
import {combineLatest} from 'rxjs';

import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {BaseSettings} from '../../lib/base.settings';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {TradingCalendarRuleSet} from '../../entities/trading.calendar.rule.set';
import {TradingCalendarRuleSetService} from '../service/trading.calendar.rule.set.service';
import {TradingCalendarRuleSetEditComponent} from './trading-calendar-rule-set-edit.component';
import {StockexchangeService} from '../service/stockexchange.service';

/**
 * Shows the trading calendar rule sets, the shared holiday rules from which the closures of a stock exchange can be
 * calculated instead of deriving them from the quotes of a reference index.
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems" [contextMenuAppendTo]="'body'"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{entityNameUpper | translate}}</h4>

      <!-- Country flag cell (templateName: 'icon'); country resolved from the MIC, tooltip shows
           the technical id_trading_calendar_rule_set -->
      <ng-template #iconCell let-row>
        <img src="assets/icons/flag_placeholder.png"
             [class]="'fi fi-' + getFlagCountryCode(row.mic)"
             style="width: 20px"
             [pTooltip]="row.idTradingCalendarRuleSet" tooltipPosition="top"/>
      </ng-template>

    </configurable-table>

    @if (visibleDialog) {
      <trading-calendar-rule-set-edit [visibleDialog]="visibleDialog"
                                      [callParam]="callParam"
                                      (closeDialog)="handleCloseDialog($event)">
      </trading-calendar-rule-set-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, TooltipModule, ConfigurableTableComponent, TradingCalendarRuleSetEditComponent]
})
export class TradingCalendarRuleSetTableComponent extends TableCrudSupportMenu<TradingCalendarRuleSet>
  implements OnDestroy {

  callParam: TradingCalendarRuleSet;

  /** Maps a market identifier code (MIC) to its ISO country code, for the flag column. */
  private micToCountryCode: { [mic: string]: string } = {};

  constructor(private tradingCalendarRuleSetService: TradingCalendarRuleSetService,
              private stockexchangeService: StockexchangeService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(AppSettings.TRADING_CALENDAR_RULE_SET, tradingCalendarRuleSetService, confirmationService,
      messageToastService, activePanelService, dialogService, filterService, translateService, gps, usersettingsService,
      injector);

    this.addColumnFeqH(DataType.String, 'name', true, false,
      {width: 220, templateName: BaseSettings.OWNER_TEMPLATE});
    this.addColumn(DataType.String, 'countryFlag', 'COUNTRY_FLAG', true, false,
      {width: 40, templateName: 'icon', fieldValueFN: this.getCountryCodeForSort.bind(this)});
    this.addColumnFeqH(DataType.String, 'mic', true, false, {width: 50});
    this.addColumn(DataType.String, 'nameExtendsRuleSet', 'ID_EXTENDS_RULE_SET', true, false, {width: 200});
    this.addColumn(DataType.NumericInteger, 'usedByExchanges', 'USED_BY_EXCHANGES', true, false, {width: 80});

    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: TradingCalendarRuleSet): void {
    this.callParam = entity;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_TRADING_CALENDAR_RULE;
  }

  /**
   * Resolves the ISO country code (lower-cased for the flag-icons CSS class) for a rule set's MIC.
   * Falls back to the neutral 'xx' placeholder flag when the rule set has no MIC or the MIC is unknown.
   *
   * @param mic the market identifier code of the rule set, may be null/undefined
   * @return the lower-cased ISO country code, or 'xx' as a placeholder
   */
  getFlagCountryCode(mic: string): string {
    const cc = mic ? this.micToCountryCode[mic] : null;
    return cc ? cc.toLowerCase() : 'xx';
  }

  /**
   * Sort value for the flag column: the ISO country code resolved from the row's MIC, so that clicking
   * the flag header groups the rows by country. Rows whose MIC has no country map to the 'xx'
   * placeholder and sort together.
   *
   * @param dataobject the TradingCalendarRuleSet row
   * @param field the flag column configuration (unused)
   * @param valueField unused
   * @return the lower-cased ISO country code, or 'xx' as a placeholder
   */
  getCountryCodeForSort(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.getFlagCountryCode(dataobject['mic']);
  }

  protected override readData(): void {
    combineLatest([
      this.tradingCalendarRuleSetService.getAllRuleSets(),
      this.stockexchangeService.getAllStockexchangesBaseData()
    ]).subscribe(([ruleSets, baseData]) => {
      this.micToCountryCode = {};
      baseData.stockexchangeMics.forEach(m => this.micToCountryCode[m.mic] = m.countryCode);
      this.createTranslatedValueStoreAndFilterField(ruleSets);
      this.entityList = ruleSets;
      this.refreshSelectedEntity();
    });
  }
}
