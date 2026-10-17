alter table public.places
  add column if not exists info_synced_at timestamptz;

create index if not exists places_info_pending_idx
  on public.places (next_enrichment_at, content_id)
  where is_active = true
    and intro_synced_at is not null
    and info_synced_at is null;

comment on column public.places.info_synced_at is
  'TourAPI detailInfo2 반복 상세를 정상 응답(빈 응답 포함)으로 확인한 시각.';
