import { ClassDescriptorInputAndShow } from '../dynamicfield/field.descriptor.input.and.show';

export type DashboardWidth = 'THIRD' | 'HALF' | 'TWO_THIRDS' | 'FULL';

/**
 * Settings of one widget instance. The properties a type accepts are declared by its server side descriptor, so this
 * stays open; the server rejects anything a handler does not know.
 */
export type DashboardConfig = { [setting: string]: number | string | boolean | null };
export interface DashboardWidget {
  instanceId: string;
  type: string;
  width: DashboardWidth;
  config: DashboardConfig;
}
export interface DashboardResult {
  instanceId: string;
  type: string;
  status: 'OK' | 'EMPTY' | 'PARTIAL' | 'UNAVAILABLE' | 'ERROR';
  /**
   * Counted lists for the generic renderer, and for a type with rows of its own shape the document under custom. A
   * widget fills one of the two, never both.
   */
  payload: { lists: DashboardList[]; custom?: unknown } | null;
  dataAsOf: string;
  errorCode?: string;
  stale?: boolean;
}
export interface DashboardList {
  titleKey: string;
  count: number;
  userCount: number | null;
  destination: string;
  rows: DashboardRow[];
}
export interface DashboardRow {
  id: number;
  nickname?: string;
  subject?: string;
  entity?: string;
  noteRequest?: string;
  creationTime: string;
  dayLimit?: number;
  validUntil?: string;
}
export interface DashboardDocument {
  persisted: boolean;
  revision: number | null;
  schemaVersion: number;
  activeTenantId: number | null;
  generatedAt: string;
  widgets: DashboardWidget[];
  results: DashboardResult[];
  remainingCapacity: number;
}
export interface DashboardDescriptor {
  type: string;
  titleKey: string;
  descriptionKey: string;
  defaultWidth: DashboardWidth;
  defaultConfig: DashboardConfig;
  formDefinition: ClassDescriptorInputAndShow;
}
export interface DashboardCatalogue {
  descriptors: DashboardDescriptor[];
  remainingCapacity: number;
  defaults: DashboardWidget[];
}

/** A failed refresh retains only this instance's previously successful data, visibly marked stale. */
export function mergeDashboardResult(previous: DashboardResult | undefined, next: DashboardResult): DashboardResult {
  return next.status === 'ERROR' && previous?.payload ? { ...previous, stale: true, errorCode: next.errorCode } : next;
}

/** Keep reading order and DOM order identical; moving past either boundary is a no-op. */
export function moveDashboardWidget(widgets: DashboardWidget[], from: number, to: number): DashboardWidget[] {
  if (from < 0 || from >= widgets.length || to < 0 || to >= widgets.length) return widgets;
  const result = [...widgets];
  result.splice(to, 0, result.splice(from, 1)[0]);
  return result;
}

/** A type sits either on the dashboard or in the tray of available widgets, never in both. */
export function availableDescriptors(
  descriptors: DashboardDescriptor[],
  widgets: DashboardWidget[]
): DashboardDescriptor[] {
  const used = new Set(widgets.map((w) => w.type));
  return descriptors.filter((d) => !used.has(d.type));
}

/** Insert at the drop position; an index outside the list appends. */
export function insertDashboardWidget(
  widgets: DashboardWidget[],
  widget: DashboardWidget,
  to: number
): DashboardWidget[] {
  const result = [...widgets];
  result.splice(to < 0 || to > widgets.length ? widgets.length : to, 0, widget);
  return result;
}

/** A widget taken off the dashboard keeps its width and settings until the edit session ends. */
export function widgetFromDescriptor(descriptor: DashboardDescriptor, remembered?: DashboardWidget): DashboardWidget {
  return (
    remembered ?? {
      instanceId: crypto.randomUUID(),
      type: descriptor.type,
      width: descriptor.defaultWidth,
      config: { ...descriptor.defaultConfig }
    }
  );
}
