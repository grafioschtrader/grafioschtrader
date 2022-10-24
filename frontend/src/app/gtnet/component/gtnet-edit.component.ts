import {Component, Input, OnInit} from "@angular/core";
import {SimpleEntityEditBase} from "../../shared/edit/simple.entity.edit.base";
import {GTNet} from "../model/gtnet";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";
import {MessageToastService} from "../../shared/message/message.toast.service";
import {AssetclassService} from "../../assetclass/service/assetclass.service";
import {HelpIds} from "../../shared/help/help.ids";
import {AppSettings} from "../../shared/app.settings";
import {GTNwtService} from "../service/gtnet.service";
import {AppHelper} from "../../shared/helper/app.helper";
import {CallParam} from "../../shared/maintree/types/dialog.visible";

/**
 * Add ar modify a GTNet entity. 
 */
@Component({
  selector: 'gtnet-edit',
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
export class GTNetEditComponent extends SimpleEntityEditBase<GTNet> implements OnInit {
  @Input() callParam: GTNet;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              gtNwtService: GTNwtService) {
    super(HelpIds.HELP_GTNET, AppSettings.ASSETCLASS.toUpperCase(), translateService, gps,
      messageToastService, gtNwtService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
  }

  protected override initialize(): void {

  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNet {
    return null;
  }
}
