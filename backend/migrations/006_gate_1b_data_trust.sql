create table if not exists public.provider_usage_daily (
  usage_date date not null,
  provider text not null
    check (provider in ('KAKAO_MOBILITY', 'KAKAO_LOCAL', 'TOUR_API')),
  operation text not null
    check (length(operation) between 1 and 80),
  budget_limit integer
    check (budget_limit is null or budget_limit > 0),
  reserved_count integer not null default 0
    check (reserved_count >= 0),
  success_count integer not null default 0
    check (success_count >= 0),
  quota_error_count integer not null default 0
    check (quota_error_count >= 0),
  server_error_count integer not null default 0
    check (server_error_count >= 0),
  timeout_count integer not null default 0
    check (timeout_count >= 0),
  other_error_count integer not null default 0
    check (other_error_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (usage_date, provider, operation)
);

create table if not exists public.place_curations (
  content_id text primary key references public.places(content_id) on delete cascade,
  operating_info_status text not null
    check (operating_info_status in ('VERIFIED', 'UNKNOWN')),
  opening_hours text,
  closed_days text,
  last_admission text,
  admission_info_status text not null
    check (admission_info_status in ('VERIFIED', 'NOT_APPLICABLE', 'UNKNOWN')),
  parking_info text,
  parking_info_status text not null
    check (parking_info_status in ('VERIFIED', 'UNKNOWN')),
  source_urls jsonb not null
    check (jsonb_typeof(source_urls) = 'array'),
  source_checked_at timestamptz not null,
  reviewed_at timestamptz not null,
  review_note text,
  updated_at timestamptz not null default now(),
  check (operating_info_status <> 'VERIFIED' or opening_hours is not null),
  check (admission_info_status <> 'VERIFIED' or last_admission is not null),
  check (parking_info_status <> 'VERIFIED' or parking_info is not null),
  check (jsonb_array_length(source_urls) > 0)
);

alter table public.provider_usage_daily enable row level security;
alter table public.place_curations enable row level security;

create or replace view public.effective_places
with (security_invoker = true)
as
select
  p.*,
  case
    when c.content_id is null then p.opening_hours
    when c.operating_info_status = 'VERIFIED' then c.opening_hours
    else null
  end as effective_opening_hours,
  case
    when c.content_id is null then p.closed_days
    when c.operating_info_status = 'VERIFIED' then c.closed_days
    else null
  end as effective_closed_days,
  case
    when c.admission_info_status = 'VERIFIED' then c.last_admission
    else null
  end as effective_last_admission,
  case
    when c.parking_info_status = 'VERIFIED' then c.parking_info
    else null
  end as effective_parking_info,
  case when c.content_id is null then 'TOUR_API' else 'CURATION' end
    as data_provenance,
  c.operating_info_status,
  c.admission_info_status,
  c.parking_info_status,
  c.source_urls as curation_source_urls,
  c.source_checked_at,
  c.reviewed_at,
  c.review_note
from public.places p
left join public.place_curations c on c.content_id = p.content_id;

create or replace function public.reserve_provider_usage(
  p_provider text,
  p_operation text,
  p_usage_date date,
  p_budget_limit integer,
  p_units integer default 1
) returns table (
  allowed boolean,
  reserved_count integer,
  remaining_count integer
)
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_reserved integer;
  v_budget integer;
begin
  if p_provider not in ('KAKAO_MOBILITY', 'KAKAO_LOCAL', 'TOUR_API') then
    raise exception 'invalid provider';
  end if;
  if p_operation is null or length(p_operation) < 1 or length(p_operation) > 80 then
    raise exception 'invalid operation';
  end if;
  if p_usage_date is null then
    raise exception 'usage date is required';
  end if;
  if p_budget_limit is not null and p_budget_limit < 1 then
    raise exception 'budget limit must be positive';
  end if;
  if p_units < 1 or p_units > 100 then
    raise exception 'units must be between 1 and 100';
  end if;

  insert into public.provider_usage_daily (
    usage_date,
    provider,
    operation,
    budget_limit,
    reserved_count,
    updated_at
  ) values (
    p_usage_date,
    p_provider,
    p_operation,
    p_budget_limit,
    p_units,
    pg_catalog.now()
  )
  on conflict (usage_date, provider, operation) do update
  set reserved_count = public.provider_usage_daily.reserved_count + excluded.reserved_count,
      budget_limit = excluded.budget_limit,
      updated_at = pg_catalog.now()
  where excluded.budget_limit is null
     or public.provider_usage_daily.reserved_count + excluded.reserved_count
       <= excluded.budget_limit
  returning public.provider_usage_daily.reserved_count,
            public.provider_usage_daily.budget_limit
  into v_reserved, v_budget;

  if found then
    return query select
      true,
      v_reserved,
      case when v_budget is null then null else v_budget - v_reserved end;
    return;
  end if;

  select d.reserved_count, d.budget_limit
  into v_reserved, v_budget
  from public.provider_usage_daily d
  where d.usage_date = p_usage_date
    and d.provider = p_provider
    and d.operation = p_operation;

  return query select
    false,
    coalesce(v_reserved, 0),
    case when v_budget is null then null else greatest(v_budget - coalesce(v_reserved, 0), 0) end;
end;
$$;

create or replace function public.record_provider_usage_result(
  p_provider text,
  p_operation text,
  p_usage_date date,
  p_result_kind text,
  p_units integer default 1
) returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_affected integer;
begin
  if p_result_kind not in ('success', 'quota', 'server_error', 'timeout', 'other_error') then
    raise exception 'invalid provider result kind';
  end if;
  if p_units < 1 or p_units > 100 then
    raise exception 'units must be between 1 and 100';
  end if;

  update public.provider_usage_daily
  set success_count = success_count + case when p_result_kind = 'success' then p_units else 0 end,
      quota_error_count = quota_error_count + case when p_result_kind = 'quota' then p_units else 0 end,
      server_error_count = server_error_count + case when p_result_kind = 'server_error' then p_units else 0 end,
      timeout_count = timeout_count + case when p_result_kind = 'timeout' then p_units else 0 end,
      other_error_count = other_error_count + case when p_result_kind = 'other_error' then p_units else 0 end,
      updated_at = pg_catalog.now()
  where usage_date = p_usage_date
    and provider = p_provider
    and operation = p_operation;

  get diagnostics v_affected = row_count;
  if v_affected <> 1 then
    raise exception 'provider usage reservation not found';
  end if;
end;
$$;

create or replace function public.get_gate_1b_ops_status(
  p_usage_date date,
  p_sigungu_code integer default 1,
  p_curation_target integer default 100
) returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_usage jsonb;
  v_sync_jobs jsonb;
  v_active_places integer;
  v_operating_places integer;
  v_complete_festivals integer;
  v_incomplete_festivals integer;
  v_past_festivals integer;
  v_curated_places integer;
begin
  if p_usage_date is null then
    raise exception 'usage date is required';
  end if;
  if p_sigungu_code < 1 or p_sigungu_code > 18 then
    raise exception 'invalid sigungu code';
  end if;
  if p_curation_target < 1 then
    raise exception 'curation target must be positive';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'provider', d.provider,
        'operation', d.operation,
        'budgetLimit', d.budget_limit,
        'reservedCount', d.reserved_count,
        'successCount', d.success_count,
        'quotaErrorCount', d.quota_error_count,
        'serverErrorCount', d.server_error_count,
        'timeoutCount', d.timeout_count,
        'otherErrorCount', d.other_error_count,
        'remainingCount', case
          when d.budget_limit is null then null
          else greatest(d.budget_limit - d.reserved_count, 0)
        end,
        'warning', d.provider = 'KAKAO_MOBILITY'
          and d.operation = 'DIRECTIONS'
          and d.reserved_count >= 7000,
        'blocked', d.budget_limit is not null
          and d.reserved_count >= d.budget_limit
      ) order by d.provider, d.operation
    ),
    '[]'::jsonb
  ) into v_usage
  from public.provider_usage_daily d
  where d.usage_date = p_usage_date;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', s.id,
        'lastStartedAt', s.last_started_at,
        'lastFinishedAt', s.last_finished_at,
        'lastStatus', s.last_status,
        'lastDurationMs', s.last_duration_ms,
        'lastRunSummary', s.last_run_summary
      ) order by s.id
    ),
    '[]'::jsonb
  ) into v_sync_jobs
  from public.sync_state s;

  select count(*)::integer,
         count(*) filter (
           where nullif(trim(e.effective_opening_hours), '') is not null
         )::integer
  into v_active_places, v_operating_places
  from public.effective_places e
  where e.is_active = true;

  select
    count(*) filter (
      where e.event_start_date is not null
        and e.event_end_date is not null
        and e.event_start_date <= e.event_end_date
    )::integer,
    count(*) filter (
      where e.event_start_date is null
         or e.event_end_date is null
         or e.event_start_date > e.event_end_date
    )::integer,
    count(*) filter (
      where e.event_start_date is not null
        and e.event_end_date is not null
        and e.event_start_date <= e.event_end_date
        and e.event_end_date < p_usage_date
    )::integer
  into v_complete_festivals, v_incomplete_festivals, v_past_festivals
  from public.effective_places e
  where e.is_active = true
    and (e.content_type_id = 15 or e.category = 'FESTIVAL');

  select count(*)::integer
  into v_curated_places
  from public.place_curations c
  join public.places p on p.content_id = c.content_id
  where p.is_active = true
    and p.sigungu_code = p_sigungu_code;

  return jsonb_build_object(
    'usageDate', p_usage_date,
    'usage', v_usage,
    'syncJobs', v_sync_jobs,
    'dataQuality', jsonb_build_object(
      'activePlaces', v_active_places,
      'operatingHoursPlaces', v_operating_places,
      'operatingHoursRate', case
        when v_active_places = 0 then 0
        else round(v_operating_places::numeric / v_active_places, 4)
      end,
      'festivals', jsonb_build_object(
        'complete', v_complete_festivals,
        'incomplete', v_incomplete_festivals,
        'past', v_past_festivals
      ),
      'curation', jsonb_build_object(
        'sigunguCode', p_sigungu_code,
        'target', p_curation_target,
        'reviewed', v_curated_places,
        'remaining', greatest(p_curation_target - v_curated_places, 0)
      )
    )
  );
end;
$$;

revoke all on table public.provider_usage_daily from public, anon, authenticated;
revoke all on table public.place_curations from public, anon, authenticated;
revoke all on table public.effective_places from public, anon, authenticated;

grant select, insert, update on table public.provider_usage_daily to service_role;
grant select, insert, update, delete on table public.place_curations to service_role;
grant select on table public.effective_places to service_role;

revoke execute on function public.reserve_provider_usage(text, text, date, integer, integer)
  from public, anon, authenticated;
revoke execute on function public.record_provider_usage_result(text, text, date, text, integer)
  from public, anon, authenticated;
revoke execute on function public.get_gate_1b_ops_status(date, integer, integer)
  from public, anon, authenticated;

grant execute on function public.reserve_provider_usage(text, text, date, integer, integer)
  to service_role;
grant execute on function public.record_provider_usage_result(text, text, date, text, integer)
  to service_role;
grant execute on function public.get_gate_1b_ops_status(date, integer, integer)
  to service_role;

comment on table public.provider_usage_daily is
  'KST 날짜와 공급자 operation별 외부 API 호출 예약·결과 aggregate.';
comment on table public.place_curations is
  'TourAPI 원문과 분리된 출처 기반 수동 검수 overlay.';
