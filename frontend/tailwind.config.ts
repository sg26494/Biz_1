import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Gulmohar "Pink City" palette (same values as :root in src/app/gulmohar.css).
        clay: { 50: '#FBF1EE', 100: '#F0E3DF', 300: '#E0A596', 500: '#CD7362', 600: '#A9564A', 700: '#8E463C' },
        indigo: { 50: '#F0E3DF', 500: '#6B5B56', 700: '#3A2A26', 900: '#3A2A26' },
        sand: { 50: '#FDF8F5', 100: '#F0E3DF', 200: '#E9D3CC', 400: '#B9A8A2', 600: '#6B5B56', 800: '#3A2A26' },
      },
      fontFamily: {
        display: ['"Playfair Display"', 'Georgia', 'serif'],
        hindi: ['"Tiro Devanagari Hindi"', 'serif'],
        sans: ['Montserrat', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
