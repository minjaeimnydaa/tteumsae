alter table public.places
  add column if not exists accessibility_synced_at timestamptz;

create or replace function public.save_place_accessibility(
  p_content_id text,
  p_accessibility_raw jsonb,
  p_accessibility_items jsonb,
  p_synced_at timestamptz
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  affected integer;
begin
  if p_content_id is null or btrim(p_content_id) = '' or p_synced_at is null then
    raise exception 'content id and synced timestamp are required';
  end if;
  if p_accessibility_items is null or jsonb_typeof(p_accessibility_items) <> 'array' then
    raise exception 'accessibility items must be a JSON array';
  end if;

  update public.places
  set enrichment_raw = coalesce(enrichment_raw, '{}'::jsonb) || jsonb_build_object(
        'accessibility', coalesce(p_accessibility_raw, 'null'::jsonb),
        'accessibilityItems', p_accessibility_items
      ),
      accessibility_synced_at = p_synced_at
  where content_id = p_content_id
    and is_active = true;

  get diagnostics affected = row_count;
  return affected = 1;
end;
$$;

revoke execute on function public.save_place_accessibility(text, jsonb, jsonb, timestamptz)
  from public, anon, authenticated;
grant execute on function public.save_place_accessibility(text, jsonb, jsonb, timestamptz)
  to service_role;

insert into public.sync_state (id)
values ('tour_accessibility')
on conflict (id) do nothing;

comment on column public.places.accessibility_synced_at is
  'KorWithService2 detailWithTour2 정상 응답(빈 응답 포함)을 저장한 시각.';
comment on function public.save_place_accessibility(text, jsonb, jsonb, timestamptz) is
  '무장애 원문과 공개용 정규화 항목을 기존 enrichment_raw를 잃지 않고 원자 병합한다.';
