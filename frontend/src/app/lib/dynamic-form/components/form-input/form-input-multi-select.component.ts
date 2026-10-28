import { BaseInputComponent } from '../base.input.component';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { MultiSelectModule } from '@openng/optimus-ui/multiselect';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';

/**
 * A html multi select component
 */
@Component({
  selector: 'form-input-multi-select',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <p-multiSelect
        [style.width]="config.inputWidth + 1 + 'em'"
        class="form-control input-sm"
        [class.required-input]="isRequired"
        [id]="config.field"
        optionValue="key"
        optionLabel="value"
        [options]="config.valueKeyHtmlOptions"
        [formControlName]="config.field"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}" />
    </ng-container>
  `,
  imports: [ReactiveFormsModule, MultiSelectModule, TooltipModule, TranslateModule, FilterOutPipe]
})
export class FormInputMultiSelectComponent extends BaseInputComponent {}
