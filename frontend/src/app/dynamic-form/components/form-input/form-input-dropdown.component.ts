import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
  selector: 'form-input-dropdown',
  template: `
    <ng-container [formGroup]="group">
      <p-dropdown #input
                  [group]="config.groupItemUse"
                  [options]="config.groupItem"
                  optionLabel="value"
                  optionValue="key"
                  optionGroupChildren="children"
                  [id]="config.field" [formControlName]="config.field"
                  [class.required-input]="isRequired"
                  [upperCase]="config.upperCase"
                  scrollHeight="400px"
                  class="p-autocomplete"
                  [style]="{'width':'100%'}"
                  pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
        <ng-template let-group pTemplate="group">
          <div class="flex align-items-center">
            <img *ngIf="group.img" src="assets/icons/flag_placeholder.png" [class]="group.img" style="width: 20px"/>
            <span>{{group.optionsText}}</span>
          </div>
        </ng-template>

        <ng-template let-entry pTemplate="item">
          <div class="select-item">
            <img *ngIf="entry.img" src="assets/icons/flag_placeholder.png" [class]="entry.img" style="width: 20px"/>
            <div>{{entry.optionsText}}</div>
          </div>
        </ng-template>

      </p-dropdown>
    </ng-container>
  `
})

export class FormInputDropdownComponent extends BaseInputComponent {

}
