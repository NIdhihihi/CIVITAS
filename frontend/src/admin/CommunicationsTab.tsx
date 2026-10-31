import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import { useLanguage } from '../i18n';
import type { Communication, ElectionSummary } from '../types';
import {
  Badge,
  Button,
  Card,
  Chip,
  Field,
  Muted,
  Notice,
  SectionTitle,
  Spinner,
  formatDateTime,
  localInputToIso,
  styles as uiStyles,
  theme,
} from '../ui';

type Channel = 'EMAIL' | 'SMS' | 'WHATSAPP';

export function CommunicationsTab({ token, election }: { token: string; election: ElectionSummary }) {
  const { t } = useLanguage();
  const [list, setList] = useState<Communication[]>([]);
  const [channel, setChannel] = useState<Channel>('EMAIL');
  const [subject, setSubject] = useState(`Vote now: ${election.title}`);
  const [template, setTemplate] = useState(
    'Hello {{name}}, voting is open for {{election}}. Polls close at {{endsAt}}. Your vote is secret and your receipt proves it was accepted.',
  );
  const [scheduledAt, setScheduledAt] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const campaigns = await api<Communication[]>(`/api/elections/${election.id}/communications`, { token });
      setList(campaigns);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load communications.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id]);

  useEffect(() => {
    load();
  }, [load]);

  async function schedule() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const iso = scheduledAt.trim() ? localInputToIso(scheduledAt) : null;
      if (scheduledAt.trim() && !iso) {
        throw new Error('Use YYYY-MM-DDTHH:MM for the schedule time.');
      }
      await api<Communication>(`/api/elections/${election.id}/communications`, {
        method: 'POST',
        token,
        body: { channel, subject: subject.trim(), template: template.trim(), scheduledAt: iso },
      });
      setNotice(
        iso
          ? 'Campaign scheduled. The server sends it when the time arrives.'
          : 'Campaign sent through the configured email service.',
      );
      setScheduledAt('');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not schedule the message.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.messages')} subtitle={t('admin.messages.help')} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <Text style={uiStyles.fieldLabel}>{t('admin.messages.channel')}</Text>
        <View style={styles.chipRow}>
          {(['EMAIL'] as Channel[]).map((item) => (
            <Chip key={item} label={item} active={channel === item} onPress={() => setChannel(item)} />
          ))}
        </View>
        <Notice tone="info">SMS and WhatsApp are unavailable until a delivery provider is configured.</Notice>
        <Field label={t('admin.messages.subject')} value={subject} onChangeText={setSubject} />
        <Field
          label={t('admin.messages.template')}
          value={template}
          onChangeText={setTemplate}
          multiline
          hint="Placeholders: {{name}}, {{election}}, {{society}}, {{startsAt}}, {{endsAt}}"
        />
        <Field
          label={t('admin.messages.schedule')}
          value={scheduledAt}
          onChangeText={setScheduledAt}
          placeholder="2026-11-05T09:00"
          hint={t('admin.dateHint')}
        />
        <Button
          title={t('admin.messages.send')}
          onPress={schedule}
          loading={busy}
          disabled={!subject.trim() || !template.trim()}
        />
      </Card>

      <Card>
        <SectionTitle title={t('admin.messages.list')} />
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {!loading && list.length === 0 ? <Muted>No campaigns yet.</Muted> : null}
        {list.map((item) => (
          <View key={item.id} style={styles.row}>
            <View style={{ flex: 1, gap: 2 }}>
              <View style={styles.subjectRow}>
                <Text style={styles.subject}>{item.subject}</Text>
                <Badge label={item.channel} tone={item.channel === 'EMAIL' ? 'info' : 'teal'} />
              </View>
              <Text style={uiStyles.muted}>{item.template}</Text>
              <Text style={styles.meta}>
                {item.status} · {formatDateTime(item.scheduledAt)}
                {item.automated ? ' · automatic' : ''}
              </Text>
              <Text style={styles.counts}>
                {item.sentCount} {t('admin.messages.sent')} · {item.failedCount} {t('admin.messages.failed')}
              </Text>
            </View>
          </View>
        ))}
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  row: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: theme.border },
  subjectRow: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  subject: { fontSize: 14, fontWeight: '900', color: theme.navy, flexShrink: 1 },
  meta: { fontSize: 10, fontWeight: '700', color: theme.teal, marginTop: 4 },
  counts: { fontSize: 11, color: theme.muted, marginTop: 2 },
});
