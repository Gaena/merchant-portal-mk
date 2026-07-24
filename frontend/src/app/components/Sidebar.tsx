import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
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
  Assessment as ReportsIcon,
  Notifications as NotificationsIcon,
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
  const isAdmin = user?.role === 'SYSTEM_ADMIN' || user?.role === 'ADMIN';

  const navItems: NavItem[] = [
    { label: 'Home Page', path: '/', icon: <HomeIcon /> },
    { label: 'Pay by Link', path: '/pay-by-link', icon: <LinkIcon /> },
    { 
      label: 'Transaction List', 
      icon: <ReceiptIcon />,
      children: [
        { label: 'E-commerce', path: '/transactions/ecommerce', icon: <EcommerceIcon /> },
      ]
    },
    { label: 'Terminals', path: '/terminals', icon: <POSIcon /> },
    ...(isAdmin ? [{ label: 'Companies', path: '/companies', icon: <BusinessIcon /> }] : []),
    { label: 'Users', path: '/users', icon: <GroupIcon /> },
    { label: 'Audit Logs', path: '/audit-logs', icon: <HistoryIcon /> },
    { label: 'Settings', path: '/settings', icon: <SettingsIcon /> }
  ];

  const [expandedItems, setExpandedItems] = useState<string[]>(['Transaction List']);

  const handleNavigate = (path: string) => {
    navigate(path);
    if (window.innerWidth < 900) {
      onMobileClose();
    }
  };

  const handleToggleExpand = (label: string) => {
    setExpandedItems(prev => 
      prev.includes(label) 
        ? prev.filter(item => item !== label)
        : [...prev, label]
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
          const isExpanded = expandedItems.includes(item.label);
          const isActive = isPathActive(item.path, item.children);

          if (hasChildren) {
            return (
              <React.Fragment key={item.label}>
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
                      onClick={() => handleToggleExpand(item.label)}
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
              Version 1.0.0
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
