import {ChangeDetectorRef, Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {MailInboxService} from '../service/mail.inbox.service';
import {plainToClass} from 'class-transformer';
import {MailInbox} from '../model/mail.inbox';
import {DataType} from '../../dynamic-form/models/data.type';
import {Router} from '@angular/router';
import {DynamicDialogHelper} from '../../shared/dynamicdialog/component/dynamic.dialog.helper';
import {MailSendParam} from '../../shared/dynamicdialog/component/mail.send.dynamic.component';
import {MailInOutTable} from './mail.in.out.table';
import {TranslateHelper} from '../../shared/helper/translate.helper';

@Component({
  templateUrl: '../view/mail.in.out.table.html',
  providers: [DialogService]
})
export class MailInboxTableComponent extends MailInOutTable<MailInbox> implements OnDestroy {

  replyMenuItem: MenuItem = {label: 'REPLY', command: (event) => this.replyMessage()};
  sendToAdminMenuItem: MenuItem = {label: 'SEND_TO_GROUP', command: (event) => this.sendToGroup()};

  constructor(private mailInboxService: MailInboxService,
              router: Router,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              changeDetectionStrategy: ChangeDetectorRef,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(router, 'receivedTime', 'MailInbox', mailInboxService, confirmationService, messageToastService, activePanelService,
      dialogService, changeDetectionStrategy, translateService, globalparameterService, usersettingsService);

    this.addColumnFeqH(DataType.String, 'idUserFrom', true, false, {width: 50});
    this.addColumnFeqH(DataType.String, 'roleNameTo', true, false,
      {width: 80, translateValues: true});
    this.addColumnFeqH(DataType.DateTimeString, 'receivedTime', true, false, {width: 80});
    this.addColumnFeqH(DataType.String, 'subject', true, false);
    this.addColumnFeqH(DataType.Boolean, 'hasBeenRead', true, false,
      {templateName: 'check', width: 60});
    TranslateHelper.translateMenuItems([this.replyMenuItem, this.sendToAdminMenuItem], translateService);
    this.prepareTableAndTranslate();
  }

  readData(): void {
    this.mailInboxService.getAllInboxByUser().subscribe(mails => {
      this.entityList = plainToClass(MailInbox, mails);
      this.createTranslatedValueStoreAndFilterField(this.entityList);
      this.refreshSelectedEntity();
    });
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  onRowSelect(event): void {
    super.onRowSelect(event);
    const mailInbox: MailInbox = event.data;
    if (!mailInbox.hasBeenRead) {
      this.mailInboxService.markForRead(mailInbox.idMailInOut).subscribe(mailInboxRc => mailInbox.hasBeenRead = true);
    }
  }

  replyMessage(): void {
    DynamicDialogHelper.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(this.selectedEntity.idUserFrom, this.selectedEntity));
  }

  sendToGroup(): void {
    DynamicDialogHelper.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(null));
  }

  protected resetMenu(mailInbox: MailInbox): void {
    this.selectedEntity = mailInbox;
    // Menus for the mailInbox -> only delete
    this.contextMenuItems = this.prepareEditMenu(this.selectedEntity);
    this.contextMenuItems.push({separator: true});
    this.contextMenuItems.push(this.sendToAdminMenuItem);
    if (mailInbox) {
      this.contextMenuItems.push(this.replyMenuItem);
    }
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

}
