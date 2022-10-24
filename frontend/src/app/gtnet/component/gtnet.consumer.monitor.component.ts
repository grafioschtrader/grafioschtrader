import {Component} from "@angular/core";
import {CrudMenuOptions, TableCrudSupportMenu} from "../../shared/datashowbase/table.crud.support.menu";
import {GTNet} from "../model/gtnet";
import {TaskDataChangeService} from "../../shared/taskdatamonitor/service/task.data.change.service";
import {ConfirmationService, FilterService} from "primeng/api";
import {MessageToastService} from "../../shared/message/message.toast.service";
import {ActivePanelService} from "../../shared/mainmenubar/service/active.panel.service";
import {DialogService} from "primeng/dynamicdialog";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";
import {UserSettingsService} from "../../shared/service/user.settings.service";
import {AppSettings} from "../../shared/app.settings";
import {GTNwtService} from "../service/gtnet.service";
import {TaskDataChange} from "../../entities/task.data.change";
import {GTNetMessage} from "../model/gtnet.message";

@Component({
  template: `
     Consumer Monitor
  `,
})
export class GTNetConsumerMonitorComponent {

}
