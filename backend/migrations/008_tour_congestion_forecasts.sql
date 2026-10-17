create table if not exists public.tour_congestion_forecasts (
  source_name text not null,
  normalized_name text not null,
  area_code text not null,
  area_name text,
  sigungu_code text not null,
  sigungu_name text,
  forecast_date date not null,
  concentration_rate numeric(6, 2) not null
    check (concentration_rate >= 0 and concentration_rate <= 100),
  level text not null check (level in ('LOW', 'MODERATE', 'HIGH')),
  content_id text references public.places(content_id) on delete set null,
  match_status text not null check (match_status in ('MATCHED', 'AMBIGUOUS', 'UNMATCHED')),
  fetched_at timestamptz not null,
  primary key (area_code, sigungu_code, source_name, forecast_date)
);

create index if not exists tour_congestion_content_date_idx
  on public.tour_congestion_forecasts (content_id, forecast_date)
  where content_id is not null;

create index if not exists tour_congestion_unmatched_idx
  on public.tour_congestion_forecasts (match_status, normalized_name)
  where match_status <> 'MATCHED';

alter table public.tour_congestion_forecasts enable row level security;
revoke all on table public.tour_congestion_forecasts from public, anon, authenticated;
grant select, insert, update, delete on table public.tour_congestion_forecasts to service_role;

comment on table public.tour_congestion_forecasts is
  '한국관광공사·KT 관광지별 향후 30일 집중률 예측. exact unique name만 places에 연결한다.';
comment on column public.tour_congestion_forecasts.concentration_rate is
  '해당 관광지의 과거 가장 붐빈 시기를 100으로 둔 상대 예측값이며 절대 방문자 수가 아니다.';
