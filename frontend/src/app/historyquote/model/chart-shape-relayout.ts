/**
 * Helpers that decide whether a Plotly relayout of the time-series chart changed its drawings. Zoom, pan, autoscale,
 * legend clicks and redraws for a new color scheme also raise `plotly_relayout`; they must neither create an undo
 * step nor save the shapes.
 */
export class ChartShapeRelayout {
  /**
   * Whether a relayout payload touches the shapes: drawing, erasing, undo/redo and deleting send the whole `shapes`
   * array, dragging or resizing a shape sends keys such as `shapes[0].x0`.
   *
   * @param eventData - The payload of the `plotly_relayout` event
   * @returns True when at least one key refers to the shapes
   */
  static touchesShapes(eventData: Record<string, unknown> | null | undefined): boolean {
    return eventData != null && Object.keys(eventData).some((key) => key === 'shapes' || key.startsWith('shapes['));
  }

  /**
   * Whether two shape states are equal, compared by their serialized form as stored in the history.
   *
   * @param a - First state
   * @param b - Second state
   * @returns True for equal states
   */
  static sameShapes(a: unknown[] | null | undefined, b: unknown[] | null | undefined): boolean {
    return JSON.stringify(a ?? []) === JSON.stringify(b ?? []);
  }
}
