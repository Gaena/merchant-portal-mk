import React, { useState } from 'react';
import {
  AppBar,
  Toolbar,
  Typography,
  Box,
  IconButton,
  Badge,
  Avatar,
  Tooltip,
  Chip,
  Menu,
  MenuItem,
  Divider,
  ListItemIcon,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  Popover,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Paper,
} from '@mui/material';
import {
  AccountBalance as AccountBalanceIcon,
  Menu as MenuIcon,
  Logout as LogoutIcon,
  Notifications as BellIcon,
  CheckCircle as SuccessIcon,
  ErrorOutline as FailIcon,
  AccountBalance as SettlementIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  Link as LinkIcon,
  DoneAll as MarkAllReadIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router';

// ─── Notification types ───────────────────────────────────────────────────────

type NotifType = 'success' | 'failed' | 'settlement' | 'warning' | 'info' | 'paybylink';

interface Notification {
  id: string;
  type: NotifType;
  title: string;
  body: string;
  time: string;
  read: boolean;
  path?: string;
}

const initialNotifications: Notification[] = [
  {
    id: 'n1',
    type: 'settlement',
    title: 'Settlement Completed',
    body: 'Batch SET-2024-0891 — ₼28,165.50 has been credited to your bank account.',
    time: '2 min ago',
    read: false,
    path: '/reports',
  },
  {
    id: 'n2',
    type: 'success',
    title: 'Large Transaction',
    body: 'Transaction of ₼1,500.00 from Kamran Q. was processed successfully.',
    time: '18 min ago',
    read: false,
    path: '/transactions/ecommerce',
  },
  {
    id: 'n3',
    type: 'paybylink',
    title: 'Pay by Link Paid',
    body: 'Link PL1003 was paid — ₼250.50 by Rauf Hasanov.',
    time: '34 min ago',
    read: false,
    path: '/pay-by-link',
  },
  {
    id: 'n4',
    type: 'failed',
    title: 'Failed Transactions Spike',
    body: '14 failed transactions in the last 30 minutes — above normal threshold.',
    time: '1 h ago',
    read: false,
    path: '/transactions/ecommerce',
  },
  {
    id: 'n5',
    type: 'warning',
    title: 'Pay by Link Expiring Soon',
    body: 'Link PL1007 for ₼890.00 (Nigar G.) expires in 45 minutes.',
    time: '1 h ago',
    read: true,
    path: '/pay-by-link',
  },
  {
    id: 'n6',
    type: 'info',
    title: 'Weekly Report Ready',
    body: 'Your weekly revenue summary for 09–15 Jun 2025 is ready to download.',
    time: '3 h ago',
    read: true,
    path: '/reports',
  },
  {
    id: 'n7',
    type: 'success',
    title: 'Settlement Scheduled',
    body: 'Next payout of ~₼26,400 is scheduled for 23 Jun 2025.',
    time: '5 h ago',
    read: true,
    path: '/reports',
  },
  {
    id: 'n8',
    type: 'info',
    title: 'API Key Expiring',
    body: 'Your production API key expires in 7 days. Rotate it in Settings.',
    time: '1 day ago',
    read: true,
    path: '/settings',
  },
];

const notifIconMap: Record<NotifType, React.ReactNode> = {
  success: <SuccessIcon sx={{ color: '#2e7d32' }} />,
  failed: <FailIcon sx={{ color: '#c62828' }} />,
  settlement: <SettlementIcon sx={{ color: '#1565c0' }} />,
  warning: <WarningIcon sx={{ color: '#e65100' }} />,
  info: <InfoIcon sx={{ color: '#546e7a' }} />,
  paybylink: <LinkIcon sx={{ color: '#7b1fa2' }} />,
};

const notifBgMap: Record<NotifType, string> = {
  success: 'rgba(46,125,50,0.1)',
  failed: 'rgba(198,40,40,0.1)',
  settlement: 'rgba(21,101,192,0.1)',
  warning: 'rgba(230,81,0,0.1)',
  info: 'rgba(84,110,122,0.1)',
  paybylink: 'rgba(123,31,162,0.1)',
};

// ─── Header component ─────────────────────────────────────────────────────────

import { useAuth } from '../context/AuthContext';

interface HeaderProps {
  newTransactionCount: number;
  onMenuClick: () => void;
  onDesktopDrawerToggle: () => void;
}

export const Header: React.FC<HeaderProps> = ({ newTransactionCount, onMenuClick, onDesktopDrawerToggle }) => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const displayName = user?.fullName || user?.email?.split('@')[0] || 'Merchant';
  const displayEmail = user?.email || 'N/A';
  const avatarLetter = (user?.fullName || user?.email || 'M').charAt(0).toUpperCase();
  const displayRole = user?.role || 'SYSTEM_ADMIN';

  // Account menu
  const [accountAnchor, setAccountAnchor] = useState<null | HTMLElement>(null);
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);

  // Notifications
  const [notifications, setNotifications] = useState<Notification[]>(initialNotifications);
  const [notifAnchor, setNotifAnchor] = useState<null | HTMLElement>(null);
  const notifOpen = Boolean(notifAnchor);
  const unreadCount = notifications.filter(n => !n.read).length;

  const handleMarkRead = (id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
  };

  const handleMarkAllRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const handleNotifClick = (notif: Notification) => {
    handleMarkRead(notif.id);
    if (notif.path) navigate(notif.path);
    setNotifAnchor(null);
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
          Payment Portal
        </Typography>
        <Chip
          label="Merchant Dashboard"
          size="small"
          sx={{ bgcolor: 'rgba(255,255,255,0.2)', color: 'white' }}
        />

        <Box sx={{ flexGrow: 1 }} />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {/* Notifications bell (Disabled: notification microservice not implemented) */}
          {/* <Tooltip title="Notifications">
            <IconButton
              color="inherit"
              onClick={e => setNotifAnchor(e.currentTarget)}
            >
              <Badge badgeContent={unreadCount} color="error" max={9}>
                <BellIcon />
              </Badge>
            </IconButton>
          </Tooltip> */}

          {/* Account avatar */}
          <Tooltip title="Account">
            <IconButton sx={{ ml: 0.5 }} onClick={e => setAccountAnchor(e.currentTarget)}>
              <Avatar sx={{ width: 36, height: 36, bgcolor: 'secondary.main', fontWeight: 700 }}>{avatarLetter}</Avatar>
            </IconButton>
          </Tooltip>
        </Box>
      </Toolbar>

      {/* ── Notifications Popover (Disabled) ──────────────────────────────────── */}
      {/* <Popover ... */}

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
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{displayName}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.85rem' }}>{displayEmail}</Typography>
          <Chip label={displayRole} size="small" color="primary" sx={{ mt: 1, height: 20, fontSize: '0.65rem', fontWeight: 700 }} />
        </Box>
        <Divider />
        <MenuItem onClick={() => { setLogoutDialogOpen(true); }}>
          <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
          Logout
        </MenuItem>
      </Menu>

      {/* ── Logout Dialog ──────────────────────────────────────────────────── */}
      <Dialog open={logoutDialogOpen} onClose={() => setLogoutDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Confirm Logout</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to logout? You will be redirected to the login page.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setLogoutDialogOpen(false)} variant="outlined">Cancel</Button>
          <Button onClick={() => { setLogoutDialogOpen(false); logout(); navigate('/login'); }} variant="contained" autoFocus>
            Logout
          </Button>
        </DialogActions>
      </Dialog>
    </AppBar>
  );
};
