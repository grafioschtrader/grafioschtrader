import {InjectionToken} from '@angular/core';

/**
 * Injection token for providing the task type enum.
 * The lib layer uses TaskTypeBase as the default, while the application layer
 * can provide its own extended task type enum (e.g., TaskTypeExtended in Grafioschtrader).
 *
 * This follows the Dependency Inversion Principle: lib depends on abstraction,
 * app provides the concrete enum implementation.
 */
export const TASK_TYPE_ENUM = new InjectionToken<any>('TaskTypeEnum');
