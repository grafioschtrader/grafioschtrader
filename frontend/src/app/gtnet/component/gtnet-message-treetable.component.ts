import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GTNetMessage, MsgCallParam} from '../model/gtnet.message';
import {MenuItem, TreeNode} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {TreeTableModule} from 'primeng/treetable';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';

/**
 * It shows the messages in a tree table.
 */
@Component({
  selector: 'gtnet-message-treetable',
  standalone: true,
  imports: [
    CommonModule,
    TreeTableModule,
    ContextMenuModule,
    TooltipModule,
    GTNetMessageEditComponent
  ],
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-treeTable [value]="rootNode.children" [columns]="fields" dataKey="idGtNetMessage"
                     selectionMode="single" [(selection)]="selectedNode"
                     showGridlines="true">
          <ng-template #header let-fields>
            <tr>
              @for (field of fields; track field) {
                <th [style.width.px]="field.width">
                  {{ field.headerTranslated }}
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
            <tr [ttSelectableRow]="rowNode">
              @for (field of fields; track field; let i = $index) {
                @if (field.visible) {
                  <td
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-end': ''"
                    [style.width.px]="field.width">
                    @if (i === 0) {
                      <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                    }
                    @switch (field.templateName) {
                      @case ('greenRed') {
                        <span [pTooltip]="getValueByPath(rowData, field)"
                              [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'
                              tooltipPosition="top">
                        {{ getValueByPath(rowData, field) }}
                      </span>
                      }
                      @default {
                        <span [pTooltip]="getValueByPath(rowData, field)">{{ getValueByPath(rowData, field) }}</span>
                      }
                    }
                  </td>
                }
              }
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems" appendTo="body">
        </p-contextMenu>
      </div>
    </div>
    @if (visibleDialogMsg) {
      <gtnet-message-edit [visibleDialog]="visibleDialogMsg"
                          [msgCallParam]="msgCallParam"
                          (closeDialog)="handleCloseDialogMsg($event)">
      </gtnet-message-edit>
    }
  `
})

export class GTNetMessageTreeTableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  @Input() gtNetMessages: GTNetMessage[];
  @Input() formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  @ViewChild('cm') contextMenu: any;

  public static consumedGT = 'consumedGT';

  rootNode: TreeNode = {children: []};
  selectedNode: TreeNode;
  selectedGTNetMessage: GTNetMessage;
  contextMenuItems: MenuItem[] = [];
  visibleDialogMsg = false;
  msgCallParam: MsgCallParam;


  constructor(private activePanelService: ActivePanelService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.DateTimeString, 'timestamp', 'RECEIVED_TIME', true, false);
    this.addColumnFeqH(DataType.String, 'messageCode', true, false, {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'sendRecv', true, false, {translateValues: TranslateValue.NORMAL});
    this.prepareData();
    this.createTranslateValuesStoreForTranslation(this.rootNode.children);
    this.translateHeadersAndColumns();
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
    });
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

  private setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItems()});
    this.contextMenuItems = this.getMenuItems();
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  private getMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    return menuItems;
  }

  private sendMsgSelected(): void {
    this.msgCallParam = new MsgCallParam(this.formDefinitions, this.selectedNode.data.idGtNet,
      this.selectedNode.data.idGtNetMessage, null);
    this.visibleDialogMsg = true;
  }

  handleCloseDialogMsg(dynamicMsg: any): void {
    this.visibleDialogMsg = false;
  }

}
