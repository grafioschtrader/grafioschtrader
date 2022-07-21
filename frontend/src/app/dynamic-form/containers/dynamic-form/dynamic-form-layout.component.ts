import {Component, Input} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';


import {FieldConfig} from '../../models/field.config';
import {FormConfig} from '../../models/form.config';

/**
 * It handles one input with its label.
 */
@Component({
  selector: 'dynamic-form-layout',
  template: `
    <div *ngIf="formConfig.fieldHeaders && formConfig.fieldHeaders[config.field]" class="row">
      <div class="col-md-12">
        <h5 class="text-center">{{formConfig.fieldHeaders[config.field]}}</h5>
      </div>
    </div>

    <div [ngClass]="'small-padding col-md-' + (config.usedLayoutColumns? config.usedLayoutColumns: 12)">
      <div class="form-group form-group-sm row" [hidden]="config.invisible">
        <label *ngIf="config.labelKey && !config.buttonInForm && !config.labelKey.startsWith('_')"
               [title]="config.labelTitle !== undefined? config.labelTitle: ''"
               [style.color]='config.labelTitle !== undefined? "red": null'
               [for]="config.field"
               [ngClass]="'small-padding control-label col-md-' + (12 /
        (config.usedLayoutColumns? config.usedLayoutColumns: 12) * formConfig.labelcolumns)">
          {{config.labelKey | translate }} {{config.labelSuffix}}
          <i *ngIf="config.labelHelpText" class="fa fa-question-circle-o" (click)="onHelpClick($event)"></i>
        </label>

        <div [ngClass]="'small-padding col-md-' + (config.labelKey? (12 - 12 /
               (config.usedLayoutColumns? config.usedLayoutColumns: 12) * formConfig.labelcolumns): 12)">
          <dynamicField
            [config]="config"
            [formConfig]="formConfig"
            [group]="group">
          </dynamicField>
        </div>
        <error-message [baseFieldFieldgroupConfig]="config" *ngIf="!config.buttonInForm"></error-message>
        <div *ngIf="config.labelShowText" class="bg-info" [innerHTML]="config.labelShowText"></div>
      </div>
    </div>
  `
})

export class DynamicFormLayoutComponent {
  @Input() config: FieldConfig;
  @Input() formConfig: FormConfig;
  @Input() group: UntypedFormGroup;

  onHelpClick(event) {
    this.config.labelShowText = (this.config.labelShowText) ? null : this.config.labelHelpText;
  }

}
