import {Component, OnInit} from '@angular/core';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SecurityService} from '../service/security.service';
import {
  HistoryquoteQualityGroup,
  HistoryquoteQualityGrouped,
  HistoryquoteQualityHead,
  HistoryquoteQualityIds
} from '../model/historyquote.quality.group';
import {MenuItem, SelectItem, TreeNode} from 'primeng/api';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnGroupConfig} from '../../lib/datashowbase/column.config';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {plainToInstance} from 'class-transformer';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {SecurityHistoryquoteQualityTableComponent, SecurityIdWithCurrency} from './security-historyquote-quality-table.component';
import {NgClass} from '@angular/common';
import {Panel} from 'primeng/panel';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {ConfigurableTreeTableComponent} from '../../lib/datashowbase/configurable-tree-table.component';
import {SharedModule} from 'primeng/api';

/**
 * Shows the quality of historical price data per stock exchange or data provider.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4>{{ 'SECURITY_HISTORY_QUALITY' | translate }} ({{ 'LAST_UPDATE' | translate }}: {{ lastUpdate }})</h4>
        </p-header>
        <label class="small-padding control-label" for="groupSelect">{{ 'MAIN_QUALITY_GROUP' | translate }}</label>
        <p-select id="groupSelect" [options]="groups" [(ngModel)]="selectedGroup"
                  (onChange)="groupChanged($event)">
        </p-select>
      </p-panel>
      <configurable-tree-table
        [data]="qualityNode" [fields]="fields" dataKey="uniqueKey"
        sortField="name"
        [(selection)]="selectedNode" (nodeSelect)="nodeSelect($event)"
        [contextMenuItems]="contextMenuItems" [showContextMenu]="!!contextMenuItems"
        [valueGetterFn]="getValueByPath.bind(this)"
        [footerTemplate]="qualityFooter">
      </configurable-tree-table>
      <ng-template #qualityFooter>
        <tr>
          @for (field of fields; track field) {
            @if (field.visible) {
              <td class="row-total" [style.width.px]="field.width"
                  [ngClass]="{'text-end': (field.dataType===DataType.NumericInteger || field.dataType===DataType.Numeric
                || field.dataType===DataType.DateTimeNumeric)}">
                {{ getValueColumnTotal(field, 0, historyquoteQualityHead, null) }}
              </td>
            }
          }
        </tr>
      </ng-template>
      <security-historyquote-quality-table [historyquoteQualityIds]="historyquoteQualityIds"
                                           [groupTitle]="groupTitle"
                                           (changedIdSecurity)="handleChangedIdSecurity($event)">
      </security-historyquote-quality-table>
    </div>
  `,
  standalone: true,
  imports: [
    NgClass, TranslatePipe, Panel, SharedModule, Select, FormsModule,
    ConfigurableTreeTableComponent, SecurityHistoryquoteQualityTableComponent
  ]
})
export class SecurityHistoryquoteQualityTreetableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {

  historyquoteQualityIds: HistoryquoteQualityIds;
  groupTitle: string;
  contextMenuItems: MenuItem[];
  securityIdWithCurrency: SecurityIdWithCurrency;

  qualityNode: TreeNode[] = [];
  historyquoteQualityHead: HistoryquoteQualityHead;
  selectedNode: TreeNode[] = [];
  selectedGroup: string;
  lastUpdate: string;

  groups: SelectItem[] = [];

  constructor(private timeSeriesQuotesService: TimeSeriesQuotesService,
    private activePanelService: ActivePanelService,
    private securityService: SecurityService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
    this.addColumnFeqH(DataType.String, 'name', true, false,
      {width: 250, columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]});

    this.addColumnFeqH(DataType.NumericInteger, 'numberOfSecurities', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'activeNowSecurities', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'connectorCreated', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'manualImported', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'filledLinear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'qualityPercentage', true, false,
      {headerSuffix: '%'});
    this.addColumnFeqH(DataType.Numeric, 'ohlPercentage', true, false,
      {headerSuffix: '%'});
    this.setSameFieldNameForGroupField(1);
    this.translateHeadersAndColumns();

    SelectOptionsHelper.createSelectItemForEnum(translateService, HistoryquoteQualityGrouped, this.groups);
  }

  ngOnInit(): void {
    this.selectedGroup = HistoryquoteQualityGrouped[HistoryquoteQualityGrouped.STOCKEXCHANGE_GROUPED];
    this.readData(this.selectedGroup);
  }

  readData(historyquoteQualityGrouped: string): void {
    this.securityService.getHistoryquoteQualityHead(historyquoteQualityGrouped).subscribe((hqg: HistoryquoteQualityHead) => {
      const tn: TreeNode[] = [];
      this.historyquoteQualityHead = hqg;
      this.lastUpdate = AppHelper.getDateByFormat(this.gps, this.historyquoteQualityHead.lastUpdate);
      this.addTreeNode(tn, hqg, null);
      this.qualityNode = tn[0].children;
    });
  }

  groupChanged(event): void {
    this.readData(event.value);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.resetMenu();
  }

  handleChangedIdSecurity(securityIdWithCurrency: SecurityIdWithCurrency): void {
    this.securityIdWithCurrency = securityIdWithCurrency;
    this.resetMenu();
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_HISTORYQUOTE_QUALITY;
  }

  nodeSelect(event): void {
    const hqg: HistoryquoteQualityGroup = event.node.data;
    if (hqg.categoryType !== null) {
      this.groupTitle = event.node.parent.parent.data.name + ' / ' + event.node.parent.data.name + ' / ' + hqg.name;
      this.historyquoteQualityIds = plainToInstance(HistoryquoteQualityIds, event.node.data,
        {excludeExtraneousValues: true}) as HistoryquoteQualityIds;
    }
  }

  protected resetMenu(): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: null
    });
  }

  protected getMenuShowOptions(): MenuItem[] {
    if (this.securityIdWithCurrency) {
      const menuItems: MenuItem[] = this.timeSeriesQuotesService.getMenuItems(this.securityIdWithCurrency.idSecurity,
        this.securityIdWithCurrency.currency, false);
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
      this.contextMenuItems = menuItems;
      return menuItems;
    } else {
      this.contextMenuItems = null;
      return null;
    }
  }

  private addTreeNode(tn: TreeNode[], hqg: HistoryquoteQualityGroup, parentNode: TreeNode): void {
    const treeNode: TreeNode = {
      data: hqg,
      children: [],
      expanded: false,
      leaf: hqg.childrendHqg.length === 0,
      parent: parentNode
    };
    tn.push(treeNode);
    hqg.childrendHqg.forEach((historyquoteQualityGroup: HistoryquoteQualityGroup) => this.addTreeNode(treeNode.children,
      historyquoteQualityGroup, treeNode));
  }

}

