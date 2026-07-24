import React, { useState } from 'react';
import { Box, Container } from '@mui/material';
import { Outlet } from 'react-router';
import { Header } from '../components/Header';
import { Sidebar } from '../components/Sidebar';

const DRAWER_WIDTH = 260;
const MINI_DRAWER_WIDTH = 72;

interface MainLayoutProps {
  newTransactionCount: number;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ newTransactionCount }) => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(true);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleDesktopDrawerToggle = () => {
    setDesktopOpen(!desktopOpen);
  };

  return (
    <Box sx={{ display: 'flex' }}>
      <Header 
        newTransactionCount={newTransactionCount} 
        onMenuClick={handleDrawerToggle}
        onDesktopDrawerToggle={handleDesktopDrawerToggle}
      />
      
      <Sidebar
        mobileOpen={mobileOpen}
        desktopOpen={desktopOpen}
        onMobileClose={handleDrawerToggle}
        drawerWidth={DRAWER_WIDTH}
        miniDrawerWidth={MINI_DRAWER_WIDTH}
      />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { 
            xs: '100%',
            md: desktopOpen ? `calc(100% - ${DRAWER_WIDTH}px)` : `calc(100% - ${MINI_DRAWER_WIDTH}px)` 
          },
          ml: { 
            md: desktopOpen ? `${DRAWER_WIDTH}px` : `${MINI_DRAWER_WIDTH}px` 
          },
          mt: 8, // Height of AppBar
          minHeight: '100vh',
          bgcolor: 'background.default',
          transition: (theme) => theme.transitions.create(['margin', 'width'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Container maxWidth="xl">
          <Outlet />
        </Container>
      </Box>
    </Box>
  );
};