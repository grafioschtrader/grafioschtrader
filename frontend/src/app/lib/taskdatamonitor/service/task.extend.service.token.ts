import {InjectionToken} from '@angular/core';
import {ITaskExtendService} from '../component/itask.extend.service';

/**
 * Injection token for providing application-specific extensions to task data monitoring.
 * The lib layer defines the interface contract, while the application layer provides
 * the concrete implementation (e.g., SecurityService in Grafioschtrader).
 *
 * This follows the Dependency Inversion Principle: lib depends on abstraction,
 * app provides the implementation.
 */
export const TASK_EXTENDED_SERVICE = new InjectionToken<ITaskExtendService>('TaskExtendService');
