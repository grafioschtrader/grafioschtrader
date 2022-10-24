import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
  selector: 'form-input-suggestion',
  template: `
    <ng-container [formGroup]="group">
      <p-autoComplete #input
                      [id]="config.field" [formControlName]="config.field"
                      [inputStyleClass]="'form-control ' + (isRequired? 'required-input': '')"
                      class="p-autocomplete"
                      [style]="{'width':'100%'}" [inputStyle]="{'width':'100%'}"
                      [suggestions]="config.suggestions" (completeMethod)="callSuggestionsFN($event)"
                      pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
      </p-autoComplete>
    </ng-container>
  `
})

export class FormInputSuggestionComponent extends BaseInputComponent {

  public callSuggestionsFN(event): void {
    this.config.suggestionsFN(event);
  }

}
