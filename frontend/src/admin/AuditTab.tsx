import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api, downloadFile } from '../api';
import { useLanguage } from '../i18n';
import type { AuditEntry, AuditVerification, ElectionSummary, FraudAlert } from '../types';
import {
  Badge,
  Button,
  Card,
  KeyValue,
  Muted,
  Notice,
  SectionTitle,
  Spinner,
  formatDateTime,
  shortHash,
  styles as uiStyles,
  theme,
} from '../ui';

export function AuditTab({ token, election }: { token: string; election: ElectionSummary }) {
  const { t } = useLanguage();
  const [verification, setVerification] = useState<AuditVerification | null>(null);
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [alerts, setAlerts] = useState<FraudAlert[]>([]);
  const [alertsUnavailable, setAlertsUnavailable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [check, page] = await Promise.all([
        api<AuditVerification>(`/api/audit/verify?electionId=${election.id}`, { token }),
        api<AuditEntry[]>(`/api/audit?electionId=${election.id}&limit=200`, { token }),
      ]);
      setVerification(check);
      setEntries(page);
      try {
        const fraud = await api<FraudAlert[]>('/api/audit/alerts', { token });
        setAlerts(fraud);
        setAlertsUnavailable(false);
      } catch {
        setAlertsUnavailable(true);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the audit ledger.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id]);

  useEffect(() => {
    load();
  }, [load]);

  async function exportCsv() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await downloadFile(
        `/api/audit/export?electionId=${election.id}`,
        'civitas-audit-ledger.csv',
        token,
      );
      setNotice(
        result.shared
          ? 'Audit ledger ready. Choose where to save or share it.'
          : 'Audit ledger saved as civitas-audit-ledger.csv.',
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not export the audit ledger.');
    } finally {
      setBusy(false);
    }
  }

  async function resolveAlert(alert: FraudAlert) {
    setBusy(true);
    setError('');
    try {
      await api(`/api/audit/alerts/${alert.id}/resolve`, { method: 'PATCH', token });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not resolve the alert.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.audit')} subtitle="Every important action is appended to a SHA-256 hash chain and signed with HMAC." />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {verification ? (
          <>
            <Notice tone={verification.valid ? 'success' : 'danger'}>
              {verification.valid ? t('admin.audit.valid') : t('admin.audit.invalid')} {verification.message}
            </Notice>
            <KeyValue label="Entries checked" value={String(verification.entriesChecked)} />
            <KeyValue label="Chain head" value={String(verification.headSequence)} />
          </>
        ) : null}
        <View style={styles.row}>
          <Button title={t('admin.audit.verify')} kind="secondary" small onPress={load} loading={loading} />
          <Button title={t('admin.audit.export')} kind="secondary" small onPress={exportCsv} loading={busy} />
        </View>
      </Card>

      {!alertsUnavailable ? (
        <Card>
          <SectionTitle title={t('admin.audit.alerts')} />
          {alerts.length === 0 ? <Muted>{t('admin.audit.noAlerts')}</Muted> : null}
          {alerts.map((alert) => (
            <View key={alert.id} style={styles.alertRow}>
              <View style={{ flex: 1 }}>
                <View style={styles.alertHead}>
                  <Text style={styles.alertTitle}>{alert.type}</Text>
                  <Badge label={alert.severity} tone={alert.severity === 'HIGH' ? 'danger' : 'warning'} />
                </View>
                <Text style={uiStyles.muted}>{alert.details}</Text>
                <Text style={styles.meta}>{formatDateTime(alert.createdAt)}</Text>
              </View>
              {!alert.resolved ? (
                <Button title={t('admin.audit.resolve')} kind="ghost" small onPress={() => resolveAlert(alert)} loading={busy} />
              ) : (
                <Badge label="RESOLVED" tone="success" />
              )}
            </View>
          ))}
        </Card>
      ) : null}

      <Card>
        <SectionTitle title={t('admin.audit.entries')} subtitle={`${entries.length} most recent entries`} />
        {entries.map((entry) => (
          <View key={entry.sequence} style={styles.entryRow}>
            <View style={styles.sequence}>
              <Text style={styles.sequenceText}>{entry.sequence}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.action}>{entry.action}</Text>
              <Text style={styles.meta}>
                {entry.actorType} · {formatDateTime(entry.createdAt)}
              </Text>
              <Text style={styles.hash}>hash {shortHash(entry.entryHash, 14)}</Text>
              <Text style={styles.hash}>prev {shortHash(entry.previousHash, 14)}</Text>
              {entry.metadata && entry.metadata !== '{}' ? (
                <Text style={styles.metadata}>{entry.metadata}</Text>
              ) : null}
            </View>
          </View>
        ))}
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  alertRow: { flexDirection: 'row', gap: 9, alignItems: 'flex-start', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: theme.border },
  alertHead: { flexDirection: 'row', alignItems: 'center', gap: 7, flexWrap: 'wrap' },
  alertTitle: { fontSize: 13, fontWeight: '900', color: theme.navy },
  meta: { fontSize: 10, color: theme.muted, marginTop: 3 },
  entryRow: { flexDirection: 'row', gap: 10, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'flex-start' },
  sequence: { width: 34, height: 30, borderRadius: 9, backgroundColor: theme.soft, alignItems: 'center', justifyContent: 'center' },
  sequenceText: { fontSize: 11, fontWeight: '900', color: theme.muted },
  action: { fontSize: 13, fontWeight: '900', color: theme.navy },
  hash: { fontSize: 9, fontFamily: 'monospace', color: theme.muted, marginTop: 2 },
  metadata: { fontSize: 9, fontFamily: 'monospace', color: '#4b6478', marginTop: 4 },
});
