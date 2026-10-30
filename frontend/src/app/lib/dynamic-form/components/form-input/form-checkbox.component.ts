import { Component } from '@angular/core';
import { BaseInputComponent } from '../base.input.component';
import { ReactiveFormsModule } from '@angular/forms';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';

@Component({
  selector: 'form-checkbox',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <input
        class="form-check-input"
        type="checkbox"
        #input
        [id]="fieldName"
        [formControlName]="fieldName"
        pTooltip="{{
          config.labelHelpText?.startsWith('*')
            ? config.labelHelpText.slice(1)
            : (config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP')
        }}" />
    </ng-container>
  `,
  imports: [ReactiveFormsModule, TooltipModule, TranslateModule, FilterOutPipe]
})
export class FormCheckboxComponent extends BaseInputComponent {}
