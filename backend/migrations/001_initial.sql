create table if not exists public.places (
  content_id text primary key,
  source text not null default 'TOUR_API',
  name text not null,
  category text not null,
  content_type_id integer not null,
  area_code integer not null default 32,
  sigungu_code integer,
  latitude double precision not null,
  longitude double precision not null,
  address text,
  image_url text,
  tel text,
  default_stay_minutes integer not null check (default_stay_minutes between 5 and 360),
  is_active boolean not null default true,
  source_modified_at text,
  synced_at timestamptz not null default now(),
  raw jsonb not null default '{}'::jsonb
);

create index if not exists places_category_idx on public.places (category);
create index if not exists places_location_idx on public.places (latitude, longitude);
create index if not exists places_active_idx on public.places (is_active) where is_active = true;

create table if not exists public.sync_state (
  id text primary key,
  next_page integer not null default 1,
  total_count integer not null default 0,
  last_processed_page integer not null default 0,
  last_item_count integer not null default 0,
  last_error text,
  last_completed_at timestamptz,
  updated_at timestamptz not null default now()
);

insert into public.sync_state (id)
values ('tour_api')
on conflict (id) do nothing;

alter table public.places enable row level security;
alter table public.sync_state enable row level security;

comment on table public.places is
  'TourAPI에서 동기화한 강원도 장소. 앱은 Vercel API를 통해서만 접근한다.';
comment on table public.sync_state is
  '페이지 단위 TourAPI 동기화를 재개하기 위한 커서 상태.';

