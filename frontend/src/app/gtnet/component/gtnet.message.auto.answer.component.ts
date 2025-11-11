import {Component} from '@angular/core';
import {HelpIds} from '../../lib/help/help.ids';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {GTNetMessageAnswer} from '../model/gtnet.message.answer';
import {GTNetService} from '../service/gtnet.service';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';


@Component({
  template: `
    Auto Answer Message
  `,
  providers: [DialogService],
  standalone: false
})
export class GTNetMessageAutoAnswerComponent extends TableCrudSupportMenu<GTNetMessageAnswer> {

  constructor(private gtNetService: GTNetService,
    private gtNetMessageService: GTNetMessageService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {

    super(AppSettings.GT_NET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);
  }


  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  override prepareCallParam(entity: GTNetMessageAnswer): void {
  }

  protected override readData(): void {
  }

}
