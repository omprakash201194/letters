/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        wa: {
          green: '#075E54',
          'green-light': '#25D366',
          'bubble-out': '#DCF8C6',
          'bubble-in': '#FFFFFF',
          bg: '#ECE5DD',
          tick: '#53BDEB',
        },
      },
      keyframes: {
        bubblePop: {
          '0%': { transform: 'scale(0.7)', opacity: '0' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        dotBounce: {
          '0%, 60%, 100%': { transform: 'translateY(0)' },
          '30%': { transform: 'translateY(-5px)' },
        },
      },
      animation: {
        'bubble-pop': 'bubblePop 250ms cubic-bezier(0.34, 1.56, 0.64, 1)',
        'dot-bounce': 'dotBounce 1s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
