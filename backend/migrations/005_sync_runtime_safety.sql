alter table public.sync_state
  add column if not exists lease_token text,
  add column if not exists lease_expires_at timestamptz,
  add column if not exists last_started_at timestamptz,
  add column if not exists last_finished_at timestamptz,
  add column if not exists last_status text,
  add column if not exists last_duration_ms bigint,
  add column if not exists last_run_summary jsonb not null default '{}'::jsonb;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'sync_state_last_status_check'
      and conrelid = 'public.sync_state'::regclass
  ) then
    alter table public.sync_state add constraint sync_state_last_status_check
      check (last_status is null or last_status in ('completed', 'partial', 'failed'));
  end if;
end $$;

create or replace function public.claim_sync_job(
  p_id text,
  p_token text,
  p_now timestamptz,
  p_lease_seconds integer
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  claimed boolean;
begin
  if p_id is null or p_id = '' or p_token is null or p_token = '' then
    raise exception 'job id and token are required';
  end if;
  if p_lease_seconds < 1 or p_lease_seconds > 300 then
    raise exception 'lease seconds must be between 1 and 300';
  end if;

  insert into public.sync_state (
    id,
    lease_token,
    lease_expires_at,
    last_started_at,
    updated_at
  ) values (
    p_id,
    p_token,
    p_now + make_interval(secs => p_lease_seconds),
    p_now,
    p_now
  )
  on conflict (id) do update set
    lease_token = excluded.lease_token,
    lease_expires_at = excluded.lease_expires_at,
    last_started_at = excluded.last_started_at,
    updated_at = excluded.updated_at
  where public.sync_state.lease_token is null
     or public.sync_state.lease_expires_at is null
     or public.sync_state.lease_expires_at <= p_now
  returning true into claimed;

  return coalesce(claimed, false);
end;
$$;

create or replace function public.finish_sync_job(
  p_id text,
  p_token text,
  p_status text,
  p_summary jsonb,
  p_finished_at timestamptz
) returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  affected integer;
begin
  if p_status not in ('completed', 'partial', 'failed') then
    raise exception 'invalid sync status';
  end if;

  update public.sync_state
  set lease_token = null,
      lease_expires_at = null,
      last_finished_at = p_finished_at,
      last_status = p_status,
      last_duration_ms = greatest(
        0,
        floor(extract(epoch from (p_finished_at - last_started_at)) * 1000)::bigint
      ),
      last_run_summary = coalesce(p_summary, '{}'::jsonb),
      updated_at = p_finished_at
  where id = p_id
    and lease_token = p_token;

  get diagnostics affected = row_count;
  return affected = 1;
end;
$$;

revoke execute on function public.claim_sync_job(text, text, timestamptz, integer)
  from public, anon, authenticated;
revoke execute on function public.finish_sync_job(text, text, text, jsonb, timestamptz)
  from public, anon, authenticated;
grant execute on function public.claim_sync_job(text, text, timestamptz, integer)
  to service_role;
grant execute on function public.finish_sync_job(text, text, text, jsonb, timestamptz)
  to service_role;
