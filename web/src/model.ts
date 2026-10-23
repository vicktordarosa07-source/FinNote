export type EntryType = 'expense' | 'income'
export type Visibility = 'private' | 'public'

export type Category = { id: string; name: string; subcategories: string[] }
export type Entry = {
  id: string
  title: string
  product: string
  category: string
  subcategory: string
  amountCents: number
  type: EntryType
  visibility: Visibility
  author: string
  ownerId: string
  createdAt: string
}
export type Profile = { name: string; phone: string; monthlyIncomeCents: number }
export type Session = { accessToken: string; refreshToken: string; userId: string }
export type Database = {
  categories: Category[]
  entries: Entry[]
  profile: Profile
  supabaseUrl: string
  supabaseKey: string
  session: Session | null
}

export const initialCategories: Category[] = [
  { id: 'fixed', name: 'Gastos fixos', subcategories: ['Aluguel', 'Água', 'Luz', 'Internet'] },
  { id: 'transport', name: 'Transporte', subcategories: ['Combustível', 'Aplicativo', 'Ônibus'] },
  { id: 'market', name: 'Mercado', subcategories: ['Alimentação', 'Limpeza', 'Higiene'] },
  { id: 'income', name: 'Renda', subcategories: ['Salário', 'Renda extra'] },
  { id: 'other', name: 'Outros', subcategories: [] },
]

export const initialDatabase: Database = {
  categories: initialCategories,
  entries: [],
  profile: { name: '', phone: '', monthlyIncomeCents: 0 },
  supabaseUrl: '',
  supabaseKey: '',
  session: null,
}

const storageKey = 'finnote.web.v1'
export function loadDatabase(): Database {
  try {
    const stored = localStorage.getItem(storageKey)
    if (!stored) return initialDatabase
    const parsed = JSON.parse(stored) as Partial<Database>
    return { ...initialDatabase, ...parsed, categories: parsed.categories?.length ? parsed.categories : initialCategories, entries: parsed.entries ?? [], profile: { ...initialDatabase.profile, ...parsed.profile } }
  } catch { return initialDatabase }
}
export function saveDatabase(database: Database) { localStorage.setItem(storageKey, JSON.stringify(database)) }

export const formatMoney = (cents: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100)
export function parseMoney(value: string) {
  const normalized = value.trim().replace(/R\$\s?/i, '').replace(/\s/g, '')
  if (!normalized) return 0
  const decimal = normalized.includes(',') ? normalized.replace(/\./g, '').replace(',', '.') : normalized
  const cents = Math.round(Number(decimal) * 100)
  return Number.isFinite(cents) ? Math.max(0, cents) : 0
}

export function suggestCategory(text: string, categories: Category[]) {
  const normalized = text.toLocaleLowerCase('pt-BR')
  const rules: Array<[string, string[]]> = [
    ['Gastos fixos', ['aluguel', 'condomínio', 'condominio', 'água', 'agua', 'luz', 'energia', 'internet', 'gás', 'gas']],
    ['Transporte', ['uber', 'ônibus', 'onibus', 'gasolina', 'combustível', 'combustivel']],
    ['Mercado', ['mercado', 'arroz', 'feijão', 'feijao', 'supermercado', 'fruta', 'comida', 'lanche', 'café', 'cafe']],
  ]
  const fixed = rules.find(([, words]) => words.some((word) => normalized.includes(word)))?.[0]
  if (fixed) return categories.find((category) => category.name.toLocaleLowerCase('pt-BR') === fixed.toLocaleLowerCase('pt-BR'))?.name ?? fixed
  return categories.find((category) => [category.name, ...category.subcategories].some((word) => normalized.includes(word.toLocaleLowerCase('pt-BR'))))?.name ?? 'Outros'
}

export function amountFromSpeech(text: string) {
  const match = text.match(/(?:r\$\s*)?(\d+(?:[.,]\d{1,2})?)/i)
  return match ? formatMoney(parseMoney(match[1])) : ''
}
