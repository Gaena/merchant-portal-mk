import React, { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Chip,
  Stack,
  Divider,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
  Checkbox,
  Menu,
  MenuItem,
  FormControlLabel,
  Switch,
  Collapse,
  Alert,
  Badge,
  Grid,
  Card,
  CardContent,
} from '@mui/material';
import {
  Notifications as BellIcon,
  CheckCircle as SuccessIcon,
  ErrorOutline as FailIcon,
  AccountBalance as SettlementIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  Link as LinkIcon,
  DoneAll as MarkAllReadIcon,
  DeleteOutline as DeleteIcon,
  Delete as DeleteAllIcon,
  MoreVert as MoreIcon,
  FilterList as FilterIcon,
  NotificationsOff as MuteIcon,
  Circle as DotIcon,
  Schedule as TimeIcon,
  Settings as PrefsIcon,
  ExpandMore as ExpandIcon,
  ExpandLess as CollapseIcon,
  TrendingUp as TrendingIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useNavigate } from 'react-router';

// ─── Types ────────────────────────────────────────────────────────────────────

type NotifType = 'transaction' | 'settlement' | 'paybylink' | 'warning' | 'system';
type FilterTab = 'all' | 'unread' | 'transaction' | 'settlement' | 'paybylink' | 'warning' | 'system';

interface Notification {
  id: string;
  type: NotifType;
  title: string;
  body: string;
  timestamp: Date;
  read: boolean;
  path?: string;
  meta?: string;
}

// ─── Mock data ────────────────────────────────────────────────────────────────

const ago = (minutes: number) => new Date(Date.now() - minutes * 60 * 1000);

const INITIAL: Notification[] = [
  // Today
  { id: 'n01', type: 'settlement',   title: 'Settlement Completed',            body: 'Batch SET-2024-0891 — ₼28,165.50 has been credited to your bank account.',              timestamp: ago(5),    read: false, path: '/reports',                meta: '₼28,165.50' },
  { id: 'n02', type: 'transaction',  title: 'Large Transaction Approved',      body: 'Transaction of ₼1,500.00 from Kamran Q. was processed successfully.',                   timestamp: ago(22),   read: false, path: '/transactions/ecommerce', meta: '₼1,500.00' },
  { id: 'n03', type: 'paybylink',    title: 'Pay by Link Paid',                body: 'Link PL1003 for ₼250.50 was paid by Rauf Hasanov.',                                      timestamp: ago(40),   read: false, path: '/pay-by-link',            meta: 'PL1003' },
  { id: 'n04', type: 'warning',      title: 'Failed Transactions Spike',       body: '14 failed transactions in the last 30 minutes — above normal threshold of 5.',           timestamp: ago(65),   read: false, path: '/transactions/ecommerce', meta: '14 failures' },
  { id: 'n05', type: 'paybylink',    title: 'Pay by Link Expiring Soon',       body: 'Link PL1007 for ₼890.00 (Nigar G.) expires in 45 minutes. Share it now.',               timestamp: ago(80),   read: false, path: '/pay-by-link',            meta: 'PL1007' },
  { id: 'n06', type: 'transaction',  title: 'POS Batch Closed',                body: 'Batch #B-20240619 closed with 47 transactions totalling ₼4,230.00.',                     timestamp: ago(120),  read: false, path: '/transactions/pos',       meta: '47 txns' },
  { id: 'n07', type: 'system',       title: 'Weekly Report Ready',             body: 'Your weekly revenue summary for 09–15 Jun 2025 is ready to download.',                   timestamp: ago(180),  read: true,  path: '/reports',                meta: 'PDF ready' },
  { id: 'n08', type: 'settlement',   title: 'Settlement Scheduled',            body: 'Next payout of ~₼26,400 is scheduled for 23 Jun 2025.',                                  timestamp: ago(300),  read: true,  path: '/reports',                meta: '23 Jun' },
  // Yesterday
  { id: 'n09', type: 'transaction',  title: 'Transaction Volume Milestone',    body: 'You processed 500 transactions this week — a new record!',                               timestamp: ago(1440), read: true,  path: '/reports',                meta: '500 txns' },
  { id: 'n10', type: 'warning',      title: '3D-Secure Failures Elevated',     body: 'Authentication failure rate reached 18% in the last hour. Check your MPI settings.',    timestamp: ago(1500), read: true,  path: '/transactions/ecommerce', meta: '18% rate' },
  { id: 'n11', type: 'paybylink',    title: '3 Links Expired Without Payment', body: 'Links PL0998, PL0999, PL1001 expired without being paid.',                               timestamp: ago(1600), read: true,  path: '/pay-by-link',            meta: '3 links' },
  { id: 'n12', type: 'system',       title: 'API Key Rotated Successfully',    body: 'Your production API key was rotated. Old key deactivated.',                              timestamp: ago(1700), read: true,  path: '/settings',               meta: 'Security' },
  // This week
  { id: 'n13', type: 'settlement',   title: 'Settlement Report Generated',     body: 'Batch SET-2024-0890 settlement report (25–31 May) is ready for download.',              timestamp: ago(3000), read: true,  path: '/reports',                meta: '₼26,561.70' },
  { id: 'n14', type: 'transaction',  title: 'Refund Processed',                body: 'Refund of ₼299.99 for transaction TXN-20240612-0041 was successfully issued.',          timestamp: ago(3600), read: true,  path: '/transactions/ecommerce', meta: '₼299.99' },
  { id: 'n15', type: 'system',       title: 'API Key Expiring Soon',           body: 'Your production API key expires in 7 days. Rotate it in Settings → API & Webhooks.',    timestamp: ago(4200), read: true,  path: '/settings',               meta: '7 days' },
  { id: 'n16', type: 'warning',      title: 'Suspicious Activity Detected',    body: 'Unusual login attempt from IP 185.220.x.x blocked. Review Security settings.',         timestamp: ago(4800), read: true,  path: '/settings',               meta: 'Security' },
  { id: 'n17', type: 'paybylink',    title: 'Pay by Link — First Payment',     body: 'First payment received via Pay by Link feature. Link PL1001 — ₼49.99.',                timestamp: ago(5400), read: true,  path: '/pay-by-link',            meta: 'PL1001' },
  { id: 'n18', type: 'system',       title: 'Monthly Statement Available',     body: 'Your May 2025 account statement is ready. Download it from Reports.',                   timestamp: ago(7200), read: true,  path: '/reports',                meta: 'May 2025' },
];

// ─── Helpers ──────────────────────────────────────────────────────────────────

const typeConfig: Record<NotifType, { label: string; color: string; bgcolor: string; icon: React.ReactNode }> = {
  transaction: { label: 'Transaction', color: '#1565c0', bgcolor: 'rgba(21,101,192,0.1)',  icon: <TrendingIcon sx={{ fontSize: 18 }} /> },
  settlement:  { label: 'Settlement',  color: '#2e7d32', bgcolor: 'rgba(46,125,50,0.1)',   icon: <SettlementIcon sx={{ fontSize: 18 }} /> },
  paybylink:   { label: 'Pay by Link', color: '#7b1fa2', bgcolor: 'rgba(123,31,162,0.1)', icon: <LinkIcon sx={{ fontSize: 18 }} /> },
  warning:     { label: 'Warning',     color: '#e65100', bgcolor: 'rgba(230,81,0,0.1)',    icon: <WarningIcon sx={{ fontSize: 18 }} /> },
  system:      { label: 'System',      color: '#546e7a', bgcolor: 'rgba(84,110,122,0.1)', icon: <InfoIcon sx={{ fontSize: 18 }} /> },
};

const formatTime = (d: Date) => {
  const mins = Math.floor((Date.now() - d.getTime()) / 60000);
  if (mins < 1)   return 'Just now';
  if (mins < 60)  return `${mins}m ago`;
  if (mins < 1440) return `${Math.floor(mins / 60)}h ago`;
  if (mins < 2880) return 'Yesterday';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
};

const groupLabel = (d: Date) => {
  const mins = (Date.now() - d.getTime()) / 60000;
  if (mins < 1440)  return 'Today';
  if (mins < 2880)  return 'Yesterday';
  if (mins < 10080) return 'This Week';
  return 'Older';
};

const prefLabels: { key: string; label: string; sub: string }[] = [
  { key: 'txn_success',  label: 'Successful transactions',       sub: 'Notify on every successful payment' },
  { key: 'txn_large',    label: 'Large transactions',            sub: 'Threshold: ₼500+' },
  { key: 'txn_failed',   label: 'Failed transaction spikes',     sub: 'When failure rate exceeds 10%' },
  { key: 'settlement',   label: 'Settlement & payouts',          sub: 'When funds are credited to your account' },
  { key: 'paybylink',    label: 'Pay by Link activity',          sub: 'Payments received and expiry warnings' },
  { key: 'security',     label: 'Security alerts',               sub: 'Suspicious logins, API key expiry' },
  { key: 'reports',      label: 'Reports & statements',          sub: 'When new reports are ready' },
];

// ─── Component ────────────────────────────────────────────────────────────────

export const NotificationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [items, setItems]           = useState<Notification[]>(INITIAL);
  const [filter, setFilter]         = useState<FilterTab>('all');
  const [selected, setSelected]     = useState<Set<string>>(new Set());
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [prefsOpen, setPrefsOpen]   = useState(false);
  const [prefs, setPrefs]           = useState<Record<string, boolean>>(
    Object.fromEntries(prefLabels.map(p => [p.key, true]))
  );

  // Derived counts
  const counts = useMemo(() => {
    const all    = items.length;
    const unread = items.filter(n => !n.read).length;
    const byType = (t: NotifType) => items.filter(n => n.type === t).length;
    return { all, unread, transaction: byType('transaction'), settlement: byType('settlement'), paybylink: byType('paybylink'), warning: byType('warning'), system: byType('system') };
  }, [items]);

  // Filtered & grouped
  const filtered = useMemo(() => {
    return items.filter(n => {
      if (filter === 'unread')     return !n.read;
      if (filter === 'all')        return true;
      return n.type === filter;
    });
  }, [items, filter]);

  const grouped = useMemo(() => {
    const map = new Map<string, Notification[]>();
    const order = ['Today', 'Yesterday', 'This Week', 'Older'];
    filtered.forEach(n => {
      const label = groupLabel(n.timestamp);
      if (!map.has(label)) map.set(label, []);
      map.get(label)!.push(n);
    });
    return order.filter(k => map.has(k)).map(k => ({ label: k, items: map.get(k)! }));
  }, [filtered]);

  // Selection helpers
  const allSelected = filtered.length > 0 && filtered.every(n => selected.has(n.id));
  const someSelected = selected.size > 0;

  const toggleSelect = (id: string) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelected(new Set());
    } else {
      setSelected(new Set(filtered.map(n => n.id)));
    }
  };

  // Actions
  const markRead = (ids: string[]) => setItems(prev => prev.map(n => ids.includes(n.id) ? { ...n, read: true } : n));
  const markUnread = (ids: string[]) => setItems(prev => prev.map(n => ids.includes(n.id) ? { ...n, read: false } : n));
  const deleteItems = (ids: string[]) => {
    setItems(prev => prev.filter(n => !ids.includes(n.id)));
    setSelected(new Set());
  };

  const handleMarkAllRead = () => setItems(prev => prev.map(n => ({ ...n, read: true })));

  const handleBulkAction = (action: 'read' | 'unread' | 'delete') => {
    const ids = Array.from(selected);
    if (action === 'read')   markRead(ids);
    if (action === 'unread') markUnread(ids);
    if (action === 'delete') deleteItems(ids);
    setSelected(new Set());
    setMenuAnchor(null);
  };

  const handleItemClick = (n: Notification) => {
    markRead([n.id]);
    if (n.path) navigate(n.path);
  };

  return (
    <Box>
      {/* Page header */}
      <Box sx={{ mb: 4, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5 }}>Notifications</Typography>
          <Typography variant="body1" color="text.secondary">
            Stay on top of transactions, settlements, and system alerts
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            startIcon={<PrefsIcon />}
            onClick={() => setPrefsOpen(v => !v)}
          >
            Preferences
          </Button>
          <Button
            variant="outlined"
            startIcon={<MarkAllReadIcon />}
            onClick={handleMarkAllRead}
            disabled={counts.unread === 0}
          >
            Mark all read
          </Button>
        </Stack>
      </Box>

      {/* Summary cards */}
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        {[
          { label: 'Unread',      value: counts.unread,      color: '#1565c0', bgcolor: 'rgba(21,101,192,0.08)',  icon: <BellIcon sx={{ fontSize: 26 }} /> },
          { label: 'Warnings',    value: counts.warning,     color: '#e65100', bgcolor: 'rgba(230,81,0,0.08)',    icon: <WarningIcon sx={{ fontSize: 26 }} /> },
          { label: 'Settlements', value: counts.settlement,  color: '#2e7d32', bgcolor: 'rgba(46,125,50,0.08)',   icon: <SettlementIcon sx={{ fontSize: 26 }} /> },
          { label: 'Total',       value: counts.all,         color: '#546e7a', bgcolor: 'rgba(84,110,122,0.08)', icon: <InfoIcon sx={{ fontSize: 26 }} /> },
        ].map(s => (
          <Grid key={s.label} size={{ xs: 6, sm: 3 }}>
            <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
              <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box sx={{ p: 1, borderRadius: 1.5, bgcolor: s.bgcolor, color: s.color }}>{s.icon}</Box>
                  <Box>
                    <Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1 }}>{s.value}</Typography>
                    <Typography variant="caption" color="text.secondary">{s.label}</Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Preferences panel */}
      <Collapse in={prefsOpen}>
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', mb: 3, borderRadius: 2, overflow: 'hidden' }}>
          <Box sx={{ px: 3, py: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', bgcolor: 'action.hover' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <PrefsIcon color="action" fontSize="small" />
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Notification Preferences</Typography>
            </Box>
            <IconButton size="small" onClick={() => setPrefsOpen(false)}><CollapseIcon /></IconButton>
          </Box>
          <Divider />
          <Box sx={{ p: 3 }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
              Choose which events trigger in-app notifications. Email delivery settings are managed in{' '}
              <Typography component="span" variant="body2" color="primary" sx={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => navigate('/settings')}>
                Settings → Notifications
              </Typography>.
            </Typography>
            <Grid container spacing={0}>
              {prefLabels.map((p, i) => (
                <Grid key={p.key} size={{ xs: 12, sm: 6 }}>
                  <Box sx={{ py: 1.25, pr: 3, borderBottom: i < prefLabels.length - 2 ? '1px solid' : 'none', borderColor: 'divider' }}>
                    <FormControlLabel
                      control={
                        <Switch
                          size="small"
                          checked={prefs[p.key]}
                          onChange={e => setPrefs(prev => ({ ...prev, [p.key]: e.target.checked }))}
                        />
                      }
                      label={
                        <Box sx={{ ml: 0.5 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{p.label}</Typography>
                          <Typography variant="caption" color="text.secondary">{p.sub}</Typography>
                        </Box>
                      }
                      sx={{ alignItems: 'flex-start', m: 0 }}
                    />
                  </Box>
                </Grid>
              ))}
            </Grid>
            <Box sx={{ mt: 2.5, display: 'flex', gap: 1.5 }}>
              <Button variant="contained" size="small" onClick={() => setPrefsOpen(false)}>Save Preferences</Button>
              <Button variant="outlined" size="small" onClick={() => setPrefs(Object.fromEntries(prefLabels.map(p => [p.key, true])))}>Reset to Default</Button>
            </Box>
          </Box>
        </Paper>
      </Collapse>

      {/* Main panel */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>

        {/* Filter tabs */}
        <Box sx={{ px: 2, pt: 1, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={filter}
            onChange={(_, v) => { if (v) { setFilter(v); setSelected(new Set()); } }}
            sx={{ flexWrap: 'wrap', '& .MuiToggleButton-root': { border: 'none', borderRadius: '4px !important', px: 1.5, py: 0.75, fontSize: '0.8125rem' } }}
          >
            {([
              { value: 'all',         label: 'All',          count: counts.all },
              { value: 'unread',      label: 'Unread',       count: counts.unread },
              { value: 'transaction', label: 'Transactions', count: counts.transaction },
              { value: 'settlement',  label: 'Settlements',  count: counts.settlement },
              { value: 'paybylink',   label: 'Pay by Link',  count: counts.paybylink },
              { value: 'warning',     label: 'Warnings',     count: counts.warning },
              { value: 'system',      label: 'System',       count: counts.system },
            ] as { value: FilterTab; label: string; count: number }[]).map(t => (
              <ToggleButton key={t.value} value={t.value}>
                {t.label}
                {t.count > 0 && (
                  <Chip
                    label={t.count}
                    size="small"
                    sx={{ ml: 0.75, height: 16, fontSize: '0.65rem', fontWeight: 700,
                      bgcolor: filter === t.value ? 'primary.main' : 'action.selected',
                      color: filter === t.value ? 'white' : 'text.secondary',
                    }}
                  />
                )}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Box>

        {/* Bulk action toolbar */}
        <Box sx={{
          px: 2.5, py: 1.25,
          display: 'flex', alignItems: 'center', gap: 1.5,
          borderBottom: '1px solid', borderColor: 'divider',
          bgcolor: someSelected ? 'rgba(25,118,210,0.04)' : 'transparent',
          transition: 'background 0.2s',
          minHeight: 52,
        }}>
          <Checkbox
            size="small"
            checked={allSelected}
            indeterminate={someSelected && !allSelected}
            onChange={toggleSelectAll}
          />
          {someSelected ? (
            <>
              <Typography variant="body2" sx={{ fontWeight: 600, color: 'primary.main' }}>
                {selected.size} selected
              </Typography>
              <Button size="small" startIcon={<MarkAllReadIcon />} onClick={() => handleBulkAction('read')}>
                Mark read
              </Button>
              <Button size="small" startIcon={<DotIcon sx={{ fontSize: 10 }} />} onClick={() => handleBulkAction('unread')}>
                Mark unread
              </Button>
              <Button size="small" color="error" startIcon={<DeleteIcon />} onClick={() => handleBulkAction('delete')}>
                Delete
              </Button>
            </>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {filtered.length} notification{filtered.length !== 1 ? 's' : ''}
            </Typography>
          )}
        </Box>

        {/* Grouped list */}
        {grouped.length === 0 ? (
          <Box sx={{ py: 10, textAlign: 'center' }}>
            <BellIcon sx={{ fontSize: 56, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" color="text.secondary" sx={{ fontWeight: 500 }}>
              No notifications
            </Typography>
            <Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
              {filter !== 'all' ? 'Try selecting a different filter' : "You're all caught up!"}
            </Typography>
          </Box>
        ) : (
          grouped.map(group => (
            <Box key={group.label}>
              {/* Group header */}
              <Box sx={{ px: 2.5, py: 1, bgcolor: 'rgba(0,0,0,0.025)', borderBottom: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
                <TimeIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
                <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.8, color: 'text.secondary', fontSize: '0.7rem' }}>
                  {group.label}
                </Typography>
                <Chip label={group.items.length} size="small" sx={{ height: 16, fontSize: '0.65rem', ml: 0.5 }} />
              </Box>

              <List disablePadding>
                {group.items.map((notif, idx) => {
                  const cfg = typeConfig[notif.type];
                  const isSelected = selected.has(notif.id);

                  return (
                    <React.Fragment key={notif.id}>
                      <ListItem
                        alignItems="flex-start"
                        sx={{
                          px: 2.5,
                          py: 2,
                          cursor: 'pointer',
                          bgcolor: isSelected
                            ? 'rgba(25,118,210,0.06)'
                            : notif.read
                            ? 'transparent'
                            : 'rgba(25,118,210,0.035)',
                          transition: 'background 0.15s',
                          '&:hover': { bgcolor: isSelected ? 'rgba(25,118,210,0.1)' : 'action.hover' },
                          gap: 1.5,
                        }}
                      >
                        {/* Checkbox */}
                        <Box sx={{ display: 'flex', alignItems: 'flex-start', pt: 0.5 }}>
                          <Checkbox
                            size="small"
                            checked={isSelected}
                            onChange={() => toggleSelect(notif.id)}
                            onClick={e => e.stopPropagation()}
                            sx={{ p: 0.25, mr: 1 }}
                          />
                        </Box>

                        {/* Avatar */}
                        <ListItemAvatar sx={{ minWidth: 'auto', mt: 0.25 }}>
                          <Avatar sx={{ width: 40, height: 40, bgcolor: cfg.bgcolor, color: cfg.color }}>
                            {cfg.icon}
                          </Avatar>
                        </ListItemAvatar>

                        {/* Content */}
                        <ListItemText
                          onClick={() => handleItemClick(notif)}
                          sx={{ flex: 1, m: 0, cursor: 'pointer' }}
                          primary={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4, flexWrap: 'wrap' }}>
                              <Typography
                                variant="body2"
                                sx={{ fontWeight: notif.read ? 500 : 700, color: 'text.primary', lineHeight: 1.3 }}
                              >
                                {notif.title}
                              </Typography>
                              <Chip
                                label={cfg.label}
                                size="small"
                                sx={{
                                  height: 18, fontSize: '0.65rem', fontWeight: 700,
                                  bgcolor: cfg.bgcolor, color: cfg.color,
                                }}
                              />
                              {notif.meta && (
                                <Chip
                                  label={notif.meta}
                                  size="small"
                                  variant="outlined"
                                  sx={{ height: 18, fontSize: '0.65rem', fontWeight: 600 }}
                                />
                              )}
                            </Box>
                          }
                          secondary={
                            <Box>
                              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5, mb: 0.5 }}>
                                {notif.body}
                              </Typography>
                              <Typography variant="caption" color="text.disabled">
                                {formatTime(notif.timestamp)}
                              </Typography>
                            </Box>
                          }
                        />

                        {/* Right: unread dot + actions */}
                        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 1, flexShrink: 0, pt: 0.25 }}>
                          {!notif.read && (
                            <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'primary.main', mt: 0.5 }} />
                          )}
                          <Stack direction="row" spacing={0}>
                            <Tooltip title={notif.read ? 'Mark unread' : 'Mark read'}>
                              <IconButton
                                size="small"
                                onClick={e => { e.stopPropagation(); notif.read ? markUnread([notif.id]) : markRead([notif.id]); }}
                                sx={{ opacity: 0, '.MuiListItem-root:hover &': { opacity: 1 }, transition: 'opacity 0.15s' }}
                              >
                                <MarkAllReadIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete">
                              <IconButton
                                size="small"
                                color="error"
                                onClick={e => { e.stopPropagation(); deleteItems([notif.id]); }}
                                sx={{ opacity: 0, '.MuiListItem-root:hover &': { opacity: 1 }, transition: 'opacity 0.15s' }}
                              >
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Stack>
                        </Box>
                      </ListItem>
                      {idx < group.items.length - 1 && <Divider component="li" sx={{ ml: '84px' }} />}
                    </React.Fragment>
                  );
                })}
              </List>
              <Divider />
            </Box>
          ))
        )}
      </Paper>
    </Box>
  );
};
