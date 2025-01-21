import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'tri-state-checkbox',
  template: `
    <div
      class="tri-state-checkbox"
      [class.large]="size === 'large'"
      [attr.data-state]="state"
      tabindex="0"
      (click)="toggleState()"
      (keydown)="handleKeydown($event)"
    >
      {{ getSymbol() }}
    </div>
  `,
  styleUrls: ['../../dynamic-form.css'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TriStateCheckboxComponent),
      multi: true,
    },
  ],
})
export class TriStateCheckboxComponent implements ControlValueAccessor {
  @Input() size: 'small' | 'large' = 'small'; // Default size
  state: 'false' | 'true' | null = null; // Use `null` for "indeterminate"

  private readonly states: ('false' | 'true' | null)[] = ['false', 'true', null];
  private readonly symbols = ['', '✔', '-']; // "-" for the "indeterminate" state

  private onChange: (value: any) => void = () => {};
  private onTouched: () => void = () => {};

  // Toggle state when clicked
  toggleState(): void {
    const currentIndex = this.states.indexOf(this.state);
    const nextIndex = (currentIndex + 1) % this.states.length;
    this.state = this.states[nextIndex];

    // Emit the new value (`null` for "indeterminate")
    this.onChange(this.state);
    this.onTouched();
  }

  // Get the symbol for the current state
  getSymbol(): string {
    const index = this.states.indexOf(this.state);
    return this.symbols[index];
  }

  // Return a string for the data-state attribute (for styling)
  getState(): string {
    return this.state === null ? 'indeterminate' : this.state;
  }

  // Handle keyboard inputs for accessibility
  handleKeydown(event: KeyboardEvent): void {
    if (event.key === ' ' || event.key === 'Enter') {
      event.preventDefault();
      this.toggleState();
    }
  }

  // ControlValueAccessor methods to integrate with Angular forms
  writeValue(value: 'false' | 'true' | null): void {
    this.state = value ?? null; // Default to null if value is undefined
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }
}
