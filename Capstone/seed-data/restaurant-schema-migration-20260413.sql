BEGIN;

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS category_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS road_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS region_town_name VARCHAR(50);

ALTER TABLE restaurants
    ALTER COLUMN region_filter_names TYPE TEXT
    USING COALESCE(region_filter_names::text, '[]');

UPDATE restaurants r
SET category_name = src.category_name
FROM (
    SELECT restaurant_id, MIN(category_name) AS category_name
    FROM restaurant_categories
    GROUP BY restaurant_id
) src
WHERE r.id = src.restaurant_id
  AND COALESCE(TRIM(r.category_name), '') = '';

UPDATE restaurants
SET region_filter_names = array_to_json(array_remove(ARRAY[
        NULLIF(TRIM(region_city_name), ''),
        NULLIF(TRIM(region_district_name), ''),
        NULLIF(TRIM(region_county_name), ''),
        NULLIF(TRIM(region_town_name), ''),
        NULLIF(TRIM(region_name), '')
    ], NULL))::text;

UPDATE restaurant_tags
SET matched_menu_count = COALESCE(matched_menu_count, 0),
    is_primary = COALESCE(is_primary, FALSE);

WITH ranked_duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY restaurant_id, tag_id
               ORDER BY COALESCE(is_primary, FALSE) DESC,
                        COALESCE(matched_menu_count, 0) DESC,
                        updated_at DESC NULLS LAST,
                        id DESC
           ) AS rn
    FROM restaurant_tags
)
DELETE FROM restaurant_tags rt
USING ranked_duplicates dup
WHERE rt.id = dup.id
  AND dup.rn > 1;

ALTER TABLE restaurant_tags
    DROP CONSTRAINT IF EXISTS uk_restaurant_tag_source;

ALTER TABLE restaurant_tags
    ADD CONSTRAINT uk_restaurant_tag UNIQUE (restaurant_id, tag_id);

ALTER TABLE restaurant_tags
    DROP COLUMN IF EXISTS source_type,
    DROP COLUMN IF EXISTS source_text,
    DROP COLUMN IF EXISTS weight,
    DROP COLUMN IF EXISTS confidence;

ALTER TABLE restaurant_menu_items
    DROP COLUMN IF EXISTS source_menu_id;

ALTER TABLE tags
    DROP COLUMN IF EXISTS tag_type;

ALTER TABLE restaurants
    DROP COLUMN IF EXISTS menu_json;

DROP TABLE IF EXISTS restaurant_categories;

COMMIT;
