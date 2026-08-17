insert into public.sync_state (id)
values ('tour_details')
on conflict (id) do nothing;
