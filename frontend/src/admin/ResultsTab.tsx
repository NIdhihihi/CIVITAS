import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api, downloadFile } from '../api';
import { useLanguage } from '../i18n';
import type { ContestantResult, ElectionSummary, Results } from '../types';
import {
  Badge,
  Bar,
  Button,
  Card,
  Field,
  KeyValue,
  Muted,
  Notice,
  Photo,
  SectionTitle,
  Spinner,
  Stats,
  formatDateTime,
  shortHash,
  styles as uiStyles,
  theme,
} from '../ui';

export function ResultsTab({ token, election }: { token: string; election: ElectionSummary }) {
  const { t } = useLanguage();
  const [results, setResults] = useState<Results | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [password, setPassword] = useState('');
  const [downloaded, setDownloaded] = useState<{ name: string; sha?: string | null; signature?: string | null } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api<Results>(`/api/elections/${election.id}/results`, { token });
      setResults(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Results are not available yet.');
    } finally {
      setLoading(false);
    }
  }, [token, election.id]);

  useEffect(() => {
    load();
  }, [load]);

  async function exportResults(format: 'pdf' | 'excel') {
    if (password.length < 8) {
      setError(t('admin.results.exportPassword'));
      return;
    }
    setBusy(true);
    setError('');
    setNotice('');
    setDownloaded(null);
    try {
      const safeTitle = (results?.title || election.title).replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '') || 'election';
      const extension = format === 'pdf' ? 'pdf' : 'xlsx';
      const result = await downloadFile(
        `/api/elections/${election.id}/results/export`,
        `civitas-${safeTitle}-results.${extension}`,
        token,
        { method: 'POST', body: { format, password } },
      );
      setDownloaded({ name: result.filename, sha: result.sha256, signature: result.signature });
      setNotice(
        result.shared
          ? 'The protected export is ready. Choose where to save or share it.'
          : `Saved ${result.filename}. The file is password protected.`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The export could not be generated.');
    } finally {
      setBusy(false);
    }
  }

  if (election.status === 'DRAFT' || election.status === 'SCHEDULED' || election.status === 'ACTIVE') {
    return (
      <Card>
        <SectionTitle title={t('admin.results.title')} />
        <Notice tone="warning">{t('admin.results.notReady')}</Notice>
      </Card>
    );
  }

  if (loading) {
    return (
      <Card>
        <Spinner label={t('common.loading')} />
      </Card>
    );
  }

  if (!results) {
    return (
      <Card>
        <SectionTitle title={t('admin.results.title')} />
        <Notice tone="danger">{error || t('admin.results.notReady')}</Notice>
        <Button title={t('common.retry')} kind="secondary" onPress={load} />
      </Card>
    );
  }

  return (
    <>
      <Card>
        <SectionTitle title={t('admin.results.title')} subtitle={results.title} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <Stats
          items={[
            { label: t('admin.results.ballots'), value: results.totalBallots },
            { label: t('admin.results.eligible'), value: results.eligibleVoters },
            { label: 'Posts', value: results.posts.length },
          ]}
        />
        <View style={styles.badgeRow}>
          <Badge label={results.status} tone="teal" />
          <Badge
            label={results.auditChainValid ? t('admin.results.chain') : t('admin.audit.invalid')}
            tone={results.auditChainValid ? 'success' : 'danger'}
          />
        </View>
        <KeyValue label="Counted at" value={formatDateTime(results.countedAt)} />
        <KeyValue label={t('admin.results.hash')} value={results.resultHash} mono />
        <KeyValue label={t('admin.results.signature')} value={results.resultSignature} mono />
        <Button title={t('common.refresh')} kind="secondary" small onPress={load} />
      </Card>

      {results.posts.map((post) => (
        <Card key={post.postId}>
          <SectionTitle
            title={post.title}
            subtitle={`${t('admin.results.votes', { votes: post.totalVotes })}`}
          />
          {post.candidates.length > 0 ? (
            <>
              <Text style={styles.sectionLabel}>{t('admin.results.candidates')}</Text>
              <ContestantList contestants={post.candidates} leadingLabel={t('admin.results.winner')} />
            </>
          ) : null}
          {post.panels.length > 0 ? (
            <>
              <Text style={styles.sectionLabel}>{t('admin.results.panels')}</Text>
              <ContestantList contestants={post.panels} leadingLabel={t('admin.results.winner')} />
            </>
          ) : null}
          {post.candidates.length === 0 && post.panels.length === 0 ? <Muted>No results for this post yet.</Muted> : null}
        </Card>
      ))}

      <Card>
        <SectionTitle title={t('admin.results.export')} subtitle="Exports are password-protected and embed the result hash and HMAC signature." />
        <Field
          label={t('admin.results.exportPassword')}
          value={password}
          onChangeText={setPassword}
          secure
          placeholder="At least 8 characters"
        />
        <View style={styles.exportRow}>
          <Button title={t('admin.results.exportPdf')} onPress={() => exportResults('pdf')} loading={busy} style={{ flex: 1 }} />
          <Button
            title={t('admin.results.exportExcel')}
            kind="secondary"
            onPress={() => exportResults('excel')}
            loading={busy}
            style={{ flex: 1 }}
          />
        </View>
        {downloaded ? (
          <View style={{ gap: 8 }}>
            <Notice tone="success">{downloaded.name} generated.</Notice>
            <KeyValue label="X-CIVITAS-SHA256" value={shortHash(downloaded.sha, 24)} mono />
            <KeyValue label="X-CIVITAS-SIGNATURE" value={shortHash(downloaded.signature, 24)} mono />
          </View>
        ) : null}
      </Card>

      <Card>
        <SectionTitle title="Public results" subtitle="Share this with members after counting." />
        <KeyValue label="Public results API" value={`/api/public/results/${results.electionId}`} mono />
      </Card>
    </>
  );
}

function ContestantList({ contestants, leadingLabel }: { contestants: ContestantResult[]; leadingLabel: string }) {
  const max = contestants.reduce((value, item) => Math.max(value, item.votes), 0);
  return (
    <View style={{ gap: 10 }}>
      {contestants.map((contestant, index) => {
        const leading = index === 0 && contestant.votes > 0 && contestant.votes === max;
        return (
          <View key={contestant.id} style={styles.contestant}>
            <Photo uri={contestant.photoUrl} name={contestant.name} size={38} />
            <View style={{ flex: 1 }}>
              <View style={styles.contestantHead}>
                <Text style={styles.contestantName}>{contestant.name}</Text>
                {leading ? <Badge label={leadingLabel} tone="success" /> : null}
              </View>
              <Bar percentage={contestant.percentage} leading={leading} />
              <Text style={styles.contestantMeta}>
                {contestant.votes} votes · {contestant.percentage.toFixed(1)}%
              </Text>
            </View>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  badgeRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  sectionLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 0.9, color: theme.teal, textTransform: 'uppercase' },
  contestant: { flexDirection: 'row', gap: 10, alignItems: 'center' },
  contestantHead: { flexDirection: 'row', alignItems: 'center', gap: 7, flexWrap: 'wrap' },
  contestantName: { fontSize: 14, fontWeight: '800', color: theme.text, flexShrink: 1 },
  contestantMeta: { fontSize: 11, color: theme.muted, marginTop: 3 },
  exportRow: { flexDirection: 'row', gap: 9, flexWrap: 'wrap' },
});
