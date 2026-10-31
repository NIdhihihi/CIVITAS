import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, Share, StyleSheet, Text, View } from 'react-native';
import { api, API_URL } from './api';
import { useLanguage } from './i18n';
import type { ElectionStatus, ElectionSummary, Member, Tenant } from './types';
import { SetupTab } from './admin/SetupTab';
import { VotersTab } from './admin/VotersTab';
import { CommunicationsTab } from './admin/CommunicationsTab';
import { ResultsTab } from './admin/ResultsTab';
import { AuditTab } from './admin/AuditTab';
import { PreviewTab } from './admin/PreviewTab';
import { ApplicationsTab } from './admin/ApplicationsTab';
import { SocietiesTab } from './admin/SocietiesTab';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  KeyValue,
  Muted,
  Notice,
  Screen,
  SectionTitle,
  Spinner,
  Stats,
  Tabs,
  dateToLocalInput,
  formatDate,
  formatDateTime,
  localInputToIso,
  statusTone,
  styles as uiStyles,
  theme,
} from './ui';

type DetailTab = 'overview' | 'setup' | 'voters' | 'messages' | 'preview' | 'results' | 'audit';
type AdminSection = 'elections' | 'societies' | 'applications';

export function AdminElections({ token, member }: { token: string; member: Member }) {
  const { t } = useLanguage();
  const [elections, setElections] = useState<ElectionSummary[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [creating, setCreating] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<ElectionSummary | null>(null);
  const [tab, setTab] = useState<DetailTab>('overview');
  const [section, setSection] = useState<AdminSection>('elections');
  const homeTabs = [
    { key: 'elections', label: 'Elections' },
    ...(member.role === 'PLATFORM_ADMIN' ? [{ key: 'societies', label: 'Societies' }] : []),
    ...(member.role === 'PLATFORM_ADMIN' || member.role === 'TENANT_ADMIN' ? [{ key: 'applications', label: 'Applications' }] : []),
  ];

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const tenantPath = member.role === 'PLATFORM_ADMIN' ? '/api/tenants' : '/api/tenants/mine';
      const [list, societies] = await Promise.all([
        api<ElectionSummary[]>('/api/elections/mine', { token }),
        api<Tenant[]>(tenantPath, { token }),
      ]);
      setElections(list);
      setTenants(societies);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load elections.');
    } finally {
      setLoading(false);
    }
  }, [token, member.role]);

  useEffect(() => {
    load();
  }, [load]);

  const openElection = useCallback(
    async (id: string) => {
      setSelectedId(id);
      setTab('overview');
      setBusy(true);
      setError('');
      try {
        const election = await api<ElectionSummary>(`/api/elections/${id}`, { token });
        setSelected(election);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not open this election.');
        setSelectedId(null);
      } finally {
        setBusy(false);
      }
    },
    [token],
  );

  async function refreshSelected() {
    if (!selectedId) return;
    try {
      const election = await api<ElectionSummary>(`/api/elections/${selectedId}`, { token });
      setSelected(election);
    } catch {
      // The list refresh will surface a hard error if the election disappeared.
    }
    load();
  }

  async function transition(status: ElectionStatus) {
    if (!selected) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const updated = await api<ElectionSummary>(`/api/elections/${selected.id}/status`, {
        method: 'PATCH',
        token,
        body: { status },
      });
      setSelected(updated);
      setNotice(`Election status changed to ${status}.`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The status change failed.');
    } finally {
      setBusy(false);
    }
  }

  async function tallyAndPublish() {
    if (!selected) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await api(`/api/elections/${selected.id}/count`, {
        method: 'POST',
        token,
      });
      const updated = await api<ElectionSummary>(`/api/elections/${selected.id}`, { token });
      setSelected(updated);
      setNotice('Ballots counted and official results published.');
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Counting failed.');
    } finally {
      setBusy(false);
    }
  }

  function confirmTransition(status: ElectionStatus, label: string) {
    Alert.alert(label, `Change the election status to ${status}?`, [
      { text: t('common.cancel'), style: 'cancel' },
      { text: label, style: status === 'CANCELLED' ? 'destructive' : 'default', onPress: () => transition(status) },
    ]);
  }

  async function shareCode() {
    if (!selected) return;
    try {
      await Share.share({
        message: `Vote in "${selected.title}" on CIVITAS.\nElection code: ${selected.id}\nBackend: ${API_URL}`,
        title: selected.title,
      });
    } catch {
      // The share sheet can be dismissed without an error.
    }
  }

  if (selectedId) {
    return (
      <ElectionDetail
        token={token}
        member={member}
        election={selected}
        loading={busy}
        error={error}
        notice={notice}
        tab={tab}
        setTab={setTab}
        onBack={() => {
          setSelectedId(null);
          setSelected(null);
          setTab('overview');
          setError('');
          setNotice('');
          load();
        }}
        onRefresh={refreshSelected}
        onTransition={confirmTransition}
        onTally={tallyAndPublish}
        onShare={shareCode}
      />
    );
  }

  return (
    <Screen onRefresh={load} refreshing={loading}>
      <Card>
        <SectionTitle title={t('admin.title')} subtitle={t('admin.subtitle')} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        {section === 'elections' && member.role !== 'AUDITOR' ? (
          <Button
            title={creating ? t('common.cancel') : t('admin.newElection')}
            kind={creating ? 'secondary' : 'primary'}
            onPress={() => setCreating((v) => !v)}
          />
        ) : null}
      </Card>

      <Tabs tabs={homeTabs} active={section} onChange={(key) => { setSection(key as AdminSection); setCreating(false); }} />

      {section === 'applications' ? (
        <ApplicationsTab key="applications" token={token} member={member} />
      ) : null}

      {section === 'societies' && member.role === 'PLATFORM_ADMIN' ? (
        <SocietiesTab token={token} member={member} onChanged={load} />
      ) : null}

      {section === 'elections' && creating && member.role !== 'AUDITOR' ? (
        <CreateElectionForm
          token={token}
          tenants={tenants}
          onCreated={async (created) => {
            setCreating(false);
            setNotice(`"${created.title}" was created. Add posts, candidates, and voters next.`);
            await load();
            openElection(created.id);
          }}
          onError={setError}
        />
      ) : null}

      {section === 'elections' ? <Card>
        <SectionTitle title={`${t('admin.title')} · ${elections.length}`} />
        {loading ? <Spinner label={t('common.loading')} /> : null}
        {!loading && elections.length === 0 ? (
          <EmptyState title={t('admin.noElections')} body={t('admin.subtitle')} />
        ) : null}
        {elections.map((election) => (
          <Pressable key={election.id} onPress={() => openElection(election.id)} style={styles.listRow}>
            <View style={{ flex: 1, gap: 3 }}>
              <View style={styles.listTitleRow}>
                <Text style={styles.listTitle}>{election.title}</Text>
                <Badge label={election.status} tone={statusTone(election.status)} />
              </View>
              <Text style={uiStyles.muted}>{election.tenantName}</Text>
              <Text style={styles.listMeta}>
                {formatDate(election.startsAt)} → {formatDate(election.endsAt)}
              </Text>
              <View style={styles.miniStats}>
                <Text style={styles.miniStat}>{election.posts} posts</Text>
                <Text style={styles.miniStat}>{election.candidates} candidates</Text>
                <Text style={styles.miniStat}>{election.voters} voters</Text>
                <Text style={styles.miniStat}>{election.votesCast} ballots</Text>
              </View>
            </View>
            <Text style={styles.chevron}>›</Text>
          </Pressable>
        ))}
      </Card> : null}

      {section === 'elections' ? <Muted>
        Signed in as {member.fullName} ({member.role}) · {API_URL}
      </Muted> : null}
    </Screen>
  );
}

function CreateElectionForm({
  token,
  tenants,
  onCreated,
  onError,
}: {
  token: string;
  tenants: Tenant[];
  onCreated: (election: ElectionSummary) => void;
  onError: (message: string) => void;
}) {
  const { t } = useLanguage();
  const start = new Date(Date.now() + 5 * 60 * 1000);
  const end = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const [tenantId, setTenantId] = useState(tenants[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startsAt, setStartsAt] = useState(dateToLocalInput(start));
  const [endsAt, setEndsAt] = useState(dateToLocalInput(end));
  const [notify, setNotify] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!tenantId && tenants.length > 0) setTenantId(tenants[0].id);
  }, [tenantId, tenants]);

  async function submit() {
    const startIso = localInputToIso(startsAt);
    const endIso = localInputToIso(endsAt);
    if (!tenantId || !title.trim() || !startIso || !endIso) {
      onError('Choose a society, enter a title, and use valid start and end times.');
      return;
    }
    setBusy(true);
    onError('');
    try {
      const created = await api<ElectionSummary>('/api/elections', {
        method: 'POST',
        token,
        body: {
          tenantId,
          title: title.trim(),
          description: description.trim(),
          startsAt: startIso,
          endsAt: endIso,
          notifyVoters: notify,
        },
      });
      onCreated(created);
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Could not create the election.');
    } finally {
      setBusy(false);
    }
  }

  if (tenants.length === 0) {
    return (
      <Card>
        <SectionTitle title="Society required" subtitle="Create or join a society before setting up an election." />
        <Notice tone="info">Society management is available from the separate Societies section in the admin console.</Notice>
      </Card>
    );
  }

  return (
    <Card>
      <SectionTitle title={t('admin.createTitle')} />
      <Text style={uiStyles.fieldLabel}>{t('admin.society')}</Text>
      <View style={styles.tenantRow}>
        {tenants.map((tenant) => (
          <Pressable
            key={tenant.id}
            onPress={() => setTenantId(tenant.id)}
            style={[styles.tenantChip, tenantId === tenant.id && styles.tenantChipActive]}
          >
            <Text style={[styles.tenantChipText, tenantId === tenant.id && styles.tenantChipTextActive]}>
              {tenant.name}
            </Text>
          </Pressable>
        ))}
      </View>
      <Field label={t('admin.electionTitle')} value={title} onChangeText={setTitle} placeholder="Managing Committee 2026" />
      <Field
        label={t('admin.description')}
        value={description}
        onChangeText={setDescription}
        placeholder="What members are voting on"
        multiline
      />
      <Field label={t('admin.startsAt')} value={startsAt} onChangeText={setStartsAt} hint={t('admin.dateHint')} />
      <Field label={t('admin.endsAt')} value={endsAt} onChangeText={setEndsAt} hint={t('admin.dateHint')} />
      <Pressable style={styles.checkRow} onPress={() => setNotify((v) => !v)}>
        <View style={[styles.checkbox, notify && styles.checkboxOn]}>{notify ? <Text style={styles.checkMark}>✓</Text> : null}</View>
        <Text style={styles.checkLabel}>{t('admin.notifyVoters')}</Text>
      </Pressable>
      <Button title={t('admin.create')} onPress={submit} loading={busy} disabled={!tenantId || !title.trim()} />
    </Card>
  );
}

function ElectionDetail({
  token,
  member,
  election,
  loading,
  error,
  notice,
  tab,
  setTab,
  onBack,
  onRefresh,
  onTransition,
  onTally,
  onShare,
}: {
  token: string;
  member: Member;
  election: ElectionSummary | null;
  loading: boolean;
  error: string;
  notice: string;
  tab: DetailTab;
  setTab: (tab: DetailTab) => void;
  onBack: () => void;
  onRefresh: () => void;
  onTransition: (status: ElectionStatus, label: string) => void;
  onTally: () => void;
  onShare: () => void;
}) {
  const { t } = useLanguage();
  const [refreshKey, setRefreshKey] = useState(0);

  function refreshAll() {
    setRefreshKey((v) => v + 1);
    onRefresh();
  }

  if (!election) {
    return (
      <Screen>
        <Pressable onPress={onBack}>
          <Text style={uiStyles.fieldLabel}>‹ All elections</Text>
        </Pressable>
        <Spinner label={t('common.loading')} />
        {error ? <Notice tone="danger">{error}</Notice> : null}
      </Screen>
    );
  }

  const isAuditor = member.role === 'AUDITOR';
  const setupLocked = isAuditor || (election.status !== 'DRAFT' && election.status !== 'SCHEDULED');
  const tabs = [
    { key: 'overview', label: t('admin.overview') },
    { key: 'setup', label: t('admin.setup') },
    { key: 'voters', label: t('admin.voters') },
    { key: 'messages', label: t('admin.messages') },
    { key: 'preview', label: t('admin.preview') },
    { key: 'results', label: t('admin.results') },
    { key: 'audit', label: t('admin.audit') },
  ];

  return (
    <Screen>
      <Pressable onPress={onBack}>
        <Text style={styles.backText}>‹ All elections</Text>
      </Pressable>

      <Card>
        <View style={styles.detailHead}>
          <View style={{ flex: 1 }}>
            <Text style={styles.detailKicker}>{election.tenantName}</Text>
            <Text style={styles.detailTitle}>{election.title}</Text>
          </View>
          <Badge label={election.status} tone={statusTone(election.status)} />
        </View>
        <Text style={uiStyles.muted}>{election.description}</Text>
        <Stats
          items={[
            { label: t('admin.stats.posts'), value: election.posts },
            { label: t('admin.stats.candidates'), value: election.candidates },
            { label: t('admin.stats.voters'), value: election.voters },
            { label: t('admin.stats.votes'), value: election.votesCast },
          ]}
        />
        {error ? <Notice tone="danger">{error}</Notice> : null}
        {notice ? <Notice tone="success">{notice}</Notice> : null}
        <View style={styles.actionRow}>
          {!isAuditor && (election.status === 'DRAFT' || election.status === 'SCHEDULED') ? (
            <Button
              title={t('admin.activate')}
              kind="success"
              small
              loading={loading}
              onPress={() => onTransition('ACTIVE', t('admin.activate'))}
            />
          ) : null}
          {!isAuditor && election.status === 'DRAFT' ? (
            <Button
              title={t('admin.schedule')}
              kind="secondary"
              small
              loading={loading}
              onPress={() => onTransition('SCHEDULED', t('admin.schedule'))}
            />
          ) : null}
          {!isAuditor && election.status === 'SCHEDULED' ? (
            <Button
              title="Revert to Draft"
              kind="secondary"
              small
              loading={loading}
              onPress={() => onTransition('DRAFT', 'Revert to Draft')}
            />
          ) : null}
          {!isAuditor && election.status === 'ACTIVE' ? (
            <Button
              title={t('admin.closeElection')}
              kind="danger"
              small
              loading={loading}
              onPress={() => onTransition('CLOSED', t('admin.closeElection'))}
            />
          ) : null}
          {!isAuditor && election.status === 'CLOSED' ? (
            <Button
              title="Tally & Publish"
              kind="success"
              small
              loading={loading}
              onPress={onTally}
            />
          ) : null}
          {!isAuditor && election.status !== 'COUNTED' && election.status !== 'CANCELLED' ? (
            <Button
              title={t('admin.cancelElection')}
              kind="ghost"
              small
              loading={loading}
              onPress={() => onTransition('CANCELLED', t('admin.cancelElection'))}
            />
          ) : null}
          <Button title="Share code" kind="secondary" small onPress={onShare} />
          <Button title={t('common.refresh')} kind="ghost" small onPress={refreshAll} loading={loading} />
        </View>
      </Card>

      <Tabs tabs={tabs} active={tab} onChange={(key) => setTab(key as DetailTab)} />

      {tab === 'overview' ? <OverviewTab election={election} /> : null}
      {tab === 'setup' ? (
        <SetupTab
          key={`setup-${refreshKey}`}
          token={token}
          election={election}
          locked={setupLocked}
          onChanged={refreshAll}
        />
      ) : null}
      {tab === 'voters' ? (
        <VotersTab
          key={`voters-${refreshKey}`}
          token={token}
          election={election}
          locked={setupLocked}
          onChanged={refreshAll}
        />
      ) : null}
      {tab === 'messages' ? <CommunicationsTab key={`messages-${refreshKey}`} token={token} election={election} /> : null}
      {tab === 'preview' ? <PreviewTab key={`preview-${refreshKey}`} token={token} election={election} /> : null}
      {tab === 'results' ? <ResultsTab key={`results-${refreshKey}`} token={token} election={election} /> : null}
      {tab === 'audit' ? <AuditTab key={`audit-${refreshKey}`} token={token} election={election} /> : null}
    </Screen>
  );
}

function OverviewTab({ election }: { election: ElectionSummary }) {
  const { t } = useLanguage();
  return (
    <>
      <Card>
        <SectionTitle title={t('admin.overview')} />
        <KeyValue label={t('admin.society')} value={election.tenantName} />
        <KeyValue label={t('admin.startsAt')} value={formatDateTime(election.startsAt)} />
        <KeyValue label={t('admin.endsAt')} value={formatDateTime(election.endsAt)} />
        <KeyValue label="Counted at" value={formatDateTime(election.countedAt)} />
        <KeyValue label={t('admin.notifyVoters')} value={election.notifyVoters ? 'Enabled' : 'Disabled'} />
      </Card>
      <Card>
        <SectionTitle title={t('admin.publicLink')} subtitle="Share this election code with voters. It opens the public ballot without a resident account." />
        <KeyValue label="Election code" value={election.id} mono />
        <KeyValue label="Public API" value={`${API_URL}/api/public/elections/${election.id}`} mono />
        <Notice tone="info">
          Voters paste this code in the Vote tab. In production, replace the code with a short link or QR code on your society notice board.
        </Notice>
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: theme.border },
  listTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  listTitle: { fontSize: 17, fontWeight: '900', color: theme.navy, flexShrink: 1 },
  listMeta: { fontSize: 11, color: theme.muted, marginTop: 2 },
  miniStats: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginTop: 4 },
  miniStat: { fontSize: 10, fontWeight: '700', color: theme.tealDark, backgroundColor: theme.tealSoft, paddingHorizontal: 7, paddingVertical: 3, borderRadius: 7 },
  chevron: { fontSize: 26, color: '#b8c7d2' },
  tenantRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  tenantChip: { paddingHorizontal: 12, paddingVertical: 9, borderRadius: 999, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.soft },
  tenantChipActive: { backgroundColor: theme.teal, borderColor: theme.teal },
  tenantChipText: { fontSize: 12, fontWeight: '800', color: theme.text },
  tenantChipTextActive: { color: theme.white },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 8 },
  checkbox: { width: 24, height: 24, borderRadius: 7, borderWidth: 2, borderColor: '#b8c7d2', alignItems: 'center', justifyContent: 'center' },
  checkboxOn: { backgroundColor: theme.teal, borderColor: theme.teal },
  checkMark: { color: theme.white, fontSize: 14, fontWeight: '900' },
  checkLabel: { fontSize: 13, fontWeight: '700', color: theme.text, flex: 1 },
  backText: { fontSize: 13, fontWeight: '800', color: theme.teal, marginBottom: 4 },
  detailHead: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  detailKicker: { fontSize: 10, fontWeight: '900', letterSpacing: 1.2, color: theme.teal, textTransform: 'uppercase' },
  detailTitle: { fontSize: 23, lineHeight: 29, fontWeight: '900', color: theme.navy, marginTop: 2 },
  actionRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
});
