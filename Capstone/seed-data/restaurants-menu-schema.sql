ALTER TABLE restaurants
ADD COLUMN IF NOT EXISTS pcmap_place_id varchar(100),
ADD COLUMN IF NOT EXISTS menu_json jsonb,
ADD COLUMN IF NOT EXISTS menu_updated_at timestamp(6);

CREATE UNIQUE INDEX IF NOT EXISTS uq_restaurants_pcmap_place_id
ON restaurants (pcmap_place_id)
WHERE pcmap_place_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_restaurants_menu_json_gin
ON restaurants
USING gin (menu_json);