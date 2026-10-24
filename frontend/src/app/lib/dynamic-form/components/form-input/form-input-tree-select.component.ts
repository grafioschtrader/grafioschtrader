import { BaseInputComponent } from '../base.input.component';
import { Component, DestroyRef, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TreeSelectModule } from '@openng/optimus-ui/treeselect';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';

/**
 * A form input component wrapping Optimus TreeSelect with checkbox selection mode. Displays a hierarchical tree of
 * options where parent and child nodes can be selected via checkboxes. The tree nodes are provided through
 * {@code config.treeNodes}. Uses ngModel binding because Optimus TreeSelect checkbox mode requires an iterable
 * selection object that is incompatible with reactive form controls.
 */
@Component({
  selector: 'form-input-tree-select',
  template: `
    @let rev = notifier?.revision();
    <p-treeselect
      [style]="{ width: '100%' }"
      [class.required-input]="isRequired"
      [id]="config.field"
      [options]="config.treeNodes ?? noTreeNodes"
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
      pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}" />
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
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true
})
export class FormInputTreeSelectComponent extends BaseInputComponent implements OnInit {
  /** Shown while config.treeNodes is not set; one constant, so the binding does not change on every check. */
  readonly noTreeNodes: TreeNode[] = [];
  selectedNodes: any;
  private readonly destroyRef = inject(DestroyRef);
  private updatingFromNgModel = false;

  override ngOnInit(): void {
    super.ngOnInit();
    // Sync initial value from form control to ngModel
    const formControl = this.group.get(this.config.field);
    this.selectedNodes = formControl?.value || undefined;
    // Listen for programmatic changes from the form control
    formControl?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((val) => {
      if (!this.updatingFromNgModel) {
        this.selectedNodes = val || undefined;
      }
    });
  }

  onSelectionChange(value: any): void {
    this.updatingFromNgModel = true;
    this.group.get(this.config.field)?.setValue(value);
    this.updatingFromNgModel = false;
  }
}
