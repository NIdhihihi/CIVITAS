import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import type { Member, Tenant } from '../types';
import { Badge, Button, Card, Field, Muted, Notice, SectionTitle, Spinner, theme } from '../ui';

export function SocietiesTab({ token, member, onChanged }: { token: string; member: Member; onChanged: () => void }) {
  const [societies, setSocieties] = useState<Tenant[]>([]);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [city, setCity] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSocieties(await api<Tenant[]>('/api/tenants', { token }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Societies could not be loaded.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  async function create() {
    if (!name.trim() || !code.trim() || !city.trim()) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const society = await api<Tenant>('/api/tenants', {
        method: 'POST', token,
        body: { name: name.trim(), code: code.trim().toUpperCase(), city: city.trim() },
      });
      setName('');
      setCode('');
      setCity('');
      setNotice(`${society.name} has been created.`);
      await load();
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Society could not be created.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {member.role === 'PLATFORM_ADMIN' ? (
        <Card>
          <SectionTitle title="Create a society" subtitle="Register a society before creating its elections." />
          {error ? <Notice tone="danger">{error}</Notice> : null}
          {notice ? <Notice tone="success">{notice}</Notice> : null}
          <Field label="Society name" value={name} onChangeText={setName} placeholder="Green Meadows CHS" />
          <Field label="Society code" value={code} onChangeText={setCode} autoCapitalize="characters" placeholder="GREEN01" />
          <Field label="City" value={city} onChangeText={setCity} placeholder="Pune" />
          <Button title="Create society" onPress={create} loading={busy} disabled={!name.trim() || !code.trim() || !city.trim()} />
        </Card>
      ) : null}
      <Card>
        <SectionTitle title="Societies" subtitle="Registered organizations on CIVITAS." />
        {loading ? <Spinner label="Loading societies…" /> : null}
        {!loading && societies.length === 0 ? <Muted>No societies have been created yet.</Muted> : null}
        {societies.map((society) => (
          <View key={society.id} style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{society.name}</Text>
              <Text style={styles.meta}>{society.city}</Text>
            </View>
            <Badge label={society.code} tone="teal" />
          </View>
        ))}
      </Card>
    </>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: theme.border },
  name: { color: theme.text, fontWeight: '800', fontSize: 14 },
  meta: { color: theme.muted, fontSize: 12, marginTop: 3 },
});
