import {Component} from '@angular/core';
import {ReactiveFormsModule, UntypedFormGroup} from '@angular/forms';

import {FieldFormFormGroupConfig} from '../../models/field.form.form.group.config';
import {FieldConfig} from '../../models/field.config';
import {FormConfig} from '../../models/form.config';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';
import {AngularSvgIconModule} from 'angular-svg-icon';


/**
 * Output of a single button, It is assumed that the output of the buttons is from left to right.
 * It has a button with submit and to buttons with an expected function. One with function does not show
 * a label only the icon.
 */
@Component({
  selector: 'form-pbutton',
  template: `
    @if (!config.buttonFN) {
      <button pButton class="btn btn-primary ms-1" [loading]="config.groupItemUseOrLoading"
              type="submit" [label]="config.labelKey | translate" [disabled]="!group.valid || config.disabled"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP' }}">
        @if (config.icon) {
          <svg-icon [src]="config.icon" [svgStyle]="{ 'width.px':16, 'height.px':16 }"></svg-icon>
        }
      </button>
    }
    @if (config.buttonFN && !config.labelKey.endsWith('_')) {
      <button pButton class="btn btn-primary ms-1"
              [disabled]="config.disabled"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP' }}"
              type="button" [label]="config.labelKey | translate" (click)="config.buttonFN($event)">
        @if (config.icon) {
          <svg-icon [src]="config.icon" [svgStyle]="{ 'width.px':16, 'height.px':16}"></svg-icon>
        }
      </button>
    }
    @if (config.buttonFN && config.labelKey.endsWith('_')) {
      <button pButton
              class="btn btn-primary ms-1 button-no-label"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
              [disabled]="config.disabled" type="button" (click)="config.buttonFN($event)">
        @if (config.icon) {
          <svg-icon [src]="config.icon" [svgStyle]="{ 'width.px':13, 'height.px':13}"></svg-icon>
        }
      </button>
    }
  `,
  imports: [
    ButtonModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe,
    AngularSvgIconModule,
    ReactiveFormsModule
],
  standalone: true
})

export class FormPButtonComponent implements FieldFormFormGroupConfig {
  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;
}
