import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';

/**
 * A html select component
 */
@Component({
  selector: 'form-input-select',
  template: `
    <ng-container [formGroup]="group">
      <select #input
              [ngStyle]="{'width': (config.inputWidth+1) + 'em'}"
              class="form-select form-select-sm"
              [class.required-input]="isRequired"
              [id]="config.field"
              [formControlName]="config.field"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
        @for (s of config.valueKeyHtmlOptions; track s) {
          <option [value]="s.key" [disabled]="s.disabled">
            {{ s.value }}
          </option>
        }
      </select>
    </ng-container>
  `,
  imports: [
    ReactiveFormsModule,
    CommonModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ],
  standalone: true
})

export class FormInputSelectComponent extends BaseInputComponent {
}
