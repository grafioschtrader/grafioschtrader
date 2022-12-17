import {Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MailInOutTable} from './mail.in.out.table';
import {Router} from '@angular/router';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {plainToClass} from 'class-transformer';
import {MailSendboxService} from '../service/mail.sendbox.service';
import {MailSendbox} from '../model/mail.sendbox';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Display of sent messages.
 */
@Component({
  templateUrl: '../view/mail.in.out.table.html',
  providers: [DialogService]
})
export class MailSendboxTableComponent extends MailInOutTable<MailSendbox> implements OnDestroy {
  constructor(private mailSendboxService: MailSendboxService,
              router: Router,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(router, 'sendTime', AppSettings.MAIL_SENDBOX, mailSendboxService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService);
    this.addColumnFeqH(DataType.String, 'idUserTo', true, false, {width: 50});
    this.addColumnFeqH(DataType.String, 'roleNameTo', true, false,
      {width: 80, translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.DateTimeString, 'sendTime', true, false, {width: 80});
    this.addColumnFeqH(DataType.String, 'subject', true, false);
    this.prepareTableAndTranslate();
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  readData(): void {
    this.mailSendboxService.getAllSendboxByUser().subscribe(mails => {
      this.entityList = plainToClass(MailSendbox, mails);
      this.createTranslatedValueStoreAndFilterField(this.entityList);
      this.refreshSelectedEntity();
    });
  }

}
