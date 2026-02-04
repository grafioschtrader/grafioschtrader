import {Component, Input, OnInit} from '@angular/core';
import {HelpIds} from '../../lib/help/help.ids';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {TaEditParam, TaEditReturn} from './indicator.definitions';
import {DynamicFieldSimpleEditBase} from '../../lib/edit/dynamic.field.simple.edit.base.directive';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {Dialog} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

@Component({
  selector: 'indicator-edit',
  template: `
    <p-dialog header="{{'DEFINITION' | translate}}: {{taEditParam.taIndicators | translate}}"
              [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: true,
  imports: [Dialog, DynamicFormComponent, TranslatePipe]
})
export class IndicatorEditComponent extends DynamicFieldSimpleEditBase implements OnInit {
  @Input() taEditParam: TaEditParam;

  constructor(public translateService: TranslateService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
    this.config = [...this.taEditParam.fieldConfig];
  }

  submit(values: { [name: string]: any }): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED,
      new TaEditReturn(this.taEditParam.taIndicators, values)));
  }

  protected override initialize(): void {

    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    // Use requestAnimationFrame to ensure Angular has completed rendering the form controls
    // before transferring the data model values to the form
    requestAnimationFrame(() => {
      this.form.transferBusinessObjectToForm(this.taEditParam.taDynamicDataModel);
    });
  }
}
