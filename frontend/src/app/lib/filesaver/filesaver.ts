/**
 * FileSaver.ts
 * A saveAs() FileSaver implementation for Angular applications.
 *
 * Based on FileSaver.js by Eli Grey, http://eligrey.com
 * License: MIT
 */

/**
 * Options for configuring file saving behavior
 */
export interface SaveAsOptions {
  /** Automatically add BOM for UTF-8 text files */
  autoBom?: boolean;
}

/**
 * Browser environment detection and API access
 */
class BrowserEnvironment {
  static getURL() {
    return window?.URL || (window as any)?.webkitURL || null;
  }

  static isSupported(): boolean {
    return typeof document !== 'undefined' &&
      typeof window !== 'undefined' &&
      !!this.getURL();
  }
  /**
   * Get navigator object safely
   */
  static getNavigator(): Navigator | null {
    return typeof navigator !== 'undefined' ? navigator : null;
  }

  /**
   * Check if running in macOS WebView
   */
  static isMacOSWebView(): boolean {
    const nav = this.getNavigator();
    return nav !== null &&
      /Macintosh/.test(nav.userAgent) &&
      /AppleWebKit/.test(nav.userAgent) &&
      !/Safari/.test(nav.userAgent);
  }

  /**
   * Check if running in Safari
   */
  static isSafari(): boolean {
    return /constructor/i.test((window as any).HTMLElement?.toString() || '') ||
      Boolean((window as any).safari);
  }

  /**
   * Check if running in Chrome iOS
   */
  static isChromeIOS(): boolean {
    const nav = this.getNavigator();
    return nav !== null && /CriOS\/[\d]+/.test(nav.userAgent);
  }

  /**
   * Check if browser has msSaveOrOpenBlob support
   */
  static hasMsSaveBlob(): boolean {
    const nav = this.getNavigator();
    return nav !== null && 'msSaveOrOpenBlob' in nav;
  }

  /**
   * Check if browser supports download attribute
   */
  static hasDownloadAttribute(): boolean {
    return typeof HTMLAnchorElement !== 'undefined' &&
      'download' in HTMLAnchorElement.prototype;
  }
}

/**
 * Adds BOM (Byte Order Mark) to blob for UTF-8 text files if needed
 */
function addBom(blob: Blob, opts: SaveAsOptions = {}): Blob {
  const options = { autoBom: false, ...opts };

  // Add BOM for UTF-8 XML and text/* types
  if (options.autoBom &&
    /^\s*(?:text\/\S*|application\/xml|\S*\/\S*\+xml)\s*;.*charset\s*=\s*utf-8/i.test(blob.type)) {
    return new Blob([String.fromCharCode(0xFEFF), blob], { type: blob.type });
  }
  return blob;
}

/**
 * Downloads a file from URL using XMLHttpRequest
 */
function downloadFromUrl(url: string, name?: string, opts?: SaveAsOptions): void {
  const xhr = new XMLHttpRequest();
  xhr.open('GET', url);
  xhr.responseType = 'blob';

  xhr.onload = (): void => {
    if (xhr.response) {
      saveAs(xhr.response, name, opts);
    }
  };

  xhr.onerror = (): void => {
    console.error('Could not download file from URL:', url);
  };

  xhr.send();
}

/**
 * Checks if CORS is enabled for the given URL
 */
function isCorsEnabled(url: string): boolean {
  const xhr = new XMLHttpRequest();
  xhr.open('HEAD', url, false);

  try {
    xhr.send();
  } catch (e) {
    return false;
  }

  return xhr.status >= 200 && xhr.status <= 299;
}

/**
 * Triggers a click event on a DOM element with cross-browser compatibility
 */
function triggerClick(node: HTMLElement): void {
  try {
    node.dispatchEvent(new MouseEvent('click'));
  } catch (e) {
    // Fallback for older browsers
    const evt = document.createEvent('MouseEvents');
    evt.initMouseEvent('click', true, true, window, 0, 0, 0, 80, 20,
      false, false, false, false, 0, null);
    node.dispatchEvent(evt);
  }
}

/**
 * Implementation for browsers with download attribute support
 */
function saveWithDownloadAttribute(blob: Blob | string, name?: string, opts?: SaveAsOptions): void {
  const URL = BrowserEnvironment.getURL();
  if (!URL) {
    throw new Error('URL API not available');
  }

  const a = document.createElement('a');
  const fileName = name || (blob as any).name || 'download';

  a.download = fileName;
  a.rel = 'noopener'; // Prevent tabnabbing

  if (typeof blob === 'string') {
    // Handle URL strings
    a.href = blob;
    if (a.origin !== location.origin) {
      if (isCorsEnabled(a.href)) {
        downloadFromUrl(blob, fileName, opts);
      } else {
        a.target = '_blank';
        triggerClick(a);
      }
    } else {
      triggerClick(a);
    }
  } else {
    // Handle Blob objects
    a.href = URL.createObjectURL(blob);

    // Clean up object URL after 40 seconds
    setTimeout(() => URL.revokeObjectURL(a.href), 40000);

    // Trigger download
    setTimeout(() => triggerClick(a), 0);
  }
}

/**
 * Implementation for browsers with msSaveOrOpenBlob support (Internet Explorer)
 */
function saveWithMsSaveBlob(blob: Blob | string, name?: string, opts?: SaveAsOptions): void {
  const fileName = name || (blob as any).name || 'download';
  const nav = BrowserEnvironment.getNavigator();

  if (!nav) {
    throw new Error('Navigator not available');
  }

  if (typeof blob === 'string') {
    if (isCorsEnabled(blob)) {
      downloadFromUrl(blob, fileName, opts);
    } else {
      const a = document.createElement('a');
      a.href = blob;
      a.target = '_blank';
      setTimeout(() => triggerClick(a), 0);
    }
  } else {
    (nav as any).msSaveOrOpenBlob(addBom(blob, opts), fileName);
  }
}

/**
 * Fallback implementation using FileReader and popup
 */
function saveWithFallback(blob: Blob | string, name?: string, opts?: SaveAsOptions, popup?: Window | null): void {
  // Open popup immediately to avoid popup blocker
  popup = popup || window.open('', '_blank');

  if (popup) {
    popup.document.title = 'downloading...';
    popup.document.body.innerText = 'downloading...';
  }

  if (typeof blob === 'string') {
    downloadFromUrl(blob, name, opts);
    return;
  }

  const force = blob.type === 'application/octet-stream';
  const isSafari = BrowserEnvironment.isSafari();
  const isChromeIOS = BrowserEnvironment.isChromeIOS();
  const isMacOSWebView = BrowserEnvironment.isMacOSWebView();

  if ((isChromeIOS || (force && isSafari) || isMacOSWebView) && typeof FileReader !== 'undefined') {
    // Use FileReader for Safari and Chrome iOS
    const reader = new FileReader();
    reader.onloadend = (): void => {
      let url = reader.result as string;
      url = isChromeIOS ? url : url.replace(/^data:[^;]*;/, 'data:attachment/file;');

      if (popup) {
        popup.location.href = url;
      } else {
        location.href = url;
      }
      popup = null; // Prevent reverse-tabnabbing
    };
    reader.readAsDataURL(blob);
  } else {
    // Use object URL
    const URL = BrowserEnvironment.getURL();
    if (!URL) {
      throw new Error('URL API not available for fallback method');
    }

    const url = URL.createObjectURL(blob);

    if (popup) {
      popup.location.href = url;
    } else {
      location.href = url;
    }
    popup = null; // Prevent reverse-tabnabbing

    // Clean up after 40 seconds
    setTimeout(() => URL.revokeObjectURL(url), 40000);
  }
}

/**
 * Main saveAs function optimized for Angular applications
 */
export function saveAs(blob: Blob | string, name?: string, opts?: SaveAsOptions): void {
  // Ensure we're in a browser environment
  if (!BrowserEnvironment.isSupported()) {
    console.warn('saveAs is not supported in this environment');
    return;
  }

  try {
    // Choose implementation based on browser capabilities
    if (BrowserEnvironment.hasDownloadAttribute() && !BrowserEnvironment.isMacOSWebView()) {
      saveWithDownloadAttribute(blob, name, opts);
    } else if (BrowserEnvironment.hasMsSaveBlob()) {
      saveWithMsSaveBlob(blob, name, opts);
    } else {
      saveWithFallback(blob, name, opts);
    }
  } catch (error) {
    console.error('Error saving file:', error);
    throw new Error(`Failed to save file: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

/**
 * Utility function to create and save a text file
 */
export function saveTextFile(content: string, filename: string, mimeType: string = 'text/plain'): void {
  const blob = new Blob([content], { type: `${mimeType};charset=utf-8` });
  saveAs(blob, filename, { autoBom: true });
}

/**
 * Utility function to save JSON data as a file
 */
export function saveJsonFile(data: any, filename: string): void {
  const content = JSON.stringify(data, null, 2);
  saveTextFile(content, filename, 'application/json');
}

/**
 * Utility function to save CSV data as a file
 */
export function saveCsvFile(data: any[], filename: string, separator: string = ';'): void {
  if (data.length === 0) {
    throw new Error('No data provided for CSV export');
  }

  const headers = Object.keys(data[0]);
  const csvContent = [
    headers.join(separator),
    ...data.map(row => headers.map(header => {
      const value = row[header];
      // Escape values that contain the separator
      return typeof value === 'string' && value.includes(separator)
        ? `"${value.replace(/"/g, '""')}"`
        : value;
    }).join(separator))
  ].join('\n');

  saveTextFile(csvContent, filename, 'text/csv');
}

/**
 * Angular service-compatible class wrapper
 */
export class FileSaverService {
  /**
   * Save a blob or string as a file
   */
  saveAs(blob: Blob | string, name?: string, opts?: SaveAsOptions): void {
    saveAs(blob, name, opts);
  }

  /**
   * Save text content as a file
   */
  saveTextFile(content: string, filename: string, mimeType?: string): void {
    saveTextFile(content, filename, mimeType);
  }

  /**
   * Save JSON data as a file
   */
  saveJsonFile(data: any, filename: string): void {
    saveJsonFile(data, filename);
  }

  /**
   * Save CSV data as a file
   */
  saveCsvFile(data: any[], filename: string, separator?: string): void {
    saveCsvFile(data, filename, separator);
  }

  /**
   * Check if file saving is supported in current environment
   */
  isSupported(): boolean {
    return BrowserEnvironment.isSupported();
  }
}

// Attach to global scope for backward compatibility
if (typeof window !== 'undefined') {
  (window as any).saveAs = saveAs;
}

// Default export for CommonJS compatibility
export default saveAs;
