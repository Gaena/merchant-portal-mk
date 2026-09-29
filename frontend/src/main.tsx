import { createRoot } from 'react-dom/client';
import App from './app/App.tsx';

// Стили — только MUI (CssBaseline в App.tsx); Tailwind и shadcn не возвращать (AGENTS §8).
createRoot(document.getElementById('root')!).render(<App />);
