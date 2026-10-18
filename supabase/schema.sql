-- Execute no SQL Editor do Supabase antes de ativar contas/compartilhamento.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,
  phone text not null unique,
  created_at timestamptz not null default now()
);

create table if not exists public.user_connections (
  user_a uuid not null references auth.users(id) on delete cascade,
  user_b uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);

create table if not exists public.connection_invites (
  code uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  redeemed_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '7 days')
);

create table if not exists public.entries (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  category text not null,
  subcategory text not null default '',
  product text not null,
  amount_cents bigint not null check (amount_cents > 0),
  entry_type text not null check (entry_type in ('income', 'expense')),
  visibility text not null check (visibility in ('public', 'private')),
  author_name text not null,
  created_at timestamptz not null default now()
);

create or replace function public.is_connected(other_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.user_connections c
    where (c.user_a = auth.uid() and c.user_b = other_user)
       or (c.user_b = auth.uid() and c.user_a = other_user)
  );
$$;

create or replace function public.create_connection_invite()
returns uuid language plpgsql security definer set search_path = public as $$
declare new_code uuid;
begin
  if auth.uid() is null then raise exception 'login required'; end if;
  insert into public.connection_invites(owner_id) values (auth.uid()) returning code into new_code;
  return new_code;
end;
$$;

create or replace function public.accept_connection_invite(invite_code uuid)
returns boolean language plpgsql security definer set search_path = public as $$
declare inviter uuid;
begin
  if auth.uid() is null then raise exception 'login required'; end if;
  select owner_id into inviter from public.connection_invites
    where code = invite_code and redeemed_by is null and expires_at > now() for update;
  if inviter is null then raise exception 'invite expired or already used'; end if;
  if inviter = auth.uid() then raise exception 'cannot connect to yourself'; end if;
  insert into public.user_connections(user_a, user_b) values (least(inviter, auth.uid()), greatest(inviter, auth.uid())) on conflict do nothing;
  update public.connection_invites set redeemed_by = auth.uid() where code = invite_code;
  return true;
end;
$$;

alter table public.profiles enable row level security;
alter table public.user_connections enable row level security;
alter table public.connection_invites enable row level security;
alter table public.entries enable row level security;

drop policy if exists profiles_read_self_or_connected on public.profiles;
create policy profiles_read_self_or_connected on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_connected(id));
drop policy if exists profiles_write_self on public.profiles;
create policy profiles_write_self on public.profiles for all to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists connections_read_member on public.user_connections;
create policy connections_read_member on public.user_connections for select to authenticated
  using (user_a = auth.uid() or user_b = auth.uid());

drop policy if exists entries_read_self_or_connected_public on public.entries;
create policy entries_read_self_or_connected_public on public.entries for select to authenticated
  using (user_id = auth.uid() or (visibility = 'public' and public.is_connected(user_id)));
drop policy if exists entries_insert_self on public.entries;
create policy entries_insert_self on public.entries for insert to authenticated
  with check (user_id = auth.uid());
drop policy if exists entries_update_self on public.entries;
create policy entries_update_self on public.entries for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists entries_delete_self on public.entries;
create policy entries_delete_self on public.entries for delete to authenticated
  using (user_id = auth.uid());

grant execute on function public.is_connected(uuid) to authenticated;
grant execute on function public.create_connection_invite() to authenticated;
grant execute on function public.accept_connection_invite(uuid) to authenticated;
revoke all on function public.create_connection_invite() from public, anon;
revoke all on function public.accept_connection_invite(uuid) from public, anon;
grant select, insert, update, delete on public.profiles, public.user_connections, public.connection_invites, public.entries to authenticated;
