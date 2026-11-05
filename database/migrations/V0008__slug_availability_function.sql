CREATE FUNCTION business_slug_exists(p_slug text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, pg_temp
AS $$ SELECT EXISTS (SELECT 1 FROM businesses WHERE slug = p_slug) $$;

REVOKE ALL ON FUNCTION business_slug_exists(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION business_slug_exists(text) TO misva_app;
