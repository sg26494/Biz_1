import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Warm block-print palette: terracotta, indigo, sand.
        clay: { 50: '#fbf5f0', 100: '#f4e4d6', 300: '#d9a27c', 500: '#b5653b', 600: '#9a5230', 700: '#7c4127' },
        indigo: { 50: '#eef0f7', 500: '#33427a', 700: '#222c55', 900: '#151b36' },
        sand: { 50: '#fbfaf7', 100: '#f4f0e8', 200: '#e8e1d4', 400: '#b8ad9a', 600: '#7a705f', 800: '#3b352c' },
      },
      fontFamily: {
        display: ['var(--font-display)', 'Georgia', 'serif'],
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
