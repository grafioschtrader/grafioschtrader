import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';
import { TranslateModule } from '@ngx-translate/core';
import { SelectModule } from '@openng/optimus-ui/select';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { UpperCaseDirective } from '../../components/form-input/upper-case.directive';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/** Optimus Select with grouped, decorated items of a signal dynamic form. */
@Component({
  selector: 'signal-form-input-dropdown',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <p-select
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        (onBlur)="bridge.touch.emit()"
        #input
        [group]="config.groupItemUseOrLoading"
        [options]="config.groupItem"
        optionLabel="value"
        optionValue="key"
        optionGroupChildren="children"
        [filter]="!!config.filter"
        filterBy="optionsText"
        [resetFilterOnHide]="true"
        [id]="fieldName"
        [class.required-input]="isRequired()"
        [upperCase]="config.upperCase"
        scrollHeight="400px"
        class="p-autocomplete"
        [style]="{ width: '100%' }"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}">
        <ng-template let-group pTemplate="group">
          <div class="flex align-items-center">
            @if (group.img) {
              <img src="assets/icons/flag_placeholder.png" [class]="group.img" style="width: 20px" />
            }
            <span>{{ group.optionsText }}</span>
          </div>
        </ng-template>

        <ng-template let-entry pTemplate="item">
          <div class="select-item">
            @if (entry.img) {
              <img src="assets/icons/flag_placeholder.png" [class]="entry.img" style="width: 20px" />
            }
            @if (entry.iconClass) {
              <i [class]="entry.iconClass" style="width: 20px" aria-hidden="true"></i>
            }
            <div>
              @if (entry.textSegments) {
                @for (segment of entry.textSegments; track $index) {
                  <span [class]="segment.cssClass" [title]="segment.title || ''">{{ segment.text }}</span>
                }
              } @else {
                {{ entry.optionsText }}
              }
            </div>
          </div>
        </ng-template>
        <ng-template let-entry pTemplate="selectedItem">
          @if (entry?.textSegments) {
            @for (segment of entry.textSegments; track $index) {
              <span [class]="segment.cssClass" [title]="segment.title || ''">{{ segment.text }}</span>
            }
          } @else {
            {{ entry?.value }}
          }
        </ng-template>
      </p-select>
    </span>
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    SelectModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe,
    UpperCaseDirective
  ]
})
export class SignalFormInputDropdownComponent extends SignalBaseInputComponent {}
