/**
 * Background colors of the heatmap tables. Every color is chosen so the inherited text of the scheme (slate 700 in the
 * light scheme, white in the dark scheme) keeps a WCAG contrast of at least 4.5:1 on it over the whole value range: the
 * light scheme uses pale tints, the dark scheme deep shades.
 */
export class HeatmapColors {
  /**
   * Background of a correlation cell, from red at -1 over yellow to green at +1.
   *
   * @param value - Correlation coefficient in [-1, +1]
   * @param isDark - Whether the dark scheme is active
   * @returns An `hsl()` color string
   */
  static correlationCellBackground(value: number, isDark: boolean): string {
    const hue = (value + 1) * 58;
    return isDark ? `hsl(${hue}, 60%, 28%)` : `hsl(${hue}, 100%, 78%)`;
  }

  /**
   * Background of a seasonality return cell. The intensity grows with the rank of the value among the values of the same
   * sign, so outliers do not wash out the bulk of the cells.
   *
   * @param positive - Whether the return is positive (green) or negative (red)
   * @param t - Rank fraction in [0, 1]; 1 for the most extreme value of its sign
   * @param isDark - Whether the dark scheme is active
   * @returns An `hsl()` color string
   */
  static seasonalityCellBackground(positive: boolean, t: number, isDark: boolean): string {
    const hue = positive ? 120 : 0;
    return isDark
      ? `hsl(${hue}, ${Math.round(t * 55)}%, ${Math.round(10 + t * 18)}%)`
      : `hsl(${hue}, ${Math.round(t * 70)}%, ${Math.round(100 - t * 25)}%)`;
  }

  /**
   * Background of a cell whose value deviates from a reference, red or green with an intensity that grows with the
   * deviation. At intensity 0 the cell is barely tinted.
   *
   * @param hue - 0 for red, 120 for green
   * @param intensity - Strength in [0, 1]
   * @param isDark - Whether the dark scheme is active
   * @returns An `hsl()` color string
   */
  static deviationCellBackground(hue: number, intensity: number, isDark: boolean): string {
    return isDark
      ? `hsl(${hue}, ${Math.round(intensity * 55)}%, ${Math.round(10 + intensity * 18)}%)`
      : `hsl(${hue}, 70%, ${95 - intensity * 15}%)`;
  }

  /**
   * Background of a cell on a continuous green-yellow-red scale, for example the error of an estimate.
   *
   * @param hue - Hue in [0, 120]; 120 is green, 0 is red
   * @param isDark - Whether the dark scheme is active
   * @returns An `hsl()` color string
   */
  static scaleCellBackground(hue: number, isDark: boolean): string {
    return isDark ? `hsl(${hue}, 60%, 28%)` : `hsl(${hue}, 70%, 85%)`;
  }
}
