import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { AuthError } from '../auth/session';
import { returnPathFrom } from '../auth/guards';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  IconButton,
  InputAdornment,
  Alert,
  Stack,
  CircularProgress
} from '@mui/material';
import {
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
  Lock as LockIcon,
  Email as EmailIcon,
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';

/**
 * Форма входа. Здесь нет ни «Forgot password», ни двухфакторной проверки: бэкенд ни того, ни
 * другого не умеет, а надпись «Secured with 2-Factor Authentication» над недостижимым OTP-окном
 * была утверждением о защите, которой нет (Р-48 — выдуманного на экранах не бывает).
 */
export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const { tObj } = useLanguage();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isLoading) return;
    setError('');

    if (!email.trim() || !password) {
      setError(tObj.auth.fillBoth);
      return;
    }
    if (!email.includes('@')) {
      setError(tObj.auth.invalidEmail);
      return;
    }

    setIsLoading(true);
    try {
      await login(email.trim(), password);
      // Туда, откуда увели на вход (ProtectedRoute кладёт адрес в state), иначе на главную.
      // replace: «назад» не должен возвращать на форму входа.
      navigate(returnPathFrom(location.state), { replace: true });
    } catch (err: unknown) {
      setIsLoading(false);
      if (err instanceof AuthError) {
        // Сервер ответил 200, но сессию из ответа собрать нельзя — вход отклонён на клиенте
        // (fail-closed). Оба случая — свои тексты, а не отладочная строка из session.ts.
        setError(err.code === 'UNKNOWN_ROLE' ? tObj.auth.unknownRole : tObj.auth.malformedResponse);
        return;
      }
      if (axios.isAxiosError(err) && !err.response) {
        // Запрос до сервера не дошёл: предлагать «проверьте пароль» было бы неправдой.
        setError(tObj.auth.networkError);
        return;
      }
      const serverMessage = axios.isAxiosError(err)
        ? (err.response?.data?.message || err.response?.data?.error)
        : undefined;
      setError(typeof serverMessage === 'string' && serverMessage ? serverMessage : tObj.auth.authFailed);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'grey.50',
        p: 3,
        backgroundImage: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      <Paper
        elevation={8}
        sx={{
          width: '100%',
          maxWidth: 480,
          p: 5,
          position: 'relative',
          zIndex: 1,
          borderRadius: 3
        }}
      >
        {/* Header */}
        <Box sx={{ textAlign: 'center', mb: 4 }}>
          <Box
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 72,
              height: 72,
              borderRadius: '50%',
              bgcolor: 'primary.main',
              mb: 2,
              boxShadow: '0 8px 24px rgba(25, 118, 210, 0.3)'
            }}
          >
            <LockIcon sx={{ fontSize: 40, color: 'white' }} />
          </Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1, color: 'text.primary' }}>
            {tObj.header.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.auth.subtitle}
          </Typography>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError('')}>
            {error}
          </Alert>
        )}

        {/* noValidate: подсказки браузера для type="email" шли на языке браузера и раньше наших. */}
        <form onSubmit={handleLogin} noValidate>
          <Stack spacing={3}>
            <TextField
              fullWidth
              label={tObj.auth.emailLabel}
              name="username"
              type="email"
              autoComplete="username"
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <EmailIcon color="action" />
                  </InputAdornment>
                )
              }}
            />

            <TextField
              fullWidth
              label={tObj.auth.passwordLabel}
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <LockIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      onClick={() => setShowPassword(!showPassword)}
                      edge="end"
                      disabled={isLoading}
                      tabIndex={-1}
                    >
                      {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                    </IconButton>
                  </InputAdornment>
                )
              }}
            />

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isLoading}
              sx={{ py: 1.75, fontWeight: 600, fontSize: '1rem' }}
            >
              {isLoading ? <CircularProgress size={24} sx={{ color: 'white' }} /> : tObj.auth.signIn}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Box>
  );
};
