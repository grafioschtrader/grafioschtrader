import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';
import {ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {MultiSelectModule} from 'primeng/multiselect';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';


/**
 * A html multi select component
 */
@Component({
    selector: 'form-input-multi-select',
    template: `
    <ng-container [formGroup]="group">
      <p-multiSelect
              [ngStyle]="{'width': (config.inputWidth+1) + 'em'}"
              class="form-control input-sm"
              [class.required-input]="isRequired"
              [id]="config.field"
              optionValue="key"
              optionLabel="value"
              [options]="config.valueKeyHtmlOptions"
              [formControlName]="config.field"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"/>
    </ng-container>
  `,
    imports: [
        ReactiveFormsModule,
        CommonModule,
        MultiSelectModule,
        TooltipModule,
        TranslateModule,
        FilterOutPipe
    ],
    standalone: true
})
export class FormInputMultiSelectComponent extends BaseInputComponent {
}
