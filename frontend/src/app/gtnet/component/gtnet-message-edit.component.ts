import {Component, Input, OnInit} from "@angular/core";
import {SimpleEntityEditBase} from "../../shared/edit/simple.entity.edit.base";
import {GTNet} from "../model/gtnet";
import {GTNetMessageTreeTableComponent} from "./gtnet-message-treetable.component";
import {GTNetMessage} from "../model/gtnet.message";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";
import {MessageToastService} from "../../shared/message/message.toast.service";
import {GTNetService} from "../service/gtnet.service";
import {HelpIds} from "../../shared/help/help.ids";
import {AppSettings} from "../../shared/app.settings";
import {AppHelper} from "../../shared/helper/app.helper";
import {GTNetMessageService} from "../service/gtnet.message.service";
import {FieldDescriptorInputAndShow} from "../../shared/dynamicfield/field.descriptor.input.and.show";

/**
* Crate a new GTNet message
*/
@Component({
  selector: 'gtnet-message-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetMessageEditComponent extends SimpleEntityEditBase<GTNetMessage> implements OnInit {
  @Input() formDefinitions: { [type: string]: FieldDescriptorInputAndShow[]};

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              gtNetMessageService: GTNetMessageService) {
    super(HelpIds.HELP_GTNET, AppSettings.ASSETCLASS.toUpperCase(), translateService, gps,
      messageToastService, gtNetMessageService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
  }

  protected override initialize(): void {

  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetMessage {
    return null;
  }
}
