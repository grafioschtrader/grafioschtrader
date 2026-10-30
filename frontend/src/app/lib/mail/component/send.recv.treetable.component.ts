import { switchMap } from 'rxjs';
import type { PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { TreeTableConfigBase } from '../../datashowbase/tree.table.config.base';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { Textarea } from '@openng/optimus-ui/textarea';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { ConfirmationService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { MailSendRecvService } from '../service/mail.send.recv.service';
import { MailInboxWithSend, MailSendRecv, SendRecvType } from '../model/mail.send.recv';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateHelper } from '../../helper/translate.helper';
import { MailSendParam } from '../../dynamicdialog/component/mail-send-dynamic-dialog.component';
import { AppHelper } from '../../helper/app.helper';
import { InfoLevelType } from '../../message/info.leve.type';
import { MessageToastService } from '../../message/message.toast.service';
import { DynamicDialogs } from '../../dynamicdialog/component/dynamic.dialogs';
import { BaseSettings } from '../../base.settings';
import { HelpIds } from '../../help/help.ids';
import { ConfigurableTreeTableComponent } from '../../datashowbase/configurable-tree-table.component';

/**
 * This component contains a tree structure for displaying sent and received messages. The message text is displayed in a text area.
 */
@Component({
  template: `
    <configurable-tree-table
      [data]="sendRecvRootNode()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idMailSendRecv"
      [(selection)]="selectedNode"
      (nodeSelect)="nodeSelect($event)"
      sortField="sendRecvTime"
      [sortOrder]="sortOrder"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="true"
      [contextMenuAppendTo]="'body'"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [rowClassFn]="rowClass"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <div caption style="text-align:left">
        <h5>
          {{ 'MAIL_TO_FROM' | translate }} {{ gps.getIdUser() }}, {{ 'MOST_PRIVILEGED_ROLE' | translate }} :
          {{ gps.getMostPrivilegedRole() | translate }}
        </h5>
      </div>
      <ng-template #iconCell let-row let-field="field" let-value="value">
        <svg-icon [name]="value" [svgStyle]="{ 'width.px': 16, 'height.px': 16 }" />
      </ng-template>
    </configurable-tree-table>
    <textarea [rows]="15" pTextarea readonly="true">{{ selectedNode()?.data.message ?? '' }}</textarea>
  `,
  styles: ['textarea { width:100%; }'],
  providers: [DialogService],
  imports: [Textarea, AngularSvgIconModule, TranslateModule, ConfigurableTreeTableComponent]
})
export class SendRecvTreetableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  private sendRecvService: MailSendRecvService = inject(MailSendRecvService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);
  private dialogService: DialogService = inject(DialogService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private messageToastService: MessageToastService = inject(MessageToastService);

  public static createSendRecvIconMap: { [key: string]: string } = {
    [SendRecvType.SEND]: 'send',
    [SendRecvType.RECEIVE]: 'envelope',
    ['C']: 'download'
  };
  replyMenuItem: MenuItem = {
    label: 'REPLY',
    command: (event) => this.reply()
  };
  deleteMenuItem: MenuItem = {
    label: 'DELETE',
    command: (event) => this.deleteSingleOrGroup()
  };
  deleteGroupMenuItem: MenuItem = {
    label: 'DELETE_MAIL_GROUP',
    command: (event) => this.deleteSingleOrGroup()
  };
  sendToUserRoleMenuItem: MenuItem = {
    label: 'SEND_TO_USER_ROLE',
    command: (event) => this.sendToUserRoleOrUser()
  };
  sendToUserMenuItem: MenuItem = {
    label: 'SEND_TO_USER',
    command: (event) => this.sendToUserRoleOrUser(-1)
  };

  private static iconLoadDone = false;

  readonly sendRecvRootNode = signal<TreeNode[]>([]);
  readonly contextMenuItems = signal<MenuItem[] | null>(null);
  readonly selectedNode = signal<TreeNode | null>(null);
  /** Stable row class callback of the tree table */
  readonly rowClass = (rowNode: any, rowData: any): string | null => this.getRowClass(rowNode, rowData);
  private readonly destroyRef = inject(DestroyRef);
  sortOrder = -1;
  private sendRecvParentMap: { [idMailInOut: number]: TreeNode } = {};

  constructor() {
    super();
    SendRecvTreetableComponent.registerIcons(this.iconReg);

    this.addColumnFeqH(DataType.String, 'idUserFrom', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.String, 'sendRecv', true, false, {
      fieldValueFN: this.getSendRecvIcon.bind(this),
      templateName: 'icon',
      width: 25
    });
    this.addColumn(DataType.String, 'roleNameTo', 'ROLE_NAME_USER_TO', true, false, {
      fieldValueFN: this.getRollOrUser.bind(this),
      width: 100,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.DateTimeString, 'sendRecvTime', true, false, {
      width: 80
    });
    this.addColumnFeqH(DataType.String, 'subject', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'numberOfAnswer', true, false, {
      width: 60
    });
    this.addColumnFeqH(DataType.Boolean, 'hasBeenRead', true, false, {
      templateName: 'check',
      width: 60
    });
    TranslateHelper.translateMenuItems(
      [this.replyMenuItem, this.sendToUserRoleMenuItem, this.deleteMenuItem, this.deleteGroupMenuItem].concat(
        this.gps.hasRole(BaseSettings.ROLE_ADMIN) ? [this.sendToUserMenuItem] : []
      ),
      this.translateService
    );
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
    this.sendRecvService
      .getMailsByUserOrRole()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((miws: MailInboxWithSend) => {
        this.sendRecvParentMap = {};
        const tn: TreeNode[] = [];
        miws.mailSendRecvList.forEach((mailSendRecv) => {
          const replyId = mailSendRecv.idReplyToLocal;
          const parent = replyId == null ? undefined : this.sendRecvParentMap[replyId];
          if (parent) {
            (parent.children ??= []).push({
              data: mailSendRecv,
              expanded: false,
              leaf: true
            });
            parent.expanded = !this.hasBeenRead(mailSendRecv);
          } else {
            this.createParentNode(tn, miws, mailSendRecv);
          }
        });
        this.createTranslateValuesStoreForTranslation(tn);
        this.sendRecvRootNode.set(tn);
      });
  }

  private hasBeenRead(mailSendRecv: MailSendRecv): boolean {
    return mailSendRecv.hasBeenRead || mailSendRecv.idUserFrom === this.gps.getIdUser();
  }

  private createParentNode(tn: TreeNode[], miws: MailInboxWithSend, mailSendRecv: MailSendRecv): void {
    const parentId = mailSendRecv.idReplyToLocal ? mailSendRecv.idReplyToLocal : mailSendRecv.idMailSendRecv;
    const numberOfAnswer = miws.countMsgMap[parentId];
    const tnParent: TreeNode<MailSendRecv> = {
      data: Object.assign(mailSendRecv, { numberOfAnswer }),
      children: [],
      expanded: false,
      leaf: false,
      parent: this.sendRecvRootNode()[0]
    };
    this.sendRecvParentMap[parentId] = tnParent;
    tn.push(tnParent);
  }

  nodeSelect(event: { node: { data: MailSendRecv } }): void {
    const mailSendRecv: MailSendRecv = event.node.data;
    if (!this.hasBeenRead(mailSendRecv)) {
      this.sendRecvService.markForRead(mailSendRecv.idMailSendRecv).subscribe((mailSendRecvRc) => {
        event.node.data = mailSendRecvRc;
        this.notifyViewChanged();
      });
    }
  }

  getRowClass(rowNode: any, rowData: any): string | null {
    return rowNode.level === 0 && rowNode.node?.children?.length > 0 ? 'row-total' : null;
  }

  getSendRecvIcon(entity: MailSendRecv, field: ColumnConfig, valueField: any): string {
    return entity.idUserTo &&
      ((entity.idUserTo !== this.gps.getIdUser() && entity.sendRecv === SendRecvType.RECEIVE) ||
        (entity.idUserFrom !== this.gps.getIdUser() && entity.sendRecv === SendRecvType.SEND))
      ? SendRecvTreetableComponent.createSendRecvIconMap['C']
      : SendRecvTreetableComponent.createSendRecvIconMap[entity.sendRecv];
  }

  getRollOrUser(entity: MailSendRecv, field: ColumnConfig, valueField: any): string {
    return valueField ? valueField : '' + entity.idUserTo;
  }

  reply(): void {
    const selected = this.selectedNode();
    if (!selected) return;
    DynamicDialogs.getOpenedMailSendComponent(
      this.translateService,
      this.dialogService,
      new MailSendParam(selected.data.idUserFrom, selected.data)
    )
      .pipe(
        switchMap((ref) => ref.onClose),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((mailSendRecv: MailSendRecv) => {
        mailSendRecv && this.readData();
      });
  }

  sendToUserRoleOrUser(idUser?: number): void {
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService, new MailSendParam(idUser))
      .pipe(
        switchMap((ref) => ref.onClose),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((mailSendRecv: MailSendRecv) => {
        mailSendRecv && this.readData();
      });
  }

  deleteSingleOrGroup() {
    const selected = this.selectedNode();
    if (!selected) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      selected.parent ? 'MSG_CONFIRM_DELETE_MESSAGE' : 'MSG_CONFIRM_DELETE_MESSAGE_GROUP',
      () => {
        this.sendRecvService.deleteSingleOrGroup(selected.data.idMailSendRecv).subscribe((response) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: 'MAIL_SEND_RECV'
          });
          if (this.selectedNode() === selected) this.selectedNode.set(null);
          this.readData();
        });
      }
    );
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    this.setMenuItemsToActivePanel();
  }

  private setMenuItemsToActivePanel(): void {
    const menuItems = this.prepareEditMenu();
    this.contextMenuItems.set(menuItems);
    this.activePanelService.activatePanel(this, { editMenu: menuItems });
  }

  private prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [this.sendToUserRoleMenuItem].concat(
      this.gps.hasRole(BaseSettings.ROLE_ADMIN) ? [this.sendToUserMenuItem] : []
    );
    const selected = this.selectedNode();
    if (selected) {
      if (selected.data.sendRecv === SendRecvType.RECEIVE) {
        menuItems.push(this.replyMenuItem);
        menuItems.push({ separator: true });
      }
      menuItems.push(selected.parent ? this.deleteMenuItem : this.deleteGroupMenuItem);
    }
    return menuItems;
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }
}
