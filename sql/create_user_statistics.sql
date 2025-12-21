CREATE TABLE IF NOT EXISTS user_statistics (
    user_id UUID PRIMARY KEY,
    total_distance_meters DOUBLE PRECISION DEFAULT 0,
    total_runs INTEGER DEFAULT 0,
    total_moving_time_seconds BIGINT DEFAULT 0,
    first_run_date TIMESTAMP WITH TIME ZONE,
    last_run_date TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user
        FOREIGN KEY(user_id) 
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

