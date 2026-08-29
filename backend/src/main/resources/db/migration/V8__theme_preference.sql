-- Per-account color scheme, selectable from Profile. Everyone starts on the
-- original palette (MIDNIGHT/LIGHT) so nothing visually changes until a
-- member opts into something else.
alter table public.app_user
    add column theme varchar(16) not null default 'MIDNIGHT'
        check (theme in ('MIDNIGHT', 'OCEAN', 'EMBER', 'FOREST', 'SLATE'));

alter table public.app_user
    add column color_mode varchar(8) not null default 'LIGHT'
        check (color_mode in ('LIGHT', 'DARK'));
