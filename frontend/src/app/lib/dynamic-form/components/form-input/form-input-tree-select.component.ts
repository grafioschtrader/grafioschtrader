import {BaseInputComponent} from '../base.input.component';
import {Component, DoCheck, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {TreeSelectModule} from 'primeng/treeselect';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {TreeNode} from 'primeng/api';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';

/**
 * A form input component wrapping PrimeNG TreeSelect with checkbox selection mode. Displays a hierarchical tree of
 * options where parent and child nodes can be selected via checkboxes. The tree nodes are provided through
 * {@code config.treeNodes}. Uses ngModel binding because PrimeNG TreeSelect checkbox mode requires an iterable
 * selection object that is incompatible with reactive form controls.
 */
@Component({
  selector: 'form-input-tree-select',
  template: `
    <p-treeselect
      [style]="{'width': '100%'}"
      [class.required-input]="isRequired"
      [id]="config.field"
      [options]="treeOptions"
      [(ngModel)]="selectedNodes"
      (ngModelChange)="onSelectionChange($event)"
      selectionMode="checkbox"
      display="chip"
      [filter]="true"
      filterBy="label"
      [showClear]="true"
      [metaKeySelection]="false"
      [propagateSelectionDown]="config.propagateTreeSelection !== false"
      [propagateSelectionUp]="config.propagateTreeSelection !== false"
      [placeholder]="config.placeholder || ''"
      pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"/>
  `,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    CommonModule,
    TreeSelectModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ],
  standalone: true
})
export class FormInputTreeSelectComponent extends BaseInputComponent implements OnInit, DoCheck {
  selectedNodes: any;
  treeOptions: TreeNode[] = [];
  private updatingFromNgModel = false;

  override ngOnInit(): void {
    super.ngOnInit();
    this.treeOptions = this.config.treeNodes || [];
    // Sync initial value from form control to ngModel
    const formControl = this.group.get(this.config.field);
    this.selectedNodes = formControl?.value || undefined;
    // Listen for programmatic changes from the form control
    formControl?.valueChanges.subscribe(val => {
      if (!this.updatingFromNgModel) {
        this.selectedNodes = val || undefined;
      }
    });
  }

  /** Reads config.treeNodes on every change detection cycle to pick up dynamic tree changes. */
  ngDoCheck(): void {
    if (this.config.treeNodes !== this.treeOptions) {
      this.treeOptions = this.config.treeNodes || [];
    }
  }

  onSelectionChange(value: any): void {
    this.updatingFromNgModel = true;
    this.group.get(this.config.field)?.setValue(value);
    this.updatingFromNgModel = false;
  }
}
