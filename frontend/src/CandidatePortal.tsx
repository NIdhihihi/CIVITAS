import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api } from './api';
import type { Member } from './types';
import { Badge, Button, Card, Chip, Field, Muted, Notice, Screen, SectionTitle, Spinner, theme } from './ui';

type Society = { id: string; name: string; code: string; city: string };
type Application = {
  id: string;
  tenantId: string;
  tenantName: string;
  memberEmail: string;
  fullName: string;
  contestingPost: string;
  statement: string;
  phone: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  registeredAt: string;
};

export function CandidatePortal({
  token,
  member,
  onOpenAccount,
}: {
  token: string | null;
  member: Member | null;
  onOpenAccount: () => void;
}) {
  const [societies, setSocieties] = useState<Society[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [societyId, setSocietyId] = useState('');
  const [post, setPost] = useState('');
  const [statement, setStatement] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const [directoryResult, applicationsResult] = await Promise.allSettled([
        api<Society[]>('/api/tenants', { token }),
        api<Application[]>('/api/candidates/mine', { token }),
      ]);
      if (directoryResult.status === 'rejected') throw new Error(`Society list failed: ${directoryResult.reason instanceof Error ? directoryResult.reason.message : 'endpoint unavailable'}`);
      setSocieties(directoryResult.value);
      setSocietyId((current) => current || directoryResult.value[0]?.id || '');
      if (applicationsResult.status === 'fulfilled') setApplications(applicationsResult.value);
      else setError(`Application history failed: ${applicationsResult.reason instanceof Error ? applicationsResult.reason.message : 'endpoint unavailable'}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load candidate applications.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  async function submit() {
    if (!token || !societyId || !post.trim() || !statement.trim()) {
      setError('Choose a society and complete the position and statement fields.');
      return;
    }
    if (applications.some((item) => item.tenantId === societyId && item.contestingPost.trim().toLowerCase() === post.trim().toLowerCase())) {
      setError('You already have an application for this position in the selected society.');
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await api<Application>('/api/candidates/register', {
        method: 'POST',
        token,
        body: {
          tenantId: societyId,
          contestingPost: post.trim(),
          statement: statement.trim(),
          phone: phone.trim() || undefined,
        },
      });
      setPost('');
      setStatement('');
      setPhone('');
      setNotice('Your application was submitted. You can track its status below.');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Your application could not be submitted.');
    } finally {
      setBusy(false);
    }
  }

  if (!token || !member) {
    return (
      <Screen>
        <Card>
          <SectionTitle title="Candidate portal" subtitle="Apply to represent your society in an upcoming election." />
          <Notice tone="info">Sign in with a verified member account to submit an application. New here? Create a member account first, then return to this portal.</Notice>
          <Button title="Sign in or create an account" onPress={onOpenAccount} />
        </Card>
      </Screen>
    );
  }

  return (
    <Screen onRefresh={load} refreshing={loading}>
      <Card>
        <SectionTitle title="Candidate portal" subtitle={`Applying as ${member.fullName}`} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <Text style={styles.label}>Society</Text>
        {loading ? <Spinner label="Loading societies…" /> : societies.length === 0 ? (
          <Muted>No societies are available yet. Ask a platform administrator to add your society.</Muted>
        ) : (
          <View style={styles.choices}>
            {societies.map((society) => (
              <Chip key={society.id} label={`${society.name} · ${society.city}`} active={societyId === society.id} onPress={() => setSocietyId(society.id)} />
            ))}
          </View>
        )}
        <Field label="Position you want to contest" value={post} onChangeText={setPost} placeholder="For example, Secretary" />
        {applications.some((item) => item.tenantId === societyId && item.contestingPost.trim().toLowerCase() === post.trim().toLowerCase()) && post.trim() ? (
          <Notice tone="warning">You have already applied for this position in this society.</Notice>
        ) : null}
        <Field label="Candidate statement" value={statement} onChangeText={setStatement} placeholder="Tell society members about your experience and priorities" multiline />
        <Field label="Phone (optional)" value={phone} onChangeText={setPhone} keyboardType="phone-pad" placeholder="Contact number" />
        <Button title="Submit application" onPress={submit} loading={busy} disabled={loading || !societyId || !post.trim() || !statement.trim()} />
      </Card>

      <Card>
        <SectionTitle title="My applications" subtitle="Approval confirms your society application. Election admins add approved candidates to a specific ballot separately." />
        {loading && applications.length === 0 ? <Spinner label="Loading applications…" /> : null}
        {!loading && applications.length === 0 ? <Muted>You haven’t submitted an application yet.</Muted> : null}
        {applications.map((application) => (
          <View key={application.id} style={styles.application}>
            <View style={styles.applicationHead}>
              <Text style={styles.societyName}>{application.tenantName}</Text>
              <Badge label={application.status} tone={application.status === 'APPROVED' ? 'success' : application.status === 'REJECTED' ? 'danger' : 'warning'} />
            </View>
            <Text style={styles.position}>{application.contestingPost}</Text>
            <Text style={styles.statementText}>{application.statement}</Text>
            <Text style={styles.date}>Submitted {new Date(application.registeredAt).toLocaleDateString()}</Text>
          </View>
        ))}
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  label: { color: theme.muted, fontSize: 12, fontWeight: '800', marginBottom: 6 },
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 10 },
  application: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: theme.border, gap: 4 },
  applicationHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  societyName: { fontSize: 14, color: theme.text, fontWeight: '800', flexShrink: 1 },
  position: { fontSize: 13, fontWeight: '800', color: theme.teal },
  statementText: { fontSize: 12, color: theme.text, lineHeight: 18 },
  date: { fontSize: 11, color: theme.muted },
});
