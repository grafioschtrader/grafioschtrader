import {Component} from '@angular/core';
import {HelpIds} from '../../shared/help/help.ids';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {GTNetMessageAnswer} from '../model/gtnet.message.answer';
import {GTNetService} from '../service/gtnet.service';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppSettings} from '../../shared/app.settings';


@Component({
  template: `
    Auto Answer Message
  `,
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

    super(AppSettings.GTNET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
      gps.hasRole(AppSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);
  }


  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_GTNET_SETUP;
  }

  override prepareCallParam(entity: GTNetMessageAnswer): void {

  }

  protected override readData(): void {

  }

}
