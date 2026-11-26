import Aura from '@openng/optimus-ui-themes/aura';
import { definePreset } from '@openng/optimus-ui-themes';

/**
 * Aura with the compact desktop density shared by both hosts. Component tokens decide the spacing, because a
 * component level CSS rule would lose the cascade against the styles Optimus injects at runtime. The values reproduce
 * the dense layout of the former static PrimeNG stylesheet: table cells with 0.1rem padding, 2rem icon buttons and
 * paginator buttons, narrow dialog paddings and input fields without inner padding. Colors are not set here, so
 * Aura's light and dark schemes both apply.
 *
 * A host preset extends this one with `definePreset(CompactPreset, { ... })` and keeps only its own deltas.
 */
export const CompactPreset = definePreset(Aura, {
  components: {
    button: {
      root: {
        paddingX: '0.25rem',
        paddingY: '0.25rem',
        iconOnlyWidth: '2rem'
      }
    },
    datatable: {
      colorScheme: {
        // Aura's stripes are barely visible: slate 50 hardly differs from white, and zinc 950 below a zinc 900 row is a
        // darker shade the eye hardly notices on a dark page. The light stripe is the one of the former light theme;
        // the dark stripe is lighter than the row and still darker than the row hover color (zinc 800).
        light: { row: { stripedBackground: '#f2f2f2' } },
        dark: { row: { stripedBackground: '#242428' } }
      },
      headerCell: {
        padding: '0.1rem'
      },
      bodyCell: {
        padding: '0.1rem'
      },
      footerCell: {
        padding: '0 0.1rem'
      },
      row: {
        // White on blue 600 reaches 5.17:1 in both schemes.
        selectedBackground: '{blue.600}',
        selectedColor: '#ffffff'
      }
    },
    dialog: {
      header: {
        padding: '1rem'
      },
      content: {
        padding: '0.5rem'
      },
      footer: {
        padding: '1rem'
      }
    },
    inputtext: {
      root: {
        paddingX: '0rem',
        paddingY: '0rem'
      }
    },
    textarea: {
      root: {
        paddingX: '0.1rem',
        paddingY: '0rem'
      }
    },
    menubar: {
      root: {
        padding: '0rem 0rem'
      }
    },
    multiselect: {
      root: {
        paddingX: '0rem',
        paddingY: '0rem'
      }
    },
    select: {
      root: {
        paddingX: '0rem',
        paddingY: '0rem'
      }
    },
    paginator: {
      navButton: {
        width: '2rem',
        height: '2rem'
      }
    },
    tabs: {
      tab: {
        borderWidth: '1px 1px 1px 1px'
      },
      tabpanel: {
        padding: '0rem'
      }
    },
    tree: {
      root: {
        padding: '0.5rem'
      },
      node: {
        padding: '0rem 0rem'
      },
      nodeToggleButton: {
        size: '0.5rem'
      }
    },
    treetable: {
      colorScheme: {
        light: { header: { background: '{surface.100}' } },
        dark: { header: { background: '{surface.800}' } }
      },
      headerCell: {
        padding: '0.1rem'
      },
      bodyCell: {
        padding: '0.1rem',
        gap: '0rem'
      },
      footerCell: {
        // Aura defaults to 0.75rem 1rem here. Without this the total row of a tree table is twice as high as a data
        // row and its right aligned figures sit further left than the values above.
        padding: '0 0.1rem'
      },
      nodeToggleButton: {
        size: '1rem'
      }
    }
  }
});
