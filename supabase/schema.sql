-- Mazos, tarjetas y repasos de MemoFlash. Cada fila pertenece a quien inició sesión.
-- Las imágenes se quedan en el teléfono: aquí solo va el texto y el historial de estudio.

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create or replace function private.asignar_usuario()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.user_id := auth.uid();
  return new;
end;
$$;

revoke all on function private.asignar_usuario() from public;
grant execute on function private.asignar_usuario() to authenticated;

-- El proyecto pide confirmar el correo y no hay forma de apagarlo desde aquí.
-- Esta función marca el correo como confirmado al crear la cuenta, así se puede entrar al momento.
create or replace function private.confirmar_correo()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.email_confirmed_at is null then
    new.email_confirmed_at := now();
  end if;
  return new;
end;
$$;

revoke all on function private.confirmar_correo() from public, anon, authenticated;
grant usage on schema private to supabase_auth_admin;
grant execute on function private.confirmar_correo() to supabase_auth_admin;

drop trigger if exists confirmar_correo on auth.users;
create trigger confirmar_correo
before insert on auth.users
for each row execute function private.confirmar_correo();

create table public.mazos (
  uuid uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  nombre text not null,
  descripcion text not null default '',
  en_reloj boolean not null default true,
  actualizado bigint not null,
  unique (user_id, uuid)
);

create table public.tarjetas (
  uuid uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  mazo_uuid uuid not null,
  frente text not null,
  reverso text not null,
  imagen_frente text,
  imagen_reverso text,
  nivel_dificultad double precision not null default 2.5,
  intervalo_dias double precision not null default 0,
  repeticiones integer not null default 0,
  proximo_repaso bigint not null default 0,
  actualizado bigint not null,
  unique (user_id, uuid),
  foreign key (user_id, mazo_uuid) references public.mazos (user_id, uuid) on delete cascade
);

create table public.historial_repasos (
  uuid uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  tarjeta_uuid uuid not null,
  fecha bigint not null,
  calificacion integer not null check (calificacion in (1, 2, 3)),
  intervalo_resultante double precision not null,
  origen text not null check (origen in ('TELEFONO', 'RELOJ')),
  foreign key (user_id, tarjeta_uuid) references public.tarjetas (user_id, uuid) on delete cascade
);

create table public.borrados (
  user_id uuid not null references auth.users (id) on delete cascade,
  tabla text not null check (tabla in ('mazos', 'tarjetas')),
  uuid uuid not null,
  fecha bigint not null,
  primary key (user_id, tabla, uuid)
);

create index mazos_user_id_idx on public.mazos (user_id);
create index tarjetas_user_id_idx on public.tarjetas (user_id);
create index tarjetas_mazo_uuid_idx on public.tarjetas (mazo_uuid);
create index historial_repasos_user_id_idx on public.historial_repasos (user_id);
create index historial_repasos_tarjeta_uuid_idx on public.historial_repasos (tarjeta_uuid);
create index tarjetas_user_mazo_idx on public.tarjetas (user_id, mazo_uuid);
create index historial_user_tarjeta_idx on public.historial_repasos (user_id, tarjeta_uuid);

create trigger mazos_usuario before insert or update on public.mazos
  for each row execute function private.asignar_usuario();
create trigger tarjetas_usuario before insert or update on public.tarjetas
  for each row execute function private.asignar_usuario();
create trigger historial_usuario before insert or update on public.historial_repasos
  for each row execute function private.asignar_usuario();
create trigger borrados_usuario before insert or update on public.borrados
  for each row execute function private.asignar_usuario();

alter table public.mazos enable row level security;
alter table public.tarjetas enable row level security;
alter table public.historial_repasos enable row level security;
alter table public.borrados enable row level security;

alter table public.mazos force row level security;
alter table public.tarjetas force row level security;
alter table public.historial_repasos force row level security;
alter table public.borrados force row level security;

create policy mazos_propios on public.mazos
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy tarjetas_propias on public.tarjetas
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy historial_propio on public.historial_repasos
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy borrados_propios on public.borrados
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

revoke all on public.mazos, public.tarjetas, public.historial_repasos, public.borrados from anon, public;
grant select, insert, update, delete on public.mazos, public.tarjetas, public.historial_repasos, public.borrados to authenticated, service_role;
