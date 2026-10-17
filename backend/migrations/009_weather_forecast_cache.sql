create table if not exists public.weather_forecast_cache (
  nx integer not null,
  ny integer not null,
  forecast_at timestamptz not null,
  issued_at timestamptz not null,
  condition_label text not null,
  temperature_c numeric(5, 2) not null,
  precipitation_probability numeric(5, 2)
    check (precipitation_probability between 0 and 100),
  precipitation_type integer,
  sky_code integer,
  wind_speed_mps numeric(6, 2) check (wind_speed_mps >= 0),
  fetched_at timestamptz not null,
  primary key (nx, ny, forecast_at)
);

create index if not exists weather_forecast_fetched_idx
  on public.weather_forecast_cache (fetched_at);

alter table public.weather_forecast_cache enable row level security;
revoke all on table public.weather_forecast_cache from public, anon, authenticated;
grant select, insert, update, delete on table public.weather_forecast_cache to service_role;

comment on table public.weather_forecast_cache is
  '기상청 단기예보를 5km 격자와 예보시각별로 캐시한 서버 전용 데이터.';
