alter table public.provider_usage_daily
  drop constraint if exists provider_usage_daily_provider_check;

alter table public.provider_usage_daily
  add constraint provider_usage_daily_provider_check
  check (provider in (
    'KAKAO_MOBILITY',
    'KAKAO_LOCAL',
    'TOUR_API',
    'KMA',
    'PUBLIC_DATA'
  ));

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
  if p_provider not in (
    'KAKAO_MOBILITY',
    'KAKAO_LOCAL',
    'TOUR_API',
    'KMA',
    'PUBLIC_DATA'
  ) then
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
    case
      when v_budget is null then null
      else greatest(v_budget - coalesce(v_reserved, 0), 0)
    end;
end;
$$;

revoke execute on function public.reserve_provider_usage(text, text, date, integer, integer)
  from public, anon, authenticated;
grant execute on function public.reserve_provider_usage(text, text, date, integer, integer)
  to service_role;

comment on constraint provider_usage_daily_provider_check
  on public.provider_usage_daily is
  'Server-side provider usage ledger allowlist, including KMA weather and public parking APIs.';
