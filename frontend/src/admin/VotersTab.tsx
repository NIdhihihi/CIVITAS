import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import { useLanguage } from '../i18n';
import type { ElectionPost, ElectionSummary, Voter, VoterImportResult } from '../types';
import {
  Badge,
  Button,
  Card,
  Field,
  Muted,
  Notice,
  Screen,
  SectionTitle,
  Spinner,
  styles as uiStyles,
  theme,
} from '../ui';

export function VotersTab({
  token,
  election,
  locked,
  onChanged,
}: {
  token: string;
  election: ElectionSummary;
  locked: boolean;
  onChanged: () => void;
}) {
  const { t } = useLanguage();
  const [voters, setVoters] = useState<Voter[]>([]);
  const [posts, setPosts] = useState<ElectionPost[]>([]);
  const [csv, setCsv] = useState('');
  const [replace, setReplace] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [result, setResult] = useState<VoterImportResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [list, postList] = await Promise.all([
        api<Voter[]>(`/api/elections/${election.id}/voter-roll`, { token }),
        api<ElectionPost[]>(`/api/elections/${election.id}/posts`, { token }),
      ]);
      setVoters(list);
      setPosts(postList);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the voter roll.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id]);

  useEffect(() => {
    load();
  }, [load]);

  function sampleCsv() {
    const titles = posts.map((post) => post.title);
    const stamp = Date.now();
    const rows = [
      'name,email,phone,aadhaar,posts',
      `Asha Rao,asha.${stamp}@example.com,9876500001,111122223333,${titles.join(';')}`,
      `Ravi Shah,ravi.${stamp}@example.com,9876500002,444455556666,${titles[0] || ''}`,
      `Meera Joshi,meera.${stamp}@example.com,9876500003,777788889999,${titles.join(';')}`,
    ];
    setCsv(rows.join('\n'));
    setNotice('Sample CSV loaded. Replace the email addresses with real members before importing.');
  }

  async function importCsv() {
    if (!csv.trim()) return;
    setBusy(true);
    setError('');
    setNotice('');
    setResult(null);
    try {
      const imported = await api<VoterImportResult>(`/api/elections/${election.id}/voter-roll`, {
        method: 'POST',
        token,
        body: { csv, replace },
      });
      setResult(imported);
      setNotice(
        t('admin.voters.imported', {
          imported: imported.imported,
          skipped: imported.skipped,
          total: imported.totalVoters,
        }),
      );
      await load();
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The voter roll could not be imported.');
    } finally {
      setBusy(false);
    }
  }

  function removeVoter(voter: Voter) {
    Alert.alert(t('admin.voters.remove'), `Remove ${voter.fullName} from the voter roll?`, [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('admin.voters.remove'),
        style: 'destructive',
        onPress: async () => {
          setBusy(true);
          setError('');
          try {
            await api(`/api/elections/${election.id}/voter-roll/${voter.id}`, { method: 'DELETE', token });
            await load();
            onChanged();
          } catch (err) {
            setError(err instanceof Error ? err.message : 'Could not remove this voter.');
          } finally {
            setBusy(false);
          }
        },
      },
    ]);
  }

  const voted = voters.filter((voter) => voter.voted).length;

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.voters')} subtitle={t('admin.voters.help')} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <View style={styles.stats}>
          <Badge label={`${voters.length} voters`} tone="teal" />
          <Badge label={`${voted} voted`} tone="success" />
          <Badge label={`${voters.length - voted} pending`} tone="warning" />
        </View>
      </Card>

      {!locked ? (
        <Card>
          <SectionTitle title={t('admin.voters.csv')} />
          <Field
            label={t('admin.voters.csv')}
            value={csv}
            onChangeText={setCsv}
            placeholder={'name,email,phone,aadhaar,posts\nAsha Rao,asha@example.com,9876500001,111122223333,Chairperson;Secretary'}
            multiline
            autoCapitalize="none"
            autoCorrect={false}
          />
          <Pressable style={styles.checkRow} onPress={() => setReplace((v) => !v)}>
            <View style={[styles.checkbox, replace && styles.checkboxOn]}>
              {replace ? <Text style={styles.checkMark}>✓</Text> : null}
            </View>
            <Text style={styles.checkLabel}>{t('admin.voters.replace')}</Text>
          </Pressable>
          <View style={styles.buttonRow}>
            <Button title={t('admin.voters.import')} onPress={importCsv} loading={busy} disabled={!csv.trim()} />
            <Button title={t('admin.voters.template')} kind="secondary" onPress={sampleCsv} />
          </View>
        </Card>
      ) : (
        <Notice tone="warning">{t('admin.setup.locked')}</Notice>
      )}

      {result && result.errors.length > 0 ? (
        <Card>
          <SectionTitle title={t('admin.voters.errors')} />
          {result.errors.map((item, index) => (
            <View key={`${item.row}-${index}`} style={styles.errorRow}>
              <Text style={styles.errorRowText}>Row {item.row}</Text>
              <Text style={styles.errorRowMessage}>{item.field}: {item.message}</Text>
            </View>
          ))}
        </Card>
      ) : null}

      <Card>
        <SectionTitle title={t('admin.voters.list', { count: voters.length })} />
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {!loading && voters.length === 0 ? <Muted>No voters on the roll yet.</Muted> : null}
        {voters.map((voter) => (
          <View key={voter.id} style={styles.voterRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.voterName}>{voter.fullName}</Text>
              <Text style={uiStyles.muted}>{voter.email || voter.phone || `Aadhaar ••••${voter.aadhaarLast4 || ''}`}</Text>
              <Text style={styles.voterMeta}>
                {voter.eligiblePostIds ? `Eligible for ${voter.eligiblePostIds.split(',').length} posts` : 'Eligible for all posts'}
              </Text>
              {voter.votedAt ? <Text style={styles.voterMeta}>Voted {new Date(voter.votedAt).toLocaleString()}</Text> : null}
            </View>
            <View style={{ alignItems: 'flex-end', gap: 6 }}>
              <Badge label={voter.voted ? t('admin.voters.voted') : t('admin.voters.notVoted')} tone={voter.voted ? 'success' : 'neutral'} />
              {!locked && !voter.voted ? (
                <Button title={t('admin.voters.remove')} kind="ghost" small onPress={() => removeVoter(voter)} />
              ) : null}
            </View>
          </View>
        ))}
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 5 },
  checkbox: { width: 24, height: 24, borderRadius: 7, borderWidth: 2, borderColor: '#b8c7d2', alignItems: 'center', justifyContent: 'center' },
  checkboxOn: { backgroundColor: theme.teal, borderColor: theme.teal },
  checkMark: { color: theme.white, fontSize: 14, fontWeight: '900' },
  checkLabel: { fontSize: 13, color: theme.text, fontWeight: '700', flex: 1 },
  buttonRow: { flexDirection: 'row', gap: 9, flexWrap: 'wrap' },
  errorRow: { paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: theme.border },
  errorRowText: { fontSize: 11, fontWeight: '900', color: theme.danger },
  errorRowMessage: { fontSize: 12, color: theme.text, marginTop: 2 },
  voterRow: { flexDirection: 'row', gap: 10, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: theme.border, alignItems: 'flex-start' },
  voterName: { fontSize: 15, fontWeight: '900', color: theme.navy },
  voterMeta: { fontSize: 11, color: theme.muted, marginTop: 3 },
});
