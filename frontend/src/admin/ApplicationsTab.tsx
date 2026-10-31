import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import type { Member } from '../types';
import { Badge, Button, Card, Muted, Notice, SectionTitle, Spinner, theme } from '../ui';

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

export function ApplicationsTab({ token, member }: { token: string; member: Member }) {
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setApplications(await api<Application[]>('/api/candidates/admin', { token }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Candidate applications could not be loaded.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  async function decide(application: Application, status: 'APPROVED' | 'REJECTED') {
    setBusyId(application.id);
    setError('');
    try {
      const updated = await api<Application>(`/api/candidates/${application.id}/status`, {
        method: 'PATCH', token, body: { status },
      });
      setApplications((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The application could not be updated.');
    } finally {
      setBusyId('');
    }
  }

  return (
    <>
      <Card>
        <SectionTitle title="Candidate applications" subtitle="Review resident applications separately from candidates added directly to an election ballot." />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        <Notice tone="info">Approval accepts the society application. Add approved candidates to each election ballot separately from that election’s Setup screen.</Notice>
        <Button title="Refresh applications" kind="secondary" small onPress={load} loading={loading} />
      </Card>
      {loading ? <Card><Spinner label="Loading applications…" /></Card> : null}
      {!loading && applications.length === 0 ? <Card><Muted>No candidate applications have been submitted.</Muted></Card> : null}
      {applications.map((application) => (
        <Card key={application.id}>
          <View style={styles.head}>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{application.fullName}</Text>
              <Text style={styles.meta}>{application.memberEmail} · {application.tenantName}</Text>
            </View>
            <Badge label={application.status} tone={application.status === 'APPROVED' ? 'success' : application.status === 'REJECTED' ? 'danger' : 'warning'} />
          </View>
          <Text style={styles.post}>Applying for: {application.contestingPost}</Text>
          <Text style={styles.statement}>{application.statement}</Text>
          {application.phone ? <Text style={styles.meta}>Phone: {application.phone}</Text> : null}
          <Text style={styles.meta}>Submitted {new Date(application.registeredAt).toLocaleString()}</Text>
          {(member.role === 'PLATFORM_ADMIN' || member.role === 'TENANT_ADMIN') && application.status === 'PENDING' ? (
            <View style={styles.actions}>
              <Button title="Approve" kind="success" small loading={busyId === application.id} disabled={!!busyId} onPress={() => decide(application, 'APPROVED')} />
              <Button title="Reject" kind="danger" small loading={busyId === application.id} disabled={!!busyId} onPress={() => decide(application, 'REJECTED')} />
            </View>
          ) : null}
        </Card>
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  head: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  name: { color: theme.text, fontWeight: '800', fontSize: 16 },
  meta: { color: theme.muted, fontSize: 12, marginTop: 4 },
  post: { color: theme.teal, fontSize: 13, fontWeight: '800', marginTop: 13 },
  statement: { color: theme.text, fontSize: 13, lineHeight: 19, marginTop: 7 },
  actions: { flexDirection: 'row', gap: 8, marginTop: 13 },
});
