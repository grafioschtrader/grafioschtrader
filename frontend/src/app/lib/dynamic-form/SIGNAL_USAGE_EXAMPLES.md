# Dynamic Form Signal API - Usage Examples

This document demonstrates how to use both the traditional API and the new Signal-based API for the dynamic-form component.

---

## Traditional API (Existing - No Changes Required)

All existing components continue to work without any modifications.

### Example: Traditional Component

```typescript
import { Component } from '@angular/core';
import { Validators } from '@angular/forms';
import { FieldFormGroup } from './lib/dynamic-form/models/form.group.definition';
import { FormConfig } from './lib/dynamic-form/models/form.config';
import { InputType } from './lib/dynamic-form/models/input.type';
import { DataType } from './lib/dynamic-form/models/data.type';

@Component({
  selector: 'app-traditional-form',
  template: `
    <dynamic-form
      [formConfig]="formConfig"
      [config]="fieldConfigs"
      (submitBt)="onSubmit($event)"
      #dynamicForm>
    </dynamic-form>

    <button (click)="dynamicForm.setDisabled('email', true)">
      Disable Email
    </button>
  `
})
export class TraditionalFormComponent {
  formConfig: FormConfig = {
    labelColumns: 2,
    language: 'en',
    dateFormat: 'dd.mm.yyyy'
  };

  fieldConfigs: FieldFormGroup[] = [
    {
      field: 'email',
      labelKey: 'EMAIL',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required, Validators.email]
    },
    {
      field: 'name',
      labelKey: 'NAME',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required]
    },
    {
      field: 'submit',
      labelKey: 'SUBMIT',
      inputType: InputType.Button,
      dataType: DataType.None
    }
  ];

  onSubmit(value: any) {
    console.log('Form submitted:', value);
  }
}
```

---

## Signal API (New - Opt-In)

New components can use the Signal-based API for better reactivity and performance.

### Example 1: Basic Signal Form

```typescript
import { Component, signal } from '@angular/core';
import { Validators } from '@angular/forms';
import { FieldFormGroup } from './lib/dynamic-form/models/form.group.definition';
import { FormConfig } from './lib/dynamic-form/models/form.config';
import { InputType } from './lib/dynamic-form/models/input.type';
import { DataType } from './lib/dynamic-form/models/data.type';

@Component({
  selector: 'app-signal-form',
  template: `
    <dynamic-form
      [formConfigSignal]="formConfig"
      [configSignal]="fieldConfigs"
      (submitSignal)="onSubmit($event)"
      #dynamicForm>
    </dynamic-form>

    <!-- Reactive display using signals -->
    <div class="form-status">
      <p>Form Valid: {{ dynamicForm.formValid() }}</p>
      <p>Form Dirty: {{ dynamicForm.formDirty() }}</p>
      <p>Current Value: {{ dynamicForm.formValue() | json }}</p>
    </div>

    <button (click)="dynamicForm.setDisabledSignal('email', true)">
      Disable Email
    </button>
  `
})
export class SignalFormComponent {
  formConfig = signal<FormConfig>({
    labelColumns: 2,
    language: 'en',
    dateFormat: 'dd.mm.yyyy'
  });

  fieldConfigs = signal<FieldFormGroup[]>([
    {
      field: 'email',
      labelKey: 'EMAIL',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required, Validators.email]
    },
    {
      field: 'name',
      labelKey: 'NAME',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required]
    },
    {
      field: 'submit',
      labelKey: 'SUBMIT',
      inputType: InputType.Button,
      dataType: DataType.None
    }
  ]);

  onSubmit(value: any) {
    console.log('Form submitted via Signal:', value);
  }
}
```

### Example 2: Reactive Programming with Computed Signals

```typescript
import { Component, signal, computed, ViewChild } from '@angular/core';
import { Validators } from '@angular/forms';
import { DynamicFormComponent } from './lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

@Component({
  selector: 'app-reactive-signal-form',
  template: `
    <dynamic-form
      [formConfigSignal]="formConfig"
      [configSignal]="fieldConfigs"
      (submitSignal)="onSubmit($event)"
      #dynamicForm>
    </dynamic-form>

    <!-- Computed display based on form state -->
    <div class="status-panel">
      <p [class.valid]="canSubmit()">
        Status: {{ formStatus() }}
      </p>

      <p>
        Required fields: {{ requiredFieldCount() }}
      </p>

      <p>
        Total fields: {{ dynamicForm.controlsSignal().length }}
      </p>

      <button
        [disabled]="!canSubmit()"
        (click)="manualSubmit()">
        Manual Submit
      </button>
    </div>
  `,
  styles: [`
    .status-panel {
      margin-top: 20px;
      padding: 15px;
      border: 1px solid #ccc;
      border-radius: 5px;
    }

    .valid {
      color: green;
    }
  `]
})
export class ReactiveSignalFormComponent {
  @ViewChild('dynamicForm') dynamicForm!: DynamicFormComponent;

  formConfig = signal<FormConfig>({
    labelColumns: 3,
    language: 'en',
    dateFormat: 'dd.mm.yyyy'
  });

  fieldConfigs = signal<FieldFormGroup[]>([
    {
      field: 'email',
      labelKey: 'EMAIL',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required, Validators.email]
    },
    {
      field: 'password',
      labelKey: 'PASSWORD',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required, Validators.minLength(8)]
    },
    {
      field: 'name',
      labelKey: 'NAME',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required]
    },
    {
      field: 'submit',
      labelKey: 'SUBMIT',
      inputType: InputType.Button,
      dataType: DataType.None
    }
  ]);

  // Computed signals based on form state
  formStatus = computed(() =>
    this.dynamicForm?.formValid() ? 'Valid ✓' : 'Invalid ✗'
  );

  requiredFieldCount = computed(() =>
    this.dynamicForm?.controlsSignal()
      .filter(c => c.validation?.some(v => v === Validators.required))
      .length ?? 0
  );

  canSubmit = computed(() =>
    this.dynamicForm?.formValid() && this.dynamicForm?.formDirty()
  );

  onSubmit(value: any) {
    console.log('Reactive form submitted:', value);
  }

  manualSubmit() {
    if (this.canSubmit()) {
      console.log('Manual submit:', this.dynamicForm.formValue());
    }
  }
}
```

### Example 3: Dynamic Form Configuration Changes

```typescript
import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-dynamic-config-form',
  template: `
    <div>
      <button (click)="addField()">Add Field</button>
      <button (click)="removeField()">Remove Field</button>
      <button (click)="toggleRequired()">Toggle Required</button>
    </div>

    <dynamic-form
      [formConfigSignal]="formConfig"
      [configSignal]="fieldConfigs"
      (submitSignal)="onSubmit($event)"
      #dynamicForm>
    </dynamic-form>

    <div class="info">
      <p>Current field count: {{ fieldConfigs().length }}</p>
      <p>Required enabled: {{ requiredEnabled() }}</p>
    </div>
  `
})
export class DynamicConfigFormComponent {
  formConfig = signal<FormConfig>({
    labelColumns: 2,
    language: 'en',
    dateFormat: 'dd.mm.yyyy'
  });

  fieldConfigs = signal<FieldFormGroup[]>([
    {
      field: 'name',
      labelKey: 'NAME',
      inputType: InputType.Input,
      dataType: DataType.String,
      validation: [Validators.required]
    },
    {
      field: 'submit',
      labelKey: 'SUBMIT',
      inputType: InputType.Button,
      dataType: DataType.None
    }
  ]);

  requiredEnabled = signal<boolean>(true);
  fieldCounter = 1;

  addField() {
    this.fieldCounter++;
    this.fieldConfigs.update(configs => {
      // Insert before the submit button (last item)
      const withoutSubmit = configs.slice(0, -1);
      const submitButton = configs[configs.length - 1];

      return [
        ...withoutSubmit,
        {
          field: `field${this.fieldCounter}`,
          labelKey: `FIELD_${this.fieldCounter}`,
          inputType: InputType.Input,
          dataType: DataType.String,
          validation: this.requiredEnabled() ? [Validators.required] : []
        },
        submitButton
      ];
    });
  }

  removeField() {
    this.fieldConfigs.update(configs => {
      if (configs.length <= 2) return configs; // Keep at least name + submit
      // Remove second-to-last item (before submit button)
      return [...configs.slice(0, -2), configs[configs.length - 1]];
    });
  }

  toggleRequired() {
    this.requiredEnabled.update(enabled => !enabled);

    this.fieldConfigs.update(configs =>
      configs.map(config => ({
        ...config,
        validation: config.inputType === InputType.Button
          ? []
          : (this.requiredEnabled() ? [Validators.required] : [])
      }))
    );
  }

  onSubmit(value: any) {
    console.log('Dynamic form submitted:', value);
  }
}
```

### Example 4: Using Computed Signals from Dynamic Form

```typescript
import { Component, signal, computed, ViewChild, effect } from '@angular/core';
import { DynamicFormComponent } from './lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

@Component({
  selector: 'app-computed-example',
  template: `
    <dynamic-form
      [formConfigSignal]="formConfig"
      [configSignal]="fieldConfigs"
      (submitSignal)="onSubmit($event)"
      #dynamicForm>
    </dynamic-form>

    <div class="analysis">
      <h3>Form Analysis (Computed Signals)</h3>

      <p>Button count: {{ dynamicForm.buttonsSignal().length }}</p>
      <p>Control count: {{ dynamicForm.controlsSignal().length }}</p>
      <p>Total fields (flattened): {{ dynamicForm.controlsFlattenSignal().length }}</p>
      <p>Form groups: {{ dynamicForm.formGroupsSignal().length }}</p>

      <h4>Current Field Values:</h4>
      <ul>
        @for (control of dynamicForm.controlsSignal(); track control.field) {
          <li>{{ control.labelKey }}: {{ getFieldValue(control.field) }}</li>
        }
      </ul>
    </div>
  `
})
export class ComputedExampleComponent {
  @ViewChild('dynamicForm') dynamicForm!: DynamicFormComponent;

  formConfig = signal<FormConfig>({
    labelColumns: 2,
    language: 'en',
    dateFormat: 'dd.mm.yyyy'
  });

  fieldConfigs = signal<FieldFormGroup[]>([
    {
      field: 'email',
      labelKey: 'EMAIL',
      inputType: InputType.Input,
      dataType: DataType.String
    },
    {
      field: 'name',
      labelKey: 'NAME',
      inputType: InputType.Input,
      dataType: DataType.String
    },
    {
      field: 'submit',
      labelKey: 'SUBMIT',
      inputType: InputType.Button,
      dataType: DataType.None
    }
  ]);

  constructor() {
    // Effect to log form value changes
    effect(() => {
      if (this.dynamicForm) {
        console.log('Form value changed:', this.dynamicForm.formValue());
      }
    });
  }

  getFieldValue(fieldName: string): any {
    return this.dynamicForm?.formValue()[fieldName] || 'N/A';
  }

  onSubmit(value: any) {
    console.log('Form submitted:', value);
  }
}
```

---

## API Comparison

### Inputs

| Traditional API | Signal API | Description |
|----------------|------------|-------------|
| `@Input() formConfig` | `formConfigSignal = input<FormConfig>()` | Form configuration |
| `@Input() config` | `configSignal = input<FieldFormGroup[]>()` | Field configurations |
| `@Input() formGroupDefinition` | `formGroupDefinitionSignal = input<FormGroupDefinition>()` | Form group definition |

### Outputs

| Traditional API | Signal API | Description |
|----------------|------------|-------------|
| `@Output() submitBt` | `submitSignal = output<any>()` | Form submission event |

### State Access

| Traditional API | Signal API | Description |
|----------------|------------|-------------|
| `dynamicForm.value` | `dynamicForm.formValue()` | Current form value |
| `dynamicForm.valid` | `dynamicForm.formValid()` | Form validation state |
| `dynamicForm.changes` | `dynamicForm.formValue()` + `effect()` | Form value changes |
| N/A | `dynamicForm.formTouched()` | Form touched state |
| N/A | `dynamicForm.formDirty()` | Form dirty state |

### Computed Properties

| Traditional API | Signal API | Description |
|----------------|------------|-------------|
| `dynamicForm.controls` | `dynamicForm.controlsSignal()` | All control fields |
| `dynamicForm.buttons` | `dynamicForm.buttonsSignal()` | All button fields |
| `dynamicForm.formGroups` | `dynamicForm.formGroupsSignal()` | All form groups |
| `dynamicForm.controlsFlatten` | `dynamicForm.controlsFlattenSignal()` | Flattened controls |
| `dynamicForm.submitButton` | `dynamicForm.submitButtonSignal()` | Submit button config |

### Methods

| Traditional API | Signal API | Description |
|----------------|------------|-------------|
| `setDisabled(name, disable)` | `setDisabledSignal(name, disable)` | Set field disabled state |
| `setDisableAll(disable)` | `setDisableAllSignal(disable)` | Set all fields disabled |
| `setValue(name, value)` | `setValueSignal(name, value)` | Set field value |

---

## Migration Guide

### Step 1: Update Imports (if using Signals)

```typescript
// Add signal imports
import { signal, computed, effect } from '@angular/core';
```

### Step 2: Convert Properties to Signals

```typescript
// Before (Traditional)
formConfig: FormConfig = { labelColumns: 2 };
fieldConfigs: FieldFormGroup[] = [...];

// After (Signal)
formConfig = signal<FormConfig>({ labelColumns: 2 });
fieldConfigs = signal<FieldFormGroup[]>([...]);
```

### Step 3: Update Template Bindings

```typescript
// Before (Traditional)
<dynamic-form
  [formConfig]="formConfig"
  [config]="fieldConfigs"
  (submitBt)="onSubmit($event)">
</dynamic-form>

// After (Signal)
<dynamic-form
  [formConfigSignal]="formConfig"
  [configSignal]="fieldConfigs"
  (submitSignal)="onSubmit($event)">
</dynamic-form>
```

### Step 4: Use Signal State in Template

```typescript
// Access reactive state
<p>Valid: {{ dynamicForm.formValid() }}</p>
<p>Value: {{ dynamicForm.formValue() | json }}</p>
```

### Step 5: Use Signal-Friendly Methods

```typescript
// Traditional method still works
dynamicForm.setDisabled('email', true);

// Signal-friendly method (automatically syncs signals)
dynamicForm.setDisabledSignal('email', true);
```

---

## Best Practices

1. **Choose One API Per Component**: Don't mix traditional and Signal APIs in the same component
2. **Use Computed Signals**: Leverage computed signals for derived state instead of manual calculations
3. **Effect for Side Effects**: Use `effect()` to react to form changes, not subscriptions
4. **Signal Methods**: Use `setValueSignal()`, `setDisabledSignal()` when using Signal API for proper sync
5. **Type Safety**: Always provide types for signals: `signal<FormConfig>(...)`, not just `signal(...)`

---

## Performance Benefits

### Traditional API
- Change detection runs on entire component tree
- Manual subscription management required
- Observable chains can be complex

### Signal API
- Fine-grained reactivity - only what changed updates
- Automatic cleanup (no manual unsubscribe)
- Computed signals cache results
- Better TypeScript inference
- Aligned with Angular's future direction

---

## Compatibility

- ✅ Both APIs work simultaneously in the same application
- ✅ No breaking changes to existing code
- ✅ Gradual migration supported
- ✅ Signal API requires Angular 16+ (input signals)
- ✅ Best experience with Angular 17+ (output signals)

---

## Summary

The dynamic-form component now supports both traditional and Signal-based APIs:

- **Traditional API**: Continues to work exactly as before (zero breaking changes)
- **Signal API**: New opt-in approach with better reactivity and performance
- **Backward Compatible**: Both APIs coexist peacefully
- **Future-Proof**: Signal API aligns with Angular's direction (v17+)

Choose the API that best fits your project's needs and migrate at your own pace!
