alter table public.places
  add column if not exists cat1 text,
  add column if not exists cat2 text,
  add column if not exists cat3 text,
  add column if not exists opening_hours text,
  add column if not exists closed_days text,
  add column if not exists event_start_date date,
  add column if not exists event_end_date date,
  add column if not exists overview text,
  add column if not exists homepage_url text,
  add column if not exists image_urls jsonb not null default '[]'::jsonb,
  add column if not exists tags text[] not null default '{}'::text[],
  add column if not exists enrichment_raw jsonb not null default '{}'::jsonb,
  add column if not exists intro_synced_at timestamptz,
  add column if not exists common_synced_at timestamptz,
  add column if not exists media_synced_at timestamptz,
  add column if not exists enrichment_attempts integer not null default 0,
  add column if not exists enrichment_last_error text,
  add column if not exists next_enrichment_at timestamptz;

alter table public.sync_state
  add column if not exists source_cursor text,
  add column if not exists cycle_started_at timestamptz;

-- 기존 상세 동기화가 raw._tteumsae에 저장한 값은 정규화 컬럼으로 한 번만 이전한다.
update public.places
set opening_hours = coalesce(opening_hours, raw #>> '{_tteumsae,openingHours}'),
    closed_days = coalesce(closed_days, raw #>> '{_tteumsae,closedDays}'),
    image_urls = case
      when jsonb_typeof(raw #> '{_tteumsae,imageUrls}') = 'array'
        then raw #> '{_tteumsae,imageUrls}'
      else image_urls
    end,
    tags = case
      when jsonb_typeof(raw #> '{_tteumsae,tags}') = 'array'
        then array(select jsonb_array_elements_text(raw #> '{_tteumsae,tags}'))
      else tags
    end,
    enrichment_raw = enrichment_raw || jsonb_build_object('legacy', raw -> '_tteumsae')
where raw ? '_tteumsae';

-- 카탈로그 원본은 유지하고 앱 전용 상세 멤버만 제거한다.
update public.places
set raw = raw - '_tteumsae'
where raw ? '_tteumsae';

create index if not exists places_intro_pending_idx
  on public.places (next_enrichment_at, content_id)
  where is_active = true and intro_synced_at is null;

create index if not exists places_active_festival_end_idx
  on public.places (event_end_date, content_id)
  where is_active = true and content_type_id = 15;

insert into public.sync_state (id)
values
  ('tour_catalog_delta'),
  ('tour_intro'),
  ('tour_presentation'),
  ('tour_festival')
on conflict (id) do nothing;

comment on column public.places.enrichment_raw is
  'TourAPI 상세 응답 원본. areaBasedList2 원본인 raw와 분리해 카탈로그 동기화 시 보존한다.';
