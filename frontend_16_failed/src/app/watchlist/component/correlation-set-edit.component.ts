import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {CorrelationSetService} from '../service/correlation.set.service';
import {CorrelationLimit, CorrelationSet, SamplingPeriodType} from '../../entities/correlation.set';
import {AppHelper} from '../../shared/helper/app.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {CorrelationEditingSupport} from './correlation.editing.support';


/**
 * Dialog for editing the transaction import template group
 */
@Component({
  selector: 'correlation-set-edit',
  template: `
    <p-dialog header="{{'CORRELATION_SET' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class CorrelationSetEditComponent extends SimpleEntityEditBase<CorrelationSet> implements OnInit {

  @Input() callParam: CallParam;
  @Input() correlationLimit: CorrelationLimit;
  private correlationEditingSupport: CorrelationEditingSupport = new CorrelationEditingSupport();

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              correlationSetService: CorrelationSetService) {
    super(HelpIds.HELP_WATCHLIST_CORRELATION, 'CORRELATION_SET', translateService, gps,
      messageToastService, correlationSetService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = this.correlationEditingSupport.getCorrelationFieldDefinition(null, 12, 'SAVE_AND_CALC');
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.samplingPeriod.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      SamplingPeriodType);
    this.correlationEditingSupport.setUpValueChange(this.configObject, this.correlationLimit);
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): CorrelationSet {
    const newCorrelationSet = this.copyFormToPrivateBusinessObject(new CorrelationSet(),
      <CorrelationSet>this.callParam.thisObject);
    delete newCorrelationSet.securitycurrencyList;
    return newCorrelationSet;
  }

  override onHide(event): void {
    this.correlationEditingSupport.destroy();
    super.onHide(event);
  }
}
