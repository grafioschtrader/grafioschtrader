import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {TreeTableConfigBase} from '../../shared/datashowbase/tree.table.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {GTNetMessage} from '../model/gtnet.message';
import {MenuItem, TreeNode} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';

@Component({
  selector: 'gtnet-message-treetable',
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-treeTable [value]="rootNode.children" [columns]="fields">
          <ng-template pTemplate="header" let-fields>
            <tr>
              <th *ngFor="let field of fields" [style.width.px]="field.width">
                {{field.headerTranslated}}
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="fields">
            <tr>
              <ng-container *ngFor="let field of fields; let i = index">
                <td *ngIf="field.visible"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''"
                    [style.width.px]="field.width">
                  <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                  <ng-container [ngSwitch]="field.templateName">
                    <ng-container *ngSwitchCase="'greenRed'">
                  <span [pTooltip]="getValueByPath(rowData, field)"
                        [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'
                        tooltipPosition="top">
                    {{getValueByPath(rowData, field)}}
                  </span>
                    </ng-container>
                    <ng-container *ngSwitchDefault>
                      <span [pTooltip]="getValueByPath(rowData, field)">{{getValueByPath(rowData, field)}}</span>
                    </ng-container>
                  </ng-container>
                </td>
              </ng-container>
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu *ngIf="contextMenuItems && contextMenuItems.length >0" #cm
                       [target]="cmDiv" [model]="contextMenuItems" appendTo="body">
        </p-contextMenu>
      </div>
    </div>
  `
})

export class GTNetMessageTreeTableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  @Input() gtNetMessages: GTNetMessage[];
  @ViewChild('cm') contextMenu: any;

  public static consumedGT = 'consumedGT';

  rootNode: TreeNode = {children: []};
  contextMenuItems: MenuItem[] = [];

  constructor(private activePanelService: ActivePanelService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'sendRecv', true);

    this.prepareData();
  }

  private prepareData(): void {
    const nodeMap = new Map<number, TreeNode>();
    this.gtNetMessages.forEach(gtMessage => {
      let addNode = this.rootNode;
      if (gtMessage.replyTo) {
        addNode = nodeMap.get(gtMessage.replyTo);
        if (addNode.leaf) {
          addNode.leaf = false;
          addNode.children = [];
        }
      }
      const node = {data: gtMessage, leaf: true};
      nodeMap.set(gtMessage.idGtNetMessage, node);
      addNode.children.push(node);
    })
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    event[GTNetMessageTreeTableComponent.consumedGT] = true;
    this.contextMenu && this.contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  callMeDeactivate(): void {
  }

  setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItems()});
    this.contextMenuItems = this.getMenuItems();
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_GTNET;
  }

  getMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    return menuItems;
  }


}
