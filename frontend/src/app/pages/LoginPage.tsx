import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '../context/AuthContext';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
  InputAdornment,
  Alert,
  Stack,
  Link,
  Divider,
  CircularProgress
} from '@mui/material';
import {
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
  Lock as LockIcon,
  Email as EmailIcon,
  Close as CloseIcon,
  Security as SecurityIcon,
  PhoneAndroid as PhoneIcon
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const { tObj } = useLanguage();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [otpDialogOpen, setOtpDialogOpen] = useState(false);
  const [otpCode, setOtpCode] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [otpError, setOtpError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Basic validation
    if (!email || !password) {
      setError('Please enter both email and password');
      return;
    }

    if (!email.includes('@')) {
      setError('Please enter a valid email address');
      return;
    }

    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setIsLoading(true);
    try {
      await login(email, password);
      setIsLoading(false);
      navigate('/home');
    } catch (err: any) {
      setIsLoading(false);
      const serverMessage = err.response?.data?.message || err.response?.data?.error;
      setError(serverMessage || 'Failed to authenticate. Please check your credentials.');
    }
  };

  const handleOtpChange = (index: number, value: string) => {
    // Only allow digits
    if (value && !/^\d$/.test(value)) return;

    const newOtp = [...otpCode];
    newOtp[index] = value;
    setOtpCode(newOtp);
    setOtpError('');

    // Auto-focus next input
    if (value && index < 5) {
      const nextInput = document.getElementById(`otp-${index + 1}`);
      nextInput?.focus();
    }
  };

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otpCode[index] && index > 0) {
      const prevInput = document.getElementById(`otp-${index - 1}`);
      prevInput?.focus();
    }
  };

  const handleOtpPaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text').trim();

    // Only process if it's 6 digits
    if (/^\d{6}$/.test(pastedData)) {
      const newOtp = pastedData.split('');
      setOtpCode(newOtp);
    }
  };

  const handleVerifyOtp = () => {
    const code = otpCode.join('');

    if (code.length !== 6) {
      setOtpError('Please enter all 6 digits');
      return;
    }

    // In a real app, this would verify the OTP with the backend
    setIsVerifying(true);
    setTimeout(() => {
      setIsVerifying(false);
      setOtpDialogOpen(false);
      navigate('/');
    }, 1500);
  };

  const handleCloseOtpDialog = () => {
    setOtpDialogOpen(false);
    setOtpCode(['', '', '', '', '', '']);
    setOtpError('');
  };

  const handleForgotPassword = () => {
    // In a real app, this would navigate to forgot password page
    alert('Forgot password functionality will be implemented here');
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
        position: 'relative',
        '&::before': {
          content: '""',
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          bgcolor: 'rgba(255, 255, 255, 0.05)',
          backdropFilter: 'blur(10px)'
        }
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
            Sign in to access your dashboard
          </Typography>
        </Box>

        {/* Error Alert */}
        {error && (
          <Alert
            severity="error"
            sx={{
              mb: 3,
              '& .MuiAlert-message': {
                width: '100%'
              }
            }}
            onClose={() => setError('')}
          >
            {error}
          </Alert>
        )}

        {/* Login Form */}
        <form onSubmit={handleLogin}>
          <Stack spacing={3}>
            <Box>
              <Typography
                variant="body2"
                sx={{
                  mb: 1,
                  fontWeight: 600,
                  color: 'text.primary'
                }}
              >
                Email Address
              </Typography>
              <TextField
                fullWidth
                placeholder="Enter your email"
                type="email"
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
                sx={{
                  '& .MuiOutlinedInput-root': {
                    bgcolor: 'grey.50'
                  }
                }}
              />
            </Box>

            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    color: 'text.primary'
                  }}
                >
                  Password
                </Typography>
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  onClick={handleForgotPassword}
                  sx={{
                    fontWeight: 600,
                    textDecoration: 'none',
                    color: 'primary.main',
                    '&:hover': {
                      textDecoration: 'underline'
                    }
                  }}
                >
                  Forgot Password?
                </Link>
              </Box>
              <TextField
                fullWidth
                placeholder="Enter your password"
                type={showPassword ? 'text' : 'password'}
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
                      >
                        {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                      </IconButton>
                    </InputAdornment>
                  )
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    bgcolor: 'grey.50'
                  }
                }}
              />
            </Box>

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isLoading}
              sx={{
                py: 1.75,
                fontWeight: 600,
                fontSize: '1rem',
                textTransform: 'none',
                boxShadow: '0 4px 12px rgba(25, 118, 210, 0.3)',
                '&:hover': {
                  boxShadow: '0 6px 16px rgba(25, 118, 210, 0.4)'
                }
              }}
            >
              {isLoading ? (
                <CircularProgress size={24} sx={{ color: 'white' }} />
              ) : (
                'Sign In'
              )}
            </Button>
          </Stack>
        </form>

        <Divider sx={{ my: 4 }} />

        {/* Security Notice */}
        <Box sx={{ textAlign: 'center' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mb: 1 }}>
            <SecurityIcon sx={{ fontSize: 18, color: 'success.main' }} />
            <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>
              Secured with 2-Factor Authentication
            </Typography>
          </Box>
          <Typography variant="caption" color="text.secondary">
            Your data is protected with industry-standard encryption
          </Typography>
        </Box>
      </Paper>

      {/* OTP Dialog */}
      <Dialog
        open={otpDialogOpen}
        onClose={handleCloseOtpDialog}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: 3,
            maxWidth: 520
          }
        }}
      >
        <DialogTitle
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            pb: 2,
            pt: 3,
            px: 4
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 48,
                height: 48,
                borderRadius: '50%',
                bgcolor: 'primary.light',
                color: 'primary.main'
              }}
            >
              <PhoneIcon sx={{ fontSize: 28 }} />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 0.5 }}>
                Two-Factor Authentication
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Verify your identity
              </Typography>
            </Box>
          </Box>
          <IconButton
            onClick={handleCloseOtpDialog}
            size="small"
            sx={{
              color: 'text.secondary',
              '&:hover': {
                bgcolor: 'grey.100'
              }
            }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ px: 4, pb: 4 }}>
          <Box sx={{ py: 1 }}>
            {/* Info Box */}
            <Box
              sx={{
                mb: 4,
                p: 2.5,
                bgcolor: 'info.light',
                borderRadius: 2,
                border: '1px solid',
                borderColor: 'info.main'
              }}
            >
              <Typography variant="body2" sx={{ color: 'info.dark', fontWeight: 600, mb: 0.5 }}>
                <SecurityIcon sx={{ fontSize: 16, mr: 1, verticalAlign: 'middle' }} />
                Authentication Required
              </Typography>
              <Typography variant="body2" sx={{ color: 'info.dark' }}>
                Enter the 6-digit verification code from your authenticator app (Google Authenticator, Authy, etc.)
              </Typography>
            </Box>

            {/* OTP Error */}
            {otpError && (
              <Alert
                severity="error"
                sx={{ mb: 3 }}
                onClose={() => setOtpError('')}
              >
                {otpError}
              </Alert>
            )}

            {/* OTP Input Fields */}
            <Box sx={{ mb: 1 }}>
              <Typography
                variant="body2"
                sx={{
                  mb: 2,
                  fontWeight: 600,
                  color: 'text.primary',
                  textAlign: 'center'
                }}
              >
                Verification Code
              </Typography>
              <Box
                sx={{
                  display: 'flex',
                  gap: 1.5,
                  justifyContent: 'center',
                  mb: 4
                }}
              >
                {otpCode.map((digit, index) => (
                  <TextField
                    key={index}
                    id={`otp-${index}`}
                    value={digit}
                    onChange={(e) => handleOtpChange(index, e.target.value)}
                    onKeyDown={(e) => handleOtpKeyDown(index, e)}
                    onPaste={index === 0 ? handleOtpPaste : undefined}
                    disabled={isVerifying}
                    inputProps={{
                      maxLength: 1,
                      style: {
                        textAlign: 'center',
                        fontSize: '1.75rem',
                        fontWeight: 700,
                        fontFamily: 'monospace',
                        padding: '16px 0'
                      }
                    }}
                    sx={{
                      width: 64,
                      '& .MuiOutlinedInput-root': {
                        bgcolor: 'grey.50',
                        '& fieldset': {
                          borderWidth: 2,
                          borderColor: digit ? 'primary.main' : 'grey.300'
                        },
                        '&:hover fieldset': {
                          borderColor: 'primary.main'
                        },
                        '&.Mui-focused fieldset': {
                          borderColor: 'primary.main',
                          borderWidth: 2
                        }
                      }
                    }}
                  />
                ))}
              </Box>
            </Box>

            {/* Verify Button */}
            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={handleVerifyOtp}
              disabled={isVerifying || otpCode.some(d => !d)}
              sx={{
                py: 1.75,
                fontWeight: 600,
                fontSize: '1rem',
                mb: 2,
                boxShadow: '0 4px 12px rgba(25, 118, 210, 0.3)',
                '&:hover': {
                  boxShadow: '0 6px 16px rgba(25, 118, 210, 0.4)'
                }
              }}
            >
              {isVerifying ? (
                <CircularProgress size={24} sx={{ color: 'white' }} />
              ) : (
                'Verify & Continue'
              )}
            </Button>

            {/* Help Text */}
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Didn't receive the code?
              </Typography>
              <Button
                variant="text"
                size="small"
                disabled={isVerifying}
                sx={{
                  fontWeight: 600,
                  textTransform: 'none'
                }}
              >
                Resend Code
              </Button>
            </Box>
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  );
};
