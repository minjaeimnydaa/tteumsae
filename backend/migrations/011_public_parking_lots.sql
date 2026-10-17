create table if not exists public.public_parking_lots (
  source_id text primary key,
  parking_no text not null,
  name text not null,
  parking_type text,
  address text,
  capacity integer check (capacity is null or capacity >= 0),
  operation_days text,
  weekday_open text,
  weekday_close text,
  saturday_open text,
  saturday_close text,
  holiday_open text,
  holiday_close text,
  fee_info text,
  basic_minutes integer check (basic_minutes is null or basic_minutes >= 0),
  basic_fee_won integer check (basic_fee_won is null or basic_fee_won >= 0),
  additional_minutes integer check (additional_minutes is null or additional_minutes >= 0),
  additional_fee_won integer check (additional_fee_won is null or additional_fee_won >= 0),
  daily_fee_won integer check (daily_fee_won is null or daily_fee_won >= 0),
  payment_method text,
  notes text,
  operator_name text,
  phone text,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accessible_parking boolean,
  reference_date date,
  synced_at timestamptz not null,
  raw jsonb not null default '{}'::jsonb
);

create index if not exists public_parking_lots_latitude_longitude_idx
  on public.public_parking_lots (latitude, longitude);

alter table public.public_parking_lots enable row level security;
revoke all on table public.public_parking_lots from public, anon, authenticated;
grant select, insert, update, delete on table public.public_parking_lots to service_role;

insert into public.sync_state (id)
values ('public_parking')
on conflict (id) do nothing;

comment on table public.public_parking_lots is
  '전국주차장정보표준데이터 중 좌표가 확인된 강원 공영주차장의 서버 전용 캐시.';
