import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowDownRight, ArrowLeftRight, ArrowUpRight, CalendarDays, Check, ChevronDown,
  CircleHelp, Download, Globe2, Home, LockKeyhole, LogOut, Mic, Plus, Search, Settings,
  Tag, Wallet, X,
} from 'lucide-react'
import type { ReactNode } from 'react'
import {
  amountFromSpeech, formatMoney, loadDatabase, parseMoney, saveDatabase,
  suggestCategory, type Category, type Database, type Entry, type EntryType, type Session, type Visibility,
} from './model'
import { SupabaseApi } from './supabase'

type InstallPromptEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }
type SpeechResultEvent = { results: ArrayLike<ArrayLike<{ transcript: string }>> }
type SpeechRecognizer = { lang: string; interimResults: boolean; onresult: ((event: SpeechResultEvent) => void) | null; onerror: (() => void) | null; onend: (() => void) | null; start: () => void; stop: () => void }
type BrowserWindow = Window & { SpeechRecognition?: new () => SpeechRecognizer; webkitSpeechRecognition?: new () => SpeechRecognizer }

const navItems = [
  { label: 'Visão geral', icon: Home },
  { label: 'Lançamentos', icon: ArrowLeftRight },
  { label: 'Categorias', icon: Tag },
  { label: 'Ajustes', icon: Settings },
]
const emptyDatabase = () => { const database = loadDatabase(); return { ...database, categories: database.categories.map((item) => ({ ...item, subcategories: [...item.subcategories] })) } }
const currentMonth = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
const monthFromDate = (value: string) => value.slice(0, 7)
const monthLabel = (value: string, short = false) => new Intl.DateTimeFormat('pt-BR', { month: short ? 'short' : 'long', ...(short ? {} : { year: 'numeric' }) }).format(new Date(`${value}-15T12:00:00`))
const moneyInput = (cents: number) => cents ? (cents / 100).toFixed(2).replace('.', ',') : ''
const uuid = () => crypto.randomUUID()

function useInstallPrompt() {
  const [available, setAvailable] = useState(false)
  const [promptEvent, setPromptEvent] = useState<InstallPromptEvent | null>(null)
  const [installed, setInstalled] = useState(false)
  useEffect(() => {
    const handleBefore = (event: Event) => { event.preventDefault(); setPromptEvent(event as InstallPromptEvent); setAvailable(true) }
    const handleInstalled = () => { setInstalled(true); setAvailable(false); setPromptEvent(null) }
    window.addEventListener('beforeinstallprompt', handleBefore)
    window.addEventListener('appinstalled', handleInstalled)
    setInstalled(window.matchMedia('(display-mode: standalone)').matches)
    return () => { window.removeEventListener('beforeinstallprompt', handleBefore); window.removeEventListener('appinstalled', handleInstalled) }
  }, [])
  const install = async () => {
    if (!promptEvent) return false
    await promptEvent.prompt()
    const choice = await promptEvent.userChoice
    if (choice.outcome === 'accepted') { setInstalled(true); setAvailable(false); setPromptEvent(null) }
    return choice.outcome === 'accepted'
  }
  return { available, installed, install }
}

export default function App() {
  const [db, setDb] = useState<Database>(emptyDatabase)
  const [section, setSection] = useState('Visão geral')
  const [month, setMonth] = useState(currentMonth)
  const [query, setQuery] = useState('')
  const [modal, setModal] = useState<'entry' | 'category' | 'invite' | 'install' | null>(null)
  const [subCategoryFor, setSubCategoryFor] = useState<Category | null>(null)
  const [entryType, setEntryType] = useState<EntryType>('expense')
  const [draft, setDraft] = useState<Partial<Entry>>({})
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const installPrompt = useInstallPrompt()
  const update = useCallback((patch: Partial<Database>) => setDb((previous) => ({ ...previous, ...patch })), [])
  const api = useMemo(() => new SupabaseApi(db, (session: Session) => update({ session })), [db, update])

  useEffect(() => { saveDatabase(db) }, [db])

  const monthEntries = useMemo(() => db.entries.filter((entry) => monthFromDate(entry.createdAt) === month), [db.entries, month])
  const incomeExtras = monthEntries.filter((entry) => entry.type === 'income').reduce((sum, entry) => sum + entry.amountCents, 0)
  const expenses = monthEntries.filter((entry) => entry.type === 'expense').reduce((sum, entry) => sum + entry.amountCents, 0)
  const incomeTotal = db.profile.monthlyIncomeCents + incomeExtras
  const recentEntries = db.entries.filter((entry) => matchesSearch(entry, query)).slice(0, 6)
  const installApp = async () => {
    const accepted = await installPrompt.install()
    if (!accepted) setModal('install')
  }

  useEffect(() => {
    const code = new URLSearchParams(location.search).get('invite')
    if (!code) return
    setSection('Ajustes')
    setDraft({ title: code })
    if (db.session) {
      setBusy(true)
      new SupabaseApi(db, (session) => update({ session })).acceptInvite(code)
        .then((entries) => { update({ entries }); setMessage('Convite aceito; lançamentos públicos sincronizados.') })
        .catch((error: Error) => setMessage(error.message))
        .finally(() => { setBusy(false); history.replaceState({}, '', location.pathname) })
    } else {
      setModal('invite')
    }
  // An invite is consumed once after the app has a session.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [Boolean(db.session)])

  const createEntry = async (entry: Omit<Entry, 'id' | 'ownerId' | 'author' | 'createdAt'>) => {
    const record: Entry = { ...entry, id: uuid(), ownerId: '', author: db.profile.name || 'Pessoa', createdAt: new Date().toISOString() }
    const next = { ...db, entries: [record, ...db.entries] }
    update({ entries: next.entries })
    setModal(null)
    if (record.visibility === 'public' && db.session) {
      setBusy(true)
      try { update({ entries: await new SupabaseApi(next, (session) => update({ session })).sync() }); setMessage('Lançamento público compartilhado.') }
      catch (error) { setMessage(`Salvo neste aparelho; sincronização pendente: ${(error as Error).message}`) }
      finally { setBusy(false) }
    } else if (record.visibility === 'public') setMessage('Salvo como público neste aparelho. Entre para compartilhar com suas conexões.')
    else setMessage('Lançamento privado salvo neste aparelho.')
  }

  const openEntry = (type: EntryType, voiceText = '') => {
    setEntryType(type)
    const suggested = voiceText ? suggestCategory(voiceText, db.categories) : type === 'income' ? 'Renda' : 'Mercado'
    setDraft({ title: voiceText, product: '', amountCents: 0, category: suggested, subcategory: '', visibility: 'private' })
    if (voiceText) {
      const amountText = amountFromSpeech(voiceText)
      setDraft({ title: voiceText, product: '', amountCents: parseMoney(amountText), category: suggested, subcategory: '', visibility: 'private' })
    }
    setModal('entry')
  }

  const recordVoice = () => {
    const browser = window as BrowserWindow
    const Recognition = browser.SpeechRecognition ?? browser.webkitSpeechRecognition
    if (!Recognition) { setMessage('Este navegador não oferece ditado. Use o microfone do teclado no campo Descrição.'); return }
    const recognition = new Recognition()
    recognition.lang = 'pt-BR'; recognition.interimResults = false
    recognition.onresult = (event) => openEntry('expense', event.results[0]?.[0]?.transcript ?? '')
    recognition.onerror = () => setMessage('Não foi possível reconhecer o áudio. Confira a permissão do microfone e tente novamente.')
    recognition.start()
  }

  useEffect(() => {
    const action = new URLSearchParams(location.search).get('action')
    if (!action) return
    history.replaceState({}, '', location.pathname)
    if (action === 'voice') recordVoice()
    else if (action === 'new-entry') openEntry('expense')
  // PWA shortcuts are consumed once on launch.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const refreshData = async () => {
    setBusy(true)
    try { update({ entries: await api.sync() }); setMessage('Dados sincronizados.') }
    catch (error) { setMessage((error as Error).message) }
    finally { setBusy(false) }
  }

  const installControls = { available: installPrompt.available, installed: installPrompt.installed, onInstall: installApp }
  return <div className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="#inicio" onClick={() => setSection('Visão geral')} aria-label="FinNote, início"><span className="brand-mark"><Wallet size={21} strokeWidth={2.35} /></span><span>Fin<span>Note</span></span></a>
      <nav className="side-nav" aria-label="Navegação principal">
        {navItems.map(({ label, icon: Icon }) => <button key={label} className={`nav-link ${section === label ? 'selected' : ''}`} onClick={() => setSection(label)}><Icon size={18} strokeWidth={2} /><span>{label}</span></button>)}
      </nav>
      <div className="sidebar-bottom"><div className="connection-state"><span className={`state-dot ${db.session ? 'online' : ''}`} /><span>{db.session ? 'Conta conectada' : 'Dados neste aparelho'}</span></div><button className="profile-mini" onClick={() => setSection('Ajustes')}><span className="avatar">{(db.profile.name || '?').slice(0, 1).toUpperCase()}</span><span className="profile-mini-copy"><strong>{db.profile.name || 'Seu perfil'}</strong><small>{db.profile.phone || 'Configurar conta'}</small></span><ChevronDown size={15} /></button></div>
    </aside>

    <main className="workspace">
      <header className="topbar">
        <div className="mobile-brand"><span className="brand-mark"><Wallet size={19} /></span><strong>Fin<span>Note</span></strong></div>
          <label className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar lançamentos, categorias..." aria-label="Buscar lançamentos" /></label>
        <div className="topbar-actions">
          <button className="voice-top-button" onClick={recordVoice} aria-label="Adicionar lançamento por voz"><Mic size={17} /></button>
          {installControls.installed ? <span className="installed-state"><Check size={15} /> Instalado</span> : <button className="install-button" onClick={installControls.onInstall}><Download size={16} /> Instalar app</button>}
          <button className="avatar top-avatar" onClick={() => setSection('Ajustes')} aria-label="Abrir ajustes do perfil">{(db.profile.name || '?').slice(0, 1).toUpperCase()}</button>
        </div>
      </header>

      <div className="page-wrap">
        {section === 'Visão geral' && <Dashboard db={db} month={month} setMonth={setMonth} entries={monthEntries} income={incomeTotal} expense={expenses} recent={recentEntries} onAdd={() => openEntry('expense')} onViewAll={() => setSection('Lançamentos')} />}
        {section === 'Lançamentos' && <Transactions entries={db.entries.filter((entry) => matchesSearch(entry, query))} onAdd={() => openEntry('expense')} />}
        {section === 'Categorias' && <Categories categories={db.categories} addCategory={() => setModal('category')} addSubcategory={setSubCategoryFor} />}
        {section === 'Ajustes' && <SettingsPage db={db} update={update} api={api} busy={busy} setBusy={setBusy} showMessage={setMessage} installControls={installControls} onSync={refreshData} />}
      </div>
    </main>

    <nav className="mobile-nav" aria-label="Navegação móvel">
      {navItems.slice(0, 2).map(({ label, icon: Icon }) => <button key={label} className={section === label ? 'active' : ''} onClick={() => setSection(label)}><Icon size={19} /><span>{label === 'Visão geral' ? 'Início' : label}</span></button>)}
      <button className="mobile-add" onClick={() => openEntry('expense')} aria-label="Adicionar lançamento"><Plus size={24} /></button>
      {navItems.slice(2).map(({ label, icon: Icon }) => <button key={label} className={section === label ? 'active' : ''} onClick={() => setSection(label)}><Icon size={19} /><span>{label}</span></button>)}
    </nav>

    {modal === 'entry' && <EntryModal categories={db.categories} type={entryType} initial={draft} close={() => setModal(null)} save={createEntry} />}
    {modal === 'category' && <TextModal title="Nova categoria" label="Nome da categoria" close={() => setModal(null)} submit={(name) => { const clean = name.trim(); if (!clean || db.categories.some((category) => category.name.toLocaleLowerCase('pt-BR') === clean.toLocaleLowerCase('pt-BR'))) { setMessage('Informe um nome novo para a categoria.'); return } update({ categories: [...db.categories, { id: uuid(), name: clean, subcategories: [] }] }); setModal(null) }} />}
    {subCategoryFor && <TextModal title={`Subcategoria de ${subCategoryFor.name}`} label="Nome da subcategoria" close={() => setSubCategoryFor(null)} submit={(name) => { const clean = name.trim(); update({ categories: db.categories.map((category) => category.id === subCategoryFor.id && !category.subcategories.some((item) => item.toLocaleLowerCase('pt-BR') === clean.toLocaleLowerCase('pt-BR')) ? { ...category, subcategories: [...category.subcategories, clean] } : category) }); setSubCategoryFor(null) }} />}
    {modal === 'invite' && <TextModal title="Aceitar conexão" label="Link ou código do convite" initial={draft.title} close={() => setModal(null)} submit={async (value) => { setBusy(true); try { const entries = await api.acceptInvite(value); update({ entries }); setMessage('Conexão feita; lançamentos públicos sincronizados.'); history.replaceState({}, '', location.pathname) } catch (error) { setMessage((error as Error).message) } finally { setBusy(false); setModal(null) } }} />}
    {modal === 'install' && <InstallHelp close={() => setModal(null)} />}
    {message && <Toast message={message} busy={busy} close={() => setMessage('')} />}
    {busy && !message && <div className="busy-pill"><span className="spinner" /> Sincronizando</div>}
  </div>
}

function matchesSearch(entry: Entry, query: string) {
  const term = query.trim().toLocaleLowerCase('pt-BR')
  return !term || [entry.title, entry.product, entry.category, entry.subcategory, entry.author].some((value) => value.toLocaleLowerCase('pt-BR').includes(term))
}

function Dashboard({ db, month, setMonth, entries, income, expense, recent, onAdd, onViewAll }: {
  db: Database; month: string; setMonth: (value: string) => void; entries: Entry[]; income: number; expense: number; recent: Entry[]; onAdd: () => void; onViewAll: () => void
}) {
  const balance = income - expense
  const comparison = getComparison(db)
  const byCategory = aggregate(entries.filter((entry) => entry.type === 'expense'), (entry) => entry.subcategory ? `${entry.category} · ${entry.subcategory}` : entry.category).slice(0, 5)
  const byProduct = aggregate(entries.filter((entry) => entry.type === 'expense'), (entry) => entry.product || entry.title).slice(0, 5)
  const previous = comparison[comparison.length - 2]
  const delta = previous && (db.entries.length > 0 || db.profile.monthlyIncomeCents > 0) ? percentChange(balance, previous.income - previous.expense) : null
  return <>
    <div className="page-heading dashboard-month"><h1 className="sr-only">Visão geral</h1><label className="month-picker"><CalendarDays size={16} /><input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label></div>
    <section className="summary-grid" aria-label="Resumo financeiro">
      <article className="balance-card"><div className="balance-content"><span className="overline">Saldo do mês</span><strong>{formatMoney(balance)}</strong><span className={`balance-delta ${delta !== null && delta >= 0 ? 'positive' : ''}`}>{delta === null ? 'Comece adicionando seus lançamentos' : `${delta >= 0 ? '↗' : '↘'} ${Math.abs(delta)}% em relação a ${monthLabel(previous!.month, true)}`}</span></div><MiniBars data={comparison.map((item) => item.expense)} /></article>
      <MetricCard label="Entradas" amount={income} compare={income - (previous?.income ?? 0)} icon={<ArrowUpRight size={19} />} type="income" />
      <MetricCard label="Saídas" amount={expense} compare={expense - (previous?.expense ?? 0)} icon={<ArrowDownRight size={19} />} type="expense" />
    </section>
    <section className="analytics-grid">
      <article className="panel monthly-panel"><div className="panel-heading"><h2>Comparativo mensal</h2><div className="legend"><span><i className="legend-in" />Entradas</span><span><i className="legend-out" />Saídas</span></div></div><MonthlyChart data={comparison} /></article>
      <RankingPanel title="Gastos por categoria" data={byCategory} empty="Ainda não há gastos por categoria neste mês." />
      <RankingPanel title="Gastos por produto" data={byProduct} empty="Cadastre produtos nos lançamentos para comparar." />
    </section>
    <section className="panel recent-panel"><div className="panel-heading"><div><h2>Recentes</h2><p>Seus lançamentos mais recentes</p></div><button className="button button-dark button-sm" onClick={onAdd}><Plus size={16} />Adicionar lançamento</button></div>
      {recent.length ? <EntryTable entries={recent} /> : <EmptyState icon={<ArrowLeftRight size={20} />} title="Seu caderno começa aqui" detail="Registre um gasto ou renda para acompanhar seu mês." action="Adicionar lançamento" onAction={onAdd} />}
      {recent.length > 0 && <button className="view-all" onClick={onViewAll}>Ver todos os lançamentos <ArrowUpRight size={14} /></button>}
    </section>
  </>
}

function MetricCard({ label, amount, compare, icon, type }: { label: string; amount: number; compare: number; icon: ReactNode; type: 'income' | 'expense' }) {
  const previous = amount - compare
  const delta = percentChange(amount, previous)
  const favorable = type === 'income' ? compare >= 0 : compare <= 0
  return <article className="metric-card"><span className={`metric-icon ${type}`}>{icon}</span><div className="metric-info"><span>{label}</span><strong>{formatMoney(amount)}</strong><small className={favorable ? 'trend-up' : 'trend-down'}>{compare === 0 ? 'Sem alteração' : `${compare > 0 ? '+' : '−'}${Math.abs(delta)}%`} <em>vs. mês anterior</em></small></div></article>
}

function getComparison(db: Database) {
  const now = new Date(`${currentMonth()}-15T12:00:00`)
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(now); date.setMonth(now.getMonth() - (5 - index))
    const month = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    const monthEntries = db.entries.filter((entry) => monthFromDate(entry.createdAt) === month)
    return {
      month,
      income: db.profile.monthlyIncomeCents + monthEntries.filter((entry) => entry.type === 'income').reduce((sum, entry) => sum + entry.amountCents, 0),
      expense: monthEntries.filter((entry) => entry.type === 'expense').reduce((sum, entry) => sum + entry.amountCents, 0),
    }
  })
}

function percentChange(current: number, previous: number) { if (!previous) return current ? 100 : 0; return Math.round(((current - previous) / Math.abs(previous)) * 100) }
function aggregate(entries: Entry[], key: (entry: Entry) => string) {
  const values = new Map<string, number>()
  entries.forEach((entry) => values.set(key(entry), (values.get(key(entry)) ?? 0) + entry.amountCents))
  return [...values.entries()].map(([name, cents]) => ({ name, cents })).sort((a, b) => b.cents - a.cents)
}

function MonthlyChart({ data }: { data: ReturnType<typeof getComparison> }) {
  const maximum = Math.max(100, ...data.flatMap((item) => [item.income, item.expense]))
  const height = 184
  return <div className="chart-wrap"><div className="y-axis"><span>{formatAxis(maximum)}</span><span>{formatAxis(maximum / 2)}</span><span>R$ 0</span></div><div className="chart-content"><div className="grid-lines"><i /><i /><i /></div><div className="bar-list">{data.map((item) => <div className="month-bar-set" key={item.month}><div className="bars" style={{ height }}><span className="bar income-bar" style={{ height: `${item.income ? Math.max(3, item.income / maximum * 100) : 0}%` }} title={`Entradas: ${formatMoney(item.income)}`} /><span className="bar expense-bar" style={{ height: `${item.expense ? Math.max(3, item.expense / maximum * 100) : 0}%` }} title={`Saídas: ${formatMoney(item.expense)}`} /></div><small>{monthLabel(item.month, true).replace('.', '')}</small></div>)}</div></div></div>
}
function formatAxis(cents: number) { return cents >= 100000 ? `R$ ${(cents / 100000).toFixed(0)} mil` : formatMoney(cents) }
function MiniBars({ data }: { data: number[] }) {
  const max = Math.max(1, ...data)
  if (data.every((value) => value === 0)) return null
  return <div className="mini-bars" aria-hidden="true">{data.map((value, index) => <i key={index} style={{ height: `${value ? Math.max(8, value / max * 100) : 0}%` }} />)}</div>
}

function RankingPanel({ title, data, empty }: { title: string; data: Array<{ name: string; cents: number }>; empty: string }) {
  const max = Math.max(1, ...data.map((item) => item.cents))
  return <article className="panel ranking-panel"><h2>{title}</h2>{data.length ? <div className="ranking-list">{data.map((item, index) => <div className="ranking-row" key={item.name}><div className="ranking-top"><span className="ranking-name"><span className={`ranking-dot dot-${index % 5}`} />{item.name}</span><strong>{formatMoney(item.cents)}</strong></div><div className="ranking-track"><i className={`rank-fill fill-${index % 5}`} style={{ width: `${Math.max(4, item.cents / max * 100)}%` }} /></div></div>)}</div> : <p className="chart-empty">{empty}</p>}</article>
}

function Transactions({ entries, onAdd }: { entries: Entry[]; onAdd: () => void }) {
  const [filter, setFilter] = useState<'all' | Visibility>('all')
  const visible = entries.filter((entry) => filter === 'all' || entry.visibility === filter)
  return <><div className="page-heading"><div><h1>Lançamentos</h1><p>Entradas e gastos em ordem cronológica.</p></div><button className="button button-dark" onClick={onAdd}><Plus size={16} />Novo lançamento</button></div><div className="filter-row">{(['all', 'public', 'private'] as const).map((value) => <button key={value} className={`filter-chip ${filter === value ? 'chosen' : ''}`} onClick={() => setFilter(value)}>{value === 'all' ? 'Todos' : value === 'public' ? 'Públicos' : 'Privados'}</button>)}</div><section className="panel transaction-page">{visible.length ? <EntryTable entries={visible} /> : <EmptyState icon={<ArrowLeftRight size={20} />} title="Nenhum lançamento ainda" detail="Adicione uma entrada ou gasto para começar." action="Novo lançamento" onAction={onAdd} />}</section></>
}

function EntryTable({ entries }: { entries: Entry[] }) {
  return <div className="table-scroll"><table className="entry-table"><thead><tr><th>Data</th><th>Descrição</th><th>Categoria</th><th>Produto</th><th>Valor</th><th>Autor</th><th>Visibilidade</th></tr></thead><tbody>{entries.map((entry) => <tr key={entry.id}><td>{new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(new Date(entry.createdAt))}</td><td><span className={`entry-symbol ${entry.type}`}>{entry.type === 'income' ? <ArrowUpRight size={15} /> : <ArrowDownRight size={15} />}</span><strong>{entry.title}</strong></td><td>{entry.category}{entry.subcategory && <small className="cell-sub">{entry.subcategory}</small>}</td><td>{entry.product || '—'}</td><td className={`amount ${entry.type}`}>{entry.type === 'income' ? '+' : '−'} {formatMoney(entry.amountCents)}</td><td>{entry.author}</td><td><span className={`visibility ${entry.visibility}`}>{entry.visibility === 'public' ? <Globe2 size={12} /> : <LockKeyhole size={12} />}{entry.visibility === 'public' ? 'Público' : 'Privado'}</span></td></tr>)}</tbody></table></div>
}

function Categories({ categories, addCategory, addSubcategory }: { categories: Category[]; addCategory: () => void; addSubcategory: (category: Category) => void }) {
  return <><div className="page-heading"><div><h1>Categorias</h1><p>Organize cada lançamento do seu jeito.</p></div><button className="button button-dark" onClick={addCategory}><Plus size={16} />Nova categoria</button></div><div className="category-list">{categories.map((category, index) => <article className="category-row" key={category.id}><div className={`category-icon icon-${index % 5}`}><Tag size={19} /></div><div className="category-detail"><h2>{category.name}</h2><p>{category.subcategories.length ? category.subcategories.join(' · ') : 'Sem subcategorias'}</p></div><button className="button button-outline button-sm" onClick={() => addSubcategory(category)}><Plus size={15} />Subcategoria</button></article>)}</div></>
}

function SettingsPage({ db, update, api, busy, setBusy, showMessage, installControls, onSync }: {
  db: Database; update: (patch: Partial<Database>) => void; api: SupabaseApi; busy: boolean; setBusy: (value: boolean) => void; showMessage: (value: string) => void; installControls: { available: boolean; installed: boolean; onInstall: () => void }; onSync: () => void
}) {
  const [name, setName] = useState(db.profile.name)
  const [phone, setPhone] = useState(db.profile.phone)
  const [income, setIncome] = useState(moneyInput(db.profile.monthlyIncomeCents))
  const [url, setUrl] = useState(db.supabaseUrl)
  const [key, setKey] = useState(db.supabaseKey)
  const [code, setCode] = useState('')
  const [sent, setSent] = useState(false)

  const saveProfile = () => {
    update({ profile: { name: name.trim(), phone: phone.trim(), monthlyIncomeCents: parseMoney(income) } })
    showMessage('Perfil e rendimento mensal salvos.')
  }
  const saveBackend = () => { update({ supabaseUrl: url.trim().replace(/\/$/, ''), supabaseKey: key.trim() }); showMessage('Configuração do compartilhamento salva neste navegador.') }
  const sendOtp = async () => {
    const next: Database = { ...db, supabaseUrl: url.trim().replace(/\/$/, ''), supabaseKey: key.trim(), profile: { name: name.trim(), phone: phone.trim(), monthlyIncomeCents: parseMoney(income) } }
    update(next)
    setBusy(true)
    try { await new SupabaseApi(next, (session) => update({ session })).sendOtp(phone.trim()); setSent(true); showMessage(`Código enviado para ${phone.trim()}.`) }
    catch (error) { showMessage((error as Error).message) }
    finally { setBusy(false) }
  }
  const verify = async () => {
    const next: Database = { ...db, supabaseUrl: url.trim().replace(/\/$/, ''), supabaseKey: key.trim(), profile: { name: name.trim(), phone: phone.trim(), monthlyIncomeCents: parseMoney(income) } }
    update(next); setBusy(true)
    try {
      const session = await new SupabaseApi(next, (value) => update({ session: value })).verifyOtp(phone.trim(), code.trim())
      const authenticated: Database = { ...next, session }
      const entries = await new SupabaseApi(authenticated, (value) => update({ session: value })).sync()
      update({ session, entries }); setSent(false); setCode(''); showMessage('Login confirmado. Dados públicos sincronizados.')
    } catch (error) { showMessage((error as Error).message) }
    finally { setBusy(false) }
  }
  const shareInvite = async () => {
    setBusy(true)
    try {
      const inviteUrl = await api.createInvite(import.meta.env.BASE_URL)
      if (navigator.share) await navigator.share({ title: 'Conexão FinNote', text: 'Conecte-se para compartilhar os lançamentos públicos.', url: inviteUrl })
      else await navigator.clipboard.writeText(inviteUrl)
      showMessage('Link de conexão pronto. Envie para a pessoa que deseja conectar.')
    } catch (error) { showMessage((error as Error).message) }
    finally { setBusy(false) }
  }
  const acceptInvite = async () => {
    setBusy(true)
    try { const entries = await api.acceptInvite(code.trim()); update({ entries }); setCode(''); showMessage('Conexão aceita; lançamentos públicos sincronizados.') }
    catch (error) { showMessage((error as Error).message) }
    finally { setBusy(false) }
  }

  return <><div className="page-heading"><div><h1>Ajustes</h1><p>Seu perfil, orçamento e conexões.</p></div></div>
    <div className="settings-grid">
      <section className="panel settings-panel"><div className="settings-title"><span className="settings-icon"><Wallet size={19} /></span><div><h2>Perfil e orçamento</h2><p>Seu nome aparece nos lançamentos compartilhados.</p></div></div>
        <div className="form-grid"><Field label="Nome"><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Como podemos te chamar?" /></Field><Field label="Telefone"><input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="+5511999999999" type="tel" /></Field><Field label="Renda mensal fixa"><span className="currency-input"><span>R$</span><input value={income} onChange={(event) => setIncome(event.target.value)} inputMode="decimal" placeholder="0,00" /></span></Field></div><button className="button button-dark" onClick={saveProfile}>Salvar perfil</button>
      </section>
      <section className="panel settings-panel"><div className="settings-title"><span className="settings-icon"><Globe2 size={19} /></span><div><h2>Conexão e compartilhamento</h2><p>{db.session ? `Conectado · ${db.profile.phone}` : 'Entrar para sincronizar lançamentos públicos.'}</p></div></div>
        <div className="form-grid"><Field label="URL do projeto Supabase"><input value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://projeto.supabase.co" /></Field><Field label="Chave anon/public"><input value={key} onChange={(event) => setKey(event.target.value)} placeholder="Chave pública do projeto" type="password" /></Field></div>
        <div className="button-row"><button className="button button-outline" onClick={saveBackend}>Salvar configuração</button>{db.session && <button className="button button-outline" onClick={onSync} disabled={busy}>Sincronizar agora</button>}</div>
        {!db.session ? <div className="login-block">{sent ? <><Field label="Código recebido por SMS"><input value={code} onChange={(event) => setCode(event.target.value)} inputMode="numeric" placeholder="000000" /></Field><button className="button button-dark" onClick={verify} disabled={busy || !code.trim()}>{busy ? 'Verificando…' : 'Confirmar telefone'}</button><button className="text-button" onClick={() => setSent(false)}>Voltar</button></> : <button className="button button-dark" onClick={sendOtp} disabled={busy || !url || !key || !phone}>{busy ? 'Enviando…' : 'Entrar com código SMS'}</button>}</div> : <div className="connection-actions"><button className="button button-dark" onClick={shareInvite} disabled={busy}><Globe2 size={16} />Criar link de conexão</button><Field label="Aceitar convite com link ou código"><input value={code} onChange={(event) => setCode(event.target.value)} placeholder="Cole aqui o link recebido" /></Field><button className="button button-outline" onClick={acceptInvite} disabled={busy || !code.trim()}>Conectar usuário</button><button className="text-button sign-out" onClick={() => update({ session: null })}><LogOut size={15} />Sair da conta</button></div>}
        <p className="settings-note"><CircleHelp size={15} /> O compartilhamento usa SMS do Supabase e exige executar <code>supabase/schema.sql</code> no seu projeto. Lançamentos privados não são enviados.</p>
      </section>
      <section className="panel settings-panel install-panel"><div className="settings-title"><span className="settings-icon"><Download size={19} /></span><div><h2>Instalar no dispositivo</h2><p>Abra o FinNote como um aplicativo, mesmo quando não estiver conectado.</p></div></div><button className="button button-outline" onClick={installControls.onInstall}>{installControls.installed ? <><Check size={16} />Aplicativo instalado</> : <><Download size={16} />Instalar FinNote</>}</button><span className="settings-note">Funciona em dispositivos compatíveis quando o site está publicado em HTTPS.</span></section>
    </div>
  </>
}

function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="field"><span>{label}</span>{children}</label> }

function EntryModal({ categories, type: initialType, initial, close, save }: { categories: Category[]; type: EntryType; initial: Partial<Entry>; close: () => void; save: (entry: Omit<Entry, 'id' | 'ownerId' | 'author' | 'createdAt'>) => void }) {
  const [type, setType] = useState<EntryType>(initialType)
  const [title, setTitle] = useState(initial.title ?? '')
  const [product, setProduct] = useState(initial.product ?? '')
  const [amount, setAmount] = useState(moneyInput(initial.amountCents ?? 0))
  const [category, setCategory] = useState(initial.category ?? categories[0]?.name ?? 'Outros')
  const [subcategory, setSubcategory] = useState(initial.subcategory ?? '')
  const [visibility, setVisibility] = useState<Visibility>(initial.visibility ?? 'private')
  const subs = categories.find((item) => item.name === category)?.subcategories ?? []
  const valid = title.trim() && parseMoney(amount) > 0
  return <Modal title={type === 'income' ? 'Registrar renda extra' : 'Novo lançamento'} close={close}>
    <form className="entry-form" onSubmit={(event) => { event.preventDefault(); if (valid) save({ title: title.trim(), product: product.trim() || title.trim(), category, subcategory, amountCents: parseMoney(amount), type, visibility }) }}>
      <Field label="Descrição"><input autoFocus value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Ex.: Compra no mercado" /></Field>
      <div className="form-grid"><Field label="Produto"><input value={product} onChange={(event) => setProduct(event.target.value)} placeholder="Para o gráfico por produto" /></Field><Field label="Valor"><span className="currency-input"><span>R$</span><input value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="decimal" placeholder="0,00" /></span></Field></div>
      <Field label="Tipo"><div className="segmented"><button type="button" className={type === 'expense' ? 'active' : ''} onClick={() => setType('expense')}>Gasto</button><button type="button" className={type === 'income' ? 'active' : ''} onClick={() => setType('income')}>Renda extra</button></div></Field>
      <Field label="Categoria"><select value={category} onChange={(event) => { setCategory(event.target.value); setSubcategory('') }}>{categories.map((item) => <option key={item.id}>{item.name}</option>)}</select></Field>
      {subs.length > 0 && <Field label="Subcategoria"><select value={subcategory} onChange={(event) => setSubcategory(event.target.value)}><option value="">Sem subcategoria</option>{subs.map((item) => <option key={item}>{item}</option>)}</select></Field>}
      <Field label="Visibilidade"><div className="visibility-select"><button type="button" className={visibility === 'private' ? 'active' : ''} onClick={() => setVisibility('private')}><LockKeyhole size={15} /><span><strong>Privado</strong><small>Só você vê</small></span></button><button type="button" className={visibility === 'public' ? 'active' : ''} onClick={() => setVisibility('public')}><Globe2 size={15} /><span><strong>Público</strong><small>Suas conexões veem</small></span></button></div></Field>
      <div className="modal-actions"><button type="button" className="button button-outline" onClick={close}>Cancelar</button><button className="button button-dark" disabled={!valid}>Salvar lançamento</button></div>
    </form>
  </Modal>
}

function Modal({ title, children, close }: { title: string; children: ReactNode; close: () => void }) {
  useEffect(() => { const keyHandler = (event: KeyboardEvent) => { if (event.key === 'Escape') close() }; window.addEventListener('keydown', keyHandler); return () => window.removeEventListener('keydown', keyHandler) }, [close])
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) close() }}><section className="modal" role="dialog" aria-modal="true" aria-label={title}><header><h2>{title}</h2><button className="icon-button" onClick={close} aria-label="Fechar"><X size={18} /></button></header>{children}</section></div>
}

function TextModal({ title, label, initial = '', close, submit }: { title: string; label: string; initial?: string; close: () => void; submit: (value: string) => void | Promise<void> }) {
  const [value, setValue] = useState(initial)
  return <Modal title={title} close={close}><form className="entry-form" onSubmit={(event) => { event.preventDefault(); void submit(value) }}><Field label={label}><input autoFocus value={value} onChange={(event) => setValue(event.target.value)} /></Field><div className="modal-actions"><button type="button" className="button button-outline" onClick={close}>Cancelar</button><button className="button button-dark" disabled={!value.trim()}>Continuar</button></div></form></Modal>
}

function InstallHelp({ close }: { close: () => void }) {
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent)
  return <Modal title="Instalar FinNote" close={close}><div className="install-help"><p>{ios ? 'No iPhone ou iPad:' : 'No navegador:'}</p><ol>{ios ? <><li>Toque em <strong>Compartilhar</strong> no Safari.</li><li>Escolha <strong>Adicionar à Tela de Início</strong>.</li></> : <><li>Abra o menu do navegador.</li><li>Escolha <strong>Instalar aplicativo</strong> ou <strong>Adicionar à tela inicial</strong>.</li></>}</ol><p>O FinNote precisa estar publicado em HTTPS para ser instalado.</p><button className="button button-dark" onClick={close}>Entendi</button></div></Modal>
}

function EmptyState({ icon, title, detail, action, onAction }: { icon: ReactNode; title: string; detail: string; action: string; onAction: () => void }) {
  return <div className="empty-state"><span className="empty-icon">{icon}</span><strong>{title}</strong><p>{detail}</p><button className="button button-outline button-sm" onClick={onAction}><Plus size={15} />{action}</button></div>
}
function Toast({ message, busy, close }: { message: string; busy: boolean; close: () => void }) { return <div className="toast" role="status"><span>{busy ? <span className="spinner" /> : <Check size={16} />}</span><p>{message}</p><button onClick={close} aria-label="Fechar aviso"><X size={15} /></button></div> }
