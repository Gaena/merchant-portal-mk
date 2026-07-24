import React, { useState, useEffect } from 'react';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import {
  Box,
  Typography,
  Paper,
  Switch,
  FormControlLabel,
  Divider,
  TextField,
  Button,
  MenuItem,
  Tabs,
  Tab,
  Card,
  CardContent,
  Stack,
  Chip,
  IconButton,
  InputAdornment,
  Alert,
  List,
  ListItem,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Avatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import {
  Person as PersonIcon,
  Security as SecurityIcon,
  Notifications as NotificationsIcon,
  Payment as PaymentIcon,
  Code as CodeIcon,
  Palette as PaletteIcon,
  Language as LanguageIcon,
  Download as DownloadIcon,
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
  ContentCopy as CopyIcon,
  Refresh as RefreshIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  CheckCircle as CheckCircleIcon,
  PhoneAndroid as PhoneIcon,
  Email as EmailIcon,
  Laptop as LaptopIcon,
  Group as GroupIcon,
  History as HistoryIcon,
  PointOfSale as TerminalIcon,
  Business as BusinessIcon,
} from '@mui/icons-material';

export const SettingsPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'SYSTEM_ADMIN' || user?.role === 'ADMIN';
  const [currentTab, setCurrentTab] = useState<string>('account');
  const [showApiKey, setShowApiKey] = useState(false);
  const [showWebhookSecret, setShowWebhookSecret] = useState(false);
  const [saved, setSaved] = useState(false);
  const [apiKeyDialogOpen, setApiKeyDialogOpen] = useState(false);
  const [newApiKey, setNewApiKey] = useState('');

  // Account settings state
  const [accountSettings, setAccountSettings] = useState({
    merchantName: 'MilliKart Merchant',
    merchantEmail: user?.email || 'admin@millikart.az',
    businessPhone: '+994 12 345 6789',
    businessAddress: 'Baku, Azerbaijan',
    taxId: 'AZ1002003004',
    contactPerson: user?.fullName || (user?.email ? user.email.split('@')[0] : 'Admin User'),
  });

  // Security settings state
  const [securitySettings, setSecuritySettings] = useState({
    twoFactorEnabled: true,
    sessionTimeout: '30',
    passwordLastChanged: '2026-03-15',
  });

  // Notification settings state
  const [notificationSettings, setNotificationSettings] = useState({
    emailNewTransactions: true,
    emailFailedPayments: true,
    emailDailySummary: false,
    emailWeeklySummary: true,
    smsHighValueTransactions: false,
    pushNotifications: true,
  });

  // Payment settings state
  const [paymentSettings, setPaymentSettings] = useState({
    currency: 'AZN',
    minTransactionAmount: '1.00',
    maxTransactionAmount: '10000.00',
    allowSMS: true,
    allowDMS: true,
    allowMIT: true,
    allowCIT: false,
    require3DSecure: true,
    autoCapture: true,
    captureDelay: '7',
  });

  // API settings state
  const [apiSettings] = useState({
    apiKey: '',
    webhookUrl: 'https://api.acmecorp.com/webhooks/payments',
    webhookSecret: '',
    apiVersion: 'v1',
  });

  // Display settings state
  const [displaySettings, setDisplaySettings] = useState({
    theme: 'light',
    language: 'en',
    dateFormat: 'DD.MM.YYYY',
    timeFormat: '24h',
    timezone: 'Asia/Baku',
    currency: 'AZN',
    itemsPerPage: '10',
    compactView: false,
    showTimestamps: true,
  });

  // Company ID state
  const [companyId, setCompanyId] = useState<string | null>(null);

  // Companies management state (Admin only)
  const [companiesList, setCompaniesList] = useState<any[]>([]);
  const [companyDialogOpen, setCompanyDialogOpen] = useState(false);
  const [companyForm, setCompanyForm] = useState({ id: '', name: '' });
  const [companyError, setCompanyError] = useState('');

  // Terminal state
  const [terminalsList, setTerminalsList] = useState<any[]>([]);
  const [terminalDialogOpen, setTerminalDialogOpen] = useState(false);
  const [terminalForm, setTerminalForm] = useState({ id: '', name: '', login: '', password: '' });
  const [terminalError, setTerminalError] = useState('');

  // User management state
  const [usersList, setUsersList] = useState<any[]>([]);
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [userForm, setUserForm] = useState({ username: '', password: '', fullName: '', role: 'COMPANY_HEAD' });
  const [userError, setUserError] = useState('');

  // Audit Log state
  const [auditLogsList, setAuditLogsList] = useState<any[]>([]);

  useEffect(() => {
    // Sync user email and contact person if logged in
    if (user) {
      setAccountSettings(prev => ({
        ...prev,
        merchantEmail: user.email || prev.merchantEmail,
        contactPerson: user.fullName || (user.email ? user.email.split('@')[0] : prev.contactPerson),
      }));
    }

    // Companies
    apiClient.get('/api/v1/companies')
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setCompaniesList(list);
        if (list.length > 0) {
          const userComp = user?.companyId ? list.find((c: any) => String(c.id) === String(user.companyId)) : list[0];
          const comp = userComp || list[0];
          setCompanyId(comp.id);
          setAccountSettings(prev => ({
            ...prev,
            merchantName: comp.name || prev.merchantName,
            merchantEmail: user?.email || comp.email || prev.merchantEmail,
            businessPhone: comp.phone || prev.businessPhone,
            taxId: comp.taxId || prev.taxId,
          }));
        }
      })
      .catch(() => {});

    // Terminals
    apiClient.get('/api/v1/terminals')
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setTerminalsList(list);
      })
      .catch(() => {});

    // Users
    apiClient.get('/api/v1/users')
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setUsersList(list);
      })
      .catch(() => {});

    // Audit logs
    apiClient.get('/api/v1/audit-logs')
      .then(res => {
        const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setAuditLogsList(list);
      })
      .catch(() => {});
  }, []);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: string) => {
    setCurrentTab(newValue);
  };

  const handleCreateCompany = async () => {
    if (!companyForm.id || !companyForm.name) {
      setCompanyError('Company ID and Name are required');
      return;
    }
    try {
      const res = await apiClient.post('/api/v1/companies', {
        id: companyForm.id,
        name: companyForm.name
      });
      setCompaniesList(prev => [...prev, res.data]);
      setCompanyDialogOpen(false);
      setCompanyForm({ id: '', name: '' });
      setCompanyError('');
    } catch (err: any) {
      setCompanyError(err.response?.data?.message || 'Failed to create company');
    }
  };

  const handleDeleteCompany = async (id: string) => {
    try {
      await apiClient.delete(`/api/v1/companies/${id}`);
      setCompaniesList(prev => prev.filter(c => c.id !== id));
    } catch {}
  };

  const handleSaveCompany = async () => {
    if (companyId) {
      try {
        await apiClient.patch(`/api/v1/companies/${companyId}`, { name: accountSettings.merchantName });
      } catch {}
    }
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  const handleSave = () => {
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  const handleCreateTerminal = async () => {
    if (!terminalForm.id || !terminalForm.name || !terminalForm.login || !terminalForm.password) {
      setTerminalError('All fields are required');
      return;
    }
    try {
      const payload = {
        id: Number(terminalForm.id),
        name: terminalForm.name,
        login: terminalForm.login,
        password: terminalForm.password,
        companyId: companyId || '1',
      };
      const res = await apiClient.post('/api/v1/terminals', payload);
      setTerminalsList(prev => [...prev, res.data]);
      setTerminalDialogOpen(false);
      setTerminalForm({ id: '', name: '', login: '', password: '' });
      setTerminalError('');
    } catch (err: any) {
      setTerminalError(err.response?.data?.message || 'Failed to create terminal');
    }
  };

  const handleDeleteTerminal = async (id: number) => {
    try {
      await apiClient.delete(`/api/v1/terminals/${id}`);
      setTerminalsList(prev => prev.filter(t => t.id !== id));
    } catch {}
  };

  const handleCreateUser = async () => {
    if (!userForm.username || !userForm.password || !userForm.fullName) {
      setUserError('Username, Password, and Full Name are required');
      return;
    }
    try {
      const payload = {
        username: userForm.username,
        password: userForm.password,
        fullName: userForm.fullName,
        role: userForm.role,
        companyId: companyId || '1',
      };
      const res = await apiClient.post('/api/v1/users', payload);
      setUsersList(prev => [...prev, res.data]);
      setUserDialogOpen(false);
      setUserForm({ username: '', password: '', fullName: '', role: 'COMPANY_HEAD' });
      setUserError('');
    } catch (err: any) {
      setUserError(err.response?.data?.message || 'Failed to create user');
    }
  };

  const handleDeleteUser = async (id: string) => {
    try {
      await apiClient.delete(`/api/v1/users/${id}`);
      setUsersList(prev => prev.filter(u => u.id !== id));
    } catch {}
  };

  const handleCopyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const handleGenerateNewApiKey = () => {
    const randomKey = `sk_live_${Math.random().toString(36).substring(2, 15)}${Math.random().toString(36).substring(2, 15)}`;
    setNewApiKey(randomKey);
    setApiKeyDialogOpen(true);
  };

  const activeSessions = [
    { device: 'Chrome on Windows', location: 'Baku, Azerbaijan', lastActive: '2 minutes ago', current: true },
    { device: 'Safari on iPhone', location: 'Baku, Azerbaijan', lastActive: '1 hour ago', current: false },
    { device: 'Chrome on MacBook', location: 'Baku, Azerbaijan', lastActive: '3 days ago', current: false },
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 600, mb: 1 }}>
          Settings
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your account, security, and payment processing settings
        </Typography>
      </Box>

      {/* Success Alert */}
      {saved && (
        <Alert severity="success" sx={{ mb: 3 }} icon={<CheckCircleIcon />}>
          Settings saved successfully!
        </Alert>
      )}

      {/* Tabs */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}
        >
          <Tab value="account" icon={<PersonIcon />} label="Account" iconPosition="start" />
          <Tab value="security" icon={<SecurityIcon />} label="Security" iconPosition="start" />
          {/* <Tab value="notifications" icon={<NotificationsIcon />} label="Notifications" iconPosition="start" /> */}
          <Tab value="payment" icon={<PaymentIcon />} label="Payment" iconPosition="start" />
          <Tab value="api" icon={<CodeIcon />} label="API & Webhooks" iconPosition="start" />
          <Tab value="display" icon={<PaletteIcon />} label="Display" iconPosition="start" />
        </Tabs>

        {/* Account Tab */}
        {currentTab === 'account' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Business Information
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
              <TextField
                fullWidth
                label="Merchant Name"
                value={accountSettings.merchantName}
                onChange={(e) => setAccountSettings({ ...accountSettings, merchantName: e.target.value })}
              />
              <TextField
                fullWidth
                label="Business Email"
                type="email"
                value={accountSettings.merchantEmail}
                onChange={(e) => setAccountSettings({ ...accountSettings, merchantEmail: e.target.value })}
              />
              <TextField
                fullWidth
                label="Business Phone"
                value={accountSettings.businessPhone}
                onChange={(e) => setAccountSettings({ ...accountSettings, businessPhone: e.target.value })}
              />
              <TextField
                fullWidth
                label="Tax ID / VOEN"
                value={accountSettings.taxId}
                onChange={(e) => setAccountSettings({ ...accountSettings, taxId: e.target.value })}
              />
              <TextField
                fullWidth
                label="Contact Person"
                value={accountSettings.contactPerson}
                onChange={(e) => setAccountSettings({ ...accountSettings, contactPerson: e.target.value })}
                sx={{ gridColumn: { md: 'span 2' } }}
              />
              <TextField
                fullWidth
                label="Business Address"
                multiline
                rows={3}
                value={accountSettings.businessAddress}
                onChange={(e) => setAccountSettings({ ...accountSettings, businessAddress: e.target.value })}
                sx={{ gridColumn: { md: 'span 2' } }}
              />
            </Box>
            <Button variant="contained" size="large" onClick={handleSaveCompany}>
              Save Changes
            </Button>
          </Box>
        )}

        {/* Terminals Tab */}
        {currentTab === 'terminals' && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Acquiring Terminals
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Manage payment gateway terminals registered for your company
                </Typography>
              </Box>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setTerminalDialogOpen(true)}
              >
                Add Terminal
              </Button>
            </Box>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'action.hover' }}>
                    <TableCell sx={{ fontWeight: 700 }}>Terminal ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Login</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Company ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }} align="center">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {terminalsList.map((term: any) => (
                    <TableRow key={term.id} hover>
                      <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{term.id}</TableCell>
                      <TableCell>{term.name}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{term.login}</TableCell>
                      <TableCell>{term.companyId}</TableCell>
                      <TableCell align="center">
                        <IconButton color="error" size="small" onClick={() => handleDeleteTerminal(term.id)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {terminalsList.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">No acquiring terminals registered yet.</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}

        {/* Companies Tab (Admin Only) */}
        {currentTab === 'companies' && isAdmin && (
          <Box sx={{ p: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  System Companies Management (Admin)
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Manage all merchant companies in the directory
                </Typography>
              </Box>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setCompanyDialogOpen(true)}
              >
                Add Company
              </Button>
            </Box>
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'action.hover' }}>
                    <TableCell sx={{ fontWeight: 700 }}>Company ID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Created At</TableCell>
                    <TableCell sx={{ fontWeight: 700 }} align="center">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {companiesList.map((comp: any) => (
                    <TableRow key={comp.id} hover>
                      <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{comp.id}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{comp.name}</TableCell>
                      <TableCell>
                        <Chip label={comp.status || 'ACTIVE'} size="small" color="success" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ fontSize: '0.8rem' }}>
                        {comp.createdAt ? new Date(comp.createdAt).toLocaleString() : 'N/A'}
                      </TableCell>
                      <TableCell align="center">
                        <IconButton color="error" size="small" onClick={() => handleDeleteCompany(comp.id)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {companiesList.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">No companies registered yet.</Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}



        {/* Security Tab */}
        {currentTab === 'security' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Authentication & Access
            </Typography>

            <Card sx={{ mb: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                      Two-Factor Authentication
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Add an extra layer of security to your account
                    </Typography>
                  </Box>
                  <Switch
                    checked={securitySettings.twoFactorEnabled}
                    onChange={(e) => setSecuritySettings({ ...securitySettings, twoFactorEnabled: e.target.checked })}
                  />
                </Box>
                {securitySettings.twoFactorEnabled && (
                  <Alert severity="success" sx={{ mt: 2 }}>
                    Two-factor authentication is enabled. Your account is protected.
                  </Alert>
                )}
              </CardContent>
            </Card>

            <Card sx={{ mb: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
                  Password
                </Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    Last changed: {securitySettings.passwordLastChanged}
                  </Typography>
                  <Button variant="outlined" size="small">
                    Change Password
                  </Button>
                </Box>
              </CardContent>
            </Card>

            <Card sx={{ mb: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
                  Session Management
                </Typography>
                <TextField
                  select
                  fullWidth
                  label="Auto-logout after inactivity"
                  value={securitySettings.sessionTimeout}
                  onChange={(e) => setSecuritySettings({ ...securitySettings, sessionTimeout: e.target.value })}
                  sx={{ mb: 2 }}
                >
                  <MenuItem value="15">15 minutes</MenuItem>
                  <MenuItem value="30">30 minutes</MenuItem>
                  <MenuItem value="60">1 hour</MenuItem>
                  <MenuItem value="120">2 hours</MenuItem>
                  <MenuItem value="never">Never</MenuItem>
                </TextField>
              </CardContent>
            </Card>

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
              Active Sessions
            </Typography>
            <List sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
              {activeSessions.map((session, index) => (
                <React.Fragment key={index}>
                  <ListItem>
                    <Avatar sx={{ mr: 2, bgcolor: session.current ? 'primary.main' : 'grey.400' }}>
                      {session.device.includes('iPhone') ? <PhoneIcon /> : <LaptopIcon />}
                    </Avatar>
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body1">{session.device}</Typography>
                          {session.current && <Chip label="Current" size="small" color="primary" />}
                        </Box>
                      }
                      secondary={`${session.location} • ${session.lastActive}`}
                    />
                    <ListItemSecondaryAction>
                      {!session.current && (
                        <IconButton edge="end" color="error">
                          <DeleteIcon />
                        </IconButton>
                      )}
                    </ListItemSecondaryAction>
                  </ListItem>
                  {index < activeSessions.length - 1 && <Divider />}
                </React.Fragment>
              ))}
            </List>
          </Box>
        )}

        {/* Notifications Tab (Disabled: Notification microservice not connected) */}
        {/* {currentTab === 'notifications' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Email Notifications
            </Typography>
            <Stack spacing={2} sx={{ mb: 4 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={notificationSettings.emailNewTransactions}
                    onChange={(e) =>
                      setNotificationSettings({ ...notificationSettings, emailNewTransactions: e.target.checked })
                    }
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">New Transactions</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Receive an email for every new successful transaction
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={notificationSettings.emailFailedPayments}
                    onChange={(e) =>
                      setNotificationSettings({ ...notificationSettings, emailFailedPayments: e.target.checked })
                    }
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Failed Payments</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Get notified when a payment fails
                    </Typography>
                  </Box>
                }
              />

              <FormControlLabel
                control={
                  <Switch
                    checked={notificationSettings.emailDailySummary}
                    onChange={(e) =>
                      setNotificationSettings({ ...notificationSettings, emailDailySummary: e.target.checked })
                    }
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Daily Summary</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Daily report of all transactions at 9:00 AM
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={notificationSettings.emailWeeklySummary}
                    onChange={(e) =>
                      setNotificationSettings({ ...notificationSettings, emailWeeklySummary: e.target.checked })
                    }
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Weekly Summary</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Weekly performance report every Monday
                    </Typography>
                  </Box>
                }
              />
            </Stack>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              SMS Notifications
            </Typography>
            <Stack spacing={2} sx={{ mb: 4 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={notificationSettings.smsHighValueTransactions}
                    onChange={(e) =>
                      setNotificationSettings({ ...notificationSettings, smsHighValueTransactions: e.target.checked })
                    }
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">High-Value Transactions</Typography>
                    <Typography variant="caption" color="text.secondary">
                      SMS alert for transactions over ₼1,000
                    </Typography>
                  </Box>
                }
              />

            </Stack>

            <Button variant="contained" size="large" onClick={handleSave}>
              Save Preferences
            </Button>
          </Box>
        )} */}

        {/* Payment Settings Tab */}
        {currentTab === 'payment' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Transaction Limits
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
              <TextField
                fullWidth
                label="Minimum Transaction Amount"
                type="number"
                value={paymentSettings.minTransactionAmount}
                onChange={(e) => setPaymentSettings({ ...paymentSettings, minTransactionAmount: e.target.value })}
                InputProps={{
                  startAdornment: <InputAdornment position="start">₼</InputAdornment>,
                }}
              />
              <TextField
                fullWidth
                label="Maximum Transaction Amount"
                type="number"
                value={paymentSettings.maxTransactionAmount}
                onChange={(e) => setPaymentSettings({ ...paymentSettings, maxTransactionAmount: e.target.value })}
                InputProps={{
                  startAdornment: <InputAdornment position="start">₼</InputAdornment>,
                }}
              />
            </Box>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Payment Methods
            </Typography>
            <Stack spacing={2} sx={{ mb: 4 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.allowSMS}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, allowSMS: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">SMS (Single Message System)</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Direct debit without pre-authorization
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.allowDMS}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, allowDMS: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">DMS (Dual Message System)</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Authorization followed by capture
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.allowMIT}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, allowMIT: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">MIT (Merchant Initiated Transaction)</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Recurring payments and subscriptions
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.allowCIT}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, allowCIT: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">CIT (Customer Initiated Transaction)</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Customer-initiated one-time payment
                    </Typography>
                  </Box>
                }
              />
            </Stack>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Security & Processing
            </Typography>
            <Stack spacing={2} sx={{ mb: 4 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.require3DSecure}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, require3DSecure: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Require 3D Secure</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Enhanced security with 3D Secure authentication
                    </Typography>
                  </Box>
                }
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={paymentSettings.autoCapture}
                    onChange={(e) => setPaymentSettings({ ...paymentSettings, autoCapture: e.target.checked })}
                  />
                }
                label={
                  <Box>
                    <Typography variant="body1">Auto-capture Payments</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Automatically capture authorized payments
                    </Typography>
                  </Box>
                }
              />
              {!paymentSettings.autoCapture && (
                <TextField
                  select
                  fullWidth
                  label="Capture Delay"
                  value={paymentSettings.captureDelay}
                  onChange={(e) => setPaymentSettings({ ...paymentSettings, captureDelay: e.target.value })}
                  sx={{ ml: 4 }}
                >
                  <MenuItem value="1">1 day</MenuItem>
                  <MenuItem value="3">3 days</MenuItem>
                  <MenuItem value="7">7 days</MenuItem>
                  <MenuItem value="14">14 days</MenuItem>
                </TextField>
              )}
            </Stack>

            <Button variant="contained" size="large" onClick={handleSave}>
              Save Settings
            </Button>
          </Box>
        )}

        {/* API & Webhooks Tab */}
        {currentTab === 'api' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              API Credentials
            </Typography>

            <Card sx={{ mb: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
                  API Key
                </Typography>
                <TextField
                  fullWidth
                  value={apiSettings.apiKey}
                  type={showApiKey ? 'text' : 'password'}
                  InputProps={{
                    readOnly: true,
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowApiKey(!showApiKey)} edge="end">
                          {showApiKey ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                        <IconButton onClick={() => handleCopyToClipboard(apiSettings.apiKey)} edge="end">
                          <CopyIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                  sx={{ mb: 2 }}
                />
                <Stack direction="row" spacing={2}>
                  <Button variant="outlined" startIcon={<RefreshIcon />} onClick={handleGenerateNewApiKey}>
                    Generate New Key
                  </Button>
                  <Button variant="outlined" color="error" startIcon={<DeleteIcon />}>
                    Revoke Key
                  </Button>
                </Stack>
              </CardContent>
            </Card>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Webhooks
            </Typography>

            <Card sx={{ mb: 3, border: '1px solid', borderColor: 'divider' }} elevation={0}>
              <CardContent>
                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2 }}>
                  Webhook URL
                </Typography>
                <TextField
                  fullWidth
                  value={apiSettings.webhookUrl}
                  placeholder="https://your-domain.com/webhooks/payments"
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton edge="end">
                          <CopyIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                  sx={{ mb: 2 }}
                />

                <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2, mt: 3 }}>
                  Webhook Secret
                </Typography>
                <TextField
                  fullWidth
                  value={apiSettings.webhookSecret}
                  type={showWebhookSecret ? 'text' : 'password'}
                  InputProps={{
                    readOnly: true,
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowWebhookSecret(!showWebhookSecret)} edge="end">
                          {showWebhookSecret ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                        <IconButton onClick={() => handleCopyToClipboard(apiSettings.webhookSecret)} edge="end">
                          <CopyIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                  sx={{ mb: 2 }}
                />

                <Alert severity="info" sx={{ mt: 2 }}>
                  Use the webhook secret to verify that webhook events are sent from your payment gateway.
                </Alert>
              </CardContent>
            </Card>

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>
              Webhook Events
            </Typography>
            <List sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 1, mb: 3 }}>
              {[
                { event: 'transaction.created', description: 'Sent when a new transaction is created' },
                { event: 'transaction.completed', description: 'Sent when a transaction is successfully completed' },
                { event: 'transaction.failed', description: 'Sent when a transaction fails' },
              ].map((item, index) => (
                <React.Fragment key={index}>
                  <ListItem>
                    <ListItemText
                      primary={
                        <Typography variant="body1" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                          {item.event}
                        </Typography>
                      }
                      secondary={item.description}
                    />
                    <ListItemSecondaryAction>
                      <Switch defaultChecked />
                    </ListItemSecondaryAction>
                  </ListItem>
                  {index < 2 && <Divider />}
                </React.Fragment>
              ))}
            </List>

            <Button variant="contained" size="large" onClick={handleSave}>
              Save API Settings
            </Button>
          </Box>
        )}

        {/* Display Settings Tab */}
        {currentTab === 'display' && (
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Appearance
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
              <TextField
                select
                fullWidth
                label="Theme"
                value={displaySettings.theme}
                onChange={(e) => setDisplaySettings({ ...displaySettings, theme: e.target.value })}
              >
                <MenuItem value="light">Light</MenuItem>
                <MenuItem value="dark">Dark</MenuItem>
                <MenuItem value="auto">Auto (System)</MenuItem>
              </TextField>
              <TextField
                select
                fullWidth
                label="Language"
                value={displaySettings.language}
                onChange={(e) => setDisplaySettings({ ...displaySettings, language: e.target.value })}
              >
                <MenuItem value="en">English</MenuItem>
                <MenuItem value="az">Azerbaijani</MenuItem>
                <MenuItem value="ru">Russian</MenuItem>
                <MenuItem value="tr">Turkish</MenuItem>
              </TextField>
            </Box>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Regional Settings
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
              <TextField
                select
                fullWidth
                label="Date Format"
                value={displaySettings.dateFormat}
                onChange={(e) => setDisplaySettings({ ...displaySettings, dateFormat: e.target.value })}
              >
                <MenuItem value="DD.MM.YYYY">DD.MM.YYYY</MenuItem>
                <MenuItem value="MM/DD/YYYY">MM/DD/YYYY</MenuItem>
                <MenuItem value="YYYY-MM-DD">YYYY-MM-DD</MenuItem>
              </TextField>
              <TextField
                select
                fullWidth
                label="Time Format"
                value={displaySettings.timeFormat}
                onChange={(e) => setDisplaySettings({ ...displaySettings, timeFormat: e.target.value })}
              >
                <MenuItem value="24h">24 Hour</MenuItem>
                <MenuItem value="12h">12 Hour (AM/PM)</MenuItem>
              </TextField>
              <TextField
                select
                fullWidth
                label="Timezone"
                value={displaySettings.timezone}
                onChange={(e) => setDisplaySettings({ ...displaySettings, timezone: e.target.value })}
              >
                <MenuItem value="Asia/Baku">Asia/Baku (GMT+4)</MenuItem>
                <MenuItem value="Europe/Istanbul">Europe/Istanbul (GMT+3)</MenuItem>
                <MenuItem value="Europe/Moscow">Europe/Moscow (GMT+3)</MenuItem>
                <MenuItem value="UTC">UTC (GMT+0)</MenuItem>
              </TextField>
              <TextField
                select
                fullWidth
                label="Currency Display"
                value={displaySettings.currency}
                onChange={(e) => setDisplaySettings({ ...displaySettings, currency: e.target.value })}
              >
                <MenuItem value="AZN">AZN (₼)</MenuItem>
                <MenuItem value="USD">USD ($)</MenuItem>
                <MenuItem value="EUR">EUR (€)</MenuItem>
              </TextField>
            </Box>

            <Divider sx={{ my: 4 }} />

            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Display Preferences
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
              <TextField
                select
                fullWidth
                label="Items Per Page"
                value={displaySettings.itemsPerPage}
                onChange={(e) => setDisplaySettings({ ...displaySettings, itemsPerPage: e.target.value })}
              >
                <MenuItem value="5">5</MenuItem>
                <MenuItem value="10">10</MenuItem>
                <MenuItem value="25">25</MenuItem>
                <MenuItem value="50">50</MenuItem>
                <MenuItem value="100">100</MenuItem>
              </TextField>
            </Box>

            <Stack spacing={2} sx={{ mb: 4 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={displaySettings.compactView}
                    onChange={(e) => setDisplaySettings({ ...displaySettings, compactView: e.target.checked })}
                  />
                }
                label="Compact table view"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={displaySettings.showTimestamps}
                    onChange={(e) => setDisplaySettings({ ...displaySettings, showTimestamps: e.target.checked })}
                  />
                }
                label="Show transaction timestamps"
              />
            </Stack>

            <Button variant="contained" size="large" onClick={handleSave}>
              Save Preferences
            </Button>
          </Box>
        )}
      </Paper>

      {/* Company Dialog (Admin Only) */}
      <Dialog open={companyDialogOpen} onClose={() => setCompanyDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add New Company</DialogTitle>
        <DialogContent>
          {companyError && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{companyError}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Company ID"
              value={companyForm.id}
              onChange={e => setCompanyForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. comp_001"
              fullWidth
            />
            <TextField
              label="Company Name"
              value={companyForm.name}
              onChange={e => setCompanyForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Acme Supermarkets LLC"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCompanyDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreateCompany}>Create Company</Button>
        </DialogActions>
      </Dialog>

      {/* API Key Dialog */}
      <Dialog open={apiKeyDialogOpen} onClose={() => setApiKeyDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New API Key Generated</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Make sure to copy your API key now. You won't be able to see it again!
          </Alert>
          <TextField
            fullWidth
            value={newApiKey}
            InputProps={{
              readOnly: true,
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => handleCopyToClipboard(newApiKey)}>
                    <CopyIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApiKeyDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Terminal Dialog */}
      <Dialog open={terminalDialogOpen} onClose={() => setTerminalDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Register New Acquiring Terminal</DialogTitle>
        <DialogContent>
          {terminalError && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{terminalError}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Terminal ID (Numeric)"
              type="number"
              value={terminalForm.id}
              onChange={e => setTerminalForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. 1"
              fullWidth
            />
            <TextField
              label="Terminal Name"
              value={terminalForm.name}
              onChange={e => setTerminalForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Main E-commerce Terminal"
              fullWidth
            />
            <TextField
              label="Login"
              value={terminalForm.login}
              onChange={e => setTerminalForm(f => ({ ...f, login: e.target.value }))}
              placeholder="e.g. term_login_001"
              fullWidth
            />
            <TextField
              label="Password"
              type="password"
              value={terminalForm.password}
              onChange={e => setTerminalForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTerminalDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreateTerminal}>Create Terminal</Button>
        </DialogActions>
      </Dialog>

      {/* User Dialog */}
      <Dialog open={userDialogOpen} onClose={() => setUserDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add New User</DialogTitle>
        <DialogContent>
          {userError && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{userError}</Alert>}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Username / Email"
              type="email"
              value={userForm.username}
              onChange={e => setUserForm(f => ({ ...f, username: e.target.value }))}
              placeholder="user@company.com"
              fullWidth
            />
            <TextField
              label="Password"
              type="password"
              value={userForm.password}
              onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
            />
            <TextField
              label="Full Name"
              value={userForm.fullName}
              onChange={e => setUserForm(f => ({ ...f, fullName: e.target.value }))}
              placeholder="John Doe"
              fullWidth
            />
            <TextField
              select
              label="Role"
              value={userForm.role}
              onChange={e => setUserForm(f => ({ ...f, role: e.target.value }))}
              fullWidth
            >
              <MenuItem value="COMPANY_HEAD">Company Head (Руководитель компании)</MenuItem>
              <MenuItem value="COMPANY_MANAGER">Company Manager (Менеджер компании)</MenuItem>
              <MenuItem value="COMPANY_EMPLOYEE">Company Employee (Сотрудник компании)</MenuItem>
              <MenuItem value="AUDITOR">Auditor (Аудитор)</MenuItem>
              <MenuItem value="SYSTEM_ADMIN">System Admin (Системный администратор)</MenuItem>
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreateUser}>Create User</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
