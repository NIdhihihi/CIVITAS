import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Linking, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { api, storage } from './src/api';
import { LanguageProvider, useLanguage } from './src/i18n';
import type { Member } from './src/types';
import { VotingFlow } from './src/VotingFlow';
import { AdminElections } from './src/AdminElections';
import { Account } from './src/Account';
import { CandidatePortal } from './src/CandidatePortal';
import { Brand, LanguagePicker, theme } from './src/ui';

type Tab = 'vote' | 'admin' | 'account' | 'candidate';

export default function App() {
  return (
    <LanguageProvider>
      <AppContent />
    </LanguageProvider>
  );
}

function AppContent() {
  const { t } = useLanguage();
  const [booting, setBooting] = useState(true);
  const [token, setToken] = useState<string | null>(null);
  const [member, setMember] = useState<Member | null>(null);
  const [tab, setTab] = useState<Tab>('account');
  const [returnToCandidate, setReturnToCandidate] = useState(false);
  const [deepLinkElection, setDeepLinkElection] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const saved = await storage.token();
        if (saved) {
          try {
            const profile = await api<Member>('/api/auth/me', { token: saved });
            setToken(saved);
            setMember(profile);
            setTab(['TENANT_ADMIN', 'PLATFORM_ADMIN', 'AUDITOR'].includes(profile.role) ? 'admin' : 'account');
          } catch {
            await storage.clearSession();
          }
        }
      } finally {
        setBooting(false);
      }
    })();
  }, []);

  useEffect(() => {
    function capture(url?: string | null) {
      if (!url) return;
      const match = url.match(/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/);
      if (match) {
        setDeepLinkElection(match[0]);
        setTab('vote');
      }
    }
    Linking.getInitialURL().then(capture).catch(() => undefined);
    const subscription = Linking.addEventListener('url', (event) => capture(event.url));
    return () => subscription.remove();
  }, []);

  async function signedIn(nextToken: string, nextMember: Member) {
    await storage.saveSession(nextToken, nextMember);
    setToken(nextToken);
    setMember(nextMember);
    const isAdmin = nextMember.role === 'TENANT_ADMIN' || nextMember.role === 'PLATFORM_ADMIN' || nextMember.role === 'AUDITOR';
    setTab(isAdmin ? 'admin' : returnToCandidate ? 'candidate' : 'account');
    setReturnToCandidate(false);
  }

  async function signedOut() {
    await storage.clearSession();
    setToken(null);
    setMember(null);
    setTab('account');
  }

  if (booting) {
    return (
      <View style={styles.splash}>
        <StatusBar style="dark" />
        <Brand />
        <ActivityIndicator size="large" color={theme.teal} />
        <Text style={styles.splashText}>{t('common.loading')}</Text>
      </View>
    );
  }

  const isAdmin = !!member && (member.role === 'TENANT_ADMIN' || member.role === 'PLATFORM_ADMIN' || member.role === 'AUDITOR');

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <View style={styles.header}>
        <Brand compact />
        <View style={styles.headerRight}>
          <LanguagePicker />
        </View>
      </View>

      <View style={styles.body}>
        <View style={[styles.screenWrap, tab !== 'vote' && styles.hidden]}>
          <VotingFlow
            token={token}
            member={member}
            initialElectionId={deepLinkElection}
            onRequireAccount={() => setTab('account')}
          />
        </View>

        {tab === 'admin' && isAdmin && token && member ? (
          <View style={styles.screenWrap}><AdminElections token={token} member={member} /></View>
        ) : null}
        {tab === 'candidate' ? (
          <View style={styles.screenWrap}><CandidatePortal token={token} member={member} onOpenAccount={() => { setReturnToCandidate(true); setTab('account'); }} /></View>
        ) : null}

        <View style={[styles.screenWrap, tab !== 'account' && styles.hidden]}>
          <Account
            token={token}
            member={member}
            onSignedIn={signedIn}
            onSignedOut={signedOut}
            onOpenAdmin={() => setTab('admin')}
            onOpenVote={() => setTab('vote')}
          />
        </View>
      </View>

      <View style={styles.tabBar}>
        <TabButton label={member ? t('tab.account') : t('common.signIn')} icon="●" active={tab === 'account'} onPress={() => setTab('account')} />
        <TabButton label="Candidate" icon="◇" active={tab === 'candidate'} onPress={() => setTab('candidate')} />
        <TabButton
          label={t('tab.vote')}
          icon="🗳"
          active={tab === 'vote'}
          onPress={() => setTab('vote')}
        />
        <TabButton
          label={t('tab.admin')}
          icon="⚙"
          active={tab === 'admin'}
          onPress={() => setTab('admin')}
          hidden={!isAdmin}
        />
        <TabButton
          label={t('tab.account')}
          hidden
          icon="👤"
          active={tab === 'account'}
          onPress={() => setTab('account')}
        />
      </View>
    </View>
  );
}

function TabButton({
  label,
  icon,
  active,
  onPress,
  hidden = false,
}: {
  label: string;
  icon: string;
  active: boolean;
  onPress: () => void;
  hidden?: boolean;
}) {
  if (hidden) return null;
  return (
    <Pressable onPress={onPress} style={styles.tabButton} accessibilityRole="tab" accessibilityState={{ selected: active }}>
      <Text style={[styles.tabIcon, active && styles.tabIconActive]}>{icon}</Text>
      <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{label}</Text>
      {active ? <View style={styles.tabIndicator} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: theme.bg },
  splash: {
    flex: 1,
    backgroundColor: theme.bg,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 14,
    padding: 24,
  },
  splashText: { color: theme.muted, fontSize: 14 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    paddingHorizontal: 18,
    paddingTop: Platform.OS === 'android' ? 16 : Platform.OS === 'ios' ? 52 : 16,
    paddingBottom: 10,
    backgroundColor: theme.white,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
  },
  headerRight: { flexShrink: 0 },
  body: { flex: 1 },
  screenWrap: { flex: 1 },
  hidden: { display: 'none' },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: theme.white,
    borderTopWidth: 1,
    borderTopColor: theme.border,
    paddingBottom: Platform.OS === 'ios' ? 24 : 8,
    paddingTop: 8,
  },
  tabButton: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3, paddingVertical: 4 },
  tabIcon: { fontSize: 19, opacity: 0.55 },
  tabIconActive: { opacity: 1 },
  tabLabel: { fontSize: 11, fontWeight: '800', color: theme.muted },
  tabLabelActive: { color: theme.teal },
  tabIndicator: { width: 18, height: 3, borderRadius: 2, backgroundColor: theme.teal, marginTop: 2 },
});
