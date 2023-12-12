import {Component, OnDestroy} from '@angular/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {HelpIds} from '../../shared/help/help.ids';
import {MailSettingForwardService} from '../service/mail.setting.forward.service';
import {
  MailSendForwardDefault,
  MailSettingForward,
  MailSettingForwardParam,
  MailSettingForwardVar,
  MessageComType
} from '../model/mail.send.recv';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';

/**
 * This component shows the message settings in a table.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <p-table [columns]="fields" [value]="mailSettingForwardList" selectionMode="single"
               [(selection)]="selectedEntity" dataKey="idMailSettingForward" responsiveLayout="scroll"
               (sortFunction)="customSort($event)" [customSort]="true"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field"
                [pTooltip]="field.headerTooltipTranslated"
                [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td *ngFor="let field of fields" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{getValueByPath(el, field)}}
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
    </div>
    <mail-forward-setting-edit *ngIf="visibleDialog"
                               [visibleDialog]="visibleDialog"
                               [callParam]="callParam"
                               (closeDialog)="handleCloseDialog($event)">
    </mail-forward-setting-edit>
  `,
  providers: [DialogService]
})
export class MailForwardSettingTableComponent extends TableCrudSupportMenu<MailSettingForward> implements OnDestroy {
  callParam: MailSettingForwardParam;
  mailSettingForwardList: MailSettingForward[];
  // selectedEntity: MailSettingForward;
  private mailSendForwardDefault: MailSendForwardDefault;

  constructor(private mailSettingForwardService: MailSettingForwardService,
              private productIconService: ProductIconService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(AppSettings.MAIL_SETTING_FORWARD, mailSettingForwardService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService);
    this.addColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_COM_TYPE, true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_TARGET_TYPE, true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.NumericInteger, MailSettingForwardVar.ID_USER_DIRECT, true, false);
    this.prepareTableAndTranslate();
  }

  protected override initialize(): void {
    this.mailSettingForwardService.getSendForwardDefault().subscribe((msfd: MailSendForwardDefault) => {
      this.mailSendForwardDefault = msfd;
      this.readData();
    });
  }

  override prepareCallParam(entity: MailSettingForward): void {
    let possibleMsgComType: string [];
    if (entity) {
      possibleMsgComType = [MessageComType[MessageComType[entity.messageComType]]];
    } else {
      const mctList = this.mailSettingForwardList.map(msfl =>
        MessageComType[MessageComType[msfl.messageComType]]);
      possibleMsgComType = Object.keys(this.mailSendForwardDefault.mailSendForwardDefaultMapForUser).filter(
        mct => mctList.indexOf(MessageComType[MessageComType[mct]]) === -1);
    }
    this.callParam = new MailSettingForwardParam(possibleMsgComType, this.mailSendForwardDefault, entity);
  }

  override readData(): void {
    this.mailSettingForwardService.getMailSettingForwardByUser().subscribe((msfList: MailSettingForward[]) => {
      this.mailSettingForwardList = msfList;
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(this.mailSettingForwardList);
    });
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getHelpContextId(): HelpIds {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

}
