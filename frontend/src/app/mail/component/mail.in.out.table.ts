import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {Router} from '@angular/router';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {Directive} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {MailInOut} from '../model/mail.in.out';
import {HelpIds} from '../../shared/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';


@Directive()
export abstract class MailInOutTable<T extends MailInOut> extends TableCrudSupportMenu<T> {

  callParam: T;

  protected constructor(private router: Router,
                        sortField: string,
                        entityName: string,
                        deleteService: DeleteService,
                        confirmationService: ConfirmationService,
                        messageToastService: MessageToastService,
                        activePanelService: ActivePanelService,
                        dialogService: DialogService,
                        filterService: FilterService,
                        translateService: TranslateService,
                        gps: GlobalparameterService,
                        usersettingsService: UserSettingsService) {
    super(entityName, deleteService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, [CrudMenuOptions.Allow_Delete]);
    this.multiSortMeta.push({field: sortField, order: 1});
  }

  prepareCallParam(entity: T): void {
    this.callParam = entity;
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return true;
  }

  onRowSelect(event): void {
    this.showMessage(event.data.message);
  }

  onRowUnselect(event): void {
    this.showMessage(null);
  }

  protected showMessage(message: string): void {
    message = message || '';
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.MAIL_SHOW_MESSAGE_KEY, {message}]
      }
    }]);
  }
}
