create schema if not exists private;

create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = pg_catalog.now();
  return new;
end;
$$;

revoke all on function private.set_updated_at() from public, anon, authenticated;

create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text check (
    display_name is null
    or pg_catalog.char_length(pg_catalog.btrim(display_name)) between 1 and 40
  ),
  avatar_url text check (
    avatar_url is null or pg_catalog.char_length(avatar_url) <= 2048
  ),
  age_group text check (
    age_group is null or age_group in (
      'UNDER_20','TWENTIES','THIRTIES','FORTIES',
      'FIFTIES','SIXTY_PLUS','PREFER_NOT_TO_SAY'
    )
  ),
  gender text check (
    gender is null or gender in (
      'FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY'
    )
  ),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now()
);

create table public.user_saved_places (
  user_id uuid not null references auth.users(id) on delete cascade,
  place_id text not null references public.places(content_id) on delete cascade,
  is_saved boolean not null,
  saved_at timestamptz,
  updated_at timestamptz not null default pg_catalog.now(),
  primary key (user_id, place_id)
);

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function private.set_updated_at();

create trigger saved_places_set_updated_at
before update on public.user_saved_places
for each row execute function private.set_updated_at();

alter table public.profiles enable row level security;
alter table public.user_saved_places enable row level security;

revoke all on public.profiles, public.user_saved_places from anon, authenticated;

grant select on public.profiles, public.user_saved_places to authenticated;
grant insert (user_id, display_name, avatar_url, age_group, gender)
  on public.profiles to authenticated;
grant update (display_name, avatar_url, age_group, gender)
  on public.profiles to authenticated;
grant insert (user_id, place_id, is_saved, saved_at)
  on public.user_saved_places to authenticated;
grant update (is_saved, saved_at)
  on public.user_saved_places to authenticated;

grant all on public.profiles, public.user_saved_places to service_role;

create policy profiles_select_own
on public.profiles
for select
to authenticated
using (auth.uid() is not null and auth.uid() = user_id);

create policy profiles_insert_own
on public.profiles
for insert
to authenticated
with check (auth.uid() is not null and auth.uid() = user_id);

create policy profiles_update_own
on public.profiles
for update
to authenticated
using (auth.uid() is not null and auth.uid() = user_id)
with check (auth.uid() is not null and auth.uid() = user_id);

create policy saved_select_own
on public.user_saved_places
for select
to authenticated
using (auth.uid() is not null and auth.uid() = user_id);

create policy saved_insert_own
on public.user_saved_places
for insert
to authenticated
with check (auth.uid() is not null and auth.uid() = user_id);

create policy saved_update_own
on public.user_saved_places
for update
to authenticated
using (auth.uid() is not null and auth.uid() = user_id)
with check (auth.uid() is not null and auth.uid() = user_id);
