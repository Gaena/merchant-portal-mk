import React from 'react';
import { Box, Button, Paper, Typography } from '@mui/material';
import { useNavigate } from 'react-router';
import { useLanguage } from '../context/LanguageContext';

interface StatusPageProps {
  /** Крупный код сверху: 403, 404 или иконка. */
  code: React.ReactNode;
  title: string;
  text: string;
  /** Техническая подробность (текст ошибки) — мелко, под основным текстом. */
  detail?: string;
  /** Чем заменить переход на главную (например, полной перезагрузкой после деплоя). */
  onHome?: () => void;
}

/** Страница-заглушка для «нет доступа», «не найдено» и ошибок рендера. */
export const StatusPage: React.FC<StatusPageProps> = ({ code, title, text, detail, onHome }) => {
  const navigate = useNavigate();
  const { tObj } = useLanguage();

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
      <Paper elevation={0} sx={{ p: 5, maxWidth: 520, textAlign: 'center', bgcolor: 'transparent' }}>
        <Typography variant="h2" sx={{ fontWeight: 700, color: 'text.disabled', mb: 1 }}>
          {code}
        </Typography>
        <Typography variant="h5" sx={{ fontWeight: 600, mb: 1 }}>
          {title}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: detail ? 1 : 3 }}>
          {text}
        </Typography>
        {detail && (
          <Typography variant="caption" color="text.disabled" component="pre" sx={{ mb: 3, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
            {detail}
          </Typography>
        )}
        <Button variant="contained" onClick={onHome ?? (() => navigate('/', { replace: true }))}>
          {tObj.errors.goHome}
        </Button>
      </Paper>
    </Box>
  );
};
