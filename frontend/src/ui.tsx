import React, { useState } from 'react';
import {
  ActivityIndicator,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
  ViewStyle,
} from 'react-native';
import type { ElectionStatus } from './types';
import { useLanguage } from './i18n';
import type { Language } from './types';
import { API_URL } from './api';

export const theme = {
  bg: '#f4f7f8',
  card: '#ffffff',
  navy: '#102a43',
  teal: '#0e7490',
  tealDark: '#0b5f75',
  tealSoft: '#e0f2f7',
  mint: '#d9f2ef',
  border: '#e3eaee',
  text: '#17324a',
  muted: '#6c8297',
  soft: '#f0f5f7',
  success: '#237a4b',
  successSoft: '#e5f4ea',
  danger: '#b42318',
  dangerSoft: '#fdecea',
  warning: '#b54708',
  warningSoft: '#fef3e2',
  white: '#ffffff',
  shadow: '#0b2b3a',
};

export const spacing = (n: number) => n * 4;

export function Screen({
  children,
  scroll = true,
  refreshing,
  onRefresh,
  contentStyle,
}: {
  children: React.ReactNode;
  scroll?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
  contentStyle?: ViewStyle;
}) {
  const body = (
    <View style={[styles.screenInner, contentStyle]}>
      {children}
    </View>
  );
  if (!scroll) return <View style={styles.screen}>{body}</View>;
  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 12 : 0}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          onRefresh ? (
            <RefreshControl refreshing={Boolean(refreshing)} onRefresh={onRefresh} tintColor={theme.teal} />
          ) : undefined
        }
      >
        {body}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

export function Card({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function SectionTitle({
  title,
  subtitle,
  right,
}: {
  title: string;
  subtitle?: string;
  right?: React.ReactNode;
}) {
  return (
    <View style={styles.sectionHeader}>
      <View style={{ flex: 1 }}>
        <Text style={styles.sectionTitle}>{title}</Text>
        {subtitle ? <Text style={styles.muted}>{subtitle}</Text> : null}
      </View>
      {right}
    </View>
  );
}

export function Button({
  title,
  onPress,
  disabled,
  loading,
  kind = 'primary',
  small,
  style,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  kind?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
  small?: boolean;
  style?: ViewStyle;
}) {
  const palette = BUTTON_COLORS[kind];
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.button,
        small && styles.buttonSmall,
        { backgroundColor: palette.bg, borderColor: palette.border },
        (disabled || loading) && styles.buttonDisabled,
        pressed && !disabled && !loading ? styles.buttonPressed : null,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={palette.text} />
      ) : (
        <Text style={[styles.buttonText, small && styles.buttonTextSmall, { color: palette.text }]}>{title}</Text>
      )}
    </Pressable>
  );
}

const BUTTON_COLORS = {
  primary: { bg: theme.teal, border: theme.teal, text: theme.white },
  secondary: { bg: theme.white, border: theme.border, text: theme.text },
  ghost: { bg: 'transparent', border: 'transparent', text: theme.teal },
  danger: { bg: theme.dangerSoft, border: '#f4c7c3', text: theme.danger },
  success: { bg: theme.successSoft, border: '#bfe3cd', text: theme.success },
};

export function Field({
  label,
  hint,
  error,
  secure,
  style,
  ...props
}: TextInputProps & { label?: string; hint?: string; error?: string; secure?: boolean; style?: ViewStyle }) {
  const [visible, setVisible] = useState(false);
  return (
    <View style={[styles.field, style]}>
      {label ? <Text style={styles.fieldLabel}>{label}</Text> : null}
      <View style={styles.inputWrap}>
        <TextInput
          {...props}
          secureTextEntry={secure ? !visible : props.secureTextEntry}
          placeholderTextColor="#95a7b8"
          style={[styles.input, props.multiline && styles.multiline, secure && { paddingRight: 62 }]}
        />
        {secure ? (
          <Pressable style={styles.eye} onPress={() => setVisible((v) => !v)} accessibilityRole="button">
            <Text style={styles.eyeText}>{visible ? 'Hide' : 'Show'}</Text>
          </Pressable>
        ) : null}
      </View>
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </View>
  );
}

export function Notice({
  children,
  tone = 'info',
  title,
}: {
  children: React.ReactNode;
  tone?: 'info' | 'success' | 'danger' | 'warning';
  title?: string;
}) {
  const palette = NOTICE_COLORS[tone];
  return (
    <View style={[styles.notice, { backgroundColor: palette.bg, borderColor: palette.border }]}>
      {title ? <Text style={[styles.noticeTitle, { color: palette.text }]}>{title}</Text> : null}
      <Text style={[styles.noticeText, { color: palette.text }]}>{children}</Text>
    </View>
  );
}

const NOTICE_COLORS = {
  info: { bg: '#e8f5fa', border: '#c8e5ee', text: '#075985' },
  success: { bg: theme.successSoft, border: '#c1e5ce', text: '#195e3a' },
  danger: { bg: theme.dangerSoft, border: '#f5cbc7', text: '#8f1c13' },
  warning: { bg: theme.warningSoft, border: '#f5dcb8', text: '#8a3b08' },
};

export function Badge({
  label,
  tone = 'neutral',
}: {
  label: string;
  tone?: 'neutral' | 'success' | 'danger' | 'warning' | 'info' | 'teal';
}) {
  const palette = BADGE_COLORS[tone];
  return (
    <View style={[styles.badge, { backgroundColor: palette.bg }]}>
      <View style={[styles.badgeDot, { backgroundColor: palette.dot }]} />
      <Text style={[styles.badgeText, { color: palette.text }]}>{label}</Text>
    </View>
  );
}

const BADGE_COLORS = {
  neutral: { bg: theme.soft, dot: theme.muted, text: theme.muted },
  success: { bg: theme.successSoft, dot: theme.success, text: theme.success },
  danger: { bg: theme.dangerSoft, dot: theme.danger, text: theme.danger },
  warning: { bg: theme.warningSoft, dot: theme.warning, text: theme.warning },
  info: { bg: '#e8f5fa', dot: theme.teal, text: '#075985' },
  teal: { bg: theme.tealSoft, dot: theme.teal, text: theme.tealDark },
};

export function statusTone(status: ElectionStatus | string): 'neutral' | 'success' | 'danger' | 'warning' | 'info' | 'teal' {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'SCHEDULED':
      return 'info';
    case 'CLOSED':
    case 'COUNTED':
      return 'teal';
    case 'CANCELLED':
      return 'danger';
    case 'DRAFT':
    default:
      return 'neutral';
  }
}

export function Stats({ items }: { items: Array<{ label: string; value: string | number }> }) {
  return (
    <View style={styles.statsRow}>
      {items.map((item) => (
        <View key={item.label} style={styles.statBox}>
          <Text style={styles.statValue}>{item.value}</Text>
          <Text style={styles.statLabel}>{item.label}</Text>
        </View>
      ))}
    </View>
  );
}

export function KeyValue({ label, value, mono }: { label: string; value: string | null | undefined; mono?: boolean }) {
  return (
    <View style={styles.keyValue}>
      <Text style={styles.keyLabel}>{label}</Text>
      <Text selectable style={[styles.keyValueText, mono && styles.mono]}>{value || '—'}</Text>
    </View>
  );
}

export function Photo({
  uri,
  name,
  size = 52,
}: {
  uri?: string | null;
  name: string;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  const initial = (name || '?').trim().charAt(0).toUpperCase();
  if (!uri || failed) {
    return (
      <View style={[styles.photoFallback, { width: size, height: size, borderRadius: size / 2 }]}>
        <Text style={[styles.photoInitial, { fontSize: size * 0.4 }]}>{initial}</Text>
      </View>
    );
  }
  return (
    <Image
      source={{ uri: uri.startsWith('/') ? `${API_URL}${uri}` : uri }}
      onError={() => setFailed(true)}
      style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: theme.soft }}
    />
  );
}

export function ChoiceCard({
  selected,
  onPress,
  children,
  disabled,
  style,
}: {
  selected?: boolean;
  onPress?: () => void;
  children: React.ReactNode;
  disabled?: boolean;
  style?: ViewStyle;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.choice,
        selected && styles.choiceSelected,
        disabled && { opacity: 0.72 },
        pressed && !disabled ? { opacity: 0.9 } : null,
        style,
      ]}
    >
      <View style={[styles.radio, selected && styles.radioSelected]}>
        {selected ? <View style={styles.radioInner} /> : null}
      </View>
      <View style={{ flex: 1 }}>{children}</View>
    </Pressable>
  );
}

export function Bar({ percentage, leading }: { percentage: number; leading?: boolean }) {
  const width = `${Math.max(0, Math.min(100, percentage))}%` as unknown as number;
  return (
    <View style={styles.barTrack}>
      <View style={[styles.barFill, { width }, leading && { backgroundColor: theme.teal }]} />
    </View>
  );
}

export function Chip({
  label,
  active,
  onPress,
}: {
  label: string;
  active?: boolean;
  onPress?: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.chip, active && styles.chipActive]}
      accessibilityRole="button"
    >
      <Text style={[styles.chipText, active && styles.chipTextActive]}>{label}</Text>
    </Pressable>
  );
}

export function Tabs({
  tabs,
  active,
  onChange,
}: {
  tabs: Array<{ key: string; label: string }>;
  active: string;
  onChange: (key: string) => void;
}) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabRow}>
      {tabs.map((tab) => (
        <Pressable
          key={tab.key}
          onPress={() => onChange(tab.key)}
          style={[styles.tab, active === tab.key && styles.tabActive]}
        >
          <Text style={[styles.tabText, active === tab.key && styles.tabTextActive]}>{tab.label}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

export function Brand({ compact }: { compact?: boolean }) {
  return (
    <View style={[styles.brand, compact && { marginBottom: 0 }]}>
      <View style={styles.logo}>
        <Text style={styles.logoText}>C</Text>
      </View>
      <View>
        <Text style={styles.brandTitle}>CIVITAS</Text>
        {!compact ? <Text style={styles.brandSub}>Secure society elections</Text> : null}
      </View>
    </View>
  );
}

export function LanguagePicker() {
  const { language, setLanguage, t } = useLanguage();
  const options: Language[] = ['en', 'hi', 'mr'];
  return (
    <View style={styles.languageRow}>
      {options.map((code) => (
        <Pressable
          key={code}
          onPress={() => setLanguage(code)}
          style={[styles.languageChip, language === code && styles.languageChipActive]}
        >
          <Text style={[styles.languageText, language === code && styles.languageTextActive]}>
            {t(`language.${code}`)}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

export function EmptyState({
  title,
  body,
  action,
}: {
  title: string;
  body?: string;
  action?: React.ReactNode;
}) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyOrb}>
        <Text style={styles.emptyOrbText}>C</Text>
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      {body ? <Text style={[styles.muted, { textAlign: 'center' }]}>{body}</Text> : null}
      {action}
    </View>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <View style={styles.spinner}>
      <ActivityIndicator color={theme.teal} />
      {label ? <Text style={styles.muted}>{label}</Text> : null}
    </View>
  );
}

export function Muted({ children }: { children: React.ReactNode }) {
  return <Text style={styles.muted}>{children}</Text>;
}

export function Divider() {
  return <View style={styles.divider} />;
}

export function formatDate(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Converts a local datetime input (YYYY-MM-DDTHH:MM) to an ISO instant. */
export function localInputToIso(value: string): string | null {
  if (!value.trim()) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return parsed.toISOString();
}

export function dateToLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function shortHash(value?: string | null, length = 16): string {
  if (!value) return '—';
  return value.length <= length * 2 ? value : `${value.slice(0, length)}…${value.slice(-length)}`;
}

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: theme.bg },
  scrollContent: { flexGrow: 1 },
  screenInner: { padding: 18, paddingBottom: 44, gap: 14, width: '100%', maxWidth: 760, alignSelf: 'center' },
  card: {
    backgroundColor: theme.card,
    borderRadius: 22,
    padding: 18,
    borderWidth: 1,
    borderColor: theme.border,
    gap: 12,
    shadowColor: theme.shadow,
    shadowOpacity: 0.04,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 1,
  },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  sectionTitle: { fontSize: 19, fontWeight: '900', color: theme.navy, marginBottom: 4 },
  muted: { fontSize: 14, lineHeight: 21, color: theme.muted },
  hint: { fontSize: 12, lineHeight: 18, color: theme.muted, marginTop: 6 },
  errorText: { fontSize: 12, color: theme.danger, marginTop: 6, fontWeight: '600' },
  button: {
    minHeight: 50,
    borderRadius: 15,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 18,
    marginTop: 6,
  },
  buttonSmall: { minHeight: 38, paddingHorizontal: 13, borderRadius: 12, marginTop: 0 },
  buttonDisabled: { opacity: 0.45 },
  buttonPressed: { opacity: 0.82 },
  buttonText: { fontSize: 15, fontWeight: '800', letterSpacing: 0.2 },
  buttonTextSmall: { fontSize: 13 },
  field: { gap: 7 },
  fieldLabel: { fontSize: 11, fontWeight: '900', letterSpacing: 1, color: theme.teal, textTransform: 'uppercase' },
  inputWrap: { position: 'relative', justifyContent: 'center' },
  input: {
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'ios' ? 14 : 11,
    fontSize: 15,
    color: theme.text,
    backgroundColor: '#fbfcfd',
  },
  multiline: { minHeight: 100, textAlignVertical: 'top', paddingTop: 12 },
  eye: { position: 'absolute', right: 12, padding: 6 },
  eyeText: { fontSize: 12, fontWeight: '800', color: theme.teal },
  notice: { borderWidth: 1, borderRadius: 15, padding: 13, gap: 3 },
  noticeTitle: { fontSize: 13, fontWeight: '900' },
  noticeText: { fontSize: 13, lineHeight: 20, fontWeight: '600' },
  badge: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999 },
  badgeDot: { width: 6, height: 6, borderRadius: 3 },
  badgeText: { fontSize: 10, fontWeight: '900', letterSpacing: 0.7 },
  statsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  statBox: { flexGrow: 1, minWidth: 84, backgroundColor: theme.soft, borderRadius: 15, padding: 12 },
  statValue: { fontSize: 24, fontWeight: '900', color: theme.navy },
  statLabel: { fontSize: 11, fontWeight: '700', color: theme.muted, marginTop: 2 },
  keyValue: { gap: 4 },
  keyLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 0.9, color: theme.teal, textTransform: 'uppercase' },
  keyValueText: { fontSize: 14, color: theme.text, lineHeight: 20 },
  mono: { fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontSize: 12, lineHeight: 18 },
  photoFallback: { backgroundColor: theme.mint, alignItems: 'center', justifyContent: 'center' },
  photoInitial: { color: theme.tealDark, fontWeight: '900' },
  choice: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'flex-start',
    padding: 14,
    borderRadius: 17,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: '#fcfdfd',
  },
  choiceSelected: { borderColor: theme.teal, backgroundColor: '#effafb' },
  radio: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: '#b6c7d3',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  radioSelected: { borderColor: theme.teal },
  radioInner: { width: 10, height: 10, borderRadius: 5, backgroundColor: theme.teal },
  barTrack: { height: 9, borderRadius: 6, backgroundColor: '#edf2f4', overflow: 'hidden', marginTop: 6 },
  barFill: { height: '100%', borderRadius: 6, backgroundColor: '#8fc9d5' },
  chip: { paddingHorizontal: 13, paddingVertical: 9, borderRadius: 999, backgroundColor: theme.soft },
  chipActive: { backgroundColor: theme.teal },
  chipText: { fontSize: 12, fontWeight: '800', color: theme.muted },
  chipTextActive: { color: theme.white },
  tabRow: { gap: 8, paddingVertical: 2 },
  tab: { paddingHorizontal: 15, paddingVertical: 10, borderRadius: 999, backgroundColor: theme.white, borderWidth: 1, borderColor: theme.border },
  tabActive: { backgroundColor: theme.navy, borderColor: theme.navy },
  tabText: { fontSize: 12, fontWeight: '800', color: theme.text },
  tabTextActive: { color: theme.white },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 11, marginBottom: 12 },
  logo: { width: 44, height: 44, borderRadius: 14, backgroundColor: theme.teal, alignItems: 'center', justifyContent: 'center' },
  logoText: { fontSize: 25, fontWeight: '900', color: theme.white },
  brandTitle: { fontSize: 19, fontWeight: '900', letterSpacing: 2.4, color: theme.navy },
  brandSub: { fontSize: 11, color: theme.muted, marginTop: 1 },
  languageRow: { flexDirection: 'row', gap: 5 },
  languageChip: { paddingHorizontal: 8, paddingVertical: 6, borderRadius: 9, backgroundColor: theme.soft },
  languageChipActive: { backgroundColor: theme.teal },
  languageText: { fontSize: 10, fontWeight: '800', color: theme.muted },
  languageTextActive: { color: theme.white },
  empty: { alignItems: 'center', gap: 10, paddingVertical: 30, paddingHorizontal: 16 },
  emptyOrb: { width: 58, height: 58, borderRadius: 20, backgroundColor: theme.tealSoft, alignItems: 'center', justifyContent: 'center' },
  emptyOrbText: { fontSize: 28, fontWeight: '900', color: theme.teal },
  emptyTitle: { fontSize: 18, fontWeight: '900', color: theme.navy, textAlign: 'center' },
  spinner: { flexDirection: 'row', gap: 10, alignItems: 'center', paddingVertical: 10 },
  divider: { height: 1, backgroundColor: theme.border, marginVertical: 4 },
});
