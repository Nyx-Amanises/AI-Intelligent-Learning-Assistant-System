-- Only unambiguous official print covers for the existing full-book entries.
-- Verified 2026-11-07: OpenStax links to these Kendall Hunt products; title,
-- volume and print ISBN match OpenStax metadata. Image bytes are not mirrored.
-- Do not overwrite a cover that has already been assigned by an administrator.

-- Calculus Volume 1: paperback ISBN 9781506698069.
UPDATE study_resource
SET cover_url = 'https://he.kendallhunt.com/sites/default/files/product_images_small/1501419_0.jpg'
WHERE source_url = 'https://openstax.org/details/books/calculus-volume-1'
  AND resource_type = 'EXTERNAL'
  AND resource_kind = 'TEXTBOOK'
  AND license_code = 'LINK_ONLY'
  AND (cover_url IS NULL OR cover_url = '');

-- University Physics Volume 3: paperback ISBN 9781506698250.
UPDATE study_resource
SET cover_url = 'https://he.kendallhunt.com/sites/default/files/product_images_small/1501697.jpg'
WHERE source_url = 'https://openstax.org/details/books/university-physics-volume-3'
  AND resource_type = 'EXTERNAL'
  AND resource_kind = 'TEXTBOOK'
  AND license_code = 'LINK_ONLY'
  AND (cover_url IS NULL OR cover_url = '');

-- Writing Guide with Handbook: paperback ISBN 9781711494869.
UPDATE study_resource
SET cover_url = 'https://he.kendallhunt.com/sites/default/files/product_images_small/1501337.jpg'
WHERE source_url = 'https://openstax.org/details/books/writing-guide'
  AND resource_type = 'EXTERNAL'
  AND resource_kind = 'TEXTBOOK'
  AND license_code = 'LINK_ONLY'
  AND (cover_url IS NULL OR cover_url = '');
