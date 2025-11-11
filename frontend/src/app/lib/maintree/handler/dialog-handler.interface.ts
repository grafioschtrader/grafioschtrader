import {InjectionToken, Type} from '@angular/core';
import {Observable} from 'rxjs';

/**
 * Interface for handling dialog operations in the main tree.
 * Implementations should be provided at the application layer to avoid
 * domain dependencies in the library layer.
 */
export interface DialogHandler {
  /**
   * Opens a dynamic edit dialog for creating or editing entities.
   * @param componentType The component type to open in the dialog
   * @param parentObject The parent object (e.g., Tenant)
   * @param data The entity data to edit, or null for create
   * @param titleKey Translation key for the dialog title
   * @returns Observable that emits when the dialog closes
   */
  openEditDialog(
    componentType: Type<any>,
    parentObject: any,
    data: any,
    titleKey: string
  ): Observable<any>;

  /**
   * Opens a tenant edit dialog with optional currency-only mode.
   * @param data The tenant data to edit
   * @param onlyCurrency Whether to only allow currency editing
   * @returns Observable that emits when the dialog closes
   */
  openTenantDialog(data: any, onlyCurrency: boolean): Observable<any>;
}

/**
 * Injection token for the DialogHandler.
 * Application modules should provide an implementation of DialogHandler.
 */
export const DIALOG_HANDLER = new InjectionToken<DialogHandler>('DIALOG_HANDLER');
