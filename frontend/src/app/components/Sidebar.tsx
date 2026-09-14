import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { canAccessPath } from '../auth/routeAccess';
import { useLanguage } from '../context/LanguageContext';
import {
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Box,
  Divider,
  Typography,
  Tooltip,
  Collapse
} from '@mui/material';
import {
  Home as HomeIcon,
  Receipt as ReceiptIcon,
  Settings as SettingsIcon,
  ShoppingCart as EcommerceIcon,
  PointOfSale as POSIcon,
  Link as LinkIcon,
  Business as BusinessIcon,
  Group as GroupIcon,
  History as HistoryIcon,
  ExpandLess,
  ExpandMore
} from '@mui/icons-material';
import { useNavigate, useLocation } from 'react-router';

interface SidebarProps {
  mobileOpen: boolean;
  desktopOpen: boolean;
  onMobileClose: () => void;
  drawerWidth: number;
  miniDrawerWidth: number;
}

interface NavItem {
  /** Ключ группы или пункта: не зависит от языка, в отличие от подписи. */
  key: string;
  label: string;
  path?: string;
  icon: React.ReactNode;
  children?: NavItem[];
}

export const Sidebar: React.FC<SidebarProps> = ({ 
  mobileOpen, 
  desktopOpen,
  onMobileClose, 
  drawerWidth,
  miniDrawerWidth 
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { tObj } = useLanguage();

  // Пункт меню не показывается, если маршрут недоступен роли. Список ролей — тот же, что у
  // RoleRoute в routes.tsx (auth/routeAccess.ts): это UX, а не безопасность — права проверяет бэкенд.
  const visible = (path: string) => canAccessPath(user?.role, path);

  const navItems: NavItem[] = [
    { key: 'home', label: tObj.nav.home, path: '/', icon: <HomeIcon /> },
    { key: 'pay-by-link', label: tObj.nav.payByLink, path: '/pay-by-link', icon: <LinkIcon /> },
    {
      key: 'transactions',
      label: tObj.nav.transactions,
      icon: <ReceiptIcon />,
      children: [
        { key: 'ecommerce', label: tObj.nav.ecommerce, path: '/transactions/ecommerce', icon: <EcommerceIcon /> },
      ]
    },
    { key: 'terminals', label: tObj.nav.terminals, path: '/terminals', icon: <POSIcon /> },
    { key: 'companies', label: tObj.nav.companies, path: '/companies', icon: <BusinessIcon /> },
    { key: 'users', label: tObj.nav.users, path: '/users', icon: <GroupIcon /> },
    { key: 'audit-logs', label: tObj.nav.auditLogs, path: '/audit-logs', icon: <HistoryIcon /> },
    { key: 'settings', label: tObj.nav.settings, path: '/settings', icon: <SettingsIcon /> }
  ]
    .map(item => item.children
      ? { ...item, children: item.children.filter(child => !child.path || visible(child.path)) }
      : item)
    .filter(item => item.children ? item.children.length > 0 : (!item.path || visible(item.path)));

  // Раскрытые группы — по ключу, не по подписи: ключ 'Transaction List' не совпадал ни с одной
  // локализованной подписью, и группа операций всегда стартовала свёрнутой, а смена языка её
  // схлопывала.
  const [expandedItems, setExpandedItems] = useState<string[]>(['transactions']);

  const handleNavigate = (path: string) => {
    navigate(path);
    if (window.innerWidth < 900) {
      onMobileClose();
    }
  };

  const handleToggleExpand = (key: string) => {
    setExpandedItems(prev =>
      prev.includes(key)
        ? prev.filter(item => item !== key)
        : [...prev, key]
    );
  };

  const isPathActive = (path?: string, children?: NavItem[]): boolean => {
    if (path) {
      return location.pathname === path;
    }
    if (children) {
      return children.some(child => location.pathname === child.path);
    }
    return false;
  };

  const getDrawerContent = (isMini: boolean) => (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', pt: 2 }}>
      <List sx={{ flex: 1, pt: 2 }}>
        {navItems.map((item) => {
          const hasChildren = item.children && item.children.length > 0;
          const isExpanded = expandedItems.includes(item.key);
          const isActive = isPathActive(item.path, item.children);

          if (hasChildren) {
            return (
              <React.Fragment key={item.key}>
                {/* Parent Item */}
                <ListItem disablePadding sx={{ px: isMini ? 1 : 2, mb: 0.5 }}>
                  {isMini ? (
                    <Tooltip title={item.label} placement="right">
                      <ListItemButton
                        selected={isActive}
                        onClick={() => item.children && item.children[0].path && handleNavigate(item.children[0].path)}
                        sx={{
                          borderRadius: 1,
                          justifyContent: 'center',
                          px: 2,
                          '&.Mui-selected': {
                            bgcolor: 'primary.main',
                            color: 'white',
                            '&:hover': {
                              bgcolor: 'primary.dark',
                            },
                            '& .MuiListItemIcon-root': {
                              color: 'white',
                            }
                          }
                        }}
                      >
                        <ListItemIcon sx={{ 
                          color: isActive ? 'white' : 'inherit',
                          minWidth: 'auto'
                        }}>
                          {item.icon}
                        </ListItemIcon>
                      </ListItemButton>
                    </Tooltip>
                  ) : (
                    <ListItemButton
                      selected={isActive}
                      onClick={() => handleToggleExpand(item.key)}
                      sx={{
                        borderRadius: 1,
                        '&.Mui-selected': {
                          bgcolor: 'primary.main',
                          color: 'white',
                          '&:hover': {
                            bgcolor: 'primary.dark',
                          },
                          '& .MuiListItemIcon-root': {
                            color: 'white',
                          }
                        }
                      }}
                    >
                      <ListItemIcon sx={{ 
                        color: isActive ? 'white' : 'inherit',
                        minWidth: 40
                      }}>
                        {item.icon}
                      </ListItemIcon>
                      <ListItemText primary={item.label} />
                      {isExpanded ? <ExpandLess /> : <ExpandMore />}
                    </ListItemButton>
                  )}
                </ListItem>

                {/* Children Items */}
                {!isMini && (
                  <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                    <List component="div" disablePadding>
                      {item.children?.map((child) => {
                        const childActive = location.pathname === child.path;
                        return (
                          <ListItem key={child.path} disablePadding sx={{ px: 2 }}>
                            <ListItemButton
                              selected={childActive}
                              onClick={() => child.path && handleNavigate(child.path)}
                              sx={{
                                pl: 4,
                                borderRadius: 1,
                                '&.Mui-selected': {
                                  bgcolor: 'primary.main',
                                  color: 'white',
                                  '&:hover': {
                                    bgcolor: 'primary.dark',
                                  },
                                  '& .MuiListItemIcon-root': {
                                    color: 'white',
                                  }
                                }
                              }}
                            >
                              <ListItemIcon sx={{ 
                                color: childActive ? 'white' : 'inherit',
                                minWidth: 40
                              }}>
                                {child.icon}
                              </ListItemIcon>
                              <ListItemText primary={child.label} />
                            </ListItemButton>
                          </ListItem>
                        );
                      })}
                    </List>
                  </Collapse>
                )}
              </React.Fragment>
            );
          }

          // Regular item without children
          const content = (
            <ListItem key={item.path} disablePadding sx={{ px: isMini ? 1 : 2, mb: 0.5 }}>
              <ListItemButton
                selected={isActive}
                onClick={() => item.path && handleNavigate(item.path)}
                sx={{
                  borderRadius: 1,
                  justifyContent: isMini ? 'center' : 'flex-start',
                  px: isMini ? 2 : 2,
                  '&.Mui-selected': {
                    bgcolor: 'primary.main',
                    color: 'white',
                    '&:hover': {
                      bgcolor: 'primary.dark',
                    },
                    '& .MuiListItemIcon-root': {
                      color: 'white',
                    }
                  }
                }}
              >
                <ListItemIcon sx={{ 
                  color: isActive ? 'white' : 'inherit',
                  minWidth: isMini ? 'auto' : 40
                }}>
                  {item.icon}
                </ListItemIcon>
                {!isMini && <ListItemText primary={item.label} />}
              </ListItemButton>
            </ListItem>
          );
          
          return isMini ? (
            <Tooltip key={item.path} title={item.label} placement="right">
              {content}
            </Tooltip>
          ) : content;
        })}
      </List>
      {!isMini && (
        <>
          <Divider />
          <Box sx={{ p: 2 }}>
            <Typography variant="caption" color="text.secondary">
              {/* Версия — из package.json при сборке (vite.config.ts), не зашитая строка. */}
              v{__APP_VERSION__}
            </Typography>
          </Box>
        </>
      )}
    </Box>
  );

  return (
    <>
      {/* Mobile drawer */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onMobileClose}
        ModalProps={{
          keepMounted: true, // Better mobile performance
        }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': { 
            boxSizing: 'border-box', 
            width: drawerWidth,
            mt: { xs: 7 }
          },
        }}
      >
        {getDrawerContent(false)}
      </Drawer>

      {/* Desktop drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': { 
            boxSizing: 'border-box', 
            width: desktopOpen ? drawerWidth : miniDrawerWidth,
            mt: 8,
            overflowX: 'hidden',
            transition: (theme) => theme.transitions.create('width', {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
          },
        }}
        open
      >
        {getDrawerContent(!desktopOpen)}
      </Drawer>
    </>
  );
};
