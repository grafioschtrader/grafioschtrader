/** The distributed Plotly bundle; its CommonJS export is the library typed by plotly.js itself. */
declare module 'plotly.js/dist/plotly.min.js' {
  const Plotly: typeof import('plotly.js');
  export default Plotly;
}
