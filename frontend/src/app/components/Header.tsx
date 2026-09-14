import React, { useState } from 'react';
import {
  AppBar,
  Toolbar,
  Typography,
  Box,
  IconButton,
  Avatar,
  Tooltip,
  Chip,
  Menu,
  MenuItem,
  Divider,
  ListItemIcon,
  Button,
} from '@mui/material';
import {
  AccountBalance as AccountBalanceIcon,
  Menu as MenuIcon,
  Logout as LogoutIcon,
  Language as LanguageIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { ConfirmDialog } from './ConfirmDialog';
import type { Language } from '../i18n/translations';

// ─── Header component ─────────────────────────────────────────────────────────

interface HeaderProps {
  onMenuClick: () => void;
  onDesktopDrawerToggle: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onMenuClick, onDesktopDrawerToggle }) => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { language, setLanguage, tObj } = useLanguage();

  // Имени пользователя бэкенд в ответе логина не отдаёт (эндпоинта профиля нет) — показываем
  // честный email. Роль без запасного значения: если её нет, сессии нет (session.ts отказывает
  // во входе), поэтому здесь она всегда есть.
  const displayEmail = user?.email || '—';
  const avatarLetter = (user?.email || '?').charAt(0).toUpperCase();
  const displayRole = user?.role ?? '—';

  // Account menu
  const [accountAnchor, setAccountAnchor] = useState<null | HTMLElement>(null);
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);

  // Language menu
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null);

  const langLabels: Record<Language, { label: string; flag: string }> = {
    en: { label: 'English', flag: '🇬🇧' },
    az: { label: 'Azərbaycan', flag: '🇦🇿' },
    ru: { label: 'Русский', flag: '🇷🇺' },
  };

  return (
    <AppBar position="fixed" elevation={2} sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
      <Toolbar>
        {/* Mobile menu toggle */}
        <IconButton color="inherit" edge="start" onClick={onMenuClick} sx={{ mr: 2, display: { md: 'none' } }}>
          <MenuIcon />
        </IconButton>
        {/* Desktop drawer toggle */}
        <IconButton color="inherit" edge="start" onClick={onDesktopDrawerToggle} sx={{ mr: 2, display: { xs: 'none', md: 'block' } }}>
          <MenuIcon />
        </IconButton>

        <AccountBalanceIcon sx={{ mr: 2, fontSize: 32 }} />
        <Typography variant="h6" component="div" sx={{ flexGrow: 0, mr: 3 }}>
          {tObj.header.title}
        </Typography>

        <Box sx={{ flexGrow: 1 }} />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Quick Language Selector */}
          <Tooltip title={tObj.header.language}>
            <Button
              color="inherit"
              onClick={e => setLangAnchor(e.currentTarget)}
              startIcon={<LanguageIcon />}
              sx={{ textTransform: 'none', fontWeight: 600, px: 1.5 }}
            >
              {langLabels[language]?.flag} {language.toUpperCase()}
            </Button>
          </Tooltip>

          {/* Account avatar */}
          <Tooltip title={tObj.header.profile}>
            <IconButton sx={{ ml: 0.5 }} onClick={e => setAccountAnchor(e.currentTarget)}>
              <Avatar sx={{ width: 36, height: 36, bgcolor: 'secondary.main', fontWeight: 700 }}>{avatarLetter}</Avatar>
            </IconButton>
          </Tooltip>
        </Box>
      </Toolbar>

      {/* ── Language Menu ──────────────────────────────────────────────────── */}
      <Menu
        anchorEl={langAnchor}
        open={Boolean(langAnchor)}
        onClose={() => setLangAnchor(null)}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        PaperProps={{
          elevation: 3,
          sx: { mt: 1.5, minWidth: 160 },
        }}
      >
        {(['en', 'az', 'ru'] as Language[]).map((lang) => (
          <MenuItem
            key={lang}
            selected={language === lang}
            onClick={() => {
              setLanguage(lang);
              setLangAnchor(null);
            }}
          >
            <Typography variant="body2" sx={{ mr: 1.5, fontSize: '1.2rem' }}>
              {langLabels[lang].flag}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: language === lang ? 700 : 400 }}>
              {langLabels[lang].label}
            </Typography>
          </MenuItem>
        ))}
      </Menu>

      {/* ── Account Menu ───────────────────────────────────────────────────── */}
      <Menu
        anchorEl={accountAnchor}
        open={Boolean(accountAnchor)}
        onClose={() => setAccountAnchor(null)}
        onClick={() => setAccountAnchor(null)}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        PaperProps={{
          elevation: 3,
          sx: { mt: 1.5, minWidth: 220, '& .MuiMenuItem-root': { px: 2, py: 1.5 } },
        }}
      >
        <Box sx={{ px: 2, py: 1.5 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, wordBreak: 'break-all' }}>{displayEmail}</Typography>
          <Chip label={displayRole} size="small" color="primary" sx={{ mt: 1, height: 20, fontSize: '0.65rem', fontWeight: 700 }} />
        </Box>
        <Divider />
        <MenuItem onClick={() => { setLogoutDialogOpen(true); }}>
          <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
          {tObj.header.logout}
        </MenuItem>
      </Menu>

      {/* Выход — через общее окно подтверждения (P3-5b): фокус на безопасной кнопке, Enter по
          инерции не выходит из системы. */}
      <ConfirmDialog
        open={logoutDialogOpen}
        maxWidth="xs"
        title={tObj.header.logout}
        question={tObj.header.logoutQuestion}
        confirmLabel={tObj.header.logout}
        confirmColor="primary"
        confirmIcon={<LogoutIcon />}
        onConfirm={() => {
          setLogoutDialogOpen(false);
          // logout сбрасывает сессию сразу (ProtectedRoute уведёт на /login сам) и вдогонку
          // гасит refresh-токен на сервере; ошибку сети он логирует и не пробрасывает.
          void logout();
          navigate('/login', { replace: true });
        }}
        onCancel={() => setLogoutDialogOpen(false)}
      />
    </AppBar>
  );
};
