import {TreeTableConfigBase} from '../../datashowbase/tree.table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {Component, OnInit, ViewChild} from '@angular/core';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {ConfirmationService, MenuItem, TreeNode} from 'primeng/api';
import {MailSendRecvService} from '../service/mail.send.recv.service';
import {MailInboxWithSend, MailSendRecv, SendRecvType} from '../model/mail.send.recv';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateHelper} from '../../helper/translate.helper';
import {MailSendParam} from '../../dynamicdialog/component/mail.send.dynamic.component';
import {AppHelper} from '../../helper/app.helper';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {DynamicDialogs} from '../../dynamicdialog/component/dynamic.dialogs';
import {BaseSettings} from '../../base.settings';
import {HelpIds} from '../../help/help.ids';

/**
 * This component contains a tree structure for displaying sent and received messages. The message text is displayed in a text area.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         #cmDiv [ngClass]=" {'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-treeTable [value]="sendRecvRootNode" [columns]="fields" dataKey="idMailSendRecv"
                   selectionMode="single" [(selection)]="selectedNode" (onNodeSelect)="nodeSelect($event)"
                   sortField="sendRecvTime" [sortOrder]="sortOrder" showGridlines="true">
        <ng-template #caption>
          <div style="text-align:left">
            <h5>{{ "MAIL_TO_FROM" | translate }} {{ gps.getIdUser() }}, {{ "MOST_PRIVILEGED_ROLE" | translate }}
              : {{ gps.getMostPrivilegedRole() | translate }}</h5>
          </div>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field.field) {
              <th [ttSortableColumn]="field.field" [style.width.px]="field.width"
                  [pTooltip]="field.headerTooltipTranslated">
                {{ field.headerTranslated }}
                <p-treeTableSortIcon [field]="field.field"></p-treeTableSortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
          <tr [ngClass]="rowNode.level === 0 && rowNode.node?.children.length > 0 ? 'row-total' : null"
              [ttSelectableRow]="rowNode">
            @for (field of fields; track field.field; let i = $index) {
              <td [ngClass]="{'text-right': (field.dataType===DataType.NumericInteger  || field.dataType===DataType.Numeric
            || field.dataType===DataType.DateTimeNumeric)}">
                @if (i === 0) {
                  <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                }
                @switch (field.templateName) {
                  @case ('check') {
                    <span><i [ngClass]="{'fa fa-check': getValueByPath(rowData, field)}"
                             aria-hidden="true"></i></span>
                  }
                  @case ('icon') {
                    <svg-icon [name]="getValueByPath(rowData, field)"
                              [svgStyle]="{ 'width.px':16, 'height.px':16 }"></svg-icon>
                  }
                  @default {
                    {{ getValueByPath(rowData, field) }}
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
      </p-treeTable>
      <textarea [rows]="15" pTextarea
                readonly="true">{{ selectedNode ? selectedNode.data.message : "" }}</textarea>
      <p-contextMenu [model]="contextMenuItems" [target]="cmDiv"></p-contextMenu>
    </div>
  `,
  styles: ['textarea { width:100%; }'],
  providers: [DialogService],
  standalone: false
})
export class SendRecvTreetableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  @ViewChild('cm') contextMenu: any;
  public static createSendRecvIconMap: { [key: string]: string } = {
    [SendRecvType.SEND]: 'send',
    [SendRecvType.RECEIVE]: 'envelope',
    ['C']: 'download'
  };
  replyMenuItem: MenuItem = {label: 'REPLY', command: (event) => this.reply()};
  deleteMenuItem: MenuItem = {label: 'DELETE', command: (event) => this.deleteSingleOrGroup()};
  deleteGroupMenuItem: MenuItem = {label: 'DELETE_MAIL_GROUP', command: (event) => this.deleteSingleOrGroup()};
  sendToUserRoleMenuItem: MenuItem = {label: 'SEND_TO_USER_ROLE', command: (event) => this.sendToUserRoleOrUser()};
  sendToUserMenuItem: MenuItem = {label: 'SEND_TO_USER', command: (event) => this.sendToUserRoleOrUser(-1)};

  private static iconLoadDone = false;

  sendRecvRootNode: TreeNode[] = [];
  contextMenuItems: MenuItem[];
  selectedNode: TreeNode;
  sortOrder = -1;
  private sendRecvParentMap: { [idMailInOut: number]: TreeNode } = {};

  constructor(private sendRecvService: MailSendRecvService,
    private activePanelService: ActivePanelService,
    private iconReg: SvgIconRegistryService,
    private dialogService: DialogService,
    private confirmationService: ConfirmationService,
    private messageToastService: MessageToastService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
    SendRecvTreetableComponent.registerIcons(this.iconReg);

    this.addColumnFeqH(DataType.String, 'idUserFrom', true, false, {width: 80});
    this.addColumnFeqH(DataType.String, 'sendRecv', true, false,
      {fieldValueFN: this.getSendRecvIcon.bind(this), templateName: 'icon', width: 25});
    this.addColumn(DataType.String, 'roleNameTo', 'ROLE_NAME_USER_TO', true, false,
      {fieldValueFN: this.getRollOrUser.bind(this), width: 100, translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.DateTimeString, 'sendRecvTime', true, false, {width: 80});
    this.addColumnFeqH(DataType.String, 'subject', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'numberOfAnswer', true, false, {width: 60});
    this.addColumnFeqH(DataType.Boolean, 'hasBeenRead', true, false,
      {templateName: 'check', width: 60});
    TranslateHelper.translateMenuItems([this.replyMenuItem, this.sendToUserRoleMenuItem, this.deleteMenuItem,
      this.deleteGroupMenuItem].concat(this.gps.hasRole(BaseSettings.ROLE_ADMIN) ? [this.sendToUserMenuItem] : []), translateService);
    this.translateHeadersAndColumns();
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!SendRecvTreetableComponent.iconLoadDone) {
      for (const [key, iconName] of Object.entries(SendRecvTreetableComponent.createSendRecvIconMap)) {
        iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
      }
      SendRecvTreetableComponent.iconLoadDone = false;
    }
  }

  ngOnInit(): void {
    this.readData();
  }

  private readData(): void {
    this.sendRecvService.getMailsByUserOrRole().subscribe((miws: MailInboxWithSend) => {
      this.sendRecvParentMap = {};
      const tn: TreeNode[] = [];
      miws.mailSendRecvList.forEach(mailSendRecv => {
        const parent: TreeNode = mailSendRecv.idReplyToLocal && this.sendRecvParentMap[mailSendRecv.idReplyToLocal];
        if (parent) {
          parent.children.push({
            data: mailSendRecv,
            expanded: false,
            leaf: true
          });
          parent.expanded = !this.hasBeenRead(mailSendRecv);
        } else {
          this.createParentNode(tn, miws, mailSendRecv);
        }
      });
      this.sendRecvRootNode = tn;
      this.createTranslateValuesStoreForTranslation(this.sendRecvRootNode);
    });
  }

  private hasBeenRead(mailSendRecv: MailSendRecv): boolean {
    return mailSendRecv.hasBeenRead || mailSendRecv.idUserFrom === this.gps.getIdUser();
  }

  private createParentNode(tn: TreeNode[], miws: MailInboxWithSend, mailSendRecv: MailSendRecv): void {
    const parentId = mailSendRecv.idReplyToLocal ? mailSendRecv.idReplyToLocal : mailSendRecv.idMailSendRecv;
    const numberOfAnswer = miws.countMsgMap[parentId];
    const tnParent = {
      data: Object.assign(mailSendRecv, {numberOfAnswer}),
      children: [],
      expanded: false,
      leaf: false,
      parent: this.sendRecvRootNode[0]
    };
    this.sendRecvParentMap[parentId] = tnParent;
    tn.push(tnParent);
  }

  nodeSelect(event): void {
    const mailSendRecv: MailSendRecv = event.node.data;
    if (!this.hasBeenRead(mailSendRecv)) {
      this.sendRecvService.markForRead(mailSendRecv.idMailSendRecv).subscribe(mailSendRecvRc => {
        event.node.data = mailSendRecvRc;
      });
    }
  }

  getSendRecvIcon(entity: MailSendRecv, field: ColumnConfig,
    valueField: any): string {
    return entity.idUserTo && (entity.idUserTo !== this.gps.getIdUser() && entity.sendRecv === SendRecvType.RECEIVE
      || entity.idUserFrom !== this.gps.getIdUser() && entity.sendRecv === SendRecvType.SEND) ?
      SendRecvTreetableComponent.createSendRecvIconMap['C']
      : SendRecvTreetableComponent.createSendRecvIconMap[entity.sendRecv];
  }

  getRollOrUser(entity: MailSendRecv, field: ColumnConfig,
    valueField: any): string {
    return valueField ? valueField : '' + entity.idUserTo;
  }

  reply(): void {
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(this.selectedNode.data.idUserFrom, this.selectedNode.data)).onClose.subscribe((mailSendRecv: MailSendRecv) => {
      mailSendRecv && this.readData();
    });
  }

  sendToUserRoleOrUser(idUser?: number): void {
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(idUser)).onClose.subscribe((mailSendRecv: MailSendRecv) => {
      mailSendRecv && this.readData();
    });
  }

  deleteSingleOrGroup() {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      this.selectedNode.parent ? 'MSG_CONFIRM_DELETE_MESSAGE' : 'MSG_CONFIRM_DELETE_MESSAGE_GROUP', () => {
        this.sendRecvService.deleteSingleOrGroup(this.selectedNode.data.idMailSendRecv).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: 'MAIL_SEND_RECV'});
          this.selectedNode = null;
          this.readData();
        });
      });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.contextMenu && this.contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  private setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.prepareEditMenu()});
    this.contextMenuItems = this.prepareEditMenu();
  }

  private prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [this.sendToUserRoleMenuItem].concat(this.gps.hasRole(BaseSettings.ROLE_ADMIN) ?
      [this.sendToUserMenuItem] : []);
    if (this.selectedNode) {
      if (this.selectedNode.data.sendRecv === SendRecvType.RECEIVE) {
        menuItems.push(this.replyMenuItem);
        menuItems.push({separator: true});
      }
      menuItems.push(this.selectedNode.parent ? this.deleteMenuItem : this.deleteGroupMenuItem);
    }
    return menuItems;
  }

  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

}
