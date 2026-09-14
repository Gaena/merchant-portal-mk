import { createRoot } from 'react-dom/client';
import App from './app/App.tsx';

// Стили — только MUI (CssBaseline в App.tsx). Файлов Tailwind/shadcn больше нет: ни один класс
// и ни одна CSS-переменная из них не использовались.
createRoot(document.getElementById('root')!).render(<App />);
