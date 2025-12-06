import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {ReactiveFormsModule} from '@angular/forms';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'form-checkbox',
    template: `
    <ng-container [formGroup]="group">
      <input class="form-check-input" type="checkbox" #input [id]="config.field" [formControlName]="config.field"
             pTooltip="{{config.labelHelpText?.startsWith('*')? config.labelHelpText.slice(1):
              config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
    </ng-container>
  `,
    imports: [
        ReactiveFormsModule,
        TooltipModule,
        TranslateModule,
        FilterOutPipe,
        CommonModule
    ],
    standalone: true
})
export class FormCheckboxComponent extends BaseInputComponent {
}
