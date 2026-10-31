import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { api } from './api';
import { useLanguage } from './i18n';
import type { EligibleElection, Member, Receipt } from './types';
import {
  Badge,
  Button,
  Card,
  Chip,
  Field,
  KeyValue,
  Muted,
  Notice,
  Screen,
  SectionTitle,
  Spinner,
  formatDateTime,
  shortHash,
  styles as uiStyles,
  theme,
} from './ui';

export function Account({
  token,
  member,
  onSignedIn,
  onSignedOut,
  onOpenAdmin,
  onOpenVote,
}: {
  token: string | null;
  member: Member | null;
  onSignedIn: (token: string, member: Member) => void;
  onSignedOut: () => void;
  onOpenAdmin: () => void;
  onOpenVote: () => void;
}) {
  const { t } = useLanguage();
  const [mode, setMode] = useState<'login' | 'register' | 'verify'>('login');
  const [portal, setPortal] = useState<'member' | 'admin'>('member');
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [eligible, setEligible] = useState<EligibleElection[]>([]);
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [loading, setLoading] = useState(false);

  const loadAccount = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const [elections, history] = await Promise.all([
        api<EligibleElection[]>('/api/voting/elections', { token }),
        api<Receipt[]>('/api/voting/receipts/mine', { token }),
      ]);
      setEligible(elections);
      setReceipts(history);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your account.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadAccount();
  }, [loadAccount]);

  async function login() {
    if (!email.includes('@') || password.length < 8) {
      setError(t('auth.invalid'));
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const path = portal === 'admin' ? '/api/auth/admin/login' : '/api/auth/login';
      const result = await api<{ token: string; member: Member }>(path, {
        method: 'POST',
        body: { email: email.trim(), password },
      });
      onSignedIn(result.token, result.member);
      setNotice(portal === 'admin' ? 'Administrator session started.' : 'Signed in.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed.');
    } finally {
      setBusy(false);
    }
  }

  async function register() {
    if (!email.includes('@') || password.length < 8 || !fullName.trim()) {
      setError(t('auth.invalid'));
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await api<{ developmentOtp?: string; message?: string }>('/api/auth/register', {
        method: 'POST',
        body: { email: email.trim(), fullName: fullName.trim(), password },
      });
      if (result.developmentOtp) setOtp(result.developmentOtp);
      setMode('verify');
      setNotice(
        result.developmentOtp
          ? t('account.devCode', { code: result.developmentOtp })
          : result.message || t('account.verifyEmail'),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed.');
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    setError('');
    try {
      const result = await api<{ token: string; member: Member }>('/api/auth/verify-email', {
        method: 'POST',
        body: { email: email.trim(), code: otp.trim() },
      });
      onSignedIn(result.token, result.member);
      setNotice('Email verified. Welcome to CIVITAS.');
      setMode('login');
      setOtp('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'That verification code was not accepted.');
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setBusy(true);
    setError('');
    try {
      const result = await api<{ developmentOtp?: string; message?: string }>('/api/auth/resend-otp', {
        method: 'POST',
        body: { email: email.trim() },
      });
      if (result.developmentOtp) setOtp(result.developmentOtp);
      setNotice(
        result.developmentOtp
          ? t('account.devCode', { code: result.developmentOtp })
          : result.message || 'A new code was sent.',
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not resend the code.');
    } finally {
      setBusy(false);
    }
  }

  if (!token || !member) {
    return (
      <Screen>
        <Card>
          <SectionTitle title={portal === 'admin' ? 'Administrator sign in' : t('account.signInTitle')} subtitle={portal === 'admin' ? 'For platform and society administrators' : t('account.signInText')} />
          {error ? <Notice tone="danger">{error}</Notice> : null}
          {notice ? <Notice tone="info">{notice}</Notice> : null}
          {mode !== 'verify' ? (
            <>
              {mode === 'login' ? (
                <>
                <Muted>Choose your sign-in portal</Muted>
                <View style={styles.portalRow}>
                  <Chip label={t('account.memberLogin')} active={portal === 'member'} onPress={() => setPortal('member')} />
                  <Chip label={t('account.adminLogin')} active={portal === 'admin'} onPress={() => setPortal('admin')} />
                </View>
                </>
              ) : null}
              {mode === 'register' ? (
                <Field label={t('common.fullName')} value={fullName} onChangeText={setFullName} placeholder="Your full name" />
              ) : null}
              <Field
                label={t('common.email')}
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                placeholder="you@example.com"
              />
              <Field label={t('common.password')} value={password} onChangeText={setPassword} secure placeholder="At least 8 characters" />
              {mode === 'login' ? (
                <>
                  <Button
                    title={portal === 'admin' ? t('account.adminLogin') : t('common.signIn')}
                    onPress={login}
                    loading={busy}
                    disabled={!email.includes('@') || password.length < 8}
                  />
                  {portal === 'member' ? (
                    <Button title={t('account.switchRegister')} kind="secondary" onPress={() => setMode('register')} />
                  ) : null}
                </>
              ) : (
                <>
                  <Button
                    title={t('account.register')}
                    onPress={register}
                    loading={busy}
                    disabled={!email.includes('@') || password.length < 8 || !fullName.trim()}
                  />
                  <Button title={t('account.switchLogin')} kind="secondary" onPress={() => { setPortal('member'); setMode('login'); }} />
                </>
              )}
            </>
          ) : (
            <>
              <Notice tone="info">{notice || t('account.verifyEmail')}</Notice>
              <Field
                label={t('account.otp')}
                value={otp}
                onChangeText={setOtp}
                keyboardType="number-pad"
                maxLength={6}
                placeholder="123456"
              />
              <Button title={t('account.verifyEmail')} onPress={verify} loading={busy} disabled={otp.trim().length !== 6} />
              <Button title="Resend code" kind="secondary" onPress={resend} loading={busy} />
              <Button title={t('common.back')} kind="ghost" onPress={() => setMode('login')} />
            </>
          )}
        </Card>
        <Card>
          <SectionTitle title="Voting access" subtitle="Eligible voters can cast a ballot without creating a CIVITAS account." />
          <Muted>Open your society’s election link and verify with the contact information on its voter roll.</Muted>
          <Button title="Open voting" kind="secondary" onPress={onOpenVote} />
        </Card>
      </Screen>
    );
  }

  const isAdmin = member.role === 'TENANT_ADMIN' || member.role === 'PLATFORM_ADMIN' || member.role === 'AUDITOR';

  return (
    <Screen onRefresh={loadAccount} refreshing={loading}>
      <Card>
        <SectionTitle title={t('account.title')} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <KeyValue label={t('account.signedInAs')} value={`${member.fullName} · ${member.email}`} />
        <View style={styles.badgeRow}>
          <Badge label={member.role} tone={isAdmin ? 'teal' : 'neutral'} />
          <Badge label={member.emailVerified ? 'EMAIL VERIFIED' : 'EMAIL UNVERIFIED'} tone={member.emailVerified ? 'success' : 'warning'} />
        </View>
        {isAdmin ? <Button title={t('admin.title')} onPress={onOpenAdmin} /> : null}
        <Button title={t('common.signOut')} kind="secondary" onPress={onSignedOut} />
      </Card>

      <Card>
        <SectionTitle title={t('account.eligible')} />
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {!loading && eligible.length === 0 ? <Muted>You are not on any voter roll yet.</Muted> : null}
        {eligible.map((election) => (
          <View key={election.electionId} style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.rowTitle}>{election.title}</Text>
              <Text style={uiStyles.muted}>{election.society}</Text>
              <Text style={styles.meta}>
                {election.status} · {election.posts} posts · closes {formatDateTime(election.endsAt)}
              </Text>
            </View>
            <Badge label={election.voted ? t('account.voted') : t('account.notVoted')} tone={election.voted ? 'success' : 'warning'} />
          </View>
        ))}
      </Card>

      <Card>
        <SectionTitle title={t('account.receipts')} />
        {!loading && receipts.length === 0 ? <Muted>{t('account.noReceipts')}</Muted> : null}
        {receipts.map((receipt) => (
          <View key={receipt.receiptId} style={styles.receiptRow}>
            <Text style={styles.rowTitle}>{receipt.electionTitle}</Text>
            <Text style={styles.meta}>{formatDateTime(receipt.castAt)} · {receipt.postsVoted} posts</Text>
            <Text style={styles.hash}>{shortHash(receipt.receiptHash, 18)}</Text>
            <Text style={styles.verify}>{receipt.verification}</Text>
          </View>
        ))}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  portalRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  badgeRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: theme.border },
  rowTitle: { fontSize: 15, fontWeight: '900', color: theme.navy },
  meta: { fontSize: 11, color: theme.muted, marginTop: 3 },
  receiptRow: { paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: theme.border, gap: 2 },
  hash: { fontSize: 10, fontFamily: 'monospace', color: theme.muted, marginTop: 3 },
  verify: { fontSize: 11, fontWeight: '700', color: theme.success, marginTop: 2 },
});
