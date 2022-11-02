import {Component, Input, OnInit} from "@angular/core";
import {SimpleEntityEditBase} from "../../shared/edit/simple.entity.edit.base";
import {GTNet} from "../model/gtnet";
import {GTNetMessageTreeTableComponent} from "./gtnet-message-treetable.component";
import {GTNetMessage, GTNetMessageCodeType, MsgCallParam} from "../model/gtnet.message";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";
import {MessageToastService} from "../../shared/message/message.toast.service";
import {GTNetService} from "../service/gtnet.service";
import {HelpIds} from "../../shared/help/help.ids";
import {AppSettings} from "../../shared/app.settings";
import {AppHelper} from "../../shared/helper/app.helper";
import {GTNetMessageService} from "../service/gtnet.message.service";
import {FieldDescriptorInputAndShow} from "../../shared/dynamicfield/field.descriptor.input.and.show";
import {DynamicFieldHelper} from "../../shared/helper/dynamic.field.helper";
import {AlgoStrategyHelper} from "../../algo/component/algo.strategy.helper";
import {TranslateHelper} from "../../shared/helper/translate.helper";
import {Subscription} from "rxjs";

/**
* Crate a new GTNet message. A message can not be changed.
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
  @Input() msgCallParam: MsgCallParam;
  private readonly MESSAGE_CODE = "messageCode";
  messageCodeSubscription: Subscription;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              gtNetMessageService: GTNetMessageService) {
    super(HelpIds.HELP_GTNET, AppSettings.GTNETMESSAGE.toUpperCase(), translateService, gps,
      messageToastService, gtNetMessageService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF(this.MESSAGE_CODE,  true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.valueChangedOnMessageCode();
  }

  private valueChangedOnMessageCode(): void {
    this.messageCodeSubscription = this.configObject[this.MESSAGE_CODE].formControl.valueChanges.subscribe((messageCode: GTNetMessageCodeType) => {

    });
  }

  override onHide(event): void {
    this.messageCodeSubscription && this.messageCodeSubscription.unsubscribe();
    super.onHide(event);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNetMessage {
    const gtNetMessage: GTNetMessage =  new GTNetMessage();
    this.form.cleanMaskAndTransferValuesToBusinessObject(gtNetMessage);
    return gtNetMessage;
  }
}
