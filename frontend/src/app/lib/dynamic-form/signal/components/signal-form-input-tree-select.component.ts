import { Component, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TreeNode } from '@openng/optimus-ui/api';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TreeSelectModule } from '@openng/optimus-ui/treeselect';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Optimus TreeSelect in checkbox mode of a signal dynamic form. Like FormInputTreeSelectComponent it binds through
 * ngModel, because the checkbox mode needs an iterable instead of the null of an empty field. The form stores node
 * keys: Optimus adds cyclic parent references to its option nodes, which must stay outside the signal form model.
 */
@Component({
  selector: 'signal-form-input-tree-select',
  template: `
    @let config = this.view();
    <p-treeselect
      [style]="{ width: '100%' }"
      [class.required-input]="isRequired()"
      [id]="fieldName"
      [options]="config.treeNodes ?? noTreeNodes"
      [ngModel]="selectedNodes()"
      [ngModelOptions]="{ standalone: true }"
      (ngModelChange)="selectNodes($event)"
      (onHide)="field()().markAsTouched()"
      [disabled]="field()().disabled()"
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
  imports: [FormsModule, TreeSelectModule, TooltipModule, TranslateModule, FilterOutPipe]
})
export class SignalFormInputTreeSelectComponent extends SignalBaseInputComponent<string[] | null> {
  /** Shown while config.treeNodes is not set; one constant, so the binding does not change on every check. */
  readonly noTreeNodes: TreeNode[] = [];

  protected readonly selectedNodes = computed(() => {
    const keys = new Set(this.field()().value() ?? []);
    const selected: TreeNode[] = [];
    const visit = (nodes: TreeNode[]): void => {
      for (const node of nodes) {
        if (node.key != null && keys.has(node.key)) selected.push(node);
        if (node.children) visit(node.children);
      }
    };
    visit(this.view().treeNodes ?? this.noTreeNodes);
    return selected.length ? selected : undefined;
  });

  protected selectNodes(nodes: TreeNode[] | null): void {
    const keys = nodes?.flatMap((node) => (node.key == null ? [] : [node.key])) ?? [];
    this.field()().value.set(keys.length ? keys : null);
    this.field()().markAsDirty();
  }
}
