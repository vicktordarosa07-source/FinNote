import type { Database, Entry, Session } from './model'

export class SupabaseApi {
  constructor(private readonly database: Database, private readonly onSession: (session: Session) => void) {}

  private async request(path: string, init: RequestInit = {}, token?: string, retry = true): Promise<string> {
    if (!this.database.supabaseUrl || !this.database.supabaseKey) throw new Error('Configure a URL e a chave pública do Supabase em Ajustes.')
    const response = await fetch(`${this.database.supabaseUrl.replace(/\/$/, '')}${path}`, {
      ...init,
      headers: {
        apikey: this.database.supabaseKey,
        Authorization: `Bearer ${token ?? this.database.session?.accessToken ?? this.database.supabaseKey}`,
        'Content-Type': 'application/json',
        ...(init.headers ?? {}),
      },
    })
    const text = await response.text()
    if (response.status === 401 && retry && token && this.database.session?.refreshToken) {
      const updated = await this.refresh()
      return this.request(path, init, updated.accessToken, false)
    }
    if (!response.ok) throw new Error(`Supabase ${response.status}: ${text.slice(0, 280)}`)
    return text
  }

  private sessionFrom(data: Record<string, unknown>): Session {
    const user = data.user as Record<string, unknown> | undefined
    const accessToken = data.access_token
    const refreshToken = data.refresh_token
    const userId = user?.id
    if (typeof accessToken !== 'string' || typeof refreshToken !== 'string' || typeof userId !== 'string') {
      throw new Error('O serviço de autenticação não retornou uma sessão válida.')
    }
    return { accessToken, refreshToken, userId }
  }

  private async persistProfile(session: Session, phone: string, name: string) {
    await this.request('/rest/v1/profiles?on_conflict=id', {
      method: 'POST', headers: { Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify({ id: session.userId, name: name.trim() || 'Pessoa', phone }),
    }, session.accessToken)
  }

  async signUpWithPassword(phone: string, password: string, name: string): Promise<Session | null> {
    const data = JSON.parse(await this.request('/auth/v1/signup', {
      method: 'POST', body: JSON.stringify({ phone, password, data: { name: name.trim() } }),
    }, this.database.supabaseKey, false)) as Record<string, unknown>
    if (!data.access_token) return null
    const session = this.sessionFrom(data)
    this.onSession(session)
    await this.persistProfile(session, phone, name)
    return session
  }

  async signInWithPassword(phone: string, password: string): Promise<{ session: Session; name: string }> {
    const data = JSON.parse(await this.request('/auth/v1/token?grant_type=password', {
      method: 'POST', body: JSON.stringify({ phone, password }),
    }, this.database.supabaseKey, false)) as Record<string, unknown>
    const session = this.sessionFrom(data)
    this.onSession(session)
    const rows = JSON.parse(await this.request(`/rest/v1/profiles?select=name&id=eq.${encodeURIComponent(session.userId)}&limit=1`, {}, session.accessToken)) as Array<{ name?: string }>
    const user = data.user as Record<string, unknown> | undefined
    const metadata = user?.user_metadata as Record<string, unknown> | undefined
    const name = rows[0]?.name || (typeof metadata?.name === 'string' ? metadata.name : '') || this.database.profile.name || 'Pessoa'
    await this.persistProfile(session, phone, name)
    return { session, name }
  }

  async verifyPhoneSignup(phone: string, code: string, name: string): Promise<Session> {
    const data = JSON.parse(await this.request('/auth/v1/verify', {
      method: 'POST', body: JSON.stringify({ phone, token: code, type: 'sms' }),
    }, this.database.supabaseKey, false)) as Record<string, unknown>
    const session = this.sessionFrom(data)
    this.onSession(session)
    await this.persistProfile(session, phone, name)
    return session
  }

  async resendPhoneSignupCode(phone: string) {
    await this.request('/auth/v1/resend', {
      method: 'POST', body: JSON.stringify({ phone, type: 'signup', channel: 'sms' }),
    }, this.database.supabaseKey, false)
  }

  private async refresh(): Promise<Session> {
    const refreshToken = this.database.session?.refreshToken
    if (!refreshToken) throw new Error('Sessão expirada. Entre novamente pelo telefone.')
    const data = JSON.parse(await this.request('/auth/v1/token?grant_type=refresh_token', { method: 'POST', body: JSON.stringify({ refresh_token: refreshToken }) }, this.database.supabaseKey, false))
    const session = { accessToken: data.access_token as string, refreshToken: data.refresh_token as string, userId: data.user.id as string }
    this.onSession(session)
    return session
  }

  private async publish(entry: Entry, session: Session) {
    await this.request('/rest/v1/entries', {
      method: 'POST', headers: { Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify({
        id: entry.id, user_id: session.userId, title: entry.title, category: entry.category,
        subcategory: entry.subcategory, product: entry.product, amount_cents: entry.amountCents,
        entry_type: entry.type, visibility: entry.visibility, author_name: entry.author,
        created_at: entry.createdAt,
      }),
    }, session.accessToken)
  }

  async sync(): Promise<Entry[]> {
    const session = this.database.session
    if (!session) throw new Error('Entre com seu telefone para sincronizar.')
    const pending = this.database.entries.filter((entry) => entry.visibility === 'public' && !entry.ownerId)
    for (const entry of pending) await this.publish(entry, session)
    const updated = new Map(pending.map((entry) => [entry.id, { ...entry, ownerId: session.userId }]))
    const rows = JSON.parse(await this.request('/rest/v1/entries?select=*&order=created_at.desc', {}, session.accessToken)) as Array<Record<string, unknown>>
    const remote: Entry[] = rows.map((row) => ({
      id: String(row.id), title: String(row.title), product: String(row.product ?? row.title), category: String(row.category ?? 'Outros'),
      subcategory: String(row.subcategory ?? ''), amountCents: Number(row.amount_cents ?? 0), type: row.entry_type === 'income' ? 'income' : 'expense',
      visibility: row.visibility === 'public' ? 'public' : 'private', author: String(row.author_name ?? 'Pessoa'), ownerId: String(row.user_id),
      createdAt: String(row.created_at ?? new Date().toISOString()),
    }))
    const merged = new Map<string, Entry>()
    for (const entry of this.database.entries) merged.set(entry.id, updated.get(entry.id) ?? entry)
    for (const entry of remote) merged.set(entry.id, entry)
    return [...merged.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }

  async createInvite(baseUrl: string) {
    const session = this.database.session
    if (!session) throw new Error('Entre com seu telefone para criar um convite.')
    const result = await this.request('/rest/v1/rpc/create_connection_invite', { method: 'POST', body: '{}' }, session.accessToken)
    const code = result.trim().replace(/^"|"$/g, '')
    if (!code) throw new Error('O servidor não retornou o código do convite.')
    const url = new URL(baseUrl, location.origin)
    url.searchParams.set('invite', code)
    return url.toString()
  }

  async acceptInvite(codeOrUrl: string) {
    const session = this.database.session
    if (!session) throw new Error('Entre com seu telefone antes de aceitar o convite.')
    let code = codeOrUrl.trim()
    try { code = new URL(code).searchParams.get('invite') ?? new URL(code).searchParams.get('code') ?? code } catch { /* the user pasted the raw UUID */ }
    await this.request('/rest/v1/rpc/accept_connection_invite', { method: 'POST', body: JSON.stringify({ invite_code: code }) }, session.accessToken)
    return this.sync()
  }
}
