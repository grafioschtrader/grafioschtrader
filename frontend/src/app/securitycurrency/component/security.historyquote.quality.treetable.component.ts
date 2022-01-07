import {Component, OnInit} from '@angular/core';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SecurityService} from '../service/security.service';
import {
  HistoryquoteQualityGroup,
  HistoryquoteQualityGrouped,
  HistoryquoteQualityHead,
  HistoryquoteQualityIds
} from '../model/historyquote.quality.group';
import {MenuItem, SelectItem, TreeNode} from 'primeng/api';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {plainToClass} from 'class-transformer';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {SecurityIdWithCurrency} from './security-historyquote-quality-table.component';

/**
 * Shows the quality of historical price data per stock exchange or data provider.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         #cmDiv [ngClass]=" {'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4>{{'SECURITY_HISTORY_QUALITY' | translate}} ({{'LAST_UPDATE' | translate}}: {{lastUpdate}})</h4>
        </p-header>
        <label class="small-padding control-label" for="groupSelect">{{'MAIN_QUALITY_GROUP' | translate}}</label>
        <p-dropdown id="groupSelect" [options]="groups" [(ngModel)]="selectedGroup"
                    (onChange)="groupChanged($event)">
        </p-dropdown>
      </p-panel>
      <p-treeTable [value]="qualityNode" [columns]="fields" dataKey="uniqueKey" sortField="name"
                   selectionMode="single" [(selection)]="selectedNodes" (onNodeSelect)="nodeSelect($event)">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [ttSortableColumn]="field.field" [style.width.px]="field.width">
              {{field.headerTranslated}}
              <p-treeTableSortIcon [field]="field.field"></p-treeTableSortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
          <tr [ttSelectableRow]="rowNode">
            <td *ngFor="let field of fields; let i = index"
                [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric)}">
              <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
              {{getValueByPath(rowData, field)}}
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="footer">
          <tr>
            <ng-container *ngFor="let field of fields">
              <td *ngIf="field.visible" class="row-total" [style.width.px]="field.width"
                  [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
              || field.dataType===DataType.DateTimeNumeric)}">
                {{getValueColumnTotal(field, 0, historyquoteQualityHead, null)}}
              </td>
            </ng-container>
          </tr>
        </ng-template>
      </p-treeTable>
      <security-historyquote-quality-table [historyquoteQualityIds]="historyquoteQualityIds"
                                           [groupTitle]="groupTitle"
                                           (changedIdSecurity)="handleChangedIdSecurity($event)">
      </security-historyquote-quality-table>
      <p-contextMenu *ngIf="contextMenuItems" #contextMenu [model]="contextMenuItems" [target]="cmDiv" appendTo="body">
      </p-contextMenu>
    </div>
  `,
})
export class SecurityHistoryquoteQualityTreetableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {

  historyquoteQualityIds: HistoryquoteQualityIds;
  groupTitle: string;
  contextMenuItems: MenuItem[];
  securityIdWithCurrency: SecurityIdWithCurrency;

  qualityNode: TreeNode[] = [];
  historyquoteQualityHead: HistoryquoteQualityHead;
  selectedNodes: TreeNode[] = [];
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

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_HISTORYQUOTE_QUALITY;
  }

  nodeSelect(event) {
    const hqg: HistoryquoteQualityGroup = event.node.data;
    if (hqg.categoryType !== null) {
      this.groupTitle = event.node.parent.parent.data.name + ' / ' + event.node.parent.data.name + ' / ' + hqg.name;
      this.historyquoteQualityIds = plainToClass(HistoryquoteQualityIds, event.node.data, {excludeExtraneousValues: true});
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

