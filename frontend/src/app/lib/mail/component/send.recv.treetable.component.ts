import {TreeTableConfigBase} from '../../datashowbase/tree.table.config.base';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Textarea} from 'primeng/textarea';
import {AngularSvgIconModule} from 'angular-svg-icon';
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
import {ConfigurableTreeTableComponent} from '../../datashowbase/configurable-tree-table.component';

/**
 * This component contains a tree structure for displaying sent and received messages. The message text is displayed in a text area.
 */
@Component({
  template: `
    <configurable-tree-table
      [data]="sendRecvRootNode" [fields]="fields" dataKey="idMailSendRecv"
      [(selection)]="selectedNode" (nodeSelect)="nodeSelect($event)"
      sortField="sendRecvTime" [sortOrder]="sortOrder"
      [contextMenuItems]="contextMenuItems" [showContextMenu]="true"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [rowClassFn]="getRowClass.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">
      <div caption style="text-align:left">
        <h5>{{ "MAIL_TO_FROM" | translate }} {{ gps.getIdUser() }}, {{ "MOST_PRIVILEGED_ROLE" | translate }}
          : {{ gps.getMostPrivilegedRole() | translate }}</h5>
      </div>
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px':16, 'height.px':16 }"></svg-icon>
      </ng-template>
    </configurable-tree-table>
    <textarea [rows]="15" pTextarea
              readonly="true">{{ selectedNode ? selectedNode.data.message : "" }}</textarea>
  `,
  styles: ['textarea { width:100%; }'],
  providers: [DialogService],
  standalone: true,
  imports: [CommonModule, Textarea, AngularSvgIconModule, TranslateModule, ConfigurableTreeTableComponent]
})
export class SendRecvTreetableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
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

  getRowClass(rowNode: any, rowData: any): string | null {
    return rowNode.level === 0 && rowNode.node?.children?.length > 0 ? 'row-total' : null;
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
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

}
